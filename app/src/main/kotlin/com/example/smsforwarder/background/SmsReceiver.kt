package com.example.smsforwarder.background

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.example.smsforwarder.SmsForwarderApp
import com.example.smsforwarder.domain.ForwardRequest
import com.example.smsforwarder.domain.SmsMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val receivedAt = System.currentTimeMillis()
        val parts =
            Telephony.Sms.Intents.getMessagesFromIntent(intent).mapNotNull { sms ->
                val sender = sms.displayOriginatingAddress ?: return@mapNotNull null
                SmsMessage(sender, sms.displayMessageBody ?: "")
            }
        if (parts.isEmpty()) return

        val container = (context.applicationContext as SmsForwarderApp).container
        // goAsync keeps the receiver alive while settings are read; WorkManager does the actual sending.
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                container.selectMessagesToForward(parts).forEach {
                    container.forwardScheduler.schedule(ForwardRequest(it.sender, it.body, receivedAt))
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
