package com.expensetracker.android

import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.net.HttpURLConnection
import java.net.URL

/** Live backend required with local,e2e profiles; run only on the dedicated emulator. */
class MobileWalkthroughTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val password = "Emulator-Test123!"

    private fun api(path: String, body: JSONObject? = null, token: String? = null): JSONObject {
        val connection = URL("http://10.0.2.2:8080$path").openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 10_000
            connection.readTimeout = 30_000
            token?.let { connection.setRequestProperty("Authorization", "Bearer $it") }
            if (body != null) {
                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { it.write(body.toString().toByteArray()) }
            }
            check(connection.responseCode in 200..299) { "HTTP ${connection.responseCode} at $path" }
            return JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
        } finally {
            connection.disconnect()
        }
    }

    private fun waitFor(text: String) {
        compose.waitUntil(45_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun field(label: String, value: String) {
        waitFor(label)
        val node = compose.onNodeWithText(label)
        if (runCatching { node.assertIsDisplayed() }.isFailure) node.performScrollTo()
        node.performTextReplacement(value)
    }

    private fun click(text: String) {
        compose.waitUntil(45_000) {
            runCatching { compose.onNode(hasText(text) and hasClickAction()).assertIsEnabled() }.isSuccess
        }
        compose.onNode(hasText(text) and hasClickAction()).performClick()
    }

    private fun syncNow() {
        compose.waitUntil(45_000) {
            runCatching { compose.onNodeWithTag("sync-status").assertIsEnabled() }.isSuccess
        }
        compose.onNodeWithTag("sync-status").performClick()
    }

    private fun waitSynced() {
        compose.waitUntil(45_000) {
            compose.onAllNodes(hasTestTag("sync-status") and hasAnyDescendant(hasTextStartingWith("Synced")))
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun hasTextStartingWith(prefix: String) = SemanticsMatcher("text starts with $prefix") { node ->
        node.config.getOrNull(SemanticsProperties.Text)?.any { it.text.startsWith(prefix) } == true
    }

    private fun signOut() {
        compose.onNodeWithTag("overflow-menu").performClick()
        click("Sign out")
    }

    private fun waitForDialogClosed() {
        compose.waitUntil(15_000) { compose.onAllNodes(isDialog()).fetchSemanticsNodes().isEmpty() }
    }

    private fun shell(command: String) {
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command).use {
            android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes()
        }
    }

    private fun network(enabled: Boolean) {
        shell("svc wifi ${if (enabled) "enable" else "disable"}")
        shell("svc data ${if (enabled) "enable" else "disable"}")
        Thread.sleep(3_000)
    }

    @Test
    fun signupOfflineWritesSyncDeleteAndSignOut() {
        val email = "emulator-${System.currentTimeMillis()}@example.com"
        // The local backend cannot deliver email. Get its dev OTP, then enter it in the real signup UI.
        val otp = api("/api/auth/send-otp", JSONObject().put("name", "Emulator").put("email", email)).getString("devOtp")
        waitFor("Sign up")
        click("Sign up")
        field("Name", "Emulator")
        field("Email", email)
        field("Password", password)
        field("OTP", otp)
        compose.onNode(hasText("Create account") and hasClickAction()).performScrollTo().performClick()
        waitFor("Add expense")
        compose.waitUntil(45_000) { runCatching { compose.onNodeWithText("Add expense").assertIsEnabled() }.isSuccess }
        try {
            network(false)
            click("Add expense")
            field("Description", "Offline lunch")
            field("Amount", "12.50")
            click("Save")
            waitForDialogClosed()
            waitFor("Offline lunch")
            click("Add expense")
            field("Description", "Offline taxi")
            field("Amount", "20")
            click("Save")
            waitForDialogClosed()
            waitFor("Offline taxi")
            waitFor("2 pending")
        } finally {
            network(true)
        }
        syncNow()
        waitSynced()
        compose.waitUntil(45_000) { runCatching { compose.onAllNodesWithText("Edit").onFirst().assertIsEnabled() }.isSuccess }
        compose.onAllNodesWithText("Edit").onFirst().performClick()
        field("Description", "Edited on emulator")
        click("Save")
        waitForDialogClosed()
        waitFor("1 pending")
        val workManager = WorkManager.getInstance(compose.activity)
        val work = OneTimeWorkRequestBuilder<SyncWorker>().build()
        workManager.enqueue(work).result.get()
        compose.waitUntil(45_000) {
            workManager.getWorkInfoById(work.id).get()?.state == WorkInfo.State.SUCCEEDED
        }
        waitSynced()
        click("Categories")
        click("Create category")
        field("Category name", "Pets")
        click("Save")
        waitForDialogClosed()
        compose.onNodeWithTag("categories").performScrollToNode(hasText("Pets"))
        waitFor("Pets")
        click("Rename")
        field("Category name", "Animals")
        click("Save")
        waitForDialogClosed()
        compose.onNodeWithTag("categories").performScrollToNode(hasText("Animals"))
        waitFor("Animals")
        click("Create category")
        field("Category name", "food")
        click("Save")
        waitFor("Select Food")
        click("Select Food")
        waitForDialogClosed()
        click("Expenses")
        click("Add expense")
        compose.onNode(hasText("Food") and hasClickAction()).assertIsDisplayed()
        click("Cancel")
        waitForDialogClosed()
        syncNow()
        waitSynced()
        val login = api("/api/auth/login", JSONObject().put("email", email).put("password", password))
        val token = login.getString("token")
        val before = api("/api/v1/expenses/changes", token = token).getJSONArray("items")
        assertEquals(2, before.length())
        assertEquals(setOf(1250L, 2000L), (0 until before.length()).map { before.getJSONObject(it).getLong("amountMinor") }.toSet())
        assertTrue((0 until before.length()).any { before.getJSONObject(it).getString("description") == "Edited on emulator" })
        val serverCategories = api("/api/v1/categories/changes", token = token).getJSONArray("items")
        assertTrue((0 until serverCategories.length()).any { serverCategories.getJSONObject(it).getString("name") == "Animals" })
        compose.onAllNodesWithText("Delete").onFirst().performClick()
        compose.onNode(hasText("Delete") and hasAnyAncestor(isDialog())).performClick()
        waitForDialogClosed()
        syncNow()
        waitSynced()
        val after = api("/api/v1/expenses/changes", token = token).getJSONArray("items")
        assertEquals(1, (0 until after.length()).count { !after.getJSONObject(it).isNull("deletedAt") })
        click("Add expense")
        field("Description", "Rejected amount")
        field("Amount", "10000000000.00")
        click("Save")
        waitForDialogClosed()
        syncNow()
        waitFor("Failed (1)")
        click("Failed (1)")
        val graph = (compose.activity.application as ExpenseApplication).graph
        assertTrue(graph.engine.failedOutboxItems().single().error?.isNotBlank() == true)
        click("Retry")
        compose.waitUntil(45_000) { runCatching { compose.onNodeWithText("Retry").assertIsEnabled() }.isSuccess }
        click("Discard")
        compose.onNode(hasText("Discard") and hasAnyAncestor(isDialog())).performClick()
        waitForDialogClosed()
        waitFor("No failed items")
        waitSynced()
        signOut()
        waitFor("Sign up")
        compose.onNodeWithText("Expired sessions keep your offline data. Sign in with the same account to continue syncing.").assertIsDisplayed()
        field("Email", email)
        field("Password", password)
        click("Sign in")
        waitFor("Add expense")
        compose.waitUntil(45_000) { runCatching { compose.onNodeWithText("Add expense").assertIsEnabled() }.isSuccess }
        signOut()
        waitFor("Sign up")
    }
}
