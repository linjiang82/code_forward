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
    val hasUnsavedChanges: Boolean = false,
) {
    // The test email uses the saved settings, so edits must be saved first.
    val canSendTestEmail: Boolean get() = saved && !hasUnsavedChanges
}

class SettingsViewModel(
    private val repository: SettingsRepository,
    private val scheduler: ForwardScheduler,
    private val validator: SettingsValidator = SettingsValidator(),
    private val now: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    private val state = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = state

    // What the repository currently holds; the form is "dirty" when it differs from this.
    private var lastSaved = ForwardingSettings()

    init {
        viewModelScope.launch {
            val saved = repository.load()
            lastSaved = saved
            state.update { it.copy(form = saved, saved = saved.apiKey.isNotBlank(), hasUnsavedChanges = false) }
        }
        viewModelScope.launch {
            repository.lastStatus.collect { status -> state.update { it.copy(lastStatus = status) } }
        }
    }

    fun update(transform: (ForwardingSettings) -> ForwardingSettings) {
        state.update {
            val form = transform(it.form)
            it.copy(form = form, hasUnsavedChanges = form != lastSaved)
        }
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
            lastSaved = form
            state.update { it.copy(saved = true, hasUnsavedChanges = it.form != form, message = "Configuration saved.") }
        }
    }

    /** Uses the saved settings, so it is only available after a successful save. */
    fun sendTestEmail() {
        if (state.value.hasUnsavedChanges) {
            state.update { it.copy(message = "Save your changes before sending a test email.") }
            return
        }
        if (!state.value.saved) return
        scheduler.schedule(
            ForwardRequest(sender = "test", body = "This is a test email from SMS Forwarder.", receivedAtMillis = now(), isTest = true),
        )
        state.update { it.copy(message = "Test email queued. Check the status below.") }
    }
}
