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
            val apiKey = "sg_test_key"
            val to = "to@test.com"
            val from = "from@test.com"

            settingsManager.saveSettings(target, apiKey, to, from)

            assertEquals(target, settingsManager.targetNumber.first())
            assertEquals(apiKey, settingsManager.emailApiKey.first())
            assertEquals(to, settingsManager.toEmail.first())
            assertEquals(from, settingsManager.fromEmail.first())
        }
}
