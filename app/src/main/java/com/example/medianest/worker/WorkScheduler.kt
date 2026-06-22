package com.example.medianest.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object WorkScheduler {
    fun scheduleSubscriptionCheck(context: Context) {
        val request = PeriodicWorkRequestBuilder<SubscriptionWorker>(
            6, TimeUnit.HOURS
        ).setConstraints(
            Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
        ).build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "subscription_check",
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    fun scheduleSync(context: Context, intervalHours: Long = 6) {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(
            intervalHours, TimeUnit.HOURS
        ).setConstraints(
            Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
        ).build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "sync_check",
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    fun updateSyncInterval(context: Context, intervalHours: Long) {
        WorkManager.getInstance(context).cancelUniqueWork("sync_check")
        scheduleSync(context, intervalHours)
    }
}
