package com.expensetracker.mobile.api

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val BASE_URL = "https://api.test"

private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

private val emptyPageJson =
    """{"items":[],"nextCursor":null,"hasMore":false,"serverTime":"2026-01-01T00:00:00Z"}"""

private val authJson =
    """{"token":"access-1","refreshToken":"refresh-1","expiresIn":900,"name":"Asha","email":"asha@example.com"}"""

private fun expenseJson(amountJson: String) =
    """{"id":1,"clientId":"c1","description":"Lunch","amount":$amountJson,"date":"2026-01-01","createdAt":"2026-01-01T00:00:00Z","updatedAt":"2026-01-01T00:00:00Z"}"""

private val categoryJson =
    """{"id":3,"clientId":"cat-1","name":"Food","updatedAt":"2026-01-01T00:00:00Z"}"""

private val writeRequest = ExpenseWriteDto(
    clientId = "c1",
    description = "Lunch",
    amount = "12.50",
    date = LocalDate.parse("2026-01-01"),
)

private class FakeTokens(var token: String?, private val refreshedToken: String? = null) : AccessTokenProvider {
    var refreshCalls = 0

    override suspend fun currentAccessToken(): String? = token

    override suspend fun refreshAfterUnauthorized(): Boolean {
        refreshCalls++
        val fresh = refreshedToken ?: return false
        token = fresh
        return true
    }
}

class KtorExpenseFlowApiTest {

    @Test
    fun bearerHeaderSentOnExpenseCallsAndOmittedOnAuthCalls() = runTest {
        val requests = mutableListOf<HttpRequestData>()
        val engine = MockEngine { request ->
            requests += request
            if (request.url.encodedPath.startsWith("/api/auth/")) {
                respond(authJson, HttpStatusCode.OK, jsonHeaders)
            } else {
                respond(emptyPageJson, HttpStatusCode.OK, jsonHeaders)
            }
        }
        val api = KtorExpenseFlowApi(BASE_URL, engine, FakeTokens("access-0"))

        api.getExpenseChanges(null, null, 10)
        api.login(AuthRequestDto("asha@example.com", "secret"))

        assertEquals("Bearer access-0", requests[0].headers[HttpHeaders.Authorization])
        assertNull(requests[1].headers[HttpHeaders.Authorization])
    }

    @Test
    fun noBearerHeaderWhenProviderIsAbsent() = runTest {
        val requests = mutableListOf<HttpRequestData>()
        val engine = MockEngine { request ->
            requests += request
            respond(emptyPageJson, HttpStatusCode.OK, jsonHeaders)
        }
        val api = KtorExpenseFlowApi(BASE_URL, engine)

        api.getCategoryChanges(null, null, 10)

        assertNull(requests.single().headers[HttpHeaders.Authorization])
    }

    @Test
    fun changesQueryContainsAllParams() = runTest {
        val requests = mutableListOf<HttpRequestData>()
        val engine = MockEngine { request ->
            requests += request
            respond(emptyPageJson, HttpStatusCode.OK, jsonHeaders)
        }
        val api = KtorExpenseFlowApi(BASE_URL, engine, FakeTokens("tok"))

        api.getExpenseChanges(Instant.parse("2026-01-01T00:00:00Z"), "cursor-1", 50)

        val url = requests.single().url
        assertEquals("/api/v1/expenses/changes", url.encodedPath)
        assertEquals("2026-01-01T00:00:00Z", url.parameters["updatedSince"])
        assertEquals("cursor-1", url.parameters["cursor"])
        assertEquals("50", url.parameters["limit"])
    }

    @Test
    fun changesQueryOmitsNullParams() = runTest {
        val requests = mutableListOf<HttpRequestData>()
        val engine = MockEngine { request ->
            requests += request
            respond(emptyPageJson, HttpStatusCode.OK, jsonHeaders)
        }
        val api = KtorExpenseFlowApi(BASE_URL, engine, FakeTokens("tok"))

        api.getCategoryChanges(null, null, 100)

        val url = requests.single().url
        assertEquals("/api/v1/categories/changes", url.encodedPath)
        assertNull(url.parameters["updatedSince"])
        assertNull(url.parameters["cursor"])
        assertEquals("100", url.parameters["limit"])
    }

    @Test
    fun unauthorizedRefreshesOnceAndRetriesOnce() = runTest {
        val requests = mutableListOf<HttpRequestData>()
        val engine = MockEngine { request ->
            requests += request
            if (request.headers[HttpHeaders.Authorization] == "Bearer fresh") {
                respond(emptyPageJson, HttpStatusCode.OK, jsonHeaders)
            } else {
                respond("""{"message":"Token expired"}""", HttpStatusCode.Unauthorized, jsonHeaders)
            }
        }
        val tokens = FakeTokens("stale", refreshedToken = "fresh")
        val api = KtorExpenseFlowApi(BASE_URL, engine, tokens)

        val page = api.getExpenseChanges(null, null, 10)

        assertTrue(page.items.isEmpty())
        assertEquals(1, tokens.refreshCalls)
        assertEquals(2, requests.size)
        assertEquals("Bearer stale", requests[0].headers[HttpHeaders.Authorization])
        assertEquals("Bearer fresh", requests[1].headers[HttpHeaders.Authorization])
    }

    @Test
    fun persistentUnauthorizedIsNotRetriedMoreThanOnce() = runTest {
        val requests = mutableListOf<HttpRequestData>()
        val engine = MockEngine { request ->
            requests += request
            respond("""{"message":"Still unauthorized"}""", HttpStatusCode.Unauthorized, jsonHeaders)
        }
        val tokens = FakeTokens("stale", refreshedToken = "fresh")
        val api = KtorExpenseFlowApi(BASE_URL, engine, tokens)

        val error = assertFailsWith<ApiException> { api.deleteExpense(5) }

        assertEquals(401, error.status)
        assertEquals(1, tokens.refreshCalls)
        assertEquals(2, requests.size)
    }

    @Test
    fun unauthorizedThrowsWhenRefreshFails() = runTest {
        val requests = mutableListOf<HttpRequestData>()
        val engine = MockEngine { request ->
            requests += request
            respond("""{"message":"Session expired"}""", HttpStatusCode.Unauthorized, jsonHeaders)
        }
        val tokens = FakeTokens("stale", refreshedToken = null)
        val api = KtorExpenseFlowApi(BASE_URL, engine, tokens)

        val error = assertFailsWith<ApiException> { api.getExpenseChanges(null, null, 10) }

        assertEquals(401, error.status)
        assertEquals("Session expired", error.message)
        assertEquals(1, tokens.refreshCalls)
        assertEquals(1, requests.size)
    }

    @Test
    fun errorMessageComesFromJsonMessageField() = runTest {
        val engine = MockEngine {
            respond(
                """{"timestamp":"2026-01-01T00:00:00Z","status":400,"error":"Bad Request","message":"Invalid email or password","path":"/api/auth/login"}""",
                HttpStatusCode.BadRequest,
                jsonHeaders,
            )
        }
        val api = KtorExpenseFlowApi(BASE_URL, engine)

        val error = assertFailsWith<ApiException> {
            api.login(AuthRequestDto("asha@example.com", "wrong"))
        }

        assertEquals(400, error.status)
        assertEquals("Invalid email or password", error.message)
    }

    @Test
    fun nonJsonErrorBodyFallsBackToGenericMessage() = runTest {
        val engine = MockEngine { respond("gateway exploded", HttpStatusCode.InternalServerError) }
        val api = KtorExpenseFlowApi(BASE_URL, engine)

        val error = assertFailsWith<ApiException> { api.sendOtp(SendOtpRequestDto("Asha", "asha@example.com")) }

        assertEquals(500, error.status)
        assertEquals("HTTP 500", error.message)
    }

    @Test
    fun deleteWith204Succeeds() = runTest {
        val requests = mutableListOf<HttpRequestData>()
        val engine = MockEngine { request ->
            requests += request
            respond("", HttpStatusCode.NoContent)
        }
        val api = KtorExpenseFlowApi(BASE_URL, engine, FakeTokens("tok"))

        api.deleteExpense(7)
        api.deleteCategory(9)

        assertEquals(HttpMethod.Delete, requests[0].method)
        assertEquals("/api/v1/expenses/7", requests[0].url.encodedPath)
        assertEquals(HttpMethod.Delete, requests[1].method)
        assertEquals("/api/v1/categories/9", requests[1].url.encodedPath)
    }

    @Test
    fun createExpenseAcceptsCreatedAndOk() = runTest {
        for (status in listOf(HttpStatusCode.Created, HttpStatusCode.OK)) {
            val engine = MockEngine { respond(expenseJson("12.50"), status, jsonHeaders) }
            val api = KtorExpenseFlowApi(BASE_URL, engine, FakeTokens("tok"))

            val created = api.createExpense(writeRequest)

            assertEquals(1L, created.id)
            assertEquals("c1", created.clientId)
            assertEquals("Lunch", created.description)
        }
    }

    @Test
    fun createCategoryAcceptsCreatedAndOk() = runTest {
        for (status in listOf(HttpStatusCode.Created, HttpStatusCode.OK)) {
            val engine = MockEngine { respond(categoryJson, status, jsonHeaders) }
            val api = KtorExpenseFlowApi(BASE_URL, engine, FakeTokens("tok"))

            val created = api.createCategory(CategoryWriteDto(clientId = "cat-1", name = "Food"))

            assertEquals(3L, created.id)
            assertEquals("Food", created.name)
        }
    }

    @Test
    fun amountDecodesFromJsonNumber() = runTest {
        val engine = MockEngine { respond(expenseJson("12.5"), HttpStatusCode.OK, jsonHeaders) }
        val api = KtorExpenseFlowApi(BASE_URL, engine, FakeTokens("tok"))

        val dto = api.updateExpense(1, writeRequest)

        assertEquals("12.5", dto.amount)
    }

    @Test
    fun amountDecodesFromJsonString() = runTest {
        val engine = MockEngine { respond(expenseJson("\"12.50\""), HttpStatusCode.OK, jsonHeaders) }
        val api = KtorExpenseFlowApi(BASE_URL, engine, FakeTokens("tok"))

        val dto = api.updateExpense(1, writeRequest)

        assertEquals("12.50", dto.amount)
    }

    @Test
    fun logoutSendsRefreshTokenWithoutBearer() = runTest {
        val requests = mutableListOf<HttpRequestData>()
        val engine = MockEngine { request ->
            requests += request
            respond("", HttpStatusCode.NoContent)
        }
        val api = KtorExpenseFlowApi(BASE_URL, engine, FakeTokens("tok"))

        api.logout(RefreshRequestDto("refresh-1"))

        assertEquals("/api/auth/logout", requests.single().url.encodedPath)
        assertNull(requests.single().headers[HttpHeaders.Authorization])
    }
}
