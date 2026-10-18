package com.example.smsforwarder.domain.usecase

import com.example.smsforwarder.domain.SettingsRepository
import com.example.smsforwarder.domain.SmsMessage
import com.example.smsforwarder.domain.SmsProcessor

/** Joins multipart PDUs and keeps only messages the user wants forwarded. */
class SelectMessagesToForwardUseCase(
    private val settingsRepository: SettingsRepository,
    private val processor: SmsProcessor = SmsProcessor(),
) {
    suspend operator fun invoke(parts: List<SmsMessage>): List<SmsMessage> {
        if (parts.isEmpty()) return emptyList()
        val settings = settingsRepository.load()
        return processor.combineParts(parts)
            .filter { processor.shouldForward(it.sender, settings.targetNumbers, settings.forwardAll) }
    }
}
