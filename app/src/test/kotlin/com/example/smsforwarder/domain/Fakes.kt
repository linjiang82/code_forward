package com.example.smsforwarder.domain

import com.example.smsforwarder.domain.email.Email
import com.example.smsforwarder.domain.email.EmailSender
import com.example.smsforwarder.domain.email.EmailSenderFactory
import com.example.smsforwarder.domain.email.SendResult
import kotlinx.coroutines.flow.MutableStateFlow

class FakeSettingsRepository(var settings: ForwardingSettings = ForwardingSettings()) : SettingsRepository {
    override val lastStatus = MutableStateFlow<String?>(null)
    var saveCount = 0

    override suspend fun load() = settings

    override suspend fun save(settings: ForwardingSettings) {
        this.settings = settings
        saveCount++
    }

    override suspend fun saveLastStatus(status: String) {
        lastStatus.value = status
    }
}

class FakeEmailSender(private val result: SendResult = SendResult.Success) : EmailSender, EmailSenderFactory {
    val sent = mutableListOf<Pair<Email, String>>()
    var apiKeyUsed: String? = null

    override fun create(apiKey: String): EmailSender {
        apiKeyUsed = apiKey
        return this
    }

    override suspend fun send(
        email: Email,
        idempotencyKey: String,
    ): SendResult {
        sent += email to idempotencyKey
        return result
    }
}

class FakePdfBuilder(private val error: Exception? = null) : EncryptedPdfBuilder {
    val calls = mutableListOf<List<String>>()

    override fun build(
        sender: String,
        body: String,
        receivedAt: String,
        password: String,
    ): ByteArray {
        error?.let { throw it }
        calls += listOf(sender, body, receivedAt, password)
        return byteArrayOf(1, 2, 3)
    }
}

class FakeForwardScheduler : ForwardScheduler {
    val scheduled = mutableListOf<ForwardRequest>()

    override fun schedule(request: ForwardRequest) {
        scheduled += request
    }
}

val validSettings =
    ForwardingSettings(
        targetNumbers = "+15550123456",
        apiKey = "re_test",
        toEmail = "to@example.com",
        fromEmail = "from@example.com",
    )
