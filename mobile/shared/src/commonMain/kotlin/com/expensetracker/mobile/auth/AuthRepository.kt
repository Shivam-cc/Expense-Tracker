package com.expensetracker.mobile.auth

import com.expensetracker.mobile.api.AccessTokenProvider
import com.expensetracker.mobile.api.ApiException
import com.expensetracker.mobile.api.AuthRequestDto
import com.expensetracker.mobile.api.AuthResponseDto
import com.expensetracker.mobile.api.ExpenseFlowApi
import com.expensetracker.mobile.api.RefreshRequestDto
import com.expensetracker.mobile.api.RegisterRequestDto
import com.expensetracker.mobile.api.SendOtpRequestDto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** Backend statuses for a refresh token that is invalid, expired, or revoked. The session must be cleared. */
private val INVALID_REFRESH_STATUSES = setOf(400, 401, 403)

class AuthRepository(
    private val api: ExpenseFlowApi,
    private val store: TokenStore,
    private val clock: Clock = Clock.System,
    private val refreshSkew: Duration = 60.seconds,
) : AccessTokenProvider {
    private val refreshMutex = Mutex()

    suspend fun sendOtp(name: String, email: String) {
        api.sendOtp(SendOtpRequestDto(name, email))
    }

    suspend fun register(name: String, email: String, password: String, otp: String): StoredSession =
        saveFrom(api.register(RegisterRequestDto(name, email, password, otp)))

    suspend fun login(email: String, password: String): StoredSession =
        saveFrom(api.login(AuthRequestDto(email, password)))

    fun currentSession(): StoredSession? = store.load()

    override suspend fun currentAccessToken(): String? {
        val session = store.load() ?: return null
        if (session.accessExpiresAt - clock.now() > refreshSkew) return session.accessToken
        return if (refreshIfStillStale(session.accessToken)) store.load()?.accessToken else null
    }

    override suspend fun refreshAfterUnauthorized(): Boolean {
        val staleToken = store.load()?.accessToken ?: return false
        return refreshIfStillStale(staleToken)
    }

    /**
     * Clears the local session even when the server call fails. Server-side revocation is best effort.
     */
    suspend fun logout() {
        try {
            store.load()?.let { api.logout(RefreshRequestDto(it.refreshToken)) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Ignored on purpose: the local session is cleared in finally.
        } finally {
            store.clear()
        }
    }

    /**
     * Refreshes unless another caller already rotated the token that [staleAccessToken] belongs to.
     * Returns false only when the refresh token was rejected, in which case the session has been cleared.
     */
    private suspend fun refreshIfStillStale(staleAccessToken: String): Boolean = refreshMutex.withLock {
        val current = store.load() ?: return@withLock false
        if (current.accessToken != staleAccessToken) return@withLock true
        try {
            saveFrom(api.refresh(RefreshRequestDto(current.refreshToken)))
            true
        } catch (e: ApiException) {
            if (e.status !in INVALID_REFRESH_STATUSES) throw e
            store.clear()
            false
        }
    }

    private fun saveFrom(response: AuthResponseDto): StoredSession {
        val session = StoredSession(
            accessToken = response.token,
            accessExpiresAt = clock.now() + response.expiresIn.seconds,
            refreshToken = response.refreshToken,
            email = response.email,
            name = response.name,
            currency = response.currency,
        )
        store.save(session)
        return session
    }
}
