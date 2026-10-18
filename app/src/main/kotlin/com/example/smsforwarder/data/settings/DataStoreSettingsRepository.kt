package com.example.smsforwarder.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.smsforwarder.domain.ForwardingSettings
import com.example.smsforwarder.domain.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** Stores settings in Preferences DataStore; the API key and PDF PIN are encrypted with [SecretCipher]. */
class DataStoreSettingsRepository(private val dataStore: DataStore<Preferences>) : SettingsRepository {
    private companion object {
        val TARGET_NUMBER = stringPreferencesKey("target_number")
        val FORWARD_ALL = booleanPreferencesKey("forward_all")

        // Plaintext key written by early versions; read as a fallback, removed on the next save.
        val LEGACY_EMAIL_API_KEY = stringPreferencesKey("email_api_key")
        val EMAIL_API_KEY_ENCRYPTED = stringPreferencesKey("email_api_key_enc")
        val TO_EMAIL = stringPreferencesKey("to_email")
        val FROM_EMAIL = stringPreferencesKey("from_email")
        val ENCRYPT_PDF = booleanPreferencesKey("encrypt_pdf")
        val PDF_PIN_ENCRYPTED = stringPreferencesKey("pdf_pin_enc")
        val LAST_STATUS = stringPreferencesKey("last_status")
    }

    override suspend fun load(): ForwardingSettings {
        val prefs = dataStore.data.first()
        return ForwardingSettings(
            targetNumbers = prefs[TARGET_NUMBER] ?: "",
            forwardAll = prefs[FORWARD_ALL] ?: false,
            apiKey = prefs[EMAIL_API_KEY_ENCRYPTED]?.let { SecretCipher.decrypt(it) } ?: prefs[LEGACY_EMAIL_API_KEY] ?: "",
            toEmail = prefs[TO_EMAIL] ?: "",
            fromEmail = prefs[FROM_EMAIL] ?: "",
            encryptPdf = prefs[ENCRYPT_PDF] ?: false,
            pdfPin = prefs[PDF_PIN_ENCRYPTED]?.let { SecretCipher.decrypt(it) } ?: "",
        )
    }

    override suspend fun save(settings: ForwardingSettings) {
        val encryptedKey = SecretCipher.encrypt(settings.apiKey.trim())
        val encryptedPin = settings.pdfPin.takeIf { it.isNotEmpty() }?.let { SecretCipher.encrypt(it) }
        dataStore.edit { prefs ->
            prefs[TARGET_NUMBER] = settings.targetNumbers
            prefs[FORWARD_ALL] = settings.forwardAll
            prefs[EMAIL_API_KEY_ENCRYPTED] = encryptedKey
            prefs.remove(LEGACY_EMAIL_API_KEY)
            prefs[TO_EMAIL] = settings.toEmail.trim()
            prefs[FROM_EMAIL] = settings.fromEmail.trim()
            prefs[ENCRYPT_PDF] = settings.encryptPdf
            if (encryptedPin != null) prefs[PDF_PIN_ENCRYPTED] = encryptedPin else prefs.remove(PDF_PIN_ENCRYPTED)
        }
    }

    override val lastStatus: Flow<String?> = dataStore.data.map { it[LAST_STATUS] }

    override suspend fun saveLastStatus(status: String) {
        dataStore.edit { it[LAST_STATUS] = status }
    }
}
