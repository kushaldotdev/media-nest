package com.example.medianest.worker

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.example.medianest.MainActivity
import com.example.medianest.R
import com.example.medianest.data.local.dao.BulkDownloadDao
import com.example.medianest.data.local.entity.BulkDownloadItemStatus
import com.example.medianest.data.local.entity.BulkDownloadJobStatus
import com.example.medianest.data.preferences.DownloadPreferences
import com.example.medianest.data.repository.VideoRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

@HiltWorker
class BulkDownloadPreparationWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val bulkDownloadDao: BulkDownloadDao,
    private val videoRepository: VideoRepository,
    private val downloadPreferences: DownloadPreferences
) : CoroutineWorker(context, params) {

    companion object {
        const val INPUT_JOB_ID = "bulk_job_id"
        const val CHANNEL_ID = "bulk_downloads"
        const val NOTIFICATION_ID = 2002
        const val READY_NOTIFICATION_ID = 2003
        const val FAILED_NOTIFICATION_ID = 2004
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val jobId = inputData.getLong(INPUT_JOB_ID, -1L)
        if (jobId <= 0L) return@withContext Result.failure()

        val job = bulkDownloadDao.getJobOnce(jobId) ?: return@withContext Result.failure()
        try {
            bulkDownloadDao.updateJobStatus(jobId, BulkDownloadJobStatus.RUNNING)
            setForeground(createForegroundInfo(job.sourceName, 0, job.totalVideos, "Starting"))

            val sourceItems = bulkDownloadDao.getItemsOnce(jobId)

            var processed = 0
            var downloadable = 0
            var unavailable = 0
            var failed = 0
            var totalSize = 0L

            for ((index, item) in sourceItems.withIndex()) {
                val currentTitle = item.title.ifBlank { "Unknown" }
                bulkDownloadDao.updateJobState(
                    jobId = jobId,
                    status = BulkDownloadJobStatus.RUNNING,
                    processedVideos = processed,
                    currentTitle = currentTitle,
                    downloadableVideos = downloadable,
                    unavailableVideos = unavailable,
                    failedVideos = failed,
                    totalSizeBytes = totalSize,
                    usableSpaceBytes = 0L,
                    errorMessage = null
                )
                setForeground(createForegroundInfo(job.sourceName, processed, sourceItems.size, currentTitle))

                val info = try {
                    fetchVideoInfo(item.videoId)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    null
                }

                processed = index + 1
                if (info == null) {
                    failed++
                    bulkDownloadDao.updateItemStatus(item.id, BulkDownloadItemStatus.FAILED, "Failed to fetch metadata")
                    continue
                }

                val selected = selectStream(info, job.quality)
                if (selected == null) {
                    unavailable++
                    bulkDownloadDao.updateItemStatus(item.id, BulkDownloadItemStatus.UNAVAILABLE, "No ${job.quality} stream available")
                    continue
                }

                val estimatedSize = estimateSize(info, selected)
                totalSize += estimatedSize
                downloadable++
                bulkDownloadDao.updateItem(
                    itemId = item.id,
                    status = BulkDownloadItemStatus.READY,
                    quality = selected.quality,
                    format = selected.format,
                    codec = selected.codec,
                    url = selected.url,
                    contentLengthBytes = estimatedSize,
                    errorMessage = null
                )
            }

            val usableSpace = resolveUsableSpace()
            bulkDownloadDao.updateJobState(
                jobId = jobId,
                status = BulkDownloadJobStatus.READY,
                processedVideos = sourceItems.size,
                currentTitle = "Ready to confirm",
                downloadableVideos = downloadable,
                unavailableVideos = unavailable,
                failedVideos = failed,
                totalSizeBytes = totalSize,
                usableSpaceBytes = usableSpace,
                errorMessage = null
            )
            showStatusNotification(READY_NOTIFICATION_ID, createReadyNotification(job.sourceName, downloadable, sourceItems.size))

            Result.success()
        } catch (e: CancellationException) {
            bulkDownloadDao.updateJobStatus(jobId, BulkDownloadJobStatus.CANCELLED)
            throw e
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            bulkDownloadDao.markJobFailed(jobId, e.message ?: "Bulk download preparation failed")
            showStatusNotification(FAILED_NOTIFICATION_ID, createFailedNotification(job.sourceName))
            Result.failure()
        }
    }

    private suspend fun fetchVideoInfo(videoId: String) = withContext(Dispatchers.IO) {
        var attempts = 0
        while (attempts < 3) {
            try {
                return@withContext videoRepository.searchAndSave("https://www.youtube.com/watch?v=$videoId")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                attempts++
                if (attempts < 3) delay(1_000L)
            }
        }
        null
    }

    private fun selectStream(info: com.example.medianest.data.model.ExtractedVideoInfo, targetQuality: String): com.example.medianest.data.model.StreamSource? {
        if (targetQuality == "Audio") {
            return info.streamSources
                .filter { it.format == "audio" }
                .maxByOrNull { it.quality.replace("kbps", "").toIntOrNull() ?: 0 }
        }

        val targetHeight = Regex("""\d+""").find(targetQuality)?.value?.toIntOrNull() ?: 360
        val videoStreams = info.streamSources.filter { it.format == "video" || it.format == "video_only" }
        val groupedByHeight = videoStreams.groupBy { Regex("""\d+""").find(it.quality)?.value?.toIntOrNull() ?: 0 }
        val sortedHeights = (groupedByHeight.keys.filter { it <= targetHeight }.sortedDescending() +
            groupedByHeight.keys.filter { it > targetHeight }.sorted())
        val chosenHeight = sortedHeights.firstOrNull() ?: return null
        val streamsForHeight = groupedByHeight[chosenHeight] ?: return null

        return streamsForHeight.firstOrNull { it.format == "video" }
            ?: streamsForHeight.firstOrNull { it.format == "video_only" }
    }

    private fun estimateSize(info: com.example.medianest.data.model.ExtractedVideoInfo, selected: com.example.medianest.data.model.StreamSource): Long {
        val baseSize = selected.contentLength ?: 0L
        if (selected.format != "video_only") return baseSize

        val bestAudio = info.streamSources
            .filter { it.format == "audio" }
            .maxByOrNull { it.quality.replace("kbps", "").toIntOrNull() ?: 0 }
        return baseSize + (bestAudio?.contentLength ?: 0L)
    }

    private suspend fun resolveUsableSpace(): Long {
        val customFolder = downloadPreferences.downloadFolder.first().trim()
        val base = if (customFolder.isEmpty()) {
            applicationContext.getExternalFilesDir(null) ?: applicationContext.filesDir
        } else {
            java.io.File(customFolder)
        }
        if (!base.exists()) base.mkdirs()
        return base.usableSpace
    }

    private fun createForegroundInfo(sourceName: String, processed: Int, total: Int, currentTitle: String): ForegroundInfo {
        val notification = buildNotification(
            title = "Preparing bulk download",
            text = "$sourceName: $processed of $total videos",
            progress = if (total > 0) (processed * 100 / total).coerceIn(0, 100) else 0,
            indeterminate = total <= 0,
            contentText = currentTitle,
            ongoing = true
        )
        return ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    }

    private fun createReadyNotification(sourceName: String, downloadable: Int, total: Int): Notification {
        return buildNotification(
            title = "Bulk download ready",
            text = "$sourceName: $downloadable of $total videos ready",
            progress = 100,
            indeterminate = false,
            contentText = "Open MediaNest to confirm",
            ongoing = false
        )
    }

    private fun createFailedNotification(sourceName: String): Notification {
        return buildNotification(
            title = "Bulk download failed",
            text = sourceName,
            progress = 0,
            indeterminate = false,
            contentText = "Tap to reopen MediaNest",
            ongoing = false
        )
    }

    private fun showStatusNotification(notificationId: Int, notification: Notification) {
        try {
            NotificationManagerCompat.from(applicationContext).notify(notificationId, notification)
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS may be denied; DB state still drives in-app confirmation/error UI.
        }
    }

    private fun buildNotification(title: String, text: String, progress: Int, indeterminate: Boolean, contentText: String, ongoing: Boolean): Notification {
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$text\n$contentText"))
            .setContentIntent(pendingIntent)
            .setOngoing(ongoing)
            .setAutoCancel(!ongoing)
            .setOnlyAlertOnce(true)
            .setProgress(100, progress, indeterminate)
            .build()
    }
}
