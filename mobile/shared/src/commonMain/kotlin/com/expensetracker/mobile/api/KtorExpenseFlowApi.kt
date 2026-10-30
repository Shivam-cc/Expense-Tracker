package com.expensetracker.mobile.api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private val ErrorJson = Json { ignoreUnknownKeys = true }

class KtorExpenseFlowApi(
    private val baseUrl: String,
    engine: HttpClientEngine,
    private val tokens: AccessTokenProvider? = null,
) : ExpenseFlowApi {

    private val client: HttpClient = HttpClient(engine) {
        expectSuccess = false
        install(ContentNegotiation) {
            json(
                Json {
                    ignoreUnknownKeys = true
                    explicitNulls = false
                    encodeDefaults = true
                }
            )
        }
    }

    fun close() {
        client.close()
    }

    override suspend fun sendOtp(request: SendOtpRequestDto) {
        unauthenticated(HttpMethod.Post, "/api/auth/send-otp") { jsonBody(request) }
    }

    override suspend fun register(request: RegisterRequestDto): AuthResponseDto =
        unauthenticated(HttpMethod.Post, "/api/auth/register") { jsonBody(request) }.body<AuthResponseDto>()

    override suspend fun login(request: AuthRequestDto): AuthResponseDto =
        unauthenticated(HttpMethod.Post, "/api/auth/login") { jsonBody(request) }.body<AuthResponseDto>()

    override suspend fun refresh(request: RefreshRequestDto): AuthResponseDto =
        unauthenticated(HttpMethod.Post, "/api/auth/refresh") { jsonBody(request) }.body<AuthResponseDto>()

    override suspend fun logout(request: RefreshRequestDto) {
        unauthenticated(HttpMethod.Post, "/api/auth/logout") { jsonBody(request) }
    }

    override suspend fun deleteAccount(request: DeleteAccountRequestDto) {
        authorized(HttpMethod.Delete, "/api/users/me") { jsonBody(request) }
    }

    override suspend fun getExpenseChanges(updatedSince: Instant?, cursor: String?, limit: Int): ChangePage<ExpenseDto> =
        authorized(HttpMethod.Get, "/api/v1/expenses/changes") { changeParams(updatedSince, cursor, limit) }
            .body<ChangePage<ExpenseDto>>()

    override suspend fun createExpense(request: ExpenseWriteDto): ExpenseDto =
        authorized(HttpMethod.Post, "/api/v1/expenses") { jsonBody(request) }.body<ExpenseDto>()

    override suspend fun updateExpense(id: Long, request: ExpenseWriteDto): ExpenseDto =
        authorized(HttpMethod.Put, "/api/v1/expenses/$id") { jsonBody(request) }.body<ExpenseDto>()

    override suspend fun deleteExpense(id: Long) {
        authorized(HttpMethod.Delete, "/api/v1/expenses/$id")
    }

    override suspend fun getCategoryChanges(updatedSince: Instant?, cursor: String?, limit: Int): ChangePage<CategoryDto> =
        authorized(HttpMethod.Get, "/api/v1/categories/changes") { changeParams(updatedSince, cursor, limit) }
            .body<ChangePage<CategoryDto>>()

    override suspend fun createCategory(request: CategoryWriteDto): CategoryDto =
        authorized(HttpMethod.Post, "/api/v1/categories") { jsonBody(request) }.body<CategoryDto>()

    override suspend fun updateCategory(id: Long, request: CategoryWriteDto): CategoryDto =
        authorized(HttpMethod.Put, "/api/v1/categories/$id") { jsonBody(request) }.body<CategoryDto>()

    override suspend fun deleteCategory(id: Long) {
        authorized(HttpMethod.Delete, "/api/v1/categories/$id")
    }

    /** Sends with the current bearer token. On a 401 it refreshes once and retries once. */
    private suspend fun authorized(
        verb: HttpMethod,
        path: String,
        configure: HttpRequestBuilder.() -> Unit = {},
    ): HttpResponse {
        val provider = tokens ?: return ensureSuccess(send(verb, path, null, configure))
        val first = send(verb, path, provider.currentAccessToken(), configure)
        if (first.status.value != 401 || !provider.refreshAfterUnauthorized()) {
            return ensureSuccess(first)
        }
        return ensureSuccess(send(verb, path, provider.currentAccessToken(), configure))
    }

    /** Auth endpoints: no bearer token and no retry. */
    private suspend fun unauthenticated(
        verb: HttpMethod,
        path: String,
        configure: HttpRequestBuilder.() -> Unit = {},
    ): HttpResponse = ensureSuccess(send(verb, path, null, configure))

    private suspend fun send(
        verb: HttpMethod,
        path: String,
        token: String?,
        configure: HttpRequestBuilder.() -> Unit,
    ): HttpResponse = client.request("${baseUrl.trimEnd('/')}$path") {
        method = verb
        if (token != null) header(HttpHeaders.Authorization, "Bearer $token")
        configure()
    }

    private suspend fun ensureSuccess(response: HttpResponse): HttpResponse {
        val status = response.status.value
        if (status in 200..299) return response
        val payload = runCatching { Json.parseToJsonElement(response.bodyAsText()).jsonObject }.getOrNull()
        val message = payload?.stringField("message")?.takeIf { it.isNotBlank() } ?: "HTTP $status"
        val existingCategory = payload?.get("existingCategory")?.let { element ->
            runCatching { ErrorJson.decodeFromJsonElement<CategoryDto>(element) }.getOrNull()
        }
        throw ApiException(status, message, payload?.stringField("error"), existingCategory)
    }

    private fun JsonObject.stringField(name: String): String? = this[name]?.jsonPrimitive?.contentOrNull

    private fun HttpRequestBuilder.changeParams(updatedSince: Instant?, cursor: String?, limit: Int) {
        updatedSince?.let { parameter("updatedSince", it.toString()) }
        cursor?.let { parameter("cursor", it) }
        parameter("limit", limit)
    }

    private inline fun <reified T : Any> HttpRequestBuilder.jsonBody(body: T) {
        contentType(ContentType.Application.Json)
        setBody(body)
    }
}
