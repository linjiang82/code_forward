package com.example.smsforwarder.ui

import com.example.smsforwarder.domain.FakeForwardScheduler
import com.example.smsforwarder.domain.FakeSettingsRepository
import com.example.smsforwarder.domain.ForwardRequest
import com.example.smsforwarder.domain.ForwardingSettings
import com.example.smsforwarder.domain.validSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val scheduler = FakeForwardScheduler()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(repository: FakeSettingsRepository) = SettingsViewModel(repository, scheduler, now = { 42L })

    @Test
    fun `loads saved settings and status on start`() {
        val repository = FakeSettingsRepository(validSettings)
        repository.lastStatus.value = "earlier"

        val state = viewModel(repository).uiState.value

        assertEquals(validSettings, state.form)
        assertTrue(state.saved)
        assertEquals("earlier", state.lastStatus)
    }

    @Test
    fun `nothing is marked saved when no api key is stored`() {
        assertFalse(viewModel(FakeSettingsRepository()).uiState.value.saved)
    }

    @Test
    fun `update changes the form without saving`() {
        val repository = FakeSettingsRepository(validSettings)
        val viewModel = viewModel(repository)

        viewModel.update { it.copy(forwardAll = true) }

        assertTrue(viewModel.uiState.value.form.forwardAll)
        assertEquals(0, repository.saveCount)
    }

    @Test
    fun `invalid settings show the validation message and are not saved`() {
        val repository = FakeSettingsRepository()
        val viewModel = viewModel(repository)

        viewModel.update { ForwardingSettings(targetNumbers = "+15550123456") }
        viewModel.save()

        assertEquals("Enter your Resend API key.", viewModel.uiState.value.message)
        assertEquals(0, repository.saveCount)
    }

    @Test
    fun `valid settings are saved and confirmed`() {
        val repository = FakeSettingsRepository()
        val viewModel = viewModel(repository)

        viewModel.update { validSettings }
        viewModel.save()

        assertEquals(validSettings, repository.settings)
        assertEquals("Configuration saved.", viewModel.uiState.value.message)
        assertTrue(viewModel.uiState.value.saved)
    }

    @Test
    fun `test email is queued as a test request`() {
        val viewModel = viewModel(FakeSettingsRepository(validSettings))

        viewModel.sendTestEmail()

        assertEquals(
            listOf(
                ForwardRequest(sender = "test", body = "This is a test email from SMS Forwarder.", receivedAtMillis = 42L, isTest = true),
            ),
            scheduler.scheduled,
        )
        assertEquals("Test email queued. Check the status below.", viewModel.uiState.value.message)
    }

    @Test
    fun `test email is not queued before settings are saved`() {
        val viewModel = viewModel(FakeSettingsRepository())

        viewModel.sendTestEmail()

        assertTrue(scheduler.scheduled.isEmpty())
    }

    @Test
    fun `status updates from the repository reach the ui`() {
        val repository = FakeSettingsRepository(validSettings)
        val viewModel = viewModel(repository)

        repository.lastStatus.value = "now"

        assertEquals("now", viewModel.uiState.value.lastStatus)
    }

    @Test
    fun `freshly loaded settings have no unsaved changes`() {
        val state = viewModel(FakeSettingsRepository(validSettings)).uiState.value

        assertFalse(state.hasUnsavedChanges)
        assertTrue(state.canSendTestEmail)
    }

    @Test
    fun `editing after a save blocks the test email until saved again`() {
        val viewModel = viewModel(FakeSettingsRepository(validSettings))

        viewModel.update { it.copy(encryptPdf = true, pdfPin = "1234") }

        assertTrue(viewModel.uiState.value.hasUnsavedChanges)
        assertFalse(viewModel.uiState.value.canSendTestEmail)

        viewModel.sendTestEmail()

        assertTrue(scheduler.scheduled.isEmpty())
        assertEquals("Save your changes before sending a test email.", viewModel.uiState.value.message)
    }

    @Test
    fun `reverting an edit clears unsaved changes`() {
        val viewModel = viewModel(FakeSettingsRepository(validSettings))

        viewModel.update { it.copy(forwardAll = true) }
        viewModel.update { it.copy(forwardAll = false) }

        assertFalse(viewModel.uiState.value.hasUnsavedChanges)
    }

    @Test
    fun `saving the edits allows the test email with the new settings`() {
        val repository = FakeSettingsRepository(validSettings)
        val viewModel = viewModel(repository)

        viewModel.update { it.copy(encryptPdf = true, pdfPin = "1234") }
        viewModel.save()

        assertFalse(viewModel.uiState.value.hasUnsavedChanges)
        assertTrue(viewModel.uiState.value.canSendTestEmail)
        assertTrue(repository.settings.encryptPdf)

        viewModel.sendTestEmail()

        assertEquals(1, scheduler.scheduled.size)
    }

    @Test
    fun `a save rejected by validation keeps the changes unsaved`() {
        val viewModel = viewModel(FakeSettingsRepository(validSettings))

        viewModel.update { it.copy(encryptPdf = true, pdfPin = "12") }
        viewModel.save()

        assertTrue(viewModel.uiState.value.hasUnsavedChanges)
        assertFalse(viewModel.uiState.value.canSendTestEmail)
    }
}
