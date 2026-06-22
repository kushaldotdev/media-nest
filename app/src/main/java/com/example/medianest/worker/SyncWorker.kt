package com.example.medianest.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.medianest.data.sync.SyncManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

import com.example.medianest.data.sync.SyncState

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted private val params: WorkerParameters,
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
