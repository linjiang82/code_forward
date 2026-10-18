package com.example.smsforwarder.domain.email

data class Email(
    val from: String,
    val to: String,
    val subject: String,
    val text: String,
    val attachments: List<Attachment> = emptyList(),
)

/** The provider infers the content type from the [filename] extension. */
class Attachment(val filename: String, val content: ByteArray)

sealed class SendResult {
    object Success : SendResult()

    /** Network errors, rate limiting and server errors; worth retrying later. */
    data class Retryable(val reason: String) : SendResult()

    /** Bad API key, unverified sender, malformed request; retrying won't help. */
    data class Failed(val reason: String) : SendResult()
}

/** An email delivery service. Implementations own their wire format and error mapping. */
interface EmailSender {
    /**
     * Sends [email]. [idempotencyKey] is stable across retries of the same message, so providers
     * that support it can avoid sending duplicates when a response was lost.
     */
    suspend fun send(
        email: Email,
        idempotencyKey: String,
    ): SendResult
}

/** Creates a sender for the stored API key; the implementation is chosen in the DI container. */
fun interface EmailSenderFactory {
    fun create(apiKey: String): EmailSender
}
