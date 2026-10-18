package com.example.smsforwarder.domain.usecase

import com.example.smsforwarder.domain.FakeEmailSender
import com.example.smsforwarder.domain.FakePdfBuilder
import com.example.smsforwarder.domain.FakeSettingsRepository
import com.example.smsforwarder.domain.ForwardRequest
import com.example.smsforwarder.domain.email.SendResult
import com.example.smsforwarder.domain.validSettings
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ForwardSmsUseCaseTest {
    private val request = ForwardRequest(sender = "+15550123456", body = "Your code is 482913", receivedAtMillis = 0L)

    private fun useCase(
        repository: FakeSettingsRepository,
        sender: FakeEmailSender = FakeEmailSender(),
        pdf: FakePdfBuilder = FakePdfBuilder(),
    ) = ForwardSmsUseCase(repository, sender, pdf, formatTime = { "TIME" }, now = { 0L })

    @Test
    fun `plain mode sends the message text with the sender in the subject`() =
        runBlocking {
            val repository = FakeSettingsRepository(validSettings)
            val sender = FakeEmailSender()

            val outcome = useCase(repository, sender)(request, idempotencyKey = "work-1", attempt = 1)

            assertEquals(ForwardOutcome.Sent, outcome)
            val (email, key) = sender.sent.single()
            assertEquals("work-1", key)
            assertEquals("re_test", sender.apiKeyUsed)
            assertEquals("Forwarded SMS from +15550123456", email.subject)
            assertEquals("Your code is 482913", email.text)
            assertTrue(email.attachments.isEmpty())
            assertEquals("TIME - SMS from +15550123456 forwarded", repository.lastStatus.value)
        }

    @Test
    fun `encrypted mode sends only a pdf and keeps sender and text out of the email`() =
        runBlocking {
            val repository = FakeSettingsRepository(validSettings.copy(encryptPdf = true, pdfPin = "1234"))
            val sender = FakeEmailSender()
            val pdf = FakePdfBuilder()

            val outcome = useCase(repository, sender, pdf)(request, "work-1", 1)

            assertEquals(ForwardOutcome.Sent, outcome)
            assertEquals(listOf("+15550123456", "Your code is 482913", "TIME", "1234"), pdf.calls.single())
            val email = sender.sent.single().first
            assertEquals("New SMS", email.subject)
            assertFalse(email.text.contains("482913"))
            assertFalse(email.subject.contains("+15550123456") || email.text.contains("+15550123456"))
            val attachment = email.attachments.single()
            assertTrue(attachment.filename.matches(Regex("""sms-\d{8}-\d{6}\.pdf""")))
            assertArrayEquals(byteArrayOf(1, 2, 3), attachment.content)
        }

    @Test
    fun `encrypted mode without a pin fails and sends nothing`() =
        runBlocking {
            val repository = FakeSettingsRepository(validSettings.copy(encryptPdf = true, pdfPin = ""))
            val sender = FakeEmailSender()

            val outcome = useCase(repository, sender)(request, "work-1", 1)

            assertTrue(outcome is ForwardOutcome.Failed)
            assertTrue(sender.sent.isEmpty())
            assertTrue(repository.lastStatus.value!!.contains("PDF PIN is missing"))
        }

    @Test
    fun `pdf errors fail without falling back to plain text`() =
        runBlocking {
            val repository = FakeSettingsRepository(validSettings.copy(encryptPdf = true, pdfPin = "1234"))
            val sender = FakeEmailSender()

            val outcome = useCase(repository, sender, FakePdfBuilder(IllegalStateException("boom")))(request, "work-1", 1)

            assertTrue(outcome is ForwardOutcome.Failed)
            assertTrue(sender.sent.isEmpty())
            assertTrue(repository.lastStatus.value!!.contains("could not create the PDF (boom)"))
        }

    @Test
    fun `missing api key or addresses fail without sending`() =
        runBlocking {
            val sender = FakeEmailSender()

            val outcome = useCase(FakeSettingsRepository(validSettings.copy(apiKey = "")), sender)(request, "work-1", 1)

            assertTrue(outcome is ForwardOutcome.Failed)
            assertTrue(sender.sent.isEmpty())
        }

    @Test
    fun `retryable errors retry until the last attempt`() =
        runBlocking {
            val repository = FakeSettingsRepository(validSettings)
            val sender = FakeEmailSender(SendResult.Retryable("HTTP 503"))

            val early = useCase(repository, sender)(request, "work-1", attempt = ForwardSmsUseCase.MAX_ATTEMPTS - 1)
            assertEquals(ForwardOutcome.Retry("HTTP 503"), early)
            assertTrue(repository.lastStatus.value!!.contains("pending retry"))

            val last = useCase(repository, sender)(request, "work-1", attempt = ForwardSmsUseCase.MAX_ATTEMPTS)
            assertTrue(last is ForwardOutcome.Failed)
            assertTrue(repository.lastStatus.value!!.contains("failed after ${ForwardSmsUseCase.MAX_ATTEMPTS} attempts"))
        }

    @Test
    fun `permanent errors fail immediately`() =
        runBlocking {
            val repository = FakeSettingsRepository(validSettings)

            val outcome = useCase(repository, FakeEmailSender(SendResult.Failed("HTTP 401")))(request, "work-1", 1)

            assertEquals(ForwardOutcome.Failed("SMS from +15550123456 failed: HTTP 401"), outcome)
        }

    @Test
    fun `test requests use the test subject and label`() =
        runBlocking {
            val repository = FakeSettingsRepository(validSettings)
            val sender = FakeEmailSender()

            useCase(repository, sender)(request.copy(isTest = true), "work-1", 1)

            assertEquals("SMS Forwarder test", sender.sent.single().first.subject)
            assertEquals("TIME - Test email forwarded", repository.lastStatus.value)
        }
}
