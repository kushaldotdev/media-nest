package com.example.medianest.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.medianest.MainActivity
import com.example.medianest.data.local.entity.DownloadEntity
import com.example.medianest.data.local.entity.DownloadStatus
import com.example.medianest.data.preferences.DownloadPreferences
import com.example.medianest.data.repository.DownloadRepository
import com.example.medianest.extraction.YouTubeExtractor
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

@AndroidEntryPoint
class DownloadService : Service() {

    companion object {
        const val CHANNEL_ID = "downloads"
        const val NOTIFICATION_ID = 1001
        const val ACTION_PAUSE = "com.example.medianest.PAUSE_DOWNLOAD"
        const val ACTION_RESUME = "com.example.medianest.RESUME_DOWNLOAD"
        const val ACTION_CANCEL = "com.example.medianest.CANCEL_DOWNLOAD"
        const val EXTRA_DOWNLOAD_ID = "download_id"

        fun pause(context: Context, downloadId: Long) {
            val intent = Intent(context, DownloadService::class.java).apply {
                action = ACTION_PAUSE
                putExtra(EXTRA_DOWNLOAD_ID, downloadId)
            }
            context.startForegroundService(intent)
        }

        fun resume(context: Context, downloadId: Long) {
            val intent = Intent(context, DownloadService::class.java).apply {
                action = ACTION_RESUME
                putExtra(EXTRA_DOWNLOAD_ID, downloadId)
            }
            context.startForegroundService(intent)
        }

        fun cancel(context: Context, downloadId: Long) {
            val intent = Intent(context, DownloadService::class.java).apply {
                action = ACTION_CANCEL
                putExtra(EXTRA_DOWNLOAD_ID, downloadId)
            }
            context.startForegroundService(intent)
        }
    }

    @Inject lateinit var repository: DownloadRepository
    @Inject lateinit var preferences: DownloadPreferences
    @Inject lateinit var extractor: YouTubeExtractor
    @Inject lateinit var okHttpClient: OkHttpClient

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activeJobs = mutableMapOf<Long, Job>()
    private var maxConcurrent = 2
    private val globalThrottle = Semaphore(maxConcurrent)

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        serviceScope.launch {
            maxConcurrent = preferences.maxConcurrentDownloads.first()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PAUSE -> {
                val id = intent.getLongExtra(EXTRA_DOWNLOAD_ID, -1)
                if (id != -1L) pauseDownload(id)
            }
            ACTION_RESUME -> {
                val id = intent.getLongExtra(EXTRA_DOWNLOAD_ID, -1)
                if (id != -1L) resumeDownload(id)
            }
            ACTION_CANCEL -> {
                val id = intent.getLongExtra(EXTRA_DOWNLOAD_ID, -1)
                if (id != -1L) cancelDownload(id)
            }
            else -> startForeground(NOTIFICATION_ID, buildNotification(0, 0))
        }
        processQueue()
        return START_STICKY
    }

    private fun processQueue() {
        serviceScope.launch {
            val queue = repository.getDownloadsByStatus(DownloadStatus.QUEUED).first()
            val active = repository.getActiveDownloadCount()
            val limit = maxConcurrent
            val slots = (limit - active).coerceAtLeast(0)
            queue.take(slots).forEach { enqueueDownload(it) }
        }
    }

    private fun enqueueDownload(download: DownloadEntity) {
        val job = serviceScope.launch {
            globalThrottle.acquire()
            try {
                downloadFile(download)
            } finally {
                globalThrottle.release()
                processQueue()
            }
        }
        activeJobs[download.id] = job
    }

    private suspend fun downloadFile(download: DownloadEntity) {
        repository.updateStatus(download.id, DownloadStatus.DOWNLOADING, 0f)
        updateNotification(download.id)

        val dir = if (download.format == "audio") "audio" else "video"
        val outputDir = File(filesDir, "MediaNest/$dir")
        outputDir.mkdirs()

        var currentUrl = download.url
        var retries = 0
        val maxRetries = 3

        while (retries <= maxRetries) {
            try {
                val request = Request.Builder().url(currentUrl).build()
                val response = withContext(Dispatchers.IO) {
                    okHttpClient.newCall(request).execute()
                }

                if (!response.isSuccessful) {
                    if ((response.code == 403 || response.code == 410) && retries < maxRetries) {
                        retries++
                        repository.update(download.copy(retryCount = retries))
                        val freshInfo = extractor.extractVideo(download.url)
                        val matchingStream = freshInfo.streamSources.find {
                            it.format == download.format && it.quality == download.quality
                        }
                        if (matchingStream != null) {
                            currentUrl = matchingStream.url
                            continue
                        }
                    }
                    repository.markFailed(download.id, "HTTP ${response.code}", retries)
                    return
                }

                val contentLength = response.body?.contentLength() ?: -1L
                val mimeType = response.body?.contentType()?.toString() ?: "video/mp4"
                val ext = mimeType.split("/").lastOrNull()?.split(";")?.first() ?: "mp4"
                val fileName = "${download.videoId}_${download.quality}.$ext"
                val tmpFile = File(outputDir, "${fileName}.tmp")
                val outputFile = File(outputDir, fileName)

                response.body?.byteStream()?.use { input ->
                    FileOutputStream(tmpFile).use { output ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Long = 0
                        var lastProgressUpdate = 0L

                        while (true) {
                            if (isPaused(download.id)) {
                                repository.updateStatus(download.id, DownloadStatus.PAUSED,
                                    if (contentLength > 0) bytesRead.toFloat() / contentLength else 0f)
                                return
                            }
                            if (isCancelled(download.id)) {
                                tmpFile.delete()
                                repository.delete(download)
                                return
                            }

                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            bytesRead += read

                            if (contentLength > 0 && bytesRead - lastProgressUpdate > 65536) {
                                val progress = bytesRead.toFloat() / contentLength
                                repository.updateStatus(download.id, DownloadStatus.DOWNLOADING, progress)
                                updateNotification(download.id, bytesRead, contentLength)
                                lastProgressUpdate = bytesRead
                            }
                        }

                        tmpFile.renameTo(outputFile)
                        repository.markCompleted(download.id, bytesRead)
                        repository.update(download.copy(filePath = outputFile.absolutePath, fileSizeBytes = bytesRead))
                        updateNotification(download.id, bytesRead, contentLength)
                    }
                }
                return
            } catch (e: CancellationException) {
                return
            } catch (e: Exception) {
                if (retries < maxRetries) {
                    retries++
                    repository.update(download.copy(retryCount = retries))
                    continue
                }
                repository.markFailed(download.id, e.message ?: "Download failed", retries)
                return
            }
        }
    }

    private suspend fun isPaused(id: Long): Boolean {
        val entity = repository.getDownloadById(id) ?: return false
        return entity.status == DownloadStatus.PAUSED
    }

    private fun isCancelled(id: Long): Boolean {
        return !activeJobs.containsKey(id)
    }

    private fun pauseDownload(id: Long) {
        activeJobs[id]?.cancel()
        activeJobs.remove(id)
        serviceScope.launch {
            repository.updateStatus(id, DownloadStatus.PAUSED, 0f)
            processQueue()
        }
    }

    private fun resumeDownload(id: Long) {
        serviceScope.launch {
            val download = repository.getDownloadById(id) ?: return@launch
            if (download.status == DownloadStatus.PAUSED) {
                repository.updateStatus(id, DownloadStatus.QUEUED, download.progress)
                processQueue()
            }
        }
    }

    private fun cancelDownload(id: Long) {
        activeJobs[id]?.cancel()
        activeJobs.remove(id)
        serviceScope.launch {
            val download = repository.getDownloadById(id) ?: return@launch
            if (download.filePath.isNotEmpty()) {
                File(download.filePath).delete()
            }
            repository.delete(download)
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_LOW)
            .setName("Downloads")
            .build()
        NotificationManagerCompat.from(this).createNotificationChannel(channel)
    }

    private fun buildNotification(progress: Int, max: Int): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Downloading...")
            .setContentText("$progress / $max files")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setProgress(max, progress, false)
            .setContentIntent(
                PendingIntent.getActivity(
                    this, 0,
                    Intent(this, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
                    },
                    PendingIntent.FLAG_IMMUTABLE
                )
            )
            .build()
    }

    private fun updateNotification(downloadId: Long, bytesDownloaded: Long = 0, totalBytes: Long = 0) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Downloading...")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .apply {
                if (totalBytes > 0) {
                    setProgress(totalBytes.toInt(), bytesDownloaded.toInt(), false)
                    setContentText("${bytesDownloaded / 1024}KB / ${totalBytes / 1024}KB")
                } else {
                    setProgress(0, 0, true)
                }
            }
            .setContentIntent(
                PendingIntent.getActivity(
                    this, 0,
                    Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE
                )
            )
            .build()
        NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }
}
