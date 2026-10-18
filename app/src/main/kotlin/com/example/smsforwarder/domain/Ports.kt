package com.example.smsforwarder.domain

import kotlinx.coroutines.flow.Flow

// Interfaces the domain depends on; implementations live in the data and background layers.

interface SettingsRepository {
    suspend fun load(): ForwardingSettings

    suspend fun save(settings: ForwardingSettings)

    val lastStatus: Flow<String?>

    suspend fun saveLastStatus(status: String)
}

/** Renders a message into a PDF that only opens with [password]. */
fun interface EncryptedPdfBuilder {
    fun build(
        sender: String,
        body: String,
        receivedAt: String,
        password: String,
    ): ByteArray
}

/** Queues a message for sending in the background, with retries. */
fun interface ForwardScheduler {
    fun schedule(request: ForwardRequest)
}
