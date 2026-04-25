package com.example.smsforwarder

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var settingsManager: SettingsManager

    private val requestPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions(),
        ) { permissions ->
            val granted = permissions.entries.all { it.value }
            if (!granted) {
                // Handle permission denial
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settingsManager = SettingsManager(this)

        requestPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.RECEIVE_SMS,
                Manifest.permission.READ_SMS,
            ),
        )

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    SettingsScreen(settingsManager)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("FunctionName")
fun SettingsScreen(settingsManager: SettingsManager) {
    val scope = rememberCoroutineScope()
    var targetNumber by remember { mutableStateOf("") }
    var emailApiKey by remember { mutableStateOf("") }
    var toEmail by remember { mutableStateOf("") }
    var fromEmail by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        targetNumber = settingsManager.targetNumber.first() ?: ""
        emailApiKey = settingsManager.emailApiKey.first() ?: ""
        toEmail = settingsManager.toEmail.first() ?: ""
        fromEmail = settingsManager.fromEmail.first() ?: ""
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

        OutlinedTextField(
            value = targetNumber,
            onValueChange = { targetNumber = it },
            label = { Text("Forward from Numbers (comma-separated)") },
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = emailApiKey,
            onValueChange = { emailApiKey = it },
            label = { Text("SendGrid API Key") },
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
            modifier = Modifier.fillMaxWidth(),
        )

        Button(
            onClick = {
                scope.launch {
                    settingsManager.saveSettings(targetNumber, emailApiKey, toEmail, fromEmail)
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Save Configuration")
        }

        Spacer(modifier = Modifier.height(20.dp))
        Text(
            "Instructions:\n1. Grant SMS permissions.\n2. Enter the phone numbers to watch (comma-separated).\n" +
                "3. Enter your SendGrid API key and email details.\n4. Click Save.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
