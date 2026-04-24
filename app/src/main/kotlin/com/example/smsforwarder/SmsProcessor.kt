package com.example.smsforwarder

class SmsProcessor {
    fun shouldForward(sender: String, target: String): Boolean {
        return sender.contains(target)
    }

    fun buildEmailJson(to: String, from: String, subject: String, body: String): String {
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
