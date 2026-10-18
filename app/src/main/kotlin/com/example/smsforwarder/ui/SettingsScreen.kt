package com.example.smsforwarder.ui

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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.smsforwarder.domain.ForwardingSettings
import com.example.smsforwarder.domain.SettingsValidator
import com.example.smsforwarder.domain.SmsProcessor

@Composable
@Suppress("FunctionName")
fun SettingsRoute(
    viewModel: SettingsViewModel,
    smsPermissionGranted: Boolean,
    onFixPermission: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    SettingsScreen(
        state = state,
        smsPermissionGranted = smsPermissionGranted,
        onUpdate = viewModel::update,
        onSave = viewModel::save,
        onSendTestEmail = viewModel::sendTestEmail,
        onFixPermission = onFixPermission,
        onMessageShown = viewModel::messageShown,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("FunctionName")
fun SettingsScreen(
    state: SettingsUiState,
    smsPermissionGranted: Boolean = true,
    onUpdate: ((ForwardingSettings) -> ForwardingSettings) -> Unit = {},
    onSave: () -> Unit = {},
    onSendTestEmail: () -> Unit = {},
    onFixPermission: () -> Unit = {},
    onMessageShown: () -> Unit = {},
) {
    val form = state.form
    val snackbarHostState = remember { SnackbarHostState() }

    // Show each message once as a snackbar, then tell the ViewModel it was shown.
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            onMessageShown()
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .padding(innerPadding)
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

            SwitchRow(
                title = "Forward all messages",
                description = if (form.forwardAll) "Every incoming SMS is forwarded" else "Only messages from the numbers below",
                checked = form.forwardAll,
                onCheckedChange = { checked -> onUpdate { it.copy(forwardAll = checked) } },
                testTag = "forwardAllSwitch",
            )

            OutlinedTextField(
                value = form.targetNumbers,
                onValueChange = { value -> onUpdate { it.copy(targetNumbers = value) } },
                enabled = !form.forwardAll,
                label = { Text("Forward from Numbers (comma-separated)") },
                supportingText = { Text("Full numbers or their last ${SmsProcessor.MIN_TARGET_LENGTH}+ digits") },
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = form.apiKey,
                onValueChange = { value -> onUpdate { it.copy(apiKey = value) } },
                label = { Text("Resend API Key") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = form.toEmail,
                onValueChange = { value -> onUpdate { it.copy(toEmail = value) } },
                label = { Text("To Email Address") },
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = form.fromEmail,
                onValueChange = { value -> onUpdate { it.copy(fromEmail = value) } },
                label = { Text("From Email Address") },
                supportingText = { Text("Must use a domain verified in Resend, or onboarding@resend.dev for testing") },
                modifier = Modifier.fillMaxWidth(),
            )

            SwitchRow(
                title = "Encrypt as PDF attachment",
                description =
                    if (form.encryptPdf) "The message is sent as a PDF that opens with your PIN" else "The message is sent as plain text",
                checked = form.encryptPdf,
                onCheckedChange = { checked -> onUpdate { it.copy(encryptPdf = checked) } },
                testTag = "encryptPdfSwitch",
            )

            OutlinedTextField(
                value = form.pdfPin,
                onValueChange = { value -> onUpdate { it.copy(pdfPin = value) } },
                enabled = form.encryptPdf,
                label = { Text("PDF PIN") },
                supportingText = { Text("At least ${SettingsValidator.MIN_PIN_LENGTH} digits; you type it when opening the PDF") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                modifier = Modifier.fillMaxWidth(),
            )

            Button(onClick = onSave, enabled = state.canSave, modifier = Modifier.fillMaxWidth()) {
                Text(if (state.canSave) "Save Configuration" else "Saved")
            }

            OutlinedButton(onClick = onSendTestEmail, enabled = state.canSendTestEmail, modifier = Modifier.fillMaxWidth()) {
                Text("Send Test Email")
            }

            if (state.saved && state.hasUnsavedChanges) {
                Text(
                    "Save your changes before sending a test email.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Text("Last forwarding status", style = MaterialTheme.typography.titleSmall)
            Text(state.lastStatus ?: "Nothing forwarded yet.", style = MaterialTheme.typography.bodySmall)

            Spacer(modifier = Modifier.height(20.dp))
            Text(
                "Instructions:\n1. Grant SMS permissions.\n2. Enter the phone numbers to watch (comma-separated).\n" +
                    "3. Enter your Resend API key and email details.\n4. Click Save, then Send Test Email to check the setup.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
@Suppress("FunctionName")
private fun SwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String,
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(description, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, modifier = Modifier.testTag(testTag))
    }
}
