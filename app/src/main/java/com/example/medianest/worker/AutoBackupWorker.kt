package com.example.medianest.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.medianest.data.backup.BackupRepository
import com.example.medianest.data.preferences.DownloadPreferences
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@HiltWorker
class AutoBackupWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted private val params: WorkerParameters,
    private val backupRepository: BackupRepository,
    private val downloadPreferences: DownloadPreferences
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val customFolder = downloadPreferences.downloadFolder.first()
            if (customFolder.isEmpty()) {
                return Result.failure()
            }
            val backupDir = File(customFolder, "backup")
            if (!backupDir.exists()) {
                if (!backupDir.mkdirs()) {
                    return Result.failure()
                }
            }

            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val backupFile = File(backupDir, "backup_metadata_${timestamp}.zip")

            backupFile.outputStream().use { fos ->
                backupRepository.exportToZip(fos, includeMedia = false) { _ -> }
            }

            // Prune to keep only up to 3 files
            val files = backupDir.listFiles { file ->
                file.isFile && file.name.startsWith("backup_") && file.name.endsWith(".zip")
            }
            if (files != null && files.size > 3) {
                val sortedFiles = files.sortedBy { it.lastModified() }
                for (i in 0 until (sortedFiles.size - 3)) {
                    val fileToDelete = sortedFiles[i]
                    val deleted = fileToDelete.delete()
                    if (!deleted) {
                        android.util.Log.w("AutoBackupWorker", "Failed to delete old backup: ${fileToDelete.name}")
                    }
                }
            }

            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
