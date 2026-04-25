package com.example.smsforwarder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class SmsReceiver : BroadcastReceiver() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client = OkHttpClient()
    private val processor = SmsProcessor()

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            for (sms in messages) {
                val sender = sms.displayOriginatingAddress
                val body = sms.displayMessageBody

                scope.launch {
                    forwardIfMatches(context, sender, body)
                }
            }
        }
    }

    private suspend fun forwardIfMatches(
        context: Context,
        sender: String?,
        body: String?,
    ) {
        val settings = SettingsManager(context)
        val target = settings.targetNumber.first()

        if (sender != null && target != null && processor.shouldForward(sender, target)) {
            val apiKey = settings.emailApiKey.first()
            val to = settings.toEmail.first()
            val from = settings.fromEmail.first()

            if (apiKey != null && to != null && from != null) {
                val json = processor.buildEmailJson(to, from, "Forwarded SMS from $sender", body ?: "")
                sendEmail(apiKey, json)
            }
        }
    }

    private fun sendEmail(
        apiKey: String,
        json: String,
    ) {
        val request =
            Request.Builder()
                .url("https://api.sendgrid.com/v3/mail/send")
                .addHeader("Authorization", "Bearer $apiKey")
                .post(json.toRequestBody("application/json".toMediaType()))
                .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e("SmsReceiver", "Failed to send email: ${response.code}")
                } else {
                    Log.d("SmsReceiver", "Email sent successfully")
                }
            }
        } catch (e: Exception) {
            Log.e("SmsReceiver", "Error sending email", e)
        }
    }
}
