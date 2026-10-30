package com.expensetracker.mobile.auth

import com.expensetracker.mobile.data.LocalDataWiper
import com.expensetracker.mobile.db.ExpenseFlowDatabase
import com.expensetracker.mobile.sync.SyncEngine
import kotlinx.coroutines.CancellationException

sealed interface SignInResult {
    data class SignedIn(val session: StoredSession) : SignInResult

    /**
     * The previous user has unsynced changes on this device. Nothing was changed. Ask the user to
     * confirm discarding [count] changes, then call again with discardUnsynced = [count].
     */
    data class UnsyncedRemaining(val count: Long) : SignInResult
}

sealed interface SignOutResult {
    data object SignedOut : SignOutResult

    /**
     * More unsynced changes remain than the caller agreed to discard. Nothing was changed. Ask the user to
     * confirm discarding [count] changes, then call again with discardUnsynced = [count]. This is also returned
     * when a write landed after an earlier confirmation, so the user is asked again.
     */
    data class UnsyncedRemaining(val count: Long) : SignOutResult
}

/**
 * Sign-in and sign-out flows that keep one user's data from reaching another. Sign-out attempts a sync,
 * reports any unsynced changes, and then wipes the database, outbox, and tokens. Signing in as a different
 * user runs the same sign-out first.
 */
class SessionController(
    private val auth: AuthRepository,
    private val engine: SyncEngine,
    db: ExpenseFlowDatabase,
) {
    private val queries = db.expenseFlowQueries
    private val wiper = LocalDataWiper(db)

    suspend fun signIn(email: String, password: String, discardUnsynced: Long? = null): SignInResult {
        prepareForUser(email, discardUnsynced)?.let { return SignInResult.UnsyncedRemaining(it) }
        val session = auth.login(email, password)
        setOwner(session.email)
        return SignInResult.SignedIn(session)
    }

    suspend fun register(
        name: String,
        email: String,
        password: String,
        otp: String,
        discardUnsynced: Long? = null,
    ): SignInResult {
        prepareForUser(email, discardUnsynced)?.let { return SignInResult.UnsyncedRemaining(it) }
        val session = auth.register(name, email, password, otp)
        setOwner(session.email)
        return SignInResult.SignedIn(session)
    }

    /**
     * Attempts a final sync, then signs out. [discardUnsynced] is the number of unsynced changes the user
     * agreed to discard (null means none). The count and the wipe happen under the sync lock and in one database
     * transaction, so if more changes are pending than were confirmed, nothing is wiped and the current count is
     * returned for the UI to ask again.
     */
    suspend fun signOut(discardUnsynced: Long? = null): SignOutResult {
        if (auth.currentSession() != null) {
            try {
                engine.sync()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Anything still queued is counted below and offered to the user.
            }
        }
        return engine.runExclusive {
            val pending = wiper.wipeUnlessUnsyncedExceeds(discardUnsynced ?: 0L)
            if (pending != null) {
                SignOutResult.UnsyncedRemaining(pending)
            } else {
                auth.logout()
                SignOutResult.SignedOut
            }
        }
    }

    /**
     * Clears the previous user's data when [email] is a different account. Returns the unsynced count
     * when that data cannot be discarded yet, or null when the device is free for [email].
     */
    private suspend fun prepareForUser(email: String, discardUnsynced: Long?): Long? {
        val owner = queries.selectSyncState(OWNER_KEY).executeAsOneOrNull() ?: return null
        if (owner.equals(email, ignoreCase = true)) return null
        return when (val result = signOut(discardUnsynced)) {
            SignOutResult.SignedOut -> null
            is SignOutResult.UnsyncedRemaining -> result.count
        }
    }

    private fun setOwner(email: String) {
        queries.upsertSyncState(OWNER_KEY, email)
    }

    private companion object {
        const val OWNER_KEY = "session.owner"
    }
}
