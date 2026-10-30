package com.expensetracker.mobile.auth

import com.expensetracker.mobile.api.ApiException
import com.expensetracker.mobile.api.AuthRequestDto
import com.expensetracker.mobile.api.AuthResponseDto
import com.expensetracker.mobile.api.ExpenseFlowApi
import com.expensetracker.mobile.api.RefreshRequestDto
import com.expensetracker.mobile.api.RegisterRequestDto
import com.expensetracker.mobile.data.LocalDataWiper
import com.expensetracker.mobile.sync.FakeSyncApi
import com.expensetracker.mobile.sync.SyncTestHarness
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val DAY = LocalDate(2024, 1, 5)

/** Auth endpoints return a token for whichever email was requested. Everything else goes to [sync]. */
private class StubAuthApi(sync: FakeSyncApi) : ExpenseFlowApi by sync {
    var logoutCalls = 0
    private var issued = 0

    override suspend fun login(request: AuthRequestDto): AuthResponseDto = issue(request.email)

    override suspend fun register(request: RegisterRequestDto): AuthResponseDto = issue(request.email)

    override suspend fun logout(request: RefreshRequestDto) {
        logoutCalls++
    }

    private fun issue(email: String): AuthResponseDto {
        issued++
        return AuthResponseDto(
            token = "access-$issued",
            refreshToken = "refresh-$issued",
            expiresIn = 900,
            name = "Tester",
            email = email,
            currency = "INR",
        )
    }
}

class SessionControllerTest {

    private class Fixture {
        val h = SyncTestHarness()
        val authApi = StubAuthApi(h.api)
        val auth = AuthRepository(
            api = authApi,
            store = TokenStore(),
            clock = h.clock,
            wipeLocalData = { LocalDataWiper(h.db).wipe() },
        )
        val session = SessionController(auth, h.engine, h.db)

        fun activeExpenseDescriptions(): List<String> =
            h.db.expenseFlowQueries.selectActiveExpenses().executeAsList().map { it.description }
    }

    @Test
    fun signOutWithUnsyncedDataReportsCountAndKeepsEverything() = runTest {
        val f = Fixture()
        f.session.signIn("asha@example.com", "pw")
        f.h.api.expenseWriteError = ApiException(500, "server down")
        f.h.expenses.add("Coffee", "3.50", "INR", DAY)

        val result = f.session.signOut()

        assertEquals(1L, assertIs<SignOutResult.UnsyncedRemaining>(result).count)
        assertNotNull(f.auth.currentSession(), "tokens must survive a blocked sign-out")
        assertEquals(listOf("Coffee"), f.activeExpenseDescriptions())
        assertEquals(0, f.authApi.logoutCalls)
    }

    @Test
    fun discardingUnsyncedDataWipesDatabaseOutboxAndTokens() = runTest {
        val f = Fixture()
        f.session.signIn("asha@example.com", "pw")
        f.h.api.expenseWriteError = ApiException(500, "server down")
        f.h.expenses.add("Coffee", "3.50", "INR", DAY)

        assertIs<SignOutResult.SignedOut>(f.session.signOut(discardUnsynced = 1L))

        assertNull(f.auth.currentSession())
        assertEquals(0L, f.h.outboxCount())
        assertTrue(f.activeExpenseDescriptions().isEmpty())
        assertEquals(1, f.authApi.logoutCalls)
    }

    @Test
    fun writeLandingBetweenPromptAndConfirmIsNotDiscardedUnseen() = runTest {
        val f = Fixture()
        f.session.signIn("asha@example.com", "pw")
        f.h.api.expenseWriteError = ApiException(500, "server down")
        f.h.expenses.add("Coffee", "3.50", "INR", DAY)
        val prompted = assertIs<SignOutResult.UnsyncedRemaining>(f.session.signOut())
        assertEquals(1L, prompted.count)

        // The user is looking at "discard 1 change?" while another write arrives.
        f.h.expenses.add("Tea", "2.00", "INR", DAY)
        val reprompt = f.session.signOut(discardUnsynced = prompted.count)

        assertEquals(2L, assertIs<SignOutResult.UnsyncedRemaining>(reprompt).count)
        assertNotNull(f.auth.currentSession(), "nothing may be wiped until the user confirms the new count")
        assertEquals(2L, f.h.outboxCount())
        assertEquals(listOf("Coffee", "Tea"), f.activeExpenseDescriptions().sorted())
        assertEquals(0, f.authApi.logoutCalls)

        assertIs<SignOutResult.SignedOut>(f.session.signOut(discardUnsynced = 2L))
        assertNull(f.auth.currentSession())
        assertEquals(0L, f.h.outboxCount())
    }

    @Test
    fun writeDuringTheFinalSyncIsCountedInsteadOfWiped() = runTest {
        val f = Fixture()
        f.session.signIn("asha@example.com", "pw")
        f.h.expenses.add("Coffee", "3.50", "INR", DAY)
        var injected = false
        f.h.api.beforeExpenseWrite = {
            if (!injected) {
                injected = true
                f.h.expenses.add("Tea", "2.00", "INR", DAY)
            }
        }

        val result = f.session.signOut()

        assertEquals(1L, assertIs<SignOutResult.UnsyncedRemaining>(result).count)
        assertNotNull(f.auth.currentSession())
        assertTrue("Tea" in f.activeExpenseDescriptions())
    }

    @Test
    fun confirmedCountHigherThanTheCurrentOneStillSignsOut() = runTest {
        val f = Fixture()
        f.session.signIn("asha@example.com", "pw")
        f.h.expenses.add("Coffee", "3.50", "INR", DAY)

        // The final sync pushes the one expense, so fewer changes remain than the user confirmed.
        assertIs<SignOutResult.SignedOut>(f.session.signOut(discardUnsynced = 3L))
        assertNull(f.auth.currentSession())
    }

    @Test
    fun userSwitchAsksAgainWhenAWriteLandsAfterTheConfirmation() = runTest {
        val f = Fixture()
        f.session.signIn("asha@example.com", "pw")
        f.h.api.expenseWriteError = ApiException(500, "server down")
        f.h.expenses.add("Coffee", "3.50", "INR", DAY)
        assertEquals(1L, assertIs<SignInResult.UnsyncedRemaining>(f.session.signIn("ravi@example.com", "pw")).count)
        f.h.expenses.add("Tea", "2.00", "INR", DAY)

        val result = f.session.signIn("ravi@example.com", "pw", discardUnsynced = 1L)

        assertEquals(2L, assertIs<SignInResult.UnsyncedRemaining>(result).count)
        assertEquals("asha@example.com", assertNotNull(f.auth.currentSession()).email)
        assertEquals(2, f.activeExpenseDescriptions().size)
    }

    @Test
    fun wiperRefusesWhenMoreIsPendingThanAllowedAndWipesAtTheLimit() = runTest {
        val h = SyncTestHarness()
        h.expenses.add("Coffee", "3.50", "INR", DAY)
        h.expenses.add("Tea", "2.00", "INR", DAY)
        val wiper = LocalDataWiper(h.db)

        assertEquals(2L, wiper.wipeUnlessUnsyncedExceeds(1L))
        assertEquals(2L, h.outboxCount())
        assertNull(wiper.wipeUnlessUnsyncedExceeds(2L))
        assertEquals(0L, h.outboxCount())
        assertTrue(h.db.expenseFlowQueries.selectActiveExpenses().executeAsList().isEmpty())
    }

    @Test
    fun differentUserSignInWipesPreviousUsersData() = runTest {
        val f = Fixture()
        f.session.signIn("asha@example.com", "pw")
        f.h.expenses.add("Lunch", "12.00", "INR", DAY)
        f.h.engine.sync()
        assertEquals(0L, f.h.outboxCount())

        val result = f.session.signIn("ravi@example.com", "pw")

        assertEquals("ravi@example.com", assertIs<SignInResult.SignedIn>(result).session.email)
        assertEquals("ravi@example.com", assertNotNull(f.auth.currentSession()).email)
        assertTrue(f.activeExpenseDescriptions().isEmpty(), "the second user must not see the first user's expenses")
    }

    @Test
    fun blockedSwitchKeepsPreviousSessionAndData() = runTest {
        val f = Fixture()
        f.session.signIn("asha@example.com", "pw")
        f.h.api.expenseWriteError = ApiException(500, "server down")
        f.h.expenses.add("Coffee", "3.50", "INR", DAY)

        val result = f.session.signIn("ravi@example.com", "pw")

        assertEquals(1L, assertIs<SignInResult.UnsyncedRemaining>(result).count)
        assertEquals("asha@example.com", assertNotNull(f.auth.currentSession()).email)
        assertEquals(listOf("Coffee"), f.activeExpenseDescriptions())
        assertEquals(0, f.authApi.logoutCalls)
    }

    @Test
    fun signingInAgainAsSameUserKeepsData() = runTest {
        val f = Fixture()
        f.session.signIn("asha@example.com", "pw")
        f.h.expenses.add("Coffee", "3.50", "INR", DAY)

        assertIs<SignInResult.SignedIn>(f.session.signIn("Asha@Example.com", "pw"))

        assertEquals(listOf("Coffee"), f.activeExpenseDescriptions())
        assertEquals(1L, f.h.outboxCount())
    }
}
