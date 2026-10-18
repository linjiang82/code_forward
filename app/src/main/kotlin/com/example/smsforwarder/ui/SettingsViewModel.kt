package com.example.smsforwarder.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smsforwarder.domain.ForwardRequest
import com.example.smsforwarder.domain.ForwardScheduler
import com.example.smsforwarder.domain.ForwardingSettings
import com.example.smsforwarder.domain.SettingsRepository
import com.example.smsforwarder.domain.SettingsValidator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val form: ForwardingSettings = ForwardingSettings(),
    val message: String? = null,
    val saved: Boolean = false,
    val lastStatus: String? = null,
)

class SettingsViewModel(
    private val repository: SettingsRepository,
    private val scheduler: ForwardScheduler,
    private val validator: SettingsValidator = SettingsValidator(),
    private val now: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    private val state = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = state

    init {
        viewModelScope.launch {
            val saved = repository.load()
            state.update { it.copy(form = saved, saved = saved.apiKey.isNotBlank()) }
        }
        viewModelScope.launch {
            repository.lastStatus.collect { status -> state.update { it.copy(lastStatus = status) } }
        }
    }

    fun update(transform: (ForwardingSettings) -> ForwardingSettings) {
        state.update { it.copy(form = transform(it.form)) }
    }

    fun save() {
        val form = state.value.form
        val error = validator.validate(form)
        if (error != null) {
            state.update { it.copy(message = error) }
            return
        }
        viewModelScope.launch {
            repository.save(form)
            state.update { it.copy(saved = true, message = "Configuration saved.") }
        }
    }

    /** Uses the saved settings, so it is only available after a successful save. */
    fun sendTestEmail() {
        if (!state.value.saved) return
        scheduler.schedule(
            ForwardRequest(sender = "test", body = "This is a test email from SMS Forwarder.", receivedAtMillis = now(), isTest = true),
        )
        state.update { it.copy(message = "Test email queued. Check the status below.") }
    }
}
