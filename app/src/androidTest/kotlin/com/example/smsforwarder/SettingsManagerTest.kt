package com.example.smsforwarder

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsManagerTest {
    private lateinit var settingsManager: SettingsManager

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        settingsManager = SettingsManager(context)
    }

    @Test
    fun saveAndReadSettings() =
        runBlocking {
            val target = "123456"
            val apiKey = "re_test_key"
            val to = "to@test.com"
            val from = "from@test.com"

            settingsManager.saveSettings(target, apiKey, to, from, forwardAll = true, encryptPdf = true, pdfPin = "4321")

            assertEquals(target, settingsManager.targetNumber.first())
            assertEquals(true, settingsManager.forwardAll.first())
            assertEquals(true, settingsManager.encryptPdf.first())
            assertEquals("4321", settingsManager.getPdfPin())
            assertEquals(apiKey, settingsManager.getApiKey())
            assertEquals(to, settingsManager.toEmail.first())
            assertEquals(from, settingsManager.fromEmail.first())
        }
}
