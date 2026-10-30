package com.expensetracker.mobile.auth

/** In-memory store for tests and desktop JVM. Nothing is persisted. */
actual class TokenStore actual constructor() {
    private var session: StoredSession? = null

    actual suspend fun load(): StoredSession? = session

    actual suspend fun save(session: StoredSession) {
        this.session = session
    }

    actual suspend fun clear() {
        session = null
    }
}
