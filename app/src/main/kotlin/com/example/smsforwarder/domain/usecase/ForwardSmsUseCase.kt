package com.example.smsforwarder.domain.usecase

import com.example.smsforwarder.domain.EncryptedPdfBuilder
import com.example.smsforwarder.domain.ForwardRequest
import com.example.smsforwarder.domain.ForwardingSettings
import com.example.smsforwarder.domain.SettingsRepository
import com.example.smsforwarder.domain.email.Attachment
import com.example.smsforwarder.domain.email.Email
import com.example.smsforwarder.domain.email.EmailSenderFactory
import com.example.smsforwarder.domain.email.SendResult
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed class ForwardOutcome {
    object Sent : ForwardOutcome()

    data class Retry(val reason: String) : ForwardOutcome()

    data class Failed(val reason: String) : ForwardOutcome()
}

/**
 * Builds and sends the email for one message, and records the outcome as the user-visible status.
 * Callers are responsible for scheduling retries when this returns [ForwardOutcome.Retry].
 */
class ForwardSmsUseCase(
    private val settingsRepository: SettingsRepository,
    private val emailSenderFactory: EmailSenderFactory,
    private val pdfBuilder: EncryptedPdfBuilder,
    private val formatTime: (Long) -> String = { DateFormat.getDateTimeInstance().format(Date(it)) },
    private val now: () -> Long = System::currentTimeMillis,
) {
    companion object {
        const val MAX_ATTEMPTS = 10
    }

    /** [attempt] starts at 1. [idempotencyKey] must stay the same across retries of one message. */
    suspend operator fun invoke(
        request: ForwardRequest,
        idempotencyKey: String,
        attempt: Int,
    ): ForwardOutcome {
        val settings = settingsRepository.load()
        val label = if (request.isTest) "Test email" else "SMS from ${request.sender}"

        if (settings.apiKey.isBlank() || settings.toEmail.isBlank() || settings.fromEmail.isBlank()) {
            return fail("$label not sent: API key or email addresses are missing")
        }

        val email =
            if (settings.encryptPdf) {
                // Never fall back to plain text when encryption is on.
                if (settings.pdfPin.isEmpty()) {
                    return fail("$label not sent: encryption is on but the PDF PIN is missing")
                }
                val pdf =
                    try {
                        pdfBuilder.build(request.sender, request.body, formatTime(request.receivedAtMillis), settings.pdfPin)
                    } catch (e: Exception) {
                        return fail("$label not sent: could not create the PDF (${e.message})")
                    }
                encryptedEmail(settings, request, pdf)
            } else {
                plainEmail(settings, request)
            }

        return when (val result = emailSenderFactory.create(settings.apiKey).send(email, idempotencyKey)) {
            is SendResult.Success -> {
                record("$label forwarded")
                ForwardOutcome.Sent
            }
            is SendResult.Retryable ->
                if (attempt >= MAX_ATTEMPTS) {
                    fail("$label failed after $MAX_ATTEMPTS attempts: ${result.reason}")
                } else {
                    record("$label pending retry: ${result.reason}")
                    ForwardOutcome.Retry(result.reason)
                }
            is SendResult.Failed -> fail("$label failed: ${result.reason}")
        }
    }

    private fun encryptedEmail(
        settings: ForwardingSettings,
        request: ForwardRequest,
        pdf: ByteArray,
    ): Email {
        val filename = "sms-${SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date(request.receivedAtMillis))}.pdf"
        return Email(
            from = settings.fromEmail,
            to = settings.toEmail,
            // Keep the sender out of the subject and body: they're stored unencrypted.
            subject = if (request.isTest) "SMS Forwarder test" else "New SMS",
            text = "This message is encrypted. Open the attached PDF and enter your PIN.",
            attachments = listOf(Attachment(filename, pdf)),
        )
    }

    private fun plainEmail(
        settings: ForwardingSettings,
        request: ForwardRequest,
    ) = Email(
        from = settings.fromEmail,
        to = settings.toEmail,
        subject = if (request.isTest) "SMS Forwarder test" else "Forwarded SMS from ${request.sender}",
        text = request.body,
    )

    private suspend fun fail(message: String): ForwardOutcome {
        record(message)
        return ForwardOutcome.Failed(message)
    }

    private suspend fun record(message: String) {
        settingsRepository.saveLastStatus("${formatTime(now())} - $message")
    }
}
