package com.example.smsforwarder

class SmsProcessor {
    fun shouldForward(
        sender: String,
        target: String,
    ): Boolean {
        if (target.isBlank()) return false
        val targets = target.split(",").map { it.trim() }.filter { it.isNotBlank() }
        return targets.any { sender.endsWith(it) }
    }

    fun buildEmailJson(
        to: String,
        from: String,
        subject: String,
        body: String,
    ): String {
        return """
            {
                "personalizations": [{"to": [{"email": "$to"}]}],
                "from": {"email": "$from"},
                "subject": "$subject",
                "content": [{"type": "text/plain", "value": "$body"}]
            }
            """.trimIndent()
    }
}
