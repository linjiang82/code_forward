package com.example.smsforwarder.domain

data class SmsMessage(val sender: String, val body: String)

/** Everything the user configures. Secrets are already decrypted; missing values are empty. */
data class ForwardingSettings(
    val targetNumbers: String = "",
    val forwardAll: Boolean = false,
    val apiKey: String = "",
    val toEmail: String = "",
    val fromEmail: String = "",
    val encryptPdf: Boolean = false,
    val pdfPin: String = "",
)

data class ForwardRequest(
    val sender: String,
    val body: String,
    val receivedAtMillis: Long,
    val isTest: Boolean = false,
)
