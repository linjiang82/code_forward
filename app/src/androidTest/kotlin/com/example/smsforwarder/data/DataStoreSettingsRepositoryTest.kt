package com.example.smsforwarder.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.smsforwarder.data.settings.DataStoreSettingsRepository
import com.example.smsforwarder.domain.ForwardingSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/** Uses a throwaway DataStore file, so running it never touches the app's real settings. */
@RunWith(AndroidJUnit4::class)
class DataStoreSettingsRepositoryTest {
    private lateinit var scope: CoroutineScope
    private lateinit var file: File
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var repository: DataStoreSettingsRepository

    private val settings =
        ForwardingSettings(
            targetNumbers = "+15550123456, 7890",
            forwardAll = true,
            apiKey = "re_test_key",
            toEmail = "to@test.com",
            fromEmail = "from@test.com",
            encryptPdf = true,
            pdfPin = "4321",
        )

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        file = context.preferencesDataStoreFile("test-${UUID.randomUUID()}")
        dataStore = PreferenceDataStoreFactory.create(scope = scope) { file }
        repository = DataStoreSettingsRepository(dataStore)
    }

    @After
    fun tearDown() {
        scope.cancel()
        file.delete()
    }

    @Test
    fun emptyStoreLoadsDefaults() =
        runBlocking {
            assertEquals(ForwardingSettings(), repository.load())
            assertNull(repository.lastStatus.first())
        }

    @Test
    fun saveThenLoadRoundTripsEveryField() =
        runBlocking {
            repository.save(settings)

            assertEquals(settings, repository.load())
        }

    @Test
    fun secretsAreNotStoredInPlainText() =
        runBlocking {
            repository.save(settings)

            val raw = dataStore.data.first().asMap().values.joinToString()
            assertFalse(raw, raw.contains("re_test_key"))
            assertFalse(raw, raw.contains("4321"))
        }

    @Test
    fun legacyPlaintextApiKeyIsReadThenRemovedOnSave() =
        runBlocking {
            val legacy = stringPreferencesKey("email_api_key")
            dataStore.edit { it[legacy] = "re_legacy" }

            assertEquals("re_legacy", repository.load().apiKey)

            repository.save(repository.load())
            assertNull(dataStore.data.first()[legacy])
            assertEquals("re_legacy", repository.load().apiKey)
        }

    @Test
    fun lastStatusIsObservable() =
        runBlocking {
            repository.saveLastStatus("sent")

            assertEquals("sent", repository.lastStatus.first())
        }
}
