package com.expensetracker.mobile.auth

/** In-memory store for tests and desktop JVM. Nothing is persisted. */
actual class TokenStore actual constructor() {
    private var session: StoredSession? = null

    actual fun load(): StoredSession? = session

    actual fun save(session: StoredSession) {
        this.session = session
    }

    actual fun clear() {
        session = null
    }
}
