package com.example.smsforwarder

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

suspend fun Flow<String?>.firstOrBlank(): String = first() ?: ""

class SettingsManager(private val context: Context) {
    companion object {
        val TARGET_NUMBER = stringPreferencesKey("target_number")
        val FORWARD_ALL = booleanPreferencesKey("forward_all")

        // Plaintext key written by earlier versions; read once for migration, removed on next save.
        val LEGACY_EMAIL_API_KEY = stringPreferencesKey("email_api_key")
        val EMAIL_API_KEY_ENCRYPTED = stringPreferencesKey("email_api_key_enc")
        val TO_EMAIL = stringPreferencesKey("to_email")
        val FROM_EMAIL = stringPreferencesKey("from_email")
        val LAST_STATUS = stringPreferencesKey("last_status")
        val ENCRYPT_PDF = booleanPreferencesKey("encrypt_pdf")
        val PDF_PIN_ENCRYPTED = stringPreferencesKey("pdf_pin_enc")
    }

    val targetNumber: Flow<String?> = context.dataStore.data.map { it[TARGET_NUMBER] }
    val forwardAll: Flow<Boolean> = context.dataStore.data.map { it[FORWARD_ALL] ?: false }
    val toEmail: Flow<String?> = context.dataStore.data.map { it[TO_EMAIL] }
    val fromEmail: Flow<String?> = context.dataStore.data.map { it[FROM_EMAIL] }
    val lastStatus: Flow<String?> = context.dataStore.data.map { it[LAST_STATUS] }
    val encryptPdf: Flow<Boolean> = context.dataStore.data.map { it[ENCRYPT_PDF] ?: false }

    suspend fun getApiKey(): String? {
        val prefs = context.dataStore.data.first()
        return prefs[EMAIL_API_KEY_ENCRYPTED]?.let { SecretCipher.decrypt(it) } ?: prefs[LEGACY_EMAIL_API_KEY]
    }

    suspend fun getPdfPin(): String? = context.dataStore.data.first()[PDF_PIN_ENCRYPTED]?.let { SecretCipher.decrypt(it) }

    suspend fun saveSettings(
        target: String,
        apiKey: String,
        to: String,
        from: String,
        forwardAll: Boolean = false,
        encryptPdf: Boolean = false,
        pdfPin: String = "",
    ) {
        val encryptedKey = SecretCipher.encrypt(apiKey.trim())
        val encryptedPin = pdfPin.takeIf { it.isNotEmpty() }?.let { SecretCipher.encrypt(it) }
        context.dataStore.edit { settings ->
            settings[TARGET_NUMBER] = target
            settings[FORWARD_ALL] = forwardAll
            settings[EMAIL_API_KEY_ENCRYPTED] = encryptedKey
            settings.remove(LEGACY_EMAIL_API_KEY)
            settings[TO_EMAIL] = to.trim()
            settings[FROM_EMAIL] = from.trim()
            settings[ENCRYPT_PDF] = encryptPdf
            if (encryptedPin != null) settings[PDF_PIN_ENCRYPTED] = encryptedPin else settings.remove(PDF_PIN_ENCRYPTED)
        }
    }

    suspend fun saveLastStatus(status: String) {
        context.dataStore.edit { it[LAST_STATUS] = status }
    }
}
