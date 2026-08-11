package com.example.medianest.worker

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.FileProvider
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.medianest.MainActivity
import com.example.medianest.R
import com.example.medianest.data.preferences.UpdatePreferences
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

@HiltWorker
class UpdateDownloadWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted private val params: WorkerParameters,
    private val okHttpClient: OkHttpClient,
    private val updatePreferences: UpdatePreferences
) : CoroutineWorker(context, params) {

    companion object {
        const val KEY_URL = "download_url"
        const val KEY_PROGRESS = "progress"
        const val KEY_ERROR = "error"
        const val CHANNEL_ID = "app_updates"
        const val NOTIFICATION_ID = 2005
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        return createForegroundInfo(0)
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val downloadUrl = inputData.getString(KEY_URL) ?: return@withContext Result.failure()

        // Set initial foreground info
        try {
            setForeground(createForegroundInfo(0))
        } catch (e: Exception) {
            // Background start/foreground restriction might cause this, but we log and proceed
            android.util.Log.e("UpdateDownloadWorker", "Failed to setForeground: ${e.message}")
        }

        updatePreferences.setState(UpdatePreferences.STATE_DOWNLOADING, progress = 0f)

        try {
            val request = Request.Builder().url(downloadUrl).build()
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw Exception("Download failed: HTTP ${response.code}")
                val body = response.body ?: throw Exception("Response body is empty")
                val totalBytes = body.contentLength()
                val file = File(context.filesDir, "update.apk")
                if (file.exists()) file.delete()

                body.byteStream().use { input ->
                    file.outputStream().use { output ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        var totalBytesRead = 0L
                        var lastProgressUpdate = 0L
                        var lastMarkerProgress = -1f
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            totalBytesRead += bytesRead
                            if (totalBytes > 0) {
                                val progressFloat = totalBytesRead.toFloat() / totalBytes.toFloat()
                                val progressPercent = (progressFloat * 100).toInt().coerceIn(0, 100)
                                val now = System.currentTimeMillis()
                                if (now - lastProgressUpdate > 300) {
                                    setProgress(workDataOf(KEY_PROGRESS to progressFloat))
                                    try {
                                        setForeground(createForegroundInfo(progressPercent))
                                    } catch (e: Exception) {
                                        android.util.Log.e("UpdateDownloadWorker", "Failed to update setForeground: ${e.message}")
                                    }
                                    lastProgressUpdate = now
                                }
                                // Persist progress marker throttled to >=5% steps so a fresh
                                // process/ViewModel can rehydrate the progress bar.
                                if (progressFloat - lastMarkerProgress >= 0.05f) {
                                    updatePreferences.setProgress(progressFloat)
                                    lastMarkerProgress = progressFloat
                                }
                            }
                        }
                    }
                }

                // Final progress
                setProgress(workDataOf(KEY_PROGRESS to 1f))
                updatePreferences.setProgress(1f)

                // Show completion notification with install intent
                showCompletionNotification(file)

                updatePreferences.setState(UpdatePreferences.STATE_READY)

                Result.success()
            }
        } catch (e: Exception) {
            val errorMsg = e.message ?: "Unknown error"
            showErrorNotification(errorMsg)
            updatePreferences.setState(UpdatePreferences.STATE_FAILED, error = errorMsg)
            Result.failure(workDataOf(KEY_ERROR to errorMsg))
        }
    }

    private fun createForegroundInfo(progress: Int): ForegroundInfo {
        val title = "Downloading Update"
        val text = "Progress: $progress%"

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, progress, false)
            .build()

        val foregroundServiceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        } else {
            0
        }

        return ForegroundInfo(NOTIFICATION_ID, notification, foregroundServiceType)
    }

    private fun showCompletionNotification(file: File) {
        try {
            val apkUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }

            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                installIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("Update Downloaded")
                .setContentText("Tap to install the update")
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()

            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS may be denied
        }
    }

    private fun showErrorNotification(errorMsg: String) {
        try {
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("Update Download Failed")
                .setContentText(errorMsg)
                .setAutoCancel(true)
                .build()

            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS may be denied
        }
    }
}
