package com.example.smsforwarder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SmsReceiver : BroadcastReceiver() {
    private val processor = SmsProcessor()

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val parts =
            Telephony.Sms.Intents.getMessagesFromIntent(intent).mapNotNull { sms ->
                val sender = sms.displayOriginatingAddress ?: return@mapNotNull null
                SmsMessage(sender, sms.displayMessageBody ?: "")
            }
        if (parts.isEmpty()) return

        // goAsync keeps the receiver alive while the target list is read from DataStore.
        // Only matching messages are handed to WorkManager, which does the actual sending.
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val settings = SettingsManager(context)
                val target = settings.targetNumber.firstOrBlank()
                val forwardAll = settings.forwardAll.first()
                processor.combineParts(parts)
                    .filter { processor.shouldForward(it.sender, target, forwardAll) }
                    .forEach { ForwardWorker.enqueue(context, it.sender, it.body) }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
