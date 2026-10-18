package com.example.smsforwarder.domain.usecase

import com.example.smsforwarder.domain.FakeSettingsRepository
import com.example.smsforwarder.domain.SmsMessage
import com.example.smsforwarder.domain.validSettings
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class SelectMessagesToForwardUseCaseTest {
    private val parts =
        listOf(
            SmsMessage("+15550123456", "Your code "),
            SmsMessage("+19999999999", "spam"),
            SmsMessage("+15550123456", "is 482913"),
        )

    @Test
    fun `joins multipart messages and keeps only watched senders`() =
        runBlocking {
            val select = SelectMessagesToForwardUseCase(FakeSettingsRepository(validSettings))

            assertEquals(listOf(SmsMessage("+15550123456", "Your code is 482913")), select(parts))
        }

    @Test
    fun `forward all keeps every sender`() =
        runBlocking {
            val select = SelectMessagesToForwardUseCase(FakeSettingsRepository(validSettings.copy(targetNumbers = "", forwardAll = true)))

            assertEquals(2, select(parts).size)
        }
}
