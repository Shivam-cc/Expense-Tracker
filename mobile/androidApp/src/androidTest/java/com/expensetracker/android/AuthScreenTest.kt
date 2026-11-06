package com.expensetracker.android

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import org.junit.Rule
import org.junit.Test

class AuthScreenTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun fieldsAcceptInputAndModeSwitchKeepsSignInCopy() {
        compose.onNodeWithText("Email").performTextReplacement("ada@example.com")
        compose.onNodeWithText("Password").performTextReplacement("secret")
        compose.onNodeWithText("ada@example.com").assertIsDisplayed()
        compose.onNode(hasText("Sign up") and hasClickAction()).performClick()
        compose.onNodeWithText("Name").performTextReplacement("Ada")
        compose.onNodeWithText("OTP").performTextReplacement("123456")
        compose.onNode(hasText("Create account") and hasClickAction()).assertIsDisplayed()
        compose.onNode(hasText("Sign in") and hasClickAction()).performClick()
        compose.onNodeWithText(
            "Expired sessions keep your offline data. Sign in with the same account to continue syncing."
        ).assertIsDisplayed()
        compose.onNode(hasText("Sign in") and hasClickAction()).assertIsDisplayed()
    }
}
