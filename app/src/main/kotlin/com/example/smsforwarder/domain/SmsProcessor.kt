package com.example.smsforwarder.domain

class SmsProcessor {
    companion object {
        // Shorter entries (e.g. "0") would match almost every sender.
        const val MIN_TARGET_LENGTH = 4
    }

    fun parseTargets(target: String): List<String> = target.split(",").map { normalize(it) }.filter { it.isNotEmpty() }

    /** Entries that are too short to be used safely; shown to the user as a validation error. */
    fun invalidTargets(target: String): List<String> = parseTargets(target).filter { it.length < MIN_TARGET_LENGTH }

    /** With [forwardAll] every message is forwarded and [target] is ignored. */
    fun shouldForward(
        sender: String,
        target: String,
        forwardAll: Boolean = false,
    ): Boolean {
        if (forwardAll) return true
        val normalizedSender = normalize(sender)
        if (normalizedSender.isEmpty()) return false
        return parseTargets(target)
            .filter { it.length >= MIN_TARGET_LENGTH }
            .any { normalizedSender.endsWith(it, ignoreCase = true) }
    }

    /** Joins the parts of multipart SMS, which arrive as separate PDUs, into one message per sender. */
    fun combineParts(parts: List<SmsMessage>): List<SmsMessage> =
        parts
            .groupBy { it.sender }
            .map { (sender, messages) -> SmsMessage(sender, messages.joinToString("") { it.body }) }

    private fun normalize(number: String): String =
        number.trim().filterNot { it == ' ' || it == '-' || it == '(' || it == ')' || it == '.' }
}
