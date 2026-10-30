package com.expensetracker.mobile.auth

import com.expensetracker.mobile.api.ApiException
import com.expensetracker.mobile.api.AuthRequestDto
import com.expensetracker.mobile.api.AuthResponseDto
import com.expensetracker.mobile.api.CategoryDto
import com.expensetracker.mobile.api.CategoryWriteDto
import com.expensetracker.mobile.api.ChangePage
import com.expensetracker.mobile.api.ExpenseDto
import com.expensetracker.mobile.api.ExpenseFlowApi
import com.expensetracker.mobile.api.ExpenseWriteDto
import com.expensetracker.mobile.api.RefreshRequestDto
import com.expensetracker.mobile.api.RegisterRequestDto
import com.expensetracker.mobile.api.SendOtpRequestDto
import com.expensetracker.mobile.data.LocalDataWiper
import com.expensetracker.mobile.sync.SyncTestHarness
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

private val START: Instant = Instant.fromEpochSeconds(1_700_000_000)

private fun authResponse(token: String, refreshToken: String, expiresIn: Long = 900) = AuthResponseDto(
    token = token,
    refreshToken = refreshToken,
    expiresIn = expiresIn,
    name = "Asha",
    email = "asha@example.com",
    currency = "INR",
)

private class FakeClock(var current: Instant) : Clock {
    override fun now(): Instant = current
}

private class FakeAuthApi : ExpenseFlowApi {
    var loginResponse: AuthResponseDto? = null
    var refreshHandler: suspend (String) -> AuthResponseDto = { error("refresh not stubbed") }
    var logoutHandler: suspend (String) -> Unit = {}
    var refreshCalls = 0
    val refreshedWith = mutableListOf<String>()
    var logoutCalls = 0

    override suspend fun sendOtp(request: SendOtpRequestDto) {}

    override suspend fun register(request: RegisterRequestDto): AuthResponseDto =
        loginResponse ?: error("login not stubbed")

    override suspend fun login(request: AuthRequestDto): AuthResponseDto =
        loginResponse ?: error("login not stubbed")

    override suspend fun refresh(request: RefreshRequestDto): AuthResponseDto {
        refreshCalls++
        refreshedWith += request.refreshToken
        return refreshHandler(request.refreshToken)
    }

    override suspend fun logout(request: RefreshRequestDto) {
        logoutCalls++
        logoutHandler(request.refreshToken)
    }

    override suspend fun getExpenseChanges(updatedSince: Instant?, cursor: String?, limit: Int): ChangePage<ExpenseDto> {
        throw NotImplementedError("not used by auth tests")
    }

    override suspend fun createExpense(request: ExpenseWriteDto): ExpenseDto {
        throw NotImplementedError("not used by auth tests")
    }

    override suspend fun updateExpense(id: Long, request: ExpenseWriteDto): ExpenseDto {
        throw NotImplementedError("not used by auth tests")
    }

    override suspend fun deleteExpense(id: Long) {
        throw NotImplementedError("not used by auth tests")
    }

    override suspend fun getCategoryChanges(updatedSince: Instant?, cursor: String?, limit: Int): ChangePage<CategoryDto> {
        throw NotImplementedError("not used by auth tests")
    }

    override suspend fun createCategory(request: CategoryWriteDto): CategoryDto {
        throw NotImplementedError("not used by auth tests")
    }

    override suspend fun updateCategory(id: Long, request: CategoryWriteDto): CategoryDto {
        throw NotImplementedError("not used by auth tests")
    }

    override suspend fun deleteCategory(id: Long) {
        throw NotImplementedError("not used by auth tests")
    }
}

private class Fixture(private val onWipe: () -> Unit = {}) {
    val api = FakeAuthApi()
    val store = TokenStore()
    val clock = FakeClock(START)
    val repo = AuthRepository(api, store, clock, wipeLocalData = onWipe, refreshSkew = 60.seconds)

    suspend fun signIn(access: String = "access-1", refresh: String = "refresh-1") {
        api.loginResponse = authResponse(access, refresh)
        repo.login("asha@example.com", "secret")
    }
}

class AuthRepositoryTest {
    @Test
    fun loginSavesSession() = runTest {
        val f = Fixture()
        f.api.loginResponse = authResponse("access-1", "refresh-1")

        val session = f.repo.login("asha@example.com", "secret")

        assertEquals(session, f.store.load())
        assertEquals("access-1", session.accessToken)
        assertEquals("refresh-1", session.refreshToken)
        assertEquals(START + 900.seconds, session.accessExpiresAt)
        assertEquals("asha@example.com", session.email)
        assertEquals("INR", session.currency)
    }

    @Test
    fun currentAccessTokenReturnsNullWhenSignedOut() = runTest {
        val f = Fixture()
        assertNull(f.repo.currentAccessToken())
        assertNull(f.repo.currentSession())
    }

    @Test
    fun currentAccessTokenReturnsStoredTokenFarFromExpiry() = runTest {
        val f = Fixture()
        f.signIn()

        assertEquals("access-1", f.repo.currentAccessToken())
        assertEquals(0, f.api.refreshCalls)
    }

    @Test
    fun currentAccessTokenRefreshesInsideSkewWindow() = runTest {
        val f = Fixture()
        f.signIn()
        f.api.refreshHandler = { authResponse("access-2", "refresh-2") }

        f.clock.current = START + 850.seconds // 50s left, inside the 60s skew
        assertEquals("access-2", f.repo.currentAccessToken())
        assertEquals(1, f.api.refreshCalls)
        assertEquals("refresh-2", f.store.load()?.refreshToken)
    }

    @Test
    fun refreshAfterUnauthorizedRotatesRefreshToken() = runTest {
        val f = Fixture()
        f.signIn()
        val issued = listOf(authResponse("access-2", "refresh-2"), authResponse("access-3", "refresh-3"))
        var next = 0
        f.api.refreshHandler = { issued[next++] }

        assertTrue(f.repo.refreshAfterUnauthorized())
        assertEquals("refresh-2", f.store.load()?.refreshToken)

        assertTrue(f.repo.refreshAfterUnauthorized())
        assertEquals(listOf("refresh-1", "refresh-2"), f.api.refreshedWith)
        assertEquals("refresh-3", f.store.load()?.refreshToken)
    }

    @Test
    fun refreshAfterUnauthorizedWithoutSessionReturnsFalse() = runTest {
        val f = Fixture()
        assertFalse(f.repo.refreshAfterUnauthorized())
        assertEquals(0, f.api.refreshCalls)
    }

    @Test
    fun rejectedRefreshClearsSessionAndReturnsFalse() = runTest {
        for (status in listOf(400, 401, 403, 404)) {
            var wipes = 0
            val f = Fixture(onWipe = { wipes++ })
            f.signIn()
            f.api.refreshHandler = { throw ApiException(status, "Refresh token revoked") }

            assertFalse(f.repo.refreshAfterUnauthorized(), "status $status")
            assertNull(f.store.load(), "status $status")
            assertEquals(1, wipes, "status $status")
        }
    }

    @Test
    fun deletedAccountRefreshWipesLocalDataOutboxAndTokens() = runTest {
        val h = SyncTestHarness()
        val f = Fixture(onWipe = { LocalDataWiper(h.db).wipe() })
        val categoryId = h.categories.add("Food")
        h.expenses.add("Dinner", "30.00", "INR", LocalDate.parse("2026-01-01"), categoryClientId = categoryId)
        h.db.expenseFlowQueries.upsertSyncState("expense.cursor", "2")
        f.signIn()
        assertTrue(h.outboxCount() > 0)
        f.api.refreshHandler = { throw ApiException(404, "Account not found") }

        assertFalse(f.repo.refreshAfterUnauthorized())

        assertNull(f.store.load())
        assertEquals(0L, h.outboxCount())
        assertTrue(h.db.expenseFlowQueries.selectActiveExpenses().executeAsList().isEmpty())
        assertTrue(h.db.expenseFlowQueries.selectActiveCategories().executeAsList().isEmpty())
        assertNull(h.db.expenseFlowQueries.selectSyncState("expense.cursor").executeAsOneOrNull())
    }

    @Test
    fun networkErrorOnRefreshKeepsSessionAndRethrows() = runTest {
        val f = Fixture()
        f.signIn()
        f.api.refreshHandler = { throw IOException("offline") }

        assertFailsWith<IOException> { f.repo.refreshAfterUnauthorized() }
        assertEquals("refresh-1", f.store.load()?.refreshToken)
    }

    @Test
    fun serverErrorOnRefreshKeepsSessionAndRethrows() = runTest {
        val f = Fixture()
        f.signIn()
        f.api.refreshHandler = { throw ApiException(500, "boom") }

        assertFailsWith<ApiException> { f.repo.refreshAfterUnauthorized() }
        assertEquals("refresh-1", f.store.load()?.refreshToken)
    }

    @Test
    fun concurrentRefreshesWithSameStaleTokenCallRefreshOnce() = runTest {
        val f = Fixture()
        f.signIn()
        val gate = CompletableDeferred<Unit>()
        f.api.refreshHandler = {
            gate.await()
            authResponse("access-2", "refresh-2")
        }

        // UNDISPATCHED: each caller captures the stale token before the first refresh completes.
        val first = async(start = CoroutineStart.UNDISPATCHED) { f.repo.refreshAfterUnauthorized() }
        val second = async(start = CoroutineStart.UNDISPATCHED) { f.repo.refreshAfterUnauthorized() }
        gate.complete(Unit)

        assertEquals(listOf(true, true), awaitAll(first, second))
        assertEquals(1, f.api.refreshCalls)
        assertEquals("access-2", f.store.load()?.accessToken)
    }

    @Test
    fun logoutClearsStoreEvenWhenServerLogoutFails() = runTest {
        val failures: List<Throwable> = listOf(IOException("offline"), ApiException(500, "server error"))
        for (failure in failures) {
            val f = Fixture()
            f.signIn()
            f.api.logoutHandler = { throw failure }

            f.repo.logout()

            assertNull(f.store.load(), failure.toString())
            assertEquals(1, f.api.logoutCalls)
        }
    }

    @Test
    fun logoutCallsServerWithRefreshTokenAndClearsStore() = runTest {
        val f = Fixture()
        f.signIn()

        f.repo.logout()

        assertEquals(1, f.api.logoutCalls)
        assertNull(f.store.load())
    }
}
