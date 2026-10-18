package com.example.smsforwarder

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.smsforwarder.email.Attachment
import com.example.smsforwarder.email.Email
import com.example.smsforwarder.email.SendResult
import com.example.smsforwarder.email.createEmailSender
import kotlinx.coroutines.flow.first
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Sends one email through the configured [com.example.smsforwarder.email.EmailSender]. Runs under WorkManager so the send survives the receiver
 * returning, waits for network, and retries with backoff on transient failures.
 */
class ForwardWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    companion object {
        private const val TAG = "ForwardWorker"
        private const val KEY_SENDER = "sender"
        private const val KEY_BODY = "body"
        private const val KEY_IS_TEST = "is_test"
        private const val KEY_RECEIVED_AT = "received_at"
        private const val MAX_ATTEMPTS = 10

        fun enqueue(
            context: Context,
            sender: String,
            body: String,
            isTest: Boolean = false,
        ) {
            val request =
                OneTimeWorkRequestBuilder<ForwardWorker>()
                    .setInputData(
                        workDataOf(
                            KEY_SENDER to sender,
                            KEY_BODY to body,
                            KEY_IS_TEST to isTest,
                            KEY_RECEIVED_AT to System.currentTimeMillis(),
                        ),
                    )
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                    .build()
            WorkManager.getInstance(context).enqueue(request)
        }
    }

    override suspend fun doWork(): Result {
        val settings = SettingsManager(applicationContext)
        val sender = inputData.getString(KEY_SENDER) ?: return Result.failure()
        val body = inputData.getString(KEY_BODY) ?: ""
        val isTest = inputData.getBoolean(KEY_IS_TEST, false)
        val receivedAt = Date(inputData.getLong(KEY_RECEIVED_AT, System.currentTimeMillis()))
        val label = if (isTest) "Test email" else "SMS from $sender"

        val apiKey = settings.getApiKey()
        val to = settings.toEmail.firstOrBlank()
        val from = settings.fromEmail.firstOrBlank()
        if (apiKey.isNullOrBlank() || to.isBlank() || from.isBlank()) {
            settings.saveLastStatus(status("$label not sent: API key or email addresses are missing"))
            return Result.failure()
        }

        val email =
            if (settings.encryptPdf.first()) {
                // Never fall back to plain text when encryption is on.
                val pin = settings.getPdfPin()
                if (pin.isNullOrEmpty()) {
                    settings.saveLastStatus(status("$label not sent: encryption is on but the PDF PIN is missing"))
                    return Result.failure()
                }
                val pdf =
                    try {
                        EncryptedSmsPdf.build(applicationContext, sender, body, DateFormat.getDateTimeInstance().format(receivedAt), pin)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to build PDF", e)
                        settings.saveLastStatus(status("$label not sent: could not create the PDF (${e.message})"))
                        return Result.failure()
                    }
                val filename = "sms-${SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(receivedAt)}.pdf"
                Email(
                    from = from,
                    to = to,
                    // Keep the sender out of the subject and body: they're stored unencrypted.
                    subject = if (isTest) "SMS Forwarder test" else "New SMS",
                    text = "This message is encrypted. Open the attached PDF and enter your PIN.",
                    attachments = listOf(Attachment(filename, pdf)),
                )
            } else {
                Email(
                    from = from,
                    to = to,
                    subject = if (isTest) "SMS Forwarder test" else "Forwarded SMS from $sender",
                    text = body,
                )
            }

        // The work id stays the same across retries, so it doubles as the idempotency key.
        return when (val result = createEmailSender(apiKey).send(email, idempotencyKey = id.toString())) {
            is SendResult.Success -> {
                settings.saveLastStatus(status("$label forwarded"))
                Result.success()
            }
            is SendResult.Retryable -> {
                Log.w(TAG, result.reason)
                if (runAttemptCount + 1 >= MAX_ATTEMPTS) {
                    settings.saveLastStatus(status("$label failed after $MAX_ATTEMPTS attempts: ${result.reason}"))
                    Result.failure()
                } else {
                    settings.saveLastStatus(status("$label pending retry: ${result.reason}"))
                    Result.retry()
                }
            }
            is SendResult.Failed -> {
                Log.e(TAG, result.reason)
                settings.saveLastStatus(status("$label failed: ${result.reason}"))
                Result.failure()
            }
        }
    }

    private fun status(message: String) = "${DateFormat.getDateTimeInstance().format(Date())} - $message"
}
