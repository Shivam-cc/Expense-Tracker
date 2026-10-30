package com.expensetracker.mobile.api

import com.expensetracker.mobile.auth.AuthRepository
import com.expensetracker.mobile.auth.SessionController
import com.expensetracker.mobile.auth.SignOutResult
import com.expensetracker.mobile.auth.StoredSession
import com.expensetracker.mobile.auth.TokenStore
import com.expensetracker.mobile.data.LocalDataWiper
import com.expensetracker.mobile.sync.SyncEngine
import com.expensetracker.mobile.sync.SyncTestHarness
import io.ktor.client.engine.cio.CIO
import java.io.IOException
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import java.net.ServerSocket
import java.net.Socket
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Real sockets, so connect and request timeouts are exercised end to end rather than mocked. */
class ApiTimeoutTest {
    private val timeouts = ApiTimeouts(connectMillis = 300, requestMillis = 600, logoutMillis = 300)
    private val sockets = mutableListOf<Socket>()
    private val servers = mutableListOf<ServerSocket>()

    @AfterTest
    fun cleanUp() {
        sockets.forEach { runCatching { it.close() } }
        servers.forEach { runCatching { it.close() } }
    }

    /** Accepts connections and never answers. */
    private fun hangingServerUrl(): String {
        val server = ServerSocket(0).also { servers += it }
        Thread {
            while (!server.isClosed) {
                try {
                    sockets += server.accept()
                } catch (_: Exception) {
                    return@Thread
                }
            }
        }.apply { isDaemon = true }.start()
        return "http://127.0.0.1:${server.localPort}"
    }

    /** A port nothing listens on, so connecting is refused straight away. */
    private fun unreachableUrl(): String {
        val port = ServerSocket(0).use { it.localPort }
        return "http://127.0.0.1:$port"
    }

    private fun session() = StoredSession(
        accessToken = "access",
        accessExpiresAt = Instant.parse("2099-01-01T00:00:00Z"),
        refreshToken = "refresh",
        email = "asha@example.com",
        name = "Asha",
        currency = "INR",
    )

    private class Rig(url: String, timeouts: ApiTimeouts) {
        val h = SyncTestHarness()
        val store = TokenStore()
        val api = KtorExpenseFlowApi(url, CIO.create(), timeouts = timeouts)
        val auth = AuthRepository(api, store, h.clock, wipeLocalData = { LocalDataWiper(h.db).wipe() })
        val engine = SyncEngine(h.api, h.db, h.clock)
        val controller = SessionController(auth, engine, h.db)
    }

    private fun logoutFinishesQuicklyAndClearsTokens(url: String): Unit = runBlocking {
        val rig = Rig(url, timeouts)
        try {
            rig.store.save(session())

            val started = System.nanoTime()
            rig.auth.logout()
            val elapsedMillis = (System.nanoTime() - started) / 1_000_000

            assertNull(rig.store.load(), "tokens must be cleared even though the server never answered")
            assertTrue(elapsedMillis < 3_000, "logout took ${elapsedMillis}ms")
        } finally {
            rig.api.close()
        }
    }

    @Test
    fun logoutCompletesWhenTheServerHangs() {
        logoutFinishesQuicklyAndClearsTokens(hangingServerUrl())
    }

    @Test
    fun logoutCompletesWhenTheServerIsUnreachable() {
        logoutFinishesQuicklyAndClearsTokens(unreachableUrl())
    }

    @Test
    fun logoutTimeoutIsShorterThanTheGlobalRequestTimeout(): Unit = runBlocking {
        val api = KtorExpenseFlowApi(hangingServerUrl(), CIO.create(), timeouts = ApiTimeouts(10_000, 30_000, 300))
        try {
            val started = System.nanoTime()
            assertFailsWith<IOException> { api.logout(RefreshRequestDto("refresh")) }
            assertTrue((System.nanoTime() - started) / 1_000_000 < 3_000)
        } finally {
            api.close()
        }
    }

    @Test
    fun ordinaryRequestsUseTheGlobalRequestTimeout(): Unit = runBlocking {
        val api = KtorExpenseFlowApi(hangingServerUrl(), CIO.create(), timeouts = timeouts)
        try {
            assertFailsWith<IOException> { api.getExpenseChanges(null, null, 10) }
        } finally {
            api.close()
        }
    }

    @Test
    fun signOutWipesLocalDataWhenTheServerHangs(): Unit = runBlocking {
        val rig = Rig(hangingServerUrl(), timeouts)
        try {
            rig.store.save(session())
            rig.h.expenses.add("Coffee", "3.50", "INR", LocalDate(2024, 1, 5))

            // The final sync goes to the in-memory fake and succeeds; only logout talks to the hanging server.
            val result = rig.controller.signOut()

            assertEquals(SignOutResult.SignedOut, result)
            assertNull(rig.store.load())
            assertTrue(rig.h.db.expenseFlowQueries.selectActiveExpenses().executeAsList().isEmpty())
        } finally {
            rig.api.close()
        }
    }
}
