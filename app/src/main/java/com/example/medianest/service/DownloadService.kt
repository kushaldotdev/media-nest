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
import com.example.medianest.data.local.dao.VideoDao
import com.example.medianest.data.local.entity.DownloadEntity
import com.example.medianest.data.local.entity.DownloadStatus
import com.example.medianest.data.local.entity.VideoEntity
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

@AndroidEntryPoint
class DownloadService : Service() {

    companion object {
        const val CHANNEL_ID = "downloads"
        const val NOTIFICATION_ID = 1001
        const val ACTION_PAUSE = "com.example.medianest.PAUSE_DOWNLOAD"
        const val ACTION_RESUME = "com.example.medianest.RESUME_DOWNLOAD"
        const val ACTION_CANCEL = "com.example.medianest.CANCEL_DOWNLOAD"
        const val ACTION_RESTART = "com.example.medianest.RESTART_DOWNLOAD"
        const val ACTION_PAUSE_ALL = "com.example.medianest.PAUSE_ALL_DOWNLOADS"
        const val ACTION_RESUME_ALL = "com.example.medianest.RESUME_ALL_DOWNLOADS"
        const val ACTION_CANCEL_ALL = "com.example.medianest.CANCEL_ALL_DOWNLOADS"
        const val EXTRA_DOWNLOAD_ID = "download_id"

        fun pause(context: Context, downloadId: Long) {
            val intent = Intent(context, DownloadService::class.java).apply {
                action = ACTION_PAUSE
                putExtra(EXTRA_DOWNLOAD_ID, downloadId)
            }
            try {
                context.startForegroundService(intent)
            } catch (e: Exception) {
                android.util.Log.e("DownloadService", "Failed to start pause command", e)
            }
        }

        fun resume(context: Context, downloadId: Long) {
            val intent = Intent(context, DownloadService::class.java).apply {
                action = ACTION_RESUME
                putExtra(EXTRA_DOWNLOAD_ID, downloadId)
            }
            try {
                context.startForegroundService(intent)
            } catch (e: Exception) {
                android.util.Log.e("DownloadService", "Failed to start resume command", e)
            }
        }

        fun cancel(context: Context, downloadId: Long) {
            val intent = Intent(context, DownloadService::class.java).apply {
                action = ACTION_CANCEL
                putExtra(EXTRA_DOWNLOAD_ID, downloadId)
            }
            try {
                context.startForegroundService(intent)
            } catch (e: Exception) {
                android.util.Log.e("DownloadService", "Failed to start cancel command", e)
            }
        }
    }

    @Inject lateinit var repository: DownloadRepository
    @Inject lateinit var preferences: DownloadPreferences
    @Inject lateinit var extractor: YouTubeExtractor
    @Inject lateinit var okHttpClient: OkHttpClient
    @Inject lateinit var videoDao: VideoDao

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    data class ActiveProgress(
        val title: String,
        val bytesDownloaded: Long,
        val totalBytes: Long
    )

    private val activeJobs = ConcurrentHashMap<Long, Job>()
    private val pauseFlags = ConcurrentHashMap<Long, Boolean>()
    private val cancelFlags = ConcurrentHashMap<Long, Boolean>()
    private val putToQueueFlags = ConcurrentHashMap<Long, Boolean>()
    private val activeProgress = ConcurrentHashMap<Long, ActiveProgress>()
    private var isFirstStart = true

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        serviceScope.launch {
            repository.resetStaleDownloads()
            preferences.maxConcurrentDownloads.collect { max ->
                withContext(Dispatchers.Main) {
                    processQueue()
                }
            }
        }
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            startForeground(NOTIFICATION_ID, buildNotification(0, 0))
        } catch (e: Exception) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S
                && e.javaClass.name == "android.app.ForegroundServiceStartNotAllowedException") {
                stopSelf()
                return START_NOT_STICKY
            }
            throw e
        }
        serviceScope.launch {
            withContext(Dispatchers.Main) {
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
                    ACTION_RESTART -> {
                        val id = intent.getLongExtra(EXTRA_DOWNLOAD_ID, -1)
                        if (id != -1L) restartDownload(id)
                    }
                    ACTION_PAUSE_ALL -> {
                        pauseAllDownloads()
                    }
                    ACTION_RESUME_ALL -> {
                        resumeAllDownloads()
                    }
                    ACTION_CANCEL_ALL -> {
                        cancelAllDownloads()
                    }
                    else -> processQueue()
                }
            }
        }
        return START_STICKY
    }

    private fun processQueue() {
        serviceScope.launch {
            val maxConcurrent = preferences.maxConcurrentDownloads.first()
            val queue = repository.getDownloadsByStatus(DownloadStatus.QUEUED).first()
            val active = repository.getActiveDownloadCount()
            val paused = repository.getDownloadsByStatus(DownloadStatus.PAUSED).first()

            val downloading = repository.getDownloadsByStatus(DownloadStatus.DOWNLOADING).first()
            if (downloading.size > maxConcurrent) {
                val excess = downloading.size - maxConcurrent
                downloading.takeLast(excess).forEach { download ->
                    putDownloadingToQueue(download.id)
                }
                return@launch
            }

            val slots = (maxConcurrent - active).coerceAtLeast(0)

            if (queue.isEmpty() && active == 0 && activeJobs.isEmpty() && paused.isEmpty()) {
                try {
                    NotificationManagerCompat.from(this@DownloadService).cancel(NOTIFICATION_ID)
                } catch (_: SecurityException) { }
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return@launch
            }
            queue.take(slots).forEach { enqueueDownload(it) }
            updateNotification()
        }
    }

    private fun enqueueDownload(download: DownloadEntity) {
        pauseFlags.remove(download.id)
        cancelFlags.remove(download.id)
        putToQueueFlags.remove(download.id)
        activeProgress.remove(download.id)
        val job = serviceScope.launch {
            try {
                downloadFile(download)
            } finally {
                activeJobs.remove(download.id)
                pauseFlags.remove(download.id)
                cancelFlags.remove(download.id)
                putToQueueFlags.remove(download.id)
                activeProgress.remove(download.id)
                updateNotification()
                processQueue()
            }
        }
        activeJobs[download.id] = job
    }

    private suspend fun downloadFile(download: DownloadEntity) {
        repository.updateStatus(download.id, DownloadStatus.DOWNLOADING, download.progress)
        
        val dir = if (download.format == "audio") "audio" else "video"
        val outputDir = File(filesDir, "MediaNest/$dir")
        outputDir.mkdirs()

        val tmpFile = File(outputDir, "${download.videoId}_${download.quality}.tmp")
        
        val initialBytes = if (tmpFile.exists()) tmpFile.length() else 0L
        activeProgress[download.id] = ActiveProgress(
            title = download.title.ifEmpty { download.quality },
            bytesDownloaded = initialBytes,
            totalBytes = download.fileSizeBytes
        )
        updateNotification()

        var currentUrl = download.url
        var retries = 0
        val maxRetries = 3

        while (retries <= maxRetries) {
            try {
                val existingBytes = if (tmpFile.exists()) tmpFile.length() else 0L

                val requestBuilder = Request.Builder().url(currentUrl)
                if (existingBytes > 0) {
                    requestBuilder.header("Range", "bytes=$existingBytes-")
                }
                requestBuilder.header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                requestBuilder.header("Accept-Encoding", "identity")
                val request = requestBuilder.build()

                val response = withContext(Dispatchers.IO) {
                    okHttpClient.newCall(request).execute()
                }

                if (!response.isSuccessful) {
                    if (response.code == 416) {
                        tmpFile.delete()
                        retries++
                        continue
                    }
                    if ((response.code == 403 || response.code == 410) && retries < maxRetries) {
                        retries++
                        repository.update(download.copy(retryCount = retries))
                        val freshInfo = extractor.extractVideo(download.videoUrl ?: download.url)
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

                val isRange = response.code == 206
                val actualExistingBytes = if (isRange) existingBytes else 0L
                if (!isRange && tmpFile.exists()) {
                    tmpFile.delete()
                }

                val responseLength = response.body?.contentLength() ?: -1L
                val totalLength = if (responseLength > 0) actualExistingBytes + responseLength else -1L

                if (totalLength > 0 && download.fileSizeBytes != totalLength) {
                    repository.update(download.copy(fileSizeBytes = totalLength))
                }

                val mimeType = response.body?.contentType()?.toString() ?: "video/mp4"
                val ext = mimeType.split("/").lastOrNull()?.split(";")?.first() ?: "mp4"
                val fileName = "${download.videoId}_${download.quality}.$ext"
                val outputFile = File(outputDir, fileName)

                response.body?.byteStream()?.use { input ->
                    FileOutputStream(tmpFile, isRange).use { output ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Long = actualExistingBytes
                        var lastProgressUpdate = 0L

                        while (true) {
                            if (isPaused(download.id)) {
                                repository.updateStatus(download.id, DownloadStatus.PAUSED,
                                    if (totalLength > 0) bytesRead.toFloat() / totalLength else 0f)
                                activeJobs.remove(download.id)
                                return
                            }
                            if (isCancelled(download.id)) {
                                tmpFile.delete()
                                repository.updateStatus(download.id, DownloadStatus.CANCELED, download.progress)
                                return
                            }
                            if (isPutToQueue(download.id)) {
                                repository.updateStatus(download.id, DownloadStatus.QUEUED,
                                    if (totalLength > 0) bytesRead.toFloat() / totalLength else 0f)
                                activeJobs.remove(download.id)
                                return
                            }

                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            bytesRead += read

                            if (totalLength > 0 && bytesRead - lastProgressUpdate > 65536) {
                                val progress = bytesRead.toFloat() / totalLength
                                repository.updateStatus(download.id, DownloadStatus.DOWNLOADING, progress)
                                activeProgress[download.id] = ActiveProgress(
                                    title = download.title.ifEmpty { download.quality },
                                    bytesDownloaded = bytesRead,
                                    totalBytes = totalLength
                                )
                                updateNotification()
                                lastProgressUpdate = bytesRead
                            }
                        }

                        if (!tmpFile.renameTo(outputFile)) {
                            try {
                                tmpFile.copyTo(outputFile, overwrite = true)
                                tmpFile.delete()
                            } catch (e: Exception) {
                                throw IOException("Failed to rename temp file to $outputFile: ${e.message}")
                            }
                        }
                        repository.markCompleted(download.id, bytesRead, outputFile.absolutePath)
                        activeProgress.remove(download.id)
                        updateNotification()

                        // Persist video metadata for offline playback
                        val existing = videoDao.getVideoById(download.videoId)
                        if (existing == null) {
                            videoDao.insert(
                                VideoEntity(
                                    id = download.videoId,
                                    title = download.title.ifEmpty { download.quality },
                                    channelName = "",
                                    durationSeconds = 0,
                                    thumbnailUrl = download.thumbnailUrl,
                                    localFilePath = outputFile.absolutePath
                                )
                            )
                        } else {
                            videoDao.update(existing.copy(localFilePath = outputFile.absolutePath))
                        }
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
                tmpFile.delete()
                repository.markFailed(download.id, e.message ?: "Download failed", retries)
                return
            }
        }
    }
      private fun isPaused(id: Long): Boolean = pauseFlags[id] == true
  
      private fun isCancelled(id: Long): Boolean = cancelFlags[id] == true

      private fun isPutToQueue(id: Long): Boolean = putToQueueFlags[id] == true
  
      private fun pauseDownload(id: Long) {
          pauseFlags[id] = true
          activeJobs[id]?.cancel()
          activeJobs.remove(id)
          serviceScope.launch {
              val download = repository.getDownloadById(id) ?: return@launch
              repository.updateStatus(id, DownloadStatus.PAUSED, download.progress)
              processQueue()
          }
      }

      private fun putDownloadingToQueue(id: Long) {
          putToQueueFlags[id] = true
          activeJobs[id]?.cancel()
          activeJobs.remove(id)
          serviceScope.launch {
              val download = repository.getDownloadById(id) ?: return@launch
              repository.updateStatus(id, DownloadStatus.QUEUED, download.progress)
              putToQueueFlags.remove(id)
              processQueue()
          }
      }
 
     private fun resumeDownload(id: Long) {
         pauseFlags.remove(id)
         serviceScope.launch {
             val download = repository.getDownloadById(id) ?: return@launch
             if (download.status == DownloadStatus.PAUSED || download.status == DownloadStatus.QUEUED) {
                 if (download.status == DownloadStatus.PAUSED) {
                     repository.updateStatus(id, DownloadStatus.QUEUED, download.progress)
                 }
                 processQueue()
             }
         }
     }
 
      private fun cancelDownload(id: Long) {
          cancelFlags[id] = true
          pauseFlags.remove(id)
          activeJobs[id]?.cancel()
          activeJobs.remove(id)
          serviceScope.launch {
              val download = repository.getDownloadById(id) ?: return@launch
              if (download.filePath.isNotEmpty()) {
                  File(download.filePath).delete()
              }
              val dir = if (download.format == "audio") "audio" else "video"
              val outputDir = File(filesDir, "MediaNest/$dir")
              val tmpFile = File(outputDir, "${download.videoId}_${download.quality}.tmp")
              if (tmpFile.exists()) {
                  tmpFile.delete()
              }
              repository.updateStatus(id, DownloadStatus.CANCELED, download.progress)
              processQueue()
          }
      }

      private fun restartDownload(id: Long) {
          serviceScope.launch {
              val download = repository.getDownloadById(id) ?: return@launch
              try {
                  val dir = if (download.format == "audio" || download.format == "audio_extracted") "audio" else "video"
                  val outputDir = File(filesDir, "MediaNest/$dir")
                  val tmpFile = File(outputDir, "${download.videoId}_${download.quality}.tmp")
                  if (tmpFile.exists()) {
                      tmpFile.delete()
                  }
              } catch (e: Exception) {
                  android.util.Log.e("DownloadService", "Failed to delete tmp file on restart", e)
              }
              val reset = download.copy(
                  status = DownloadStatus.QUEUED,
                  progress = 0f,
                  errorMessage = null,
                  retryCount = 0
              )
              repository.update(reset)
              resumeDownload(id)
          }
      }

      private fun pauseAllDownloads() {
          serviceScope.launch {
              val downloading = repository.getDownloadsByStatus(DownloadStatus.DOWNLOADING).first()
              val queued = repository.getDownloadsByStatus(DownloadStatus.QUEUED).first()
              downloading.forEach { pauseDownload(it.id) }
              queued.forEach { pauseDownload(it.id) }
          }
      }

      private fun resumeAllDownloads() {
          serviceScope.launch {
              val paused = repository.getDownloadsByStatus(DownloadStatus.PAUSED).first()
              paused.forEach { resumeDownload(it.id) }
          }
      }

      private fun cancelAllDownloads() {
          serviceScope.launch {
              val downloading = repository.getDownloadsByStatus(DownloadStatus.DOWNLOADING).first()
              val queued = repository.getDownloadsByStatus(DownloadStatus.QUEUED).first()
              val paused = repository.getDownloadsByStatus(DownloadStatus.PAUSED).first()
              
              downloading.forEach { cancelDownload(it.id) }
              queued.forEach { cancelDownload(it.id) }
              paused.forEach { cancelDownload(it.id) }
          }
      }
 
     private fun createNotificationChannel() {
         val channel = NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_LOW)
             .setName("Downloads")
             .build()
         NotificationManagerCompat.from(this).createNotificationChannel(channel)
     }
 
    private fun buildNotification(progress: Int, max: Int): Notification {
        val title = if (max > 0) "Downloading $progress of $max" else "Preparing download…"
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setProgress(max, progress, max == 0)
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

    private suspend fun updateNotification() {
        val paused = repository.getDownloadsByStatus(DownloadStatus.PAUSED).first()

        if (activeProgress.isEmpty()) {
            if (paused.isEmpty()) {
                val title = if (activeJobs.isNotEmpty()) "Preparing download…" else "Downloads paused"
                val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                    .setContentTitle(title)
                    .setSmallIcon(android.R.drawable.stat_sys_download)
                    .setOngoing(true)
                    .setProgress(0, 0, activeJobs.isNotEmpty())
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
                try {
                    NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification)
                } catch (_: SecurityException) {}
                return
            }

            // Single/multiple paused downloads
            val notification = if (paused.size == 1) {
                val download = paused.first()
                val pct = (download.progress * 100).toInt()
                val downloadedMb = "%.1f".format((download.progress * download.fileSizeBytes) / (1024f * 1024f))
                val totalMb = "%.1f".format(download.fileSizeBytes / (1024f * 1024f))
                val contentText = if (download.fileSizeBytes > 0) {
                    "Paused — $pct% (${downloadedMb}MB / ${totalMb}MB)"
                } else {
                    "Paused"
                }

                val resumeIntent = Intent(this, DownloadService::class.java).apply {
                    action = ACTION_RESUME
                    putExtra(EXTRA_DOWNLOAD_ID, download.id)
                }
                val pendingResume = PendingIntent.getForegroundService(
                    this, download.id.toInt() + 4000,
                    resumeIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                val cancelIntent = Intent(this, DownloadService::class.java).apply {
                    action = ACTION_CANCEL
                    putExtra(EXTRA_DOWNLOAD_ID, download.id)
                }
                val pendingCancel = PendingIntent.getForegroundService(
                    this, download.id.toInt() + 5000,
                    cancelIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                val restartIntent = Intent(this, MainActivity::class.java).apply {
                    action = "com.example.medianest.ACTION_CONFIRM_RESTART"
                    putExtra("download_id", download.id)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
                val pendingRestart = PendingIntent.getActivity(
                    this, download.id.toInt() + 6000,
                    restartIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                NotificationCompat.Builder(this, CHANNEL_ID)
                    .setContentTitle(download.title.ifEmpty { download.quality })
                    .setContentText(contentText)
                    .setSmallIcon(android.R.drawable.stat_sys_download)
                    .setOngoing(true)
                    .setProgress(100, pct, download.fileSizeBytes <= 0)
                    .addAction(android.R.drawable.ic_media_play, "Resume", pendingResume)
                    .addAction(android.R.drawable.ic_menu_revert, "Restart", pendingRestart)
                    .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel", pendingCancel)
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
            } else {
                val resumeAllIntent = Intent(this, DownloadService::class.java).apply {
                    action = ACTION_RESUME_ALL
                }
                val pendingResumeAll = PendingIntent.getForegroundService(
                    this, 7000,
                    resumeAllIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                val cancelAllIntent = Intent(this, DownloadService::class.java).apply {
                    action = ACTION_CANCEL_ALL
                }
                val pendingCancelAll = PendingIntent.getForegroundService(
                    this, 8000,
                    cancelAllIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                NotificationCompat.Builder(this, CHANNEL_ID)
                    .setContentTitle("Downloads paused")
                    .setContentText("${paused.size} downloads paused")
                    .setSmallIcon(android.R.drawable.stat_sys_download)
                    .setOngoing(true)
                    .addAction(android.R.drawable.ic_media_play, "Resume All", pendingResumeAll)
                    .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel All", pendingCancelAll)
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

            try {
                NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification)
            } catch (_: SecurityException) {}
            return
        }

        val notification = if (activeProgress.size == 1) {
            val downloadId = activeProgress.keys.first()
            val active = activeProgress.values.first()
            val pct = if (active.totalBytes > 0) ((active.bytesDownloaded * 100) / active.totalBytes).toInt() else 0
            val downloadedMb = "%.1f".format(active.bytesDownloaded / (1024f * 1024f))
            val totalMb = "%.1f".format(active.totalBytes / (1024f * 1024f))
            
            val pauseIntent = Intent(this, DownloadService::class.java).apply {
                action = ACTION_PAUSE
                putExtra(EXTRA_DOWNLOAD_ID, downloadId)
            }
            val pendingPause = PendingIntent.getForegroundService(
                this, downloadId.toInt() + 1000,
                pauseIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val cancelIntent = Intent(this, DownloadService::class.java).apply {
                action = ACTION_CANCEL
                putExtra(EXTRA_DOWNLOAD_ID, downloadId)
            }
            val pendingCancel = PendingIntent.getForegroundService(
                this, downloadId.toInt() + 3000,
                cancelIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val restartIntent = Intent(this, MainActivity::class.java).apply {
                action = "com.example.medianest.ACTION_CONFIRM_RESTART"
                putExtra("download_id", downloadId)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val pendingRestart = PendingIntent.getActivity(
                this, downloadId.toInt() + 2000,
                restartIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(active.title)
                .setContentText("Downloading… $pct% (${downloadedMb}MB / ${totalMb}MB)")
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setOngoing(true)
                .setProgress(100, pct, active.totalBytes <= 0)
                .addAction(android.R.drawable.ic_media_pause, "Pause", pendingPause)
                .addAction(android.R.drawable.ic_menu_revert, "Restart", pendingRestart)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel", pendingCancel)
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
        } else {
            var totalBytes: Long = 0
            var downloadedBytes: Long = 0
            var indeterminate = false
            activeProgress.values.forEach {
                if (it.totalBytes <= 0) {
                    indeterminate = true
                }
                totalBytes += it.totalBytes
                downloadedBytes += it.bytesDownloaded
            }
            val pct = if (totalBytes > 0) ((downloadedBytes * 100) / totalBytes).toInt() else 0
            val downloadedMb = "%.1f".format(downloadedBytes / (1024f * 1024f))
            val totalMb = "%.1f".format(totalBytes / (1024f * 1024f))
            
            val pauseAllIntent = Intent(this, DownloadService::class.java).apply {
                action = ACTION_PAUSE_ALL
            }
            val pendingPauseAll = PendingIntent.getForegroundService(
                this, 3000,
                pauseAllIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val cancelAllIntent = Intent(this, DownloadService::class.java).apply {
                action = ACTION_CANCEL_ALL
            }
            val pendingCancelAll = PendingIntent.getForegroundService(
                this, 8000,
                cancelAllIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Downloading ${activeProgress.size} files")
                .setContentText("$pct% (${downloadedMb}MB / ${totalMb}MB)")
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setOngoing(true)
                .setProgress(100, pct, indeterminate || totalBytes <= 0)
                .addAction(android.R.drawable.ic_media_pause, "Pause All", pendingPauseAll)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel All", pendingCancelAll)
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

        try {
            NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {}
    }
 
     override fun onBind(intent: Intent?): IBinder? = null
 
     override fun onDestroy() {
         serviceScope.cancel()
         super.onDestroy()
     }
 }
