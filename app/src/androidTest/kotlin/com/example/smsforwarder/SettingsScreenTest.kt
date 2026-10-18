package com.example.smsforwarder

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
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class SettingsScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var settingsManager: SettingsManager

    // The tests share the app's real DataStore, so start each one from known settings.
    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        settingsManager = SettingsManager(context)
        runBlocking { settingsManager.saveSettings("123456", "re_test_key", "to@test.com", "from@test.com", forwardAll = false) }
    }

    @Test
    fun settingsScreen_displaysAllFields() {
        composeTestRule.setContent {
            SettingsScreen(settingsManager)
        }

        composeTestRule.onNodeWithText("Forward from Numbers (comma-separated)").assertIsDisplayed()
        composeTestRule.onNodeWithText("Resend API Key").assertIsDisplayed()
        composeTestRule.onNodeWithText("To Email Address").assertIsDisplayed()
        composeTestRule.onNodeWithText("From Email Address").assertIsDisplayed()
        composeTestRule.onNodeWithText("Save Configuration").assertIsDisplayed()
    }

    @Test
    fun settingsScreen_allowsInput() {
        composeTestRule.setContent {
            SettingsScreen(settingsManager)
        }

        // Wait for saved settings to load so they don't overwrite the typed value.
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Forward from Numbers (comma-separated)")
            .performTextReplacement("987654")

        composeTestRule.onNodeWithText("987654").assertExists()
    }

    @Test
    fun forwardAllSwitch_disablesNumbersField() {
        composeTestRule.setContent {
            SettingsScreen(settingsManager)
        }
        composeTestRule.waitForIdle()

        val numbersField = composeTestRule.onNode(hasText("Forward from Numbers (comma-separated)") and hasSetTextAction())
        numbersField.assertIsEnabled()

        composeTestRule.onNodeWithTag("forwardAllSwitch").performClick()

        numbersField.assertIsNotEnabled()
        composeTestRule.onNodeWithText("Every incoming SMS is forwarded").assertIsDisplayed()
    }

    @Test
    fun encryptPdfSwitch_enablesPinField() {
        composeTestRule.setContent {
            SettingsScreen(settingsManager)
        }
        composeTestRule.waitForIdle()

        val pinField = composeTestRule.onNode(hasText("PDF PIN") and hasSetTextAction())
        pinField.assertIsNotEnabled()

        composeTestRule.onNodeWithTag("encryptPdfSwitch").performScrollTo().performClick()

        pinField.assertIsEnabled()
    }
}
