package com.example.smsforwarder.background

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.smsforwarder.domain.ForwardRequest
import com.example.smsforwarder.domain.ForwardScheduler
import java.util.concurrent.TimeUnit

class WorkManagerForwardScheduler(private val context: Context) : ForwardScheduler {
    override fun schedule(request: ForwardRequest) {
        val work =
            OneTimeWorkRequestBuilder<ForwardWorker>()
                .setInputData(ForwardWorker.inputDataFor(request))
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
        WorkManager.getInstance(context).enqueue(work)
    }
}
