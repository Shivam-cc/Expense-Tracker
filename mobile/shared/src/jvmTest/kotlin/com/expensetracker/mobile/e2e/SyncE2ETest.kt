package com.expensetracker.mobile.e2e

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.expensetracker.mobile.api.ApiException
import com.expensetracker.mobile.api.ChangePage
import com.expensetracker.mobile.api.DeleteAccountRequestDto
import com.expensetracker.mobile.api.ERROR_ACCOUNT_DELETED
import com.expensetracker.mobile.api.ExpenseDto
import com.expensetracker.mobile.api.KtorExpenseFlowApi
import com.expensetracker.mobile.api.RefreshRequestDto
import com.expensetracker.mobile.auth.AuthRepository
import com.expensetracker.mobile.auth.SessionController
import com.expensetracker.mobile.auth.SignInResult
import com.expensetracker.mobile.auth.TokenStore
import com.expensetracker.mobile.data.CategoryRepository
import com.expensetracker.mobile.data.ExpenseRepository
import com.expensetracker.mobile.data.LocalDataWiper
import com.expensetracker.mobile.db.ExpenseFlowDatabase
import com.expensetracker.mobile.sync.OutboxEntity
import com.expensetracker.mobile.sync.OutboxOperation
import com.expensetracker.mobile.sync.SyncEngine
import com.expensetracker.mobile.sync.TestClock
import com.expensetracker.mobile.sync.enqueue
import com.expensetracker.mobile.util.Uuid7
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assume.assumeTrue
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * End-to-end sync test against a running ExpenseFlow backend (local profile, dev OTP enabled).
 *
 * Skipped (JUnit assumption) when the backend is not reachable. Override the URL with EXPENSEFLOW_API_URL.
 * See docs/api/mobile-sync-v1.md, section "Running the E2E test".
 */
class SyncE2ETest {

    private val baseUrl: String = System.getenv("EXPENSEFLOW_API_URL") ?: DEFAULT_BASE_URL

    @Test
    fun offlineEditsSyncAcrossTwoDevicesAndRotateTokens() = runBlocking {
        assumeBackendRunning(baseUrl)
        val email = uniqueEmail("sync")
        val a = Device(baseUrl)
        val b = Device(baseUrl)
        try {
            // Register device A (local profile returns the OTP).
            a.auth.register(NAME, email, PASSWORD, fetchDevOtp(baseUrl, email))

            // Create a category and three expenses while offline.
            val foodId = a.categories.add("E2E Travel")
            val coffeeId = a.expenses.add("Coffee", "0.01", "INR", DATE, categoryClientId = foodId)
            val rentId = a.expenses.add("Rent", "1250.50", "INR", DATE, categoryClientId = foodId)
            a.expenses.add("Big purchase", "9999999999.99", "INR", DATE, categoryClientId = foodId)
            assertEquals(4L, a.outboxCount(), "1 category + 3 expenses should be queued offline")

            a.engine.sync()
            assertEquals(0L, a.outboxCount(), "outbox should drain after sync")

            // Server state after the first sync.
            val serverAfterCreate = a.serverExpenses()
            assertEquals(1L, serverAfterCreate.byDescription("Coffee").amountMinor)
            assertEquals(125_050L, serverAfterCreate.byDescription("Rent").amountMinor)
            assertEquals(999_999_999_999L, serverAfterCreate.byDescription("Big purchase").amountMinor)
            assertTrue(serverAfterCreate.all { it.currency == "INR" })
            assertTrue(a.serverCategories().any { it.name == "E2E Travel" && it.deletedAt == null })

            // Edit one expense and delete another, offline, then sync.
            a.expenses.updateExpense(rentId, "Rent", "1300.75", "INR", DATE, categoryClientId = foodId)
            a.expenses.deleteExpense(coffeeId)
            assertEquals(2L, a.outboxCount())
            a.engine.sync()
            assertEquals(0L, a.outboxCount())

            val serverAfterEdit = a.serverExpenses()
            assertEquals(130_075L, serverAfterEdit.byDescription("Rent").amountMinor)
            assertNotNull(serverAfterEdit.byDescription("Coffee").deletedAt, "delete must be a tombstone")

            // A second device signs in and pulls everything.
            b.auth.login(email, PASSWORD)
            b.engine.sync()
            val bExpenses = b.expenses.observe().first()
            assertEquals(
                listOf("Big purchase" to "9999999999.99", "Rent" to "1300.75"),
                bExpenses.map { it.description to it.amount }.sortedBy { it.first },
                "device B should see exact amounts and not the deleted expense",
            )

            // Token refresh and rotation on device A after the access token expires.
            val oldRefresh = assertNotNull(a.store.load()).refreshToken
            val oldAccess = assertNotNull(a.store.load()).accessToken
            a.clock.advanceHours(2)
            a.expenses.add("After rotation", "5.00", "INR", DATE)
            a.engine.sync()

            val rotated = assertNotNull(a.store.load())
            assertNotEquals(oldAccess, rotated.accessToken, "access token should be refreshed")
            assertNotEquals(oldRefresh, rotated.refreshToken, "refresh token should rotate")
            assertEquals(500L, a.serverExpenses().byDescription("After rotation").amountMinor)

            // Replaying the rotated-out token must be rejected.
            val replay = assertFailsWith<ApiException> { a.authApi.refresh(RefreshRequestDto(oldRefresh)) }
            assertEquals(401, replay.status)

            // Reuse detection revokes the family, including the newest token.
            val newest = assertFailsWith<ApiException> { a.authApi.refresh(RefreshRequestDto(rotated.refreshToken)) }
            assertEquals(401, newest.status)
        } finally {
            a.close()
            b.close()
        }
    }

    @Test
    fun exactAmountsRoundTripAcrossCurrenciesAndDevices() = runBlocking {
        assumeBackendRunning(baseUrl)
        val email = uniqueEmail("amounts")
        val a = Device(baseUrl)
        val b = Device(baseUrl)
        try {
            a.auth.register(NAME, email, PASSWORD, fetchDevOtp(baseUrl, email))
            a.expenses.add("INR minimum", "0.01", "INR", DATE)
            a.expenses.add("INR cents", "1250.50", "INR", DATE)
            a.expenses.add("INR large", "9999999999.99", "INR", DATE)
            a.expenses.add("JPY whole", "1250", "JPY", DATE)
            a.expenses.add("KWD three digits", "1.234", "KWD", DATE)
            a.engine.sync()

            val server = a.serverExpenses()
            assertEquals(1L, server.byDescription("INR minimum").amountMinor)
            assertEquals(125_050L, server.byDescription("INR cents").amountMinor)
            assertEquals(999_999_999_999L, server.byDescription("INR large").amountMinor)
            assertEquals(1_250L, server.byDescription("JPY whole").amountMinor)
            assertEquals("JPY", server.byDescription("JPY whole").currency)
            assertEquals(1_234L, server.byDescription("KWD three digits").amountMinor)
            assertEquals("KWD", server.byDescription("KWD three digits").currency)

            b.auth.login(email, PASSWORD)
            b.engine.sync()
            val display = b.expenses.observe().first().associate { it.description to it.amount }
            assertEquals("0.01", display["INR minimum"])
            assertEquals("1250.50", display["INR cents"])
            assertEquals("9999999999.99", display["INR large"])
            assertEquals("1250", display["JPY whole"])
            assertEquals("1.234", display["KWD three digits"])
        } finally {
            a.close()
            b.close()
        }
    }

    @Test
    fun deletedAccountRefreshReportsAccountDeletedAndWipesLocalData() = runBlocking {
        assumeBackendRunning(baseUrl)
        val email = uniqueEmail("deleted")
        val a = Device(baseUrl)
        try {
            a.auth.register(NAME, email, PASSWORD, fetchDevOtp(baseUrl, email))
            val categoryId = a.categories.add("E2E Deleted")
            a.expenses.add("Synced", "10.00", "INR", DATE, categoryClientId = categoryId)
            a.engine.sync()
            assertEquals(0L, a.outboxCount())
            val refreshBeforeDelete = assertNotNull(a.store.load()).refreshToken

            a.api.deleteAccount(DeleteAccountRequestDto(PASSWORD))

            // Unsynced data that must be wiped together with the account.
            a.expenses.add("Offline after delete", "3.00", "INR", DATE)
            assertEquals(1L, a.outboxCount())

            val signal = assertFailsWith<ApiException> { a.authApi.refresh(RefreshRequestDto(refreshBeforeDelete)) }
            assertEquals(401, signal.status)
            assertEquals(ERROR_ACCOUNT_DELETED, signal.error)

            assertFalse(a.auth.refreshAfterUnauthorized())
            assertNull(a.store.load())
            assertEquals(0L, a.outboxCount())
            assertTrue(a.db.expenseFlowQueries.selectActiveExpenses().executeAsList().isEmpty())
            assertTrue(a.db.expenseFlowQueries.selectActiveCategories().executeAsList().isEmpty())
        } finally {
            a.close()
        }
    }

    @Test
    fun unvalidatedFoodCategoryIsAdoptedFromTheServerDefault() = runBlocking {
        assumeBackendRunning(baseUrl)
        val email = uniqueEmail("default-food")
        val a = Device(baseUrl)
        try {
            a.auth.register(NAME, email, PASSWORD, fetchDevOtp(baseUrl, email))

            // Write a "Food" row directly, bypassing CategoryRepository's reserved-name check,
            // to model a client that drifted from the backend's defaults.
            val localFoodId = Uuid7.generate()
            a.db.expenseFlowQueries.upsertCategory(
                localFoodId, null, "Food", null, null, 0L, a.clock.now().toEpochMilliseconds(), null,
            )
            a.db.enqueue(OutboxEntity.CATEGORY, localFoodId, OutboxOperation.CREATE)
            a.expenses.add("Lunch", "7.00", "INR", DATE, categoryClientId = localFoodId)

            a.engine.sync()
            assertEquals(0L, a.outboxCount(), "adoption must drain the outbox, not leave the duplicate queued")

            val seedFood = a.serverCategories().single { it.name == "Food" && it.global && it.deletedAt == null }
            val localFoods = a.db.expenseFlowQueries.selectActiveCategories().executeAsList()
                .filter { it.name == "Food" }
            val adopted = localFoods.single()
            assertEquals(seedFood.id, adopted.server_id, "local Food must point at the server's default")
            assertTrue(adopted.is_global != 0L, "adopted category must be the global default")

            val lunch = a.expenses.observe().first().single { it.description == "Lunch" }
            assertEquals(adopted.id, lunch.categoryClientId, "expense must be remapped to the adopted category")
            assertEquals(seedFood.id, lunch.categoryServerId)
            assertEquals(seedFood.id, a.serverExpenses().byDescription("Lunch").categoryId)
        } finally {
            a.close()
        }
    }

    @Test
    fun secondUserOnSameDeviceSeesNoPreviousDataAfterLogout() = runBlocking {
        assumeBackendRunning(baseUrl)
        val emailA = uniqueEmail("logout-a")
        val emailB = uniqueEmail("logout-b")
        val device = Device(baseUrl)
        try {
            device.session.register(NAME, emailA, PASSWORD, fetchDevOtp(baseUrl, emailA))
            val categoryId = device.categories.add("E2E Private")
            device.expenses.add("A private lunch", "11.00", "INR", DATE, categoryClientId = categoryId)
            device.engine.sync()
            assertEquals(0L, device.outboxCount())

            val result = device.session.register(NAME, emailB, PASSWORD, fetchDevOtp(baseUrl, emailB))
            assertEquals(emailB, assertIs<SignInResult.SignedIn>(result).session.email)

            assertTrue(
                device.db.expenseFlowQueries.selectActiveExpenses().executeAsList().isEmpty(),
                "second user must not see the first user's expenses on this device",
            )
            assertTrue(
                device.db.expenseFlowQueries.selectActiveCategories().executeAsList().none { it.name == "E2E Private" },
                "second user must not see the first user's categories on this device",
            )

            device.engine.sync()
            assertTrue(device.serverExpenses().isEmpty(), "second user's account must hold no expenses from the first")
            assertTrue(device.serverCategories().none { it.name == "E2E Private" })
        } finally {
            device.close()
        }
    }

    /** One simulated device: its own in-memory database, token store, and HTTP clients. */
    private class Device(baseUrl: String) {
        val clock = TestClock(Clock.System.now())
        val db = ExpenseFlowDatabase(
            JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { ExpenseFlowDatabase.Schema.create(it) },
        )
        val store = TokenStore()
        val authApi = KtorExpenseFlowApi(baseUrl, CIO.create())
        val auth: AuthRepository = AuthRepository(
            api = authApi,
            store = store,
            clock = clock,
            wipeLocalData = { LocalDataWiper(db).wipe() },
        )
        val api = KtorExpenseFlowApi(baseUrl, CIO.create(), tokens = auth)
        val expenses = ExpenseRepository(db, clock)
        val categories = CategoryRepository(db, clock)
        val engine = SyncEngine(api, db, clock)
        val session = SessionController(auth, engine, db)

        fun outboxCount(): Long = db.expenseFlowQueries.countOutbox().executeAsOne()

        /** Reads all expense rows (tombstones included) straight from the server. */
        suspend fun serverExpenses(): List<ExpenseDto> = pageAll { cursor ->
            api.getExpenseChanges(updatedSince = null, cursor = cursor, limit = 200)
        }

        suspend fun serverCategories() = pageAll { cursor ->
            api.getCategoryChanges(updatedSince = null, cursor = cursor, limit = 200)
        }

        fun close() {
            authApi.close()
            api.close()
        }
    }

    private companion object {
        const val DEFAULT_BASE_URL = "http://localhost:8080"
        const val NAME = "E2E Tester"
        const val PASSWORD = "Correct-Horse-42"
        val DATE: LocalDate = LocalDate(2026, 1, 5)

        fun uniqueEmail(tag: String): String = "e2e-$tag-${System.currentTimeMillis()}@example.com"
    }
}

private fun List<ExpenseDto>.byDescription(description: String): ExpenseDto =
    single { it.description == description }

private suspend fun <T : Any> pageAll(
    fetch: suspend (cursor: String?) -> ChangePage<T>,
): List<T> {
    val out = mutableListOf<T>()
    var cursor: String? = null
    do {
        val page = fetch(cursor)
        out += page.items
        cursor = page.nextCursor
    } while (page.hasMore)
    return out
}

/**
 * Local profile only: /api/auth/send-otp returns the code as devOtp when app.otp.dev-mode is enabled.
 */
private suspend fun fetchDevOtp(baseUrl: String, email: String): String {
    val client = HttpClient(CIO.create())
    try {
        val response = client.post("${baseUrl.trimEnd('/')}/api/auth/send-otp") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"E2E Tester","email":"$email"}""")
        }
        val body = response.bodyAsText()
        return Json.parseToJsonElement(body).jsonObject["devOtp"]?.jsonPrimitive?.content
            ?: error("send-otp returned ${response.status} without devOtp: $body. Start the backend with the local profile (app.otp.dev-mode=true).")
    } finally {
        client.close()
    }
}

private fun assumeBackendRunning(baseUrl: String) {
    val uri = URI(baseUrl)
    val host = uri.host ?: "localhost"
    val port = if (uri.port != -1) uri.port else 80
    val reachable = runCatching {
        Socket().use { it.connect(InetSocketAddress(host, port), 1_000) }
    }.isSuccess
    assumeTrue("ExpenseFlow backend not reachable at $baseUrl; skipping E2E test", reachable)
}
