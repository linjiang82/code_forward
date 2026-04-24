package com.example.smsforwarder

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import org.junit.Rule
import org.junit.Test

class SettingsScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun settingsScreen_displaysAllFields() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val settingsManager = SettingsManager(context)

        composeTestRule.setContent {
            SettingsScreen(settingsManager)
        }

        composeTestRule.onNodeWithText("Forward from Number (or suffix)").assertIsDisplayed()
        composeTestRule.onNodeWithText("SendGrid API Key").assertIsDisplayed()
        composeTestRule.onNodeWithText("To Email Address").assertIsDisplayed()
        composeTestRule.onNodeWithText("From Email Address").assertIsDisplayed()
        composeTestRule.onNodeWithText("Save Configuration").assertIsDisplayed()
    }

    @Test
    fun settingsScreen_allowsInput() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val settingsManager = SettingsManager(context)

        composeTestRule.setContent {
            SettingsScreen(settingsManager)
        }

        composeTestRule.onNodeWithText("Forward from Number (or suffix)")
            .performTextInput("123456")
        
        composeTestRule.onNodeWithText("123456").assertExists()
    }
}
