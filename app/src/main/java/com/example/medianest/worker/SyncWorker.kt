package com.example.medianest.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.medianest.data.sync.SyncManager
import javax.inject.Inject

import com.example.medianest.data.sync.SyncState

@HiltWorker
class SyncWorker @Inject constructor(
    private val context: Context,
    private val params: WorkerParameters,
    private val syncManager: SyncManager
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val state = syncManager.sync()
            if (state is SyncState.Error) Result.retry() else Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
