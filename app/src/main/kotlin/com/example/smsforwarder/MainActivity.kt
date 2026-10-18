package com.example.smsforwarder

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var settingsManager: SettingsManager
    private var smsPermissionGranted by mutableStateOf(false)

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            smsPermissionGranted = granted
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settingsManager = SettingsManager(this)

        smsPermissionGranted = hasSmsPermission()
        if (!smsPermissionGranted) {
            requestPermissionLauncher.launch(Manifest.permission.RECEIVE_SMS)
        }

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    SettingsScreen(
                        settingsManager = settingsManager,
                        smsPermissionGranted = smsPermissionGranted,
                        onSendTestEmail = {
                            ForwardWorker.enqueue(
                                this,
                                "test",
                                "This is a test email from SMS Forwarder.",
                                isTest = true,
                            )
                        },
                        onFixPermission = ::openAppSettings,
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // The user may have granted the permission from system settings.
        smsPermissionGranted = hasSmsPermission()
    }

    private fun hasSmsPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED

    private fun openAppSettings() {
        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
    }
}

private const val MIN_PIN_LENGTH = 4

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("FunctionName")
fun SettingsScreen(
    settingsManager: SettingsManager,
    smsPermissionGranted: Boolean = true,
    onSendTestEmail: () -> Unit = {},
    onFixPermission: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val processor = remember { SmsProcessor() }
    var targetNumber by remember { mutableStateOf("") }
    var forwardAll by remember { mutableStateOf(false) }
    var encryptPdf by remember { mutableStateOf(false) }
    var pdfPin by remember { mutableStateOf("") }
    var emailApiKey by remember { mutableStateOf("") }
    var toEmail by remember { mutableStateOf("") }
    var fromEmail by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var saved by remember { mutableStateOf(false) }
    val lastStatus by settingsManager.lastStatus.collectAsState(initial = null)

    LaunchedEffect(Unit) {
        targetNumber = settingsManager.targetNumber.firstOrBlank()
        forwardAll = settingsManager.forwardAll.first()
        encryptPdf = settingsManager.encryptPdf.first()
        pdfPin = settingsManager.getPdfPin() ?: ""
        emailApiKey = settingsManager.getApiKey() ?: ""
        toEmail = settingsManager.toEmail.firstOrBlank()
        fromEmail = settingsManager.fromEmail.firstOrBlank()
        saved = emailApiKey.isNotBlank()
    }

    fun validate(): String? {
        val invalid = processor.invalidTargets(targetNumber)
        return when {
            !forwardAll && processor.parseTargets(targetNumber).isEmpty() ->
                "Enter at least one phone number to forward from, or turn on Forward all messages."
            !forwardAll && invalid.isNotEmpty() ->
                "These numbers are too short (need at least ${SmsProcessor.MIN_TARGET_LENGTH} characters): " +
                    invalid.joinToString(", ")
            encryptPdf && pdfPin.length < MIN_PIN_LENGTH -> "The PDF PIN must be at least $MIN_PIN_LENGTH characters."
            emailApiKey.isBlank() -> "Enter your Resend API key."
            !toEmail.contains("@") -> "Enter a valid To email address."
            !fromEmail.contains("@") -> "Enter a valid From email address."
            else -> null
        }
    }

    Column(
        modifier =
            Modifier
                .padding(16.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("SMS Forwarder Settings", style = MaterialTheme.typography.headlineMedium)

        if (!smsPermissionGranted) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "SMS permission is not granted, so no messages will be forwarded.",
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                    Button(onClick = onFixPermission) { Text("Open app settings") }
                }
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Forward all messages", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (forwardAll) "Every incoming SMS is forwarded" else "Only messages from the numbers below",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Switch(checked = forwardAll, onCheckedChange = { forwardAll = it }, modifier = Modifier.testTag("forwardAllSwitch"))
        }

        OutlinedTextField(
            value = targetNumber,
            onValueChange = { targetNumber = it },
            enabled = !forwardAll,
            label = { Text("Forward from Numbers (comma-separated)") },
            supportingText = { Text("Full numbers or their last ${SmsProcessor.MIN_TARGET_LENGTH}+ digits") },
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = emailApiKey,
            onValueChange = { emailApiKey = it },
            label = { Text("Resend API Key") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = toEmail,
            onValueChange = { toEmail = it },
            label = { Text("To Email Address") },
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = fromEmail,
            onValueChange = { fromEmail = it },
            label = { Text("From Email Address") },
            supportingText = { Text("Must use a domain verified in Resend, or onboarding@resend.dev for testing") },
            modifier = Modifier.fillMaxWidth(),
        )

        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Encrypt as PDF attachment", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (encryptPdf) "The message is sent as a PDF that opens with your PIN" else "The message is sent as plain text",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Switch(checked = encryptPdf, onCheckedChange = { encryptPdf = it }, modifier = Modifier.testTag("encryptPdfSwitch"))
        }

        OutlinedTextField(
            value = pdfPin,
            onValueChange = { pdfPin = it },
            enabled = encryptPdf,
            label = { Text("PDF PIN") },
            supportingText = { Text("At least $MIN_PIN_LENGTH digits; you type it when opening the PDF") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            modifier = Modifier.fillMaxWidth(),
        )

        Button(
            onClick = {
                val error = validate()
                if (error != null) {
                    message = error
                } else {
                    scope.launch {
                        settingsManager.saveSettings(targetNumber, emailApiKey, toEmail, fromEmail, forwardAll, encryptPdf, pdfPin)
                        saved = true
                        message = "Configuration saved."
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Save Configuration")
        }

        OutlinedButton(
            onClick = {
                onSendTestEmail()
                message = "Test email queued. Check the status below."
            },
            enabled = saved,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Send Test Email")
        }

        message?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }

        Text("Last forwarding status", style = MaterialTheme.typography.titleSmall)
        Text(lastStatus ?: "Nothing forwarded yet.", style = MaterialTheme.typography.bodySmall)

        Spacer(modifier = Modifier.height(20.dp))
        Text(
            "Instructions:\n1. Grant SMS permissions.\n2. Enter the phone numbers to watch (comma-separated).\n" +
                "3. Enter your Resend API key and email details.\n4. Click Save, then Send Test Email to check the setup.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
