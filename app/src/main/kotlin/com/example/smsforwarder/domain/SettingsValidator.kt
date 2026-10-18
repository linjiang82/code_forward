package com.example.smsforwarder.domain

class SettingsValidator(private val processor: SmsProcessor = SmsProcessor()) {
    companion object {
        const val MIN_PIN_LENGTH = 4
    }

    /** Returns a message describing the first problem, or null if the settings can be saved. */
    fun validate(settings: ForwardingSettings): String? {
        val invalid = processor.invalidTargets(settings.targetNumbers)
        return when {
            !settings.forwardAll && processor.parseTargets(settings.targetNumbers).isEmpty() ->
                "Enter at least one phone number to forward from, or turn on Forward all messages."
            !settings.forwardAll && invalid.isNotEmpty() ->
                "These numbers are too short (need at least ${SmsProcessor.MIN_TARGET_LENGTH} characters): " +
                    invalid.joinToString(", ")
            settings.encryptPdf && settings.pdfPin.length < MIN_PIN_LENGTH ->
                "The PDF PIN must be at least $MIN_PIN_LENGTH characters."
            settings.apiKey.isBlank() -> "Enter your Resend API key."
            !settings.toEmail.contains("@") -> "Enter a valid To email address."
            !settings.fromEmail.contains("@") -> "Enter a valid From email address."
            else -> null
        }
    }
}
