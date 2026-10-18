package com.example.smsforwarder.background

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.smsforwarder.SmsForwarderApp
import com.example.smsforwarder.domain.ForwardRequest
import com.example.smsforwarder.domain.usecase.ForwardOutcome

/** Runs [com.example.smsforwarder.domain.usecase.ForwardSmsUseCase] under WorkManager and maps its outcome to retries. */
class ForwardWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    companion object {
        private const val TAG = "ForwardWorker"
        private const val KEY_SENDER = "sender"
        private const val KEY_BODY = "body"
        private const val KEY_IS_TEST = "is_test"
        private const val KEY_RECEIVED_AT = "received_at"

        fun inputDataFor(request: ForwardRequest): Data =
            workDataOf(
                KEY_SENDER to request.sender,
                KEY_BODY to request.body,
                KEY_IS_TEST to request.isTest,
                KEY_RECEIVED_AT to request.receivedAtMillis,
            )
    }

    override suspend fun doWork(): Result {
        val request =
            ForwardRequest(
                sender = inputData.getString(KEY_SENDER) ?: return Result.failure(),
                body = inputData.getString(KEY_BODY) ?: "",
                receivedAtMillis = inputData.getLong(KEY_RECEIVED_AT, System.currentTimeMillis()),
                isTest = inputData.getBoolean(KEY_IS_TEST, false),
            )
        val forwardSms = (applicationContext as SmsForwarderApp).container.forwardSms

        // The work id stays the same across retries, so it doubles as the idempotency key.
        return when (val outcome = forwardSms(request, idempotencyKey = id.toString(), attempt = runAttemptCount + 1)) {
            is ForwardOutcome.Sent -> Result.success()
            is ForwardOutcome.Retry -> {
                Log.w(TAG, outcome.reason)
                Result.retry()
            }
            is ForwardOutcome.Failed -> {
                Log.e(TAG, outcome.reason)
                Result.failure()
            }
        }
    }
}
