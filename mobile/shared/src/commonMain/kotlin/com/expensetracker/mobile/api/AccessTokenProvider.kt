package com.expensetracker.mobile.api

/**
 * Supplies the bearer token for [KtorExpenseFlowApi].
 * Implemented by the auth layer, which owns refresh-token rotation.
 */
interface AccessTokenProvider {
    /** Current access token, or null when signed out. */
    suspend fun currentAccessToken(): String?

    /**
     * Called after a 401. Returns true if a fresh access token is now available and the request should be retried once.
     */
    suspend fun refreshAfterUnauthorized(): Boolean
}
