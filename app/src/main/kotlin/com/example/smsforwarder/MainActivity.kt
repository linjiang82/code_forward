package com.example.smsforwarder

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var settingsManager: SettingsManager

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
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
                Manifest.permission.READ_SMS
            )
        )

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SettingsScreen(settingsManager)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
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
        modifier = Modifier
            .padding(16.dp)
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("SMS Forwarder Settings", style = MaterialTheme.typography.headlineMedium)
        
        OutlinedTextField(
            value = targetNumber,
            onValueChange = { targetNumber = it },
            label = { Text("Forward from Number (or suffix)") },
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = emailApiKey,
            onValueChange = { emailApiKey = it },
            label = { Text("SendGrid API Key") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = toEmail,
            onValueChange = { toEmail = it },
            label = { Text("To Email Address") },
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = fromEmail,
            onValueChange = { fromEmail = it },
            label = { Text("From Email Address") },
            modifier = Modifier.fillMaxWidth()
        )

        Button(
            onClick = {
                scope.launch {
                    settingsManager.saveSettings(targetNumber, emailApiKey, toEmail, fromEmail)
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Save Configuration")
        }

        Spacer(modifier = Modifier.height(20.dp))
        Text(
            "Instructions:\n1. Grant SMS permissions.\n2. Enter the phone number to watch.\n3. Enter your SendGrid API key and email details.\n4. Click Save.",
            style = MaterialTheme.typography.bodySmall
        )
    }
}
