package com.example.smsforwarder.di

import android.content.Context
import com.example.smsforwarder.background.WorkManagerForwardScheduler
import com.example.smsforwarder.data.email.ResendEmailSender
import com.example.smsforwarder.data.pdf.EncryptedSmsPdf
import com.example.smsforwarder.data.settings.DataStoreSettingsRepository
import com.example.smsforwarder.data.settings.settingsDataStore
import com.example.smsforwarder.domain.ForwardScheduler
import com.example.smsforwarder.domain.SettingsRepository
import com.example.smsforwarder.domain.email.EmailSenderFactory
import com.example.smsforwarder.domain.usecase.ForwardSmsUseCase
import com.example.smsforwarder.domain.usecase.SelectMessagesToForwardUseCase

/** Composition root: the only place that picks concrete implementations. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val settingsRepository: SettingsRepository by lazy { DataStoreSettingsRepository(appContext.settingsDataStore) }

    val forwardScheduler: ForwardScheduler by lazy { WorkManagerForwardScheduler(appContext) }

    private val emailSenderFactory = EmailSenderFactory { apiKey -> ResendEmailSender(apiKey) }

    val forwardSms by lazy { ForwardSmsUseCase(settingsRepository, emailSenderFactory, EncryptedSmsPdf(appContext)) }

    val selectMessagesToForward by lazy { SelectMessagesToForwardUseCase(settingsRepository) }
}
