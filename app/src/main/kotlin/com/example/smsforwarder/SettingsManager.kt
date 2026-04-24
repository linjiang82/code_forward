package com.example.smsforwarder

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsManager(private val context: Context) {
    companion object {
        val TARGET_NUMBER = stringPreferencesKey("target_number")
        val EMAIL_API_KEY = stringPreferencesKey("email_api_key")
        val TO_EMAIL = stringPreferencesKey("to_email")
        val FROM_EMAIL = stringPreferencesKey("from_email")
    }

    val targetNumber: Flow<String?> = context.dataStore.data.map { it[TARGET_NUMBER] }
    val emailApiKey: Flow<String?> = context.dataStore.data.map { it[EMAIL_API_KEY] }
    val toEmail: Flow<String?> = context.dataStore.data.map { it[TO_EMAIL] }
    val fromEmail: Flow<String?> = context.dataStore.data.map { it[FROM_EMAIL] }

    suspend fun saveSettings(target: String, apiKey: String, to: String, from: String) {
        context.dataStore.edit { settings ->
            settings[TARGET_NUMBER] = target
            settings[EMAIL_API_KEY] = apiKey
            settings[TO_EMAIL] = to
            settings[FROM_EMAIL] = from
        }
    }
}
