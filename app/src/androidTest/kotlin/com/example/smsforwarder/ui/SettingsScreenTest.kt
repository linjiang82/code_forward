package com.example.smsforwarder.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import com.example.smsforwarder.domain.ForwardingSettings
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Drives the stateless screen with in-memory state; no DataStore or ViewModel involved. */
class SettingsScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private var state by mutableStateOf(SettingsUiState(form = ForwardingSettings(targetNumbers = "123456")))

    private fun setContent(smsPermissionGranted: Boolean = true) {
        composeTestRule.setContent {
            SettingsScreen(
                state = state,
                smsPermissionGranted = smsPermissionGranted,
                onUpdate = { transform -> state = state.copy(form = transform(state.form)) },
            )
        }
    }

    private fun numbersField() = composeTestRule.onNode(hasText("Forward from Numbers (comma-separated)") and hasSetTextAction())

    @Test
    fun displaysAllFields() {
        setContent()

        numbersField().assertIsDisplayed()
        composeTestRule.onNodeWithText("Resend API Key").assertIsDisplayed()
        composeTestRule.onNodeWithText("To Email Address").assertIsDisplayed()
        composeTestRule.onNodeWithText("From Email Address").assertIsDisplayed()
        composeTestRule.onNodeWithText("Save Configuration").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun typingUpdatesTheForm() {
        setContent()

        numbersField().performTextReplacement("987654")

        assertEquals("987654", state.form.targetNumbers)
    }

    @Test
    fun forwardAllSwitchDisablesNumbersField() {
        setContent()
        numbersField().assertIsEnabled()

        composeTestRule.onNodeWithTag("forwardAllSwitch").performClick()

        numbersField().assertIsNotEnabled()
        composeTestRule.onNodeWithText("Every incoming SMS is forwarded").assertIsDisplayed()
    }

    @Test
    fun encryptPdfSwitchEnablesPinField() {
        setContent()
        val pinField = composeTestRule.onNode(hasText("PDF PIN") and hasSetTextAction())
        pinField.assertIsNotEnabled()

        composeTestRule.onNodeWithTag("encryptPdfSwitch").performScrollTo().performClick()

        pinField.assertIsEnabled()
    }

    @Test
    fun testEmailButtonIsDisabledUntilSaved() {
        setContent()
        val button = composeTestRule.onNodeWithText("Send Test Email").performScrollTo()
        button.assertIsNotEnabled()

        state = state.copy(saved = true)

        button.assertIsEnabled()
    }

    @Test
    fun missingPermissionShowsWarning() {
        setContent(smsPermissionGranted = false)

        composeTestRule.onNodeWithText("SMS permission is not granted, so no messages will be forwarded.").assertIsDisplayed()
    }
}
