package com.example.smsforwarder.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.smsforwarder.SmsForwarderApp

class MainActivity : ComponentActivity() {
    private var smsPermissionGranted by mutableStateOf(false)

    private val viewModel: SettingsViewModel by viewModels {
        val container = (application as SmsForwarderApp).container
        viewModelFactory { initializer { SettingsViewModel(container.settingsRepository, container.forwardScheduler) } }
    }

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            smsPermissionGranted = granted
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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
                    SettingsRoute(viewModel, smsPermissionGranted, onFixPermission = ::openAppSettings)
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
