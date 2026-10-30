package com.expensetracker.mobile.auth

/**
 * Persists the single signed-in session. Platform actuals keep tokens encrypted at rest
 * (Android: DataStore with values encrypted by an Android Keystore AES-GCM key, iOS: Keychain). The JVM actual is in-memory.
 */
expect class TokenStore() {
    suspend fun load(): StoredSession?
    suspend fun save(session: StoredSession)
    suspend fun clear()
}
