package com.example.medianest.worker

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.medianest.MainActivity
import com.example.medianest.R
import com.example.medianest.updates.UpdateManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Periodic worker that checks GitHub for a newer app version and posts a
 * notification ("Update available vX.Y.Z") when one is found. Tapping the
 * notification opens the app on the Settings screen.
 *
 * Check-only by design: it never starts a download.
 */
@HiltWorker
class UpdateCheckWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted private val params: WorkerParameters,
    private val updateManager: UpdateManager
) : CoroutineWorker(context, params) {

    companion object {
        const val CHANNEL_ID = "app_updates"
        const val NOTIFICATION_ID = 2006
    }

    override suspend fun doWork(): Result {
        val result = updateManager.performUpdateCheckForWorker()

        if (result.errorMessage != null) {
            // Log and stop — do not retry-loop (GitHub unauthenticated 403).
            android.util.Log.w("UpdateCheckWorker", "Update check failed: ${result.errorMessage}")
            return Result.success()
        }

        if (result.updateAvailable) {
            showUpdateAvailableNotification(result.latestVersion, result.changelog)
        }
        return Result.success()
    }

    private fun showUpdateAvailableNotification(latestVersion: String, changelog: String) {
        try {
            val intent = Intent(context, MainActivity::class.java).apply {
                action = "com.example.medianest.ACTION_NAVIGATE_UPDATES"
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val snippet = changelog.trim().lineSequence().firstOrNull()?.take(120) ?: "Tap to view and download."
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("Update available v$latestVersion")
                .setContentText(snippet)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()

            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS may be denied; the Settings screen still surfaces the state.
        }
    }
}
