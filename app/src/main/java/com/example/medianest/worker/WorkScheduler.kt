package com.example.medianest.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object WorkScheduler {
    const val BULK_DOWNLOAD_PREP_WORK_NAME = "bulk_download_prep"

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

    fun scheduleSync(
        context: Context,
        intervalHours: Long = 6,
        policy: ExistingPeriodicWorkPolicy = ExistingPeriodicWorkPolicy.KEEP
    ) {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(
            intervalHours, TimeUnit.HOURS
        ).setConstraints(
            Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
        ).build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "sync_check",
            policy,
            request
        )
    }

    fun updateSyncInterval(context: Context, intervalHours: Long) {
        scheduleSync(context, intervalHours, ExistingPeriodicWorkPolicy.REPLACE)
    }

    fun scheduleAutoBackup(
        context: Context,
        intervalHours: Long,
        policy: ExistingPeriodicWorkPolicy = ExistingPeriodicWorkPolicy.KEEP
    ) {
        if (intervalHours <= 0) {
            cancelAutoBackup(context)
            return
        }
        val request = PeriodicWorkRequestBuilder<AutoBackupWorker>(
            intervalHours, TimeUnit.HOURS
        ).build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "auto_backup",
            policy,
            request
        )
    }

    fun cancelAutoBackup(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork("auto_backup")
    }

    fun updateAutoBackupInterval(context: Context, intervalHours: Long) {
        if (intervalHours <= 0) {
            cancelAutoBackup(context)
        } else {
            scheduleAutoBackup(context, intervalHours, ExistingPeriodicWorkPolicy.REPLACE)
        }
    }

    fun enqueueBulkDownloadPreparation(context: Context, jobId: Long) {
        val request = OneTimeWorkRequestBuilder<BulkDownloadPreparationWorker>()
            .setInputData(
                Data.Builder()
                    .putLong(BulkDownloadPreparationWorker.INPUT_JOB_ID, jobId)
                    .build()
            )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            BULK_DOWNLOAD_PREP_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }
}
