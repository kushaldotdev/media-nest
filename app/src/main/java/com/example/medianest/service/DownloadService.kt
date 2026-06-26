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
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlin.coroutines.coroutineContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import java.nio.ByteBuffer
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

    private suspend fun getOutputDir(format: String): File {
        val dir = if (format == "audio" || format == "audio_extracted") "audio" else "video"
        val customFolder = preferences.downloadFolder.first()
        return if (customFolder.isNotEmpty()) {
            File(File(customFolder), dir)
        } else {
            File(filesDir, "MediaNest/$dir")
        }
    }
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
    private val activeCalls = ConcurrentHashMap<Long, okhttp3.Call>()
    private val queueMutex = kotlinx.coroutines.sync.Mutex()
    private val intentMutex = kotlinx.coroutines.sync.Mutex()
    private val pausedDownloads = ConcurrentHashMap<Long, DownloadEntity>()
    private var isFirstStart = true
    private var isForeground = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        
        // Initial load of paused downloads to avoid querying DB on notification ticks
        serviceScope.launch {
            try {
                repository.getDownloadsByStatus(DownloadStatus.PAUSED).first().forEach {
                    pausedDownloads[it.id] = it
                }
            } catch (e: Exception) {
                android.util.Log.e("DownloadService", "Failed to load initial paused downloads", e)
            }
        }

        serviceScope.launch {
            preferences.maxConcurrentDownloads.collect { max ->
                withContext(Dispatchers.Main) {
                    processQueue()
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!isForeground) {
            try {
                startForeground(NOTIFICATION_ID, buildNotification(0, 0))
                isForeground = true
            } catch (e: Exception) {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S
                    && e.javaClass.name == "android.app.ForegroundServiceStartNotAllowedException") {
                    stopSelf()
                    return START_NOT_STICKY
                }
                throw e
            }
        }
        val action = intent?.action
        val id = intent?.getLongExtra(EXTRA_DOWNLOAD_ID, -1L) ?: -1L

        serviceScope.launch {
            intentMutex.withLock {
                when (action) {
                    ACTION_PAUSE -> if (id != -1L) pauseDownload(id)
                    ACTION_RESUME -> if (id != -1L) resumeDownload(id)
                    ACTION_CANCEL -> if (id != -1L) cancelDownload(id)
                    ACTION_RESTART -> if (id != -1L) restartDownload(id)
                    ACTION_PAUSE_ALL -> pauseAllDownloads()
                    ACTION_RESUME_ALL -> resumeAllDownloads()
                    ACTION_CANCEL_ALL -> cancelAllDownloads()
                    else -> processQueue()
                }
            }
        }
        return START_STICKY
    }

    private suspend fun updateDownloadStatus(id: Long, status: DownloadStatus, progress: Float) {
        repository.updateStatus(id, status, progress)
        if (status == DownloadStatus.PAUSED) {
            val download = repository.getDownloadById(id)
            if (download != null) {
                pausedDownloads[id] = download
            }
        } else {
            pausedDownloads.remove(id)
        }
    }

    private suspend fun markDownloadCompleted(id: Long, fileSizeBytes: Long, filePath: String) {
        repository.markCompleted(id, fileSizeBytes, filePath)
        pausedDownloads.remove(id)
    }

    private suspend fun markDownloadFailed(id: Long, errorMessage: String, retryCount: Int) {
        repository.markFailed(id, errorMessage, retryCount)
        pausedDownloads.remove(id)
    }

    private fun processQueue() {
        serviceScope.launch {
            queueMutex.withLock {
                val maxConcurrent = preferences.maxConcurrentDownloads.first()
                val queue = repository.getDownloadsByStatus(DownloadStatus.QUEUED).first()
                    .filter { it.format != "audio_extracted" && !activeJobs.containsKey(it.id) }
                val paused = pausedDownloads.values.toList()

                val downloading = repository.getDownloadsByStatus(DownloadStatus.DOWNLOADING).first()
                    .filter { it.format != "audio_extracted" && !activeJobs.containsKey(it.id) }

                android.util.Log.d("DownloadService", "processQueue: maxConcurrent=$maxConcurrent, queueSize=${queue.size}, activeJobsSize=${activeJobs.size}, activeJobsKeys=${activeJobs.keys}, downloadingSize=${downloading.size}, pausedSize=${paused.size}")

                // Re-enqueue ghost downloading entries (e.g. killed abruptly)
                downloading.forEach { download ->
                    if (activeJobs.size < maxConcurrent) {
                        android.util.Log.d("DownloadService", "Re-enqueuing ghost downloading entry: ${download.id}")
                        enqueueDownload(download)
                    } else {
                        android.util.Log.d("DownloadService", "Marking ghost downloading entry as QUEUED due to limit: ${download.id}")
                        updateDownloadStatus(download.id, DownloadStatus.QUEUED, download.progress)
                    }
                }

                if (activeJobs.size > maxConcurrent) {
                    val excess = activeJobs.size - maxConcurrent
                    android.util.Log.w("DownloadService", "Active jobs size (${activeJobs.size}) exceeds maxConcurrent ($maxConcurrent) by $excess")
                }

                val slots = (maxConcurrent - activeJobs.size).coerceAtLeast(0)
                android.util.Log.d("DownloadService", "Available slots: $slots")

                if (queue.isEmpty() && activeJobs.isEmpty() && paused.isEmpty() && downloading.isEmpty()) {
                    android.util.Log.d("DownloadService", "All queues and jobs empty. Stopping service.")
                    try {
                        NotificationManagerCompat.from(this@DownloadService).cancel(NOTIFICATION_ID)
                    } catch (_: SecurityException) { }
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    isForeground = false
                    stopSelf()
                    return@launch
                }
                
                if (slots > 0 && queue.isNotEmpty()) {
                    queue.take(slots).forEach { 
                        android.util.Log.d("DownloadService", "Enqueuing queued download: ${it.id} (title=${it.title})")
                        enqueueDownload(it) 
                    }
                }
                updateNotification()
            }
        }
    }

    private fun enqueueDownload(download: DownloadEntity) {
        // Guard: user may have clicked pause/cancel between processQueue finding
        // this download as QUEUED and this enqueue call actually running
        if (isPaused(download.id)) {
            serviceScope.launch {
                updateDownloadStatus(download.id, DownloadStatus.PAUSED, download.progress)
                updateNotification()
            }
            return
        }
        if (isCancelled(download.id)) {
            serviceScope.launch {
                updateDownloadStatus(download.id, DownloadStatus.CANCELED, download.progress)
                updateNotification()
            }
            return
        }
        cancelFlags.remove(download.id)
        putToQueueFlags.remove(download.id)
        activeProgress[download.id] = ActiveProgress(
            title = download.title.ifEmpty { download.quality },
            bytesDownloaded = (download.progress * download.fileSizeBytes).toLong(),
            totalBytes = download.fileSizeBytes
        )
        val job = serviceScope.launch {
            try {
                downloadFile(download)
            } finally {
                withContext(NonCancellable) {
                    val progress = activeProgress[download.id]?.let {
                        if (it.totalBytes > 0) it.bytesDownloaded.toFloat() / it.totalBytes else 0f
                    } ?: download.progress

                    if (isPaused(download.id)) {
                        updateDownloadStatus(download.id, DownloadStatus.PAUSED, progress)
                    } else if (isCancelled(download.id)) {
                        updateDownloadStatus(download.id, DownloadStatus.CANCELED, progress)
                    } else if (isPutToQueue(download.id)) {
                        updateDownloadStatus(download.id, DownloadStatus.QUEUED, progress)
                    }

                    activeJobs.remove(download.id)
                    activeCalls.remove(download.id)
                    activeProgress.remove(download.id)
                    updateNotification()
                    processQueue()
                }
            }
        }
        activeJobs[download.id] = job
    }

    private suspend fun downloadUrlToFile(
        download: DownloadEntity,
        url: String,
        tmpFile: File,
        startProgress: Float,
        endProgress: Float,
        progressMessage: String?,
        isAudioStream: Boolean = false,
        onUrlExpired: suspend () -> String?
    ): Boolean {
        var currentUrl = url
        var retries = 0
        val maxRetries = 3
        val FIRST_CHUNK_SIZE = 2L * 1024 * 1024   // 2 MB
        val NORMAL_CHUNK_SIZE = 10L * 1024 * 1024  // 10 MB

        while (retries <= maxRetries) {
            if (isPaused(download.id) || isCancelled(download.id) || isPutToQueue(download.id)) {
                return false
            }
            try {
                var offset = if (tmpFile.exists()) tmpFile.length() else 0L
                var totalSize = if (!isAudioStream && download.fileSizeBytes > 0) download.fileSizeBytes else -1L

                // Progress tracking state (persists across chunks)
                var lastProgressUpdate = 0L
                var lastProgressTime = System.currentTimeMillis()
                var lastProgressSent = startProgress + (if (totalSize > 0) (offset.toFloat() / totalSize) else 0f) * (endProgress - startProgress)
                var speedLastTime = System.currentTimeMillis()
                var speedLastBytes = offset
                var currentSpeedString: String? = null

                // Chunked download loop — each iteration requests a bounded byte range
                while (true) {
                    if (isPaused(download.id)) {
                        val pct = if (totalSize > 0) offset.toFloat() / totalSize else 0f
                        val savedProgress = startProgress + pct * (endProgress - startProgress)
                        updateDownloadStatus(download.id, DownloadStatus.PAUSED, savedProgress)
                        activeJobs.remove(download.id)
                        return false
                    }
                    if (isCancelled(download.id)) {
                        tmpFile.delete()
                        updateDownloadStatus(download.id, DownloadStatus.CANCELED, download.progress)
                        return false
                    }
                    if (isPutToQueue(download.id)) {
                        val pct = if (totalSize > 0) offset.toFloat() / totalSize else 0f
                        val savedProgress = startProgress + pct * (endProgress - startProgress)
                        updateDownloadStatus(download.id, DownloadStatus.QUEUED, savedProgress)
                        activeJobs.remove(download.id)
                        return false
                    }

                    // Done: all bytes received
                    if (totalSize > 0 && offset >= totalSize) break

                    val chunkSize = if (offset == 0L) FIRST_CHUNK_SIZE else NORMAL_CHUNK_SIZE
                    val rangeEnd = if (totalSize > 0) {
                        minOf(offset + chunkSize - 1, totalSize - 1)
                    } else {
                        offset + chunkSize - 1
                    }

                    val request = Request.Builder()
                        .url(currentUrl)
                        .header("Range", "bytes=$offset-$rangeEnd")
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                        .header("Accept-Encoding", "identity")
                        .build()

                    val call = okHttpClient.newCall(request)
                    activeCalls[download.id] = call
                    val response = withContext(Dispatchers.IO) {
                        call.execute()
                    }

                    if (!response.isSuccessful) {
                        response.body?.close()
                        if (response.code == 416) {
                            // 10. HTTP 416 Unknown EOF: Treat 416 as success if totalSize <= 0 and offset > 0
                            if (totalSize <= 0 && offset > 0) {
                                return true
                            }
                            if (totalSize > 0 && offset >= totalSize) {
                                break
                            }
                            tmpFile.delete()
                            offset = 0L
                            retries++
                            break
                        }
                        if ((response.code == 403 || response.code == 410) && retries < maxRetries) {
                            retries++
                            repository.updateRetryCount(download.id, retries)
                            val freshUrl = onUrlExpired()
                            if (freshUrl != null) {
                                currentUrl = freshUrl
                                break
                            }
                        }
                        if (isAudioStream) {
                            throw IOException("HTTP ${response.code}")
                        } else {
                            if (tmpFile.exists()) tmpFile.delete()
                            val outputDir = tmpFile.parentFile
                            val audioFile = File(outputDir, "${download.videoId}_${download.quality}_audio.tmp")
                            if (audioFile.exists()) audioFile.delete()

                            markDownloadFailed(download.id, "HTTP ${response.code}", retries)
                            return false
                        }
                    }

                    val isRange = response.code == 206

                    // Parse Content-Range to learn total file size: "bytes 0-999999/123456789"
                    if (isRange) {
                        val contentRange = response.header("Content-Range")
                        val parsedTotal = contentRange?.substringAfter("/", "")?.toLongOrNull()
                        if (parsedTotal != null && parsedTotal > 0 && totalSize != parsedTotal) {
                            totalSize = parsedTotal
                            if (!isAudioStream) {
                                repository.updateFileSize(download.id, totalSize)
                            }
                        }
                    }

                    // Fallback: server doesn't support range — stream entire response
                    if (!isRange && offset == 0L) {
                        if (tmpFile.exists()) tmpFile.delete()
                        val responseLength = response.body?.contentLength() ?: -1L
                        if (responseLength > 0 && responseLength != totalSize) {
                            totalSize = responseLength
                            if (!isAudioStream) {
                                repository.updateFileSize(download.id, totalSize)
                            }
                        }
                    } else if (!isRange) {
                        // Server lost range support mid-download — restart
                        response.body?.close()
                        tmpFile.delete()
                        offset = 0L
                        retries++
                        break
                    }

                    // Stream this chunk's bytes to file (always append)
                    response.body?.byteStream()?.use { input ->
                        FileOutputStream(tmpFile, true).use { output ->
                            val buffer = ByteArray(128 * 1024)

                            while (true) {
                                if (isPaused(download.id)) {
                                    offset = tmpFile.length()
                                    val pct = if (totalSize > 0) offset.toFloat() / totalSize else 0f
                                    val savedProgress = startProgress + pct * (endProgress - startProgress)
                                    updateDownloadStatus(download.id, DownloadStatus.PAUSED, savedProgress)
                                    activeJobs.remove(download.id)
                                    return false
                                }
                                if (isCancelled(download.id)) {
                                    tmpFile.delete()
                                    updateDownloadStatus(download.id, DownloadStatus.CANCELED, download.progress)
                                    return false
                                }
                                if (isPutToQueue(download.id)) {
                                    offset = tmpFile.length()
                                    val pct = if (totalSize > 0) offset.toFloat() / totalSize else 0f
                                    val savedProgress = startProgress + pct * (endProgress - startProgress)
                                    updateDownloadStatus(download.id, DownloadStatus.QUEUED, savedProgress)
                                    activeJobs.remove(download.id)
                                    return false
                                }

                                val read = input.read(buffer)
                                if (read == -1) break
                                
                                try {
                                    output.write(buffer, 0, read)
                                } catch (e: IOException) {
                                    // 6. Disk Full: Catch IOException containing "ENOSPC" or "No space left on device" and fail immediately
                                    val msg = e.message ?: ""
                                    if (msg.contains("ENOSPC", ignoreCase = true) || msg.contains("No space left", ignoreCase = true)) {
                                        throw IOException("Disk Full: $msg", e)
                                    }
                                    throw e
                                }
                                offset += read

                                val currentPct = if (totalSize > 0) offset.toFloat() / totalSize else 0f
                                val currentProgress = startProgress + currentPct * (endProgress - startProgress)

                                val currentTime = System.currentTimeMillis()
                                val timeElapsed = currentTime - lastProgressTime >= 250

                                // Calculate download speed every 1 second
                                if (currentTime - speedLastTime >= 1000) {
                                    val bytesDiff = offset - speedLastBytes
                                    val timeDiff = currentTime - speedLastTime
                                    if (timeDiff > 0) {
                                        val bytesPerSec = (bytesDiff * 1000f) / timeDiff
                                        currentSpeedString = if (bytesPerSec >= 1024 * 1024) {
                                            "%.1f MB/s".format(bytesPerSec / (1024f * 1024f))
                                        } else {
                                            "%.0f KB/s".format(bytesPerSec / 1024f)
                                        }
                                    }
                                    speedLastTime = currentTime
                                    speedLastBytes = offset
                                }

                                val shouldUpdate = if (totalSize > 0) {
                                    (currentProgress - lastProgressSent >= 0.01f && timeElapsed) || (offset == totalSize)
                                } else {
                                    (offset - lastProgressUpdate > 1024 * 1024) && timeElapsed
                                }

                                if (shouldUpdate) {
                                    if (!isPaused(download.id) && !isCancelled(download.id) && !isPutToQueue(download.id)) {
                                        val payload = if (progressMessage != null) {
                                            if (currentSpeedString != null) "$progressMessage|$currentSpeedString" else progressMessage
                                        } else {
                                            currentSpeedString
                                        }
                                        repository.updateProgressAndMessage(download.id, currentProgress, payload)
                                    }
                                    activeProgress[download.id] = ActiveProgress(
                                        title = if (progressMessage != null) "Downloading audio..." else (download.title.ifEmpty { download.quality }),
                                        bytesDownloaded = (currentProgress * (if (totalSize > 0) totalSize else offset)).toLong(),
                                        totalBytes = if (totalSize > 0) totalSize else offset
                                    )
                                    updateNotification()
                                    lastProgressSent = currentProgress
                                    lastProgressUpdate = offset
                                    lastProgressTime = currentTime
                                }
                            }
                        }
                    }

                    // Update offset from file in case stream closed early
                    offset = tmpFile.length()

                    // If server didn't support range (full response), we're done
                    if (!isRange) break
                }

                // Check if we completed successfully
                val finalSize = tmpFile.length()
                if (totalSize > 0 && finalSize >= totalSize) {
                    return true
                }
                // For unknown total size, if we exited the chunk loop normally, we're done
                if (totalSize <= 0 && finalSize > 0 && retries <= maxRetries) {
                    return true
                }
                // Otherwise, retry (URL expired, 416, etc.) — continue outer while loop
                continue
            } catch (e: CancellationException) {
                return false
            } catch (e: Exception) {
                if (isPaused(download.id) || isCancelled(download.id) || isPutToQueue(download.id)) {
                    return false
                }
                // 6. Disk Full: Fail immediately if disk full exception caught
                val isDiskFull = e.message?.contains("ENOSPC", ignoreCase = true) == true ||
                        e.message?.contains("No space left", ignoreCase = true) == true ||
                        e.cause?.message?.contains("ENOSPC", ignoreCase = true) == true ||
                        e.cause?.message?.contains("No space left", ignoreCase = true) == true

                if (isDiskFull) {
                    if (tmpFile.exists()) tmpFile.delete()
                    val outputDir = tmpFile.parentFile
                    val audioFile = File(outputDir, "${download.videoId}_${download.quality}_audio.tmp")
                    if (audioFile.exists()) audioFile.delete()

                    markDownloadFailed(download.id, "Disk Full: ${e.message ?: "No space left on device"}", retries)
                    return false
                }

                if (retries < maxRetries) {
                    retries++
                    repository.updateRetryCount(download.id, retries)
                    kotlinx.coroutines.delay(1000L * retries)
                    continue
                }
                if (isAudioStream) {
                    throw e
                } else {
                    if (tmpFile.exists()) tmpFile.delete()
                    val outputDir = tmpFile.parentFile
                    val audioFile = File(outputDir, "${download.videoId}_${download.quality}_audio.tmp")
                    if (audioFile.exists()) audioFile.delete()

                    markDownloadFailed(download.id, e.message ?: "Download failed", retries)
                    return false
                }
            }
        }
        return false
    }

    private suspend fun downloadFile(download: DownloadEntity) {
        if (isPaused(download.id)) {
            updateDownloadStatus(download.id, DownloadStatus.PAUSED, download.progress)
            updateNotification()
            return
        }
        updateDownloadStatus(download.id, DownloadStatus.DOWNLOADING, download.progress)
        if (isPaused(download.id)) {
            updateDownloadStatus(download.id, DownloadStatus.PAUSED, download.progress)
            updateNotification()
            return
        }
        
        val outputDir = getOutputDir(download.format)
        outputDir.mkdirs()

        val tmpFile = File(outputDir, "${download.videoId}_${download.quality}.tmp")
        val audioFile = File(outputDir, "${download.videoId}_${download.quality}_audio.tmp")
        
        var ext = if (download.url.contains("webm", ignoreCase = true) || download.quality.contains("webm", ignoreCase = true)) "webm" else "mp4"
        var videoDownloadCompleted = false

        if (download.format == "video_only" && tmpFile.exists() && download.fileSizeBytes > 0L && tmpFile.length() >= download.fileSizeBytes) {
            videoDownloadCompleted = true
        }

        // 12. Blank URL: Refresh the stream url proactively if downloadUrl is empty or blank
        var downloadUrl = download.url
        val isExpired = try {
            if (downloadUrl.contains("googlevideo.com") && downloadUrl.contains("expire=")) {
                val expireStr = android.net.Uri.parse(downloadUrl).getQueryParameter("expire")
                val expireEpoch = expireStr?.toLongOrNull() ?: 0L
                expireEpoch > 0 && expireEpoch < System.currentTimeMillis() / 1000
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }

        if (downloadUrl.isBlank() || isExpired) {
            try {
                val watchUrl = download.videoUrl ?: ("https://www.youtube.com/watch?v=" + download.videoId)
                val freshInfo = extractor.extractVideo(watchUrl)
                // Exact match first, then fallback to same format with any quality
                val matchingStream = freshInfo.streamSources.find {
                    it.format == download.format && it.quality == download.quality
                } ?: freshInfo.streamSources.find {
                    it.format == download.format
                }
                if (matchingStream != null) {
                    downloadUrl = matchingStream.url
                    repository.updateUrl(download.id, downloadUrl)
                }
            } catch (e: Exception) {
                android.util.Log.w("DownloadService", "Proactive URL refresh failed, will retry on 403", e)
            }
        }

        // Step 1: Download video (or primary stream)
        val videoSuccess = if (videoDownloadCompleted) true else {
            downloadUrlToFile(
                download = download,
                url = downloadUrl,
                tmpFile = tmpFile,
                startProgress = 0.0f,
                endProgress = if (download.format == "video_only") 0.90f else 1.0f,
                progressMessage = null,
                isAudioStream = false
            ) {
                val freshInfo = extractor.extractVideo(download.videoUrl ?: ("https://www.youtube.com/watch?v=" + download.videoId))
                // Exact match first, then fallback to same format with any quality
                val matchingStream = freshInfo.streamSources.find {
                    it.format == download.format && it.quality == download.quality
                } ?: freshInfo.streamSources.find {
                    it.format == download.format
                }
                matchingStream?.url
            }
        }

        // 11. Stuck Downloading: Call repository.markFailed(...) when downloadFile returns early on videoSuccess failures.
        if (!videoSuccess) {
            if (!isPaused(download.id) && !isCancelled(download.id) && !isPutToQueue(download.id)) {
                if (tmpFile.exists()) tmpFile.delete()
                if (audioFile.exists()) audioFile.delete()
                markDownloadFailed(download.id, "Video download failed", 0)
            }
            return
        }

        val fileName = "${download.videoId}_${download.quality}.$ext"
        val outputFile = File(outputDir, fileName)

        // Step 2: Download and merge audio if video_only
        if (download.format == "video_only") {
            var audioSuccess = false
            var audioRetries = 0
            val maxAudioRetries = 3

            while (audioRetries <= maxAudioRetries) {
                // 13. Stale Audio: Delete audioFile at the start of each audio retry loop in downloadFile.
                if (audioRetries > 0 && audioFile.exists()) {
                    audioFile.delete()
                }

                if (isPaused(download.id) || isCancelled(download.id) || isPutToQueue(download.id)) {
                    return
                }
                try {
                    repository.updateProgressAndMessage(download.id, 0.90f, "downloading_audio")
                    activeProgress[download.id] = ActiveProgress(
                        title = "Downloading audio...",
                        bytesDownloaded = (0.90f * (if (download.fileSizeBytes > 0) download.fileSizeBytes else tmpFile.length())).toLong(),
                        totalBytes = if (download.fileSizeBytes > 0) download.fileSizeBytes else tmpFile.length()
                    )
                    updateNotification()

                    val freshInfo = extractor.extractVideo(download.videoUrl ?: ("https://www.youtube.com/watch?v=" + download.videoId))
                    val isWebmVideo = ext.contains("webm", ignoreCase = true)
                    val compatibleAudioStreams = freshInfo.streamSources
                        .filter { it.format == "audio" }
                        .filter {
                            val mime = it.mimeType.lowercase()
                            val codec = it.codec.lowercase()
                            if (isWebmVideo) {
                                mime.contains("webm") || mime.contains("ogg") || codec.contains("webm") || codec.contains("opus")
                            } else {
                                mime.contains("mp4") || mime.contains("m4a") || codec.contains("m4a") || codec.contains("aac")
                            }
                        }
                    val audioStreamsToUse = if (compatibleAudioStreams.isNotEmpty()) compatibleAudioStreams else {
                        freshInfo.streamSources.filter { it.format == "audio" }
                    }
                    val audioStream = audioStreamsToUse
                        .maxByOrNull { it.quality.replace("kbps", "").toIntOrNull() ?: 0 }

                    if (audioStream == null) {
                        throw IOException("No audio stream found for merging")
                    }

                    val downloadAudioSuccess = downloadUrlToFile(
                        download = download,
                        url = audioStream.url,
                        tmpFile = audioFile,
                        startProgress = 0.90f,
                        endProgress = 0.95f,
                        progressMessage = "downloading_audio",
                        isAudioStream = true
                    ) {
                        val freshAudioInfo = extractor.extractVideo(download.videoUrl ?: ("https://www.youtube.com/watch?v=" + download.videoId))
                        val compatibleFreshAudioStreams = freshAudioInfo.streamSources
                            .filter { it.format == "audio" }
                            .filter {
                                val mime = it.mimeType.lowercase()
                                val codec = it.codec.lowercase()
                                if (isWebmVideo) {
                                    mime.contains("webm") || mime.contains("ogg") || codec.contains("webm") || codec.contains("opus")
                                } else {
                                    mime.contains("mp4") || mime.contains("m4a") || codec.contains("m4a") || codec.contains("aac")
                                }
                            }
                        val freshAudioStreamsToUse = if (compatibleFreshAudioStreams.isNotEmpty()) compatibleFreshAudioStreams else {
                            freshAudioInfo.streamSources.filter { it.format == "audio" }
                        }
                        val freshAudioStream = freshAudioStreamsToUse
                            .maxByOrNull { it.quality.replace("kbps", "").toIntOrNull() ?: 0 }
                        freshAudioStream?.url
                    }

                    // 11. Stuck Downloading: Call repository.markFailed(...) when downloadFile returns early on downloadAudioSuccess failures.
                    if (!downloadAudioSuccess) {
                        if (!isPaused(download.id) && !isCancelled(download.id) && !isPutToQueue(download.id)) {
                            if (tmpFile.exists()) tmpFile.delete()
                            if (audioFile.exists()) audioFile.delete()
                            markDownloadFailed(download.id, "Audio download failed", audioRetries)
                        }
                        return
                    }

                    repository.updateProgressAndMessage(download.id, 0.97f, "merging")
                    activeProgress[download.id] = ActiveProgress(
                        title = "Merging video & audio...",
                        bytesDownloaded = (0.97f * (if (download.fileSizeBytes > 0) download.fileSizeBytes else tmpFile.length())).toLong(),
                        totalBytes = if (download.fileSizeBytes > 0) download.fileSizeBytes else tmpFile.length()
                    )
                    updateNotification()

                    android.util.Log.d("DownloadService", "Attempting native MediaMuxer merge...")
                    var nativeMergeSuccess = false
                    try {
                        nativeMergeSuccess = mergeAudioVideoNative(tmpFile, audioFile, outputFile)
                    } catch (t: Throwable) {
                        android.util.Log.e("DownloadService", "Native MediaMuxer merge failed with exception", t)
                    }

                    if (nativeMergeSuccess) {
                        tmpFile.delete()
                        audioFile.delete()
                        audioSuccess = true
                        break
                    }

                    android.util.Log.d("DownloadService", "Native merge failed/skipped. Falling back to FFmpegKit...")
                    val audioCodec = if (ext.contains("webm", ignoreCase = true)) "opus" else "aac"
                    val ffmpegCommand = "-y -i \"${tmpFile.absolutePath}\" -i \"${audioFile.absolutePath}\" -c:v copy -c:a $audioCodec \"${outputFile.absolutePath}\""
                    
                    var ffmpegSuccess = false
                    try {
                        val session = com.arthenica.ffmpegkit.FFmpegKit.execute(ffmpegCommand)
                        if (com.arthenica.ffmpegkit.ReturnCode.isSuccess(session.returnCode)) {
                            tmpFile.delete()
                            audioFile.delete()
                            ffmpegSuccess = true
                        } else {
                            val logs = session.allLogsAsString ?: "FFmpeg merge failed with no logs"
                            android.util.Log.e("DownloadService", "FFmpeg merge failed: $logs")
                        }
                    } catch (t: Throwable) {
                        android.util.Log.e("DownloadService", "FFmpegKit execution failed completely", t)
                        throw IOException("FFmpegKit failed to load or run: ${t.message}", t)
                    }

                    if (ffmpegSuccess) {
                        audioSuccess = true
                        break
                    } else {
                        throw IOException("Both native MediaMuxer and FFmpegKit merging failed")
                    }
                } catch (e: CancellationException) {
                    return
                } catch (e: Throwable) {
                    android.util.Log.e("DownloadService", "Audio download/merge attempt $audioRetries failed", e)
                    audioRetries++
                    if (audioRetries > maxAudioRetries) {
                        // 5. File Leaks: Delete tmpFile and audioFile in markFailed paths.
                        if (audioFile.exists()) audioFile.delete()
                        if (tmpFile.exists()) tmpFile.delete()
                        markDownloadFailed(download.id, e.message ?: "Audio download/merge failed", audioRetries)
                        return
                    }
                    kotlinx.coroutines.delay(2000)
                }
            }
            if (!audioSuccess) return
        } else {
            if (!tmpFile.renameTo(outputFile)) {
                try {
                    tmpFile.copyTo(outputFile, overwrite = true)
                    tmpFile.delete()
                } catch (e: Exception) {
                    // 5. File Leaks: Delete tmpFile and audioFile in markFailed paths.
                    if (tmpFile.exists()) tmpFile.delete()
                    if (audioFile.exists()) audioFile.delete()
                    markDownloadFailed(download.id, "Failed to move temp file: ${e.message}", 0)
                    return
                }
            }
        }

        markDownloadCompleted(download.id, outputFile.length(), outputFile.absolutePath)
        activeProgress.remove(download.id)
        updateNotification()

        val existing = videoDao.getVideoById(download.videoId)
        if (existing == null) {
            videoDao.insert(
                VideoEntity(
                    id = download.videoId,
                    title = download.title.ifEmpty { download.quality },
                    channelName = "",
                    // 1. Type Mismatch: pass durationSeconds = 0L (not 0)
                    durationSeconds = 0L,
                    thumbnailUrl = download.thumbnailUrl,
                    localFilePath = outputFile.absolutePath,
                    downloadedAt = System.currentTimeMillis()
                )
            )
        } else {
            videoDao.update(existing.copy(
                localFilePath = outputFile.absolutePath,
                downloadedAt = System.currentTimeMillis()
            ))
        }
    }

      private fun isPaused(id: Long): Boolean = pauseFlags[id] == true
  
      private fun isCancelled(id: Long): Boolean = cancelFlags[id] == true

      private fun isPutToQueue(id: Long): Boolean = putToQueueFlags[id] == true

      private fun currentProgress(id: Long): Float? =
          activeProgress[id]?.let {
              if (it.totalBytes > 0) {
                  (it.bytesDownloaded.toFloat() / it.totalBytes).coerceIn(0f, 1f)
              } else {
                  null
              }
          }
  
      private suspend fun pauseDownload(id: Long) {
          pauseFlags[id] = true
          // Capture in-memory progress before job cancellation clears it
          val progressAtPause = currentProgress(id)
          activeProgress[id]?.let { progress ->
              showPausedNotification(
                  downloadId = id,
                  title = progress.title,
                  progress = progressAtPause ?: 0f,
                  totalBytes = progress.totalBytes
              )
          }
          val job = activeJobs[id]
          activeCalls[id]?.cancel()
          job?.cancel()
          
          val download = repository.getDownloadById(id) ?: return
          val effectiveProgress = progressAtPause ?: download.progress
          if (!activeProgress.containsKey(id)) {
              showPausedNotification(
                  downloadId = id,
                  title = download.title.ifEmpty { download.quality },
                  progress = effectiveProgress,
                  totalBytes = download.fileSizeBytes
              )
          }
          updateDownloadStatus(id, DownloadStatus.PAUSED, effectiveProgress)
          updateNotification()
          processQueue()
      }

      private suspend fun putDownloadingToQueue(id: Long) {
          putToQueueFlags[id] = true
          val job = activeJobs[id]
          if (job != null) {
              activeCalls[id]?.cancel()
              job.cancel()
          } else {
              val download = repository.getDownloadById(id) ?: return
              updateDownloadStatus(id, DownloadStatus.QUEUED, download.progress)
              withContext(Dispatchers.Main) {
                  putToQueueFlags.remove(id)
                  processQueue()
              }
          }
      }
 
      private suspend fun resumeDownload(id: Long) {
          pauseFlags.remove(id)
          val download = repository.getDownloadById(id) ?: return
          if (download.status == DownloadStatus.PAUSED || download.status == DownloadStatus.QUEUED) {
              if (download.status == DownloadStatus.PAUSED) {
                  updateDownloadStatus(id, DownloadStatus.QUEUED, download.progress)
              }
              processQueue()
          }
      }
 
       private suspend fun cancelDownload(id: Long) {
           cancelFlags[id] = true
           pauseFlags.remove(id)
           val job = activeJobs[id]
           if (job != null) {
               activeCalls[id]?.cancel()
               job.cancel()
               job.join()
           }
           val download = repository.getDownloadById(id) ?: return
           if (download.filePath.isNotEmpty()) {
               File(download.filePath).delete()
           }
           val outputDir = getOutputDir(download.format)
           val tmpFile = File(outputDir, "${download.videoId}_${download.quality}.tmp")
           if (tmpFile.exists()) {
               tmpFile.delete()
           }
           val audioFile = File(outputDir, "${download.videoId}_${download.quality}_audio.tmp")
           if (audioFile.exists()) {
               audioFile.delete()
           }
           updateDownloadStatus(id, DownloadStatus.CANCELED, download.progress)
           updateNotification()
           processQueue()
       }

       private suspend fun restartDownload(id: Long) {
            cancelFlags.remove(id) // Ensure it's not marked as cancelled
            val existingJob = activeJobs[id]
            if (existingJob != null) {
                activeCalls[id]?.cancel()
                existingJob.cancel()
                existingJob.join() // 3. Restart Race: join() any existing job before deleting files
            }
            val download = repository.getDownloadById(id) ?: return
            try {
                val outputDir = getOutputDir(download.format)
                val tmpFile = File(outputDir, "${download.videoId}_${download.quality}.tmp")
                if (tmpFile.exists()) {
                    tmpFile.delete()
                }
                val audioFile = File(outputDir, "${download.videoId}_${download.quality}_audio.tmp")
                if (audioFile.exists()) {
                    audioFile.delete()
                }
            } catch (e: Exception) {
                android.util.Log.e("DownloadService", "Failed to delete tmp file on restart", e)
            }
            val reset = download.copy(
                status = DownloadStatus.QUEUED,
                progress = 0f,
                errorMessage = null,
                retryCount = 0,
                fileSizeBytes = 0L // Reset to allow fetching a fresh total size if needed
            )
            repository.update(reset)
            pausedDownloads.remove(id)
            resumeDownload(id)
        }

       private suspend fun pauseAllDownloads() {
           val downloading = repository.getDownloadsByStatus(DownloadStatus.DOWNLOADING).first()
               .filter { it.format != "audio_extracted" }
           val queued = repository.getDownloadsByStatus(DownloadStatus.QUEUED).first()
               .filter { it.format != "audio_extracted" }
           
           downloading.forEach { pauseFlags[it.id] = true }
           queued.forEach { pauseFlags[it.id] = true }
           
           downloading.forEach {
               activeCalls[it.id]?.cancel()
               activeJobs[it.id]?.cancel()
               activeJobs[it.id]?.join()
           }
           
           downloading.forEach { updateDownloadStatus(it.id, DownloadStatus.PAUSED, currentProgress(it.id) ?: it.progress) }
           queued.forEach { updateDownloadStatus(it.id, DownloadStatus.PAUSED, it.progress) }
           
           updateNotification()
           processQueue()
       }

       private suspend fun resumeAllDownloads() {
           val paused = pausedDownloads.values.toList() // In-memory cache
           paused.forEach { download ->
               pauseFlags.remove(download.id)
               updateDownloadStatus(download.id, DownloadStatus.QUEUED, download.progress)
           }
           processQueue()
       }

       private suspend fun cancelAllDownloads() {
           val downloading = repository.getDownloadsByStatus(DownloadStatus.DOWNLOADING).first()
               .filter { it.format != "audio_extracted" }
           val queued = repository.getDownloadsByStatus(DownloadStatus.QUEUED).first()
               .filter { it.format != "audio_extracted" }
           val paused = pausedDownloads.values.toList() // In-memory cache
           
           val all = downloading + queued + paused
           all.forEach { download ->
               cancelFlags[download.id] = true
               pauseFlags.remove(download.id)
           }
           
           all.forEach {
               activeCalls[it.id]?.cancel()
               activeJobs[it.id]?.cancel()
               activeJobs[it.id]?.join()
           }
           
           all.forEach { download ->
               if (download.filePath.isNotEmpty()) {
                   File(download.filePath).delete()
               }
               val outputDir = getOutputDir(download.format)
               val tmpFile = File(outputDir, "${download.videoId}_${download.quality}.tmp")
               if (tmpFile.exists()) {
                   tmpFile.delete()
               }
               val audioFile = File(outputDir, "${download.videoId}_${download.quality}_audio.tmp")
               if (audioFile.exists()) {
                   audioFile.delete()
               }
               updateDownloadStatus(download.id, DownloadStatus.CANCELED, download.progress)
           }
           processQueue()
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
                         action = "com.example.medianest.ACTION_NAVIGATE_DOWNLOADS"
                     },
                     PendingIntent.FLAG_IMMUTABLE
                 )
             )
             .build()
     }

     private suspend fun updateNotification() {
         // 8. Zombie Notification: Check serviceScope.isActive in updateNotification()
         if (!serviceScope.isActive) return

         val paused = pausedDownloads.values.toList() // 9. SQLite Hammering: Cache the paused count in memory

         // Filter out downloads flagged for pause/cancel/queue but not yet cleaned up
         val reallyActive = activeProgress.filterKeys { id ->
             !isPaused(id) && !isCancelled(id) && !isPutToQueue(id)
         }

         if (reallyActive.isEmpty()) {
             if (paused.isEmpty()) {
                 val hasRealJobs = activeJobs.keys.any { id -> !isPaused(id) && !isCancelled(id) && !isPutToQueue(id) }
                 val title = if (hasRealJobs) "Preparing download\u2026" else "Downloads paused"
                 val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                     .setContentTitle(title)
                     .setSmallIcon(android.R.drawable.stat_sys_download)
                     .setOngoing(true)
                     .setProgress(0, 0, hasRealJobs)
                     .setContentIntent(
                         PendingIntent.getActivity(
                             this, 0,
                             Intent(this, MainActivity::class.java).apply {
                                 flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
                                 action = "com.example.medianest.ACTION_NAVIGATE_DOWNLOADS"
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
                     "Paused \u00b7 $pct% (${downloadedMb}/${totalMb} MB)"
                 } else {
                     "Paused"
                 }

                 val resumeIntent = Intent(this, DownloadService::class.java).apply {
                     action = ACTION_RESUME
                     putExtra(EXTRA_DOWNLOAD_ID, download.id)
                 }
                 val pendingResume = servicePendingIntent(
                     this, download.id.toInt() + 4000,
                     resumeIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                 )

                 val cancelIntent = Intent(this, DownloadService::class.java).apply {
                     action = ACTION_CANCEL
                     putExtra(EXTRA_DOWNLOAD_ID, download.id)
                 }
                 val pendingCancel = servicePendingIntent(
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
                                 action = "com.example.medianest.ACTION_NAVIGATE_DOWNLOADS"
                             },
                             PendingIntent.FLAG_IMMUTABLE
                         )
                     )
                     .build()
             } else {
                 val resumeAllIntent = Intent(this, DownloadService::class.java).apply {
                     action = ACTION_RESUME_ALL
                 }
                 val pendingResumeAll = servicePendingIntent(
                     this, 7000,
                     resumeAllIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                 )

                 val cancelAllIntent = Intent(this, DownloadService::class.java).apply {
                     action = ACTION_CANCEL_ALL
                 }
                 val pendingCancelAll = servicePendingIntent(
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
                                 action = "com.example.medianest.ACTION_NAVIGATE_DOWNLOADS"
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

         val notification = if (reallyActive.size == 1) {
             val downloadId = reallyActive.keys.first()
             val active = reallyActive.values.first()
             val pct = if (active.totalBytes > 0) ((active.bytesDownloaded * 100) / active.totalBytes).toInt() else 0
             val downloadedMb = "%.1f".format(active.bytesDownloaded / (1024f * 1024f))
             val totalMb = "%.1f".format(active.totalBytes / (1024f * 1024f))
             
             val pauseIntent = Intent(this, DownloadService::class.java).apply {
                 action = ACTION_PAUSE
                 putExtra(EXTRA_DOWNLOAD_ID, downloadId)
             }
             val pendingPause = servicePendingIntent(
                 this, downloadId.toInt() + 1000,
                 pauseIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
             )

             val cancelIntent = Intent(this, DownloadService::class.java).apply {
                 action = ACTION_CANCEL
                 putExtra(EXTRA_DOWNLOAD_ID, downloadId)
             }
             val pendingCancel = servicePendingIntent(
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
                 .setContentText("$pct% \u2014 ${downloadedMb}/${totalMb} MB")
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
                             action = "com.example.medianest.ACTION_NAVIGATE_DOWNLOADS"
                         },
                         PendingIntent.FLAG_IMMUTABLE
                     )
                 )
                 .build()
         } else {
             var totalBytes: Long = 0
             var downloadedBytes: Long = 0
             var indeterminate = false
             reallyActive.values.forEach {
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
             val pendingPauseAll = servicePendingIntent(
                 this, 3000,
                 pauseAllIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
             )

             val cancelAllIntent = Intent(this, DownloadService::class.java).apply {
                 action = ACTION_CANCEL_ALL
             }
             val pendingCancelAll = servicePendingIntent(
                 this, 8000,
                 cancelAllIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
             )

             NotificationCompat.Builder(this, CHANNEL_ID)
                 .setContentTitle("Downloading ${reallyActive.size} files")
                 .setContentText("$pct% \u2014 ${downloadedMb}/${totalMb} MB")
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
                             action = "com.example.medianest.ACTION_NAVIGATE_DOWNLOADS"
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

     private fun servicePendingIntent(
         context: Context,
         requestCode: Int,
         intent: Intent,
         flags: Int
     ): PendingIntent = PendingIntent.getForegroundService(context, requestCode, intent, flags)

     private fun showPausedNotification(
         downloadId: Long,
         title: String,
         progress: Float,
         totalBytes: Long
     ) {
         // 8. Zombie Notification: Check serviceScope.isActive in showPausedNotification()
         if (!serviceScope.isActive) return

         val boundedProgress = progress.coerceIn(0f, 1f)
         val pct = (boundedProgress * 100).toInt()
         val contentText = if (totalBytes > 0) {
             val downloadedMb = "%.1f".format((boundedProgress * totalBytes) / (1024f * 1024f))
             val totalMb = "%.1f".format(totalBytes / (1024f * 1024f))
             "Paused \u00b7 $pct% (${downloadedMb}/${totalMb} MB)"
         } else {
             "Paused"
         }

         val resumeIntent = Intent(this, DownloadService::class.java).apply {
             action = ACTION_RESUME
             putExtra(EXTRA_DOWNLOAD_ID, downloadId)
         }
         val pendingResume = servicePendingIntent(
             this,
             downloadId.toInt() + 4000,
             resumeIntent,
             PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
         )

         val cancelIntent = Intent(this, DownloadService::class.java).apply {
             action = ACTION_CANCEL
             putExtra(EXTRA_DOWNLOAD_ID, downloadId)
         }
         val pendingCancel = servicePendingIntent(
             this,
             downloadId.toInt() + 5000,
             cancelIntent,
             PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
         )

         val restartIntent = Intent(this, MainActivity::class.java).apply {
             action = "com.example.medianest.ACTION_CONFIRM_RESTART"
             putExtra("download_id", downloadId)
             flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
         }
         val pendingRestart = PendingIntent.getActivity(
             this,
             downloadId.toInt() + 6000,
             restartIntent,
             PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
         )

         val notification = NotificationCompat.Builder(this, CHANNEL_ID)
             .setContentTitle(title)
             .setContentText(contentText)
             .setSmallIcon(android.R.drawable.stat_sys_download)
             .setOngoing(true)
             .setProgress(100, pct, totalBytes <= 0)
             .addAction(android.R.drawable.ic_media_play, "Resume", pendingResume)
             .addAction(android.R.drawable.ic_menu_revert, "Restart", pendingRestart)
             .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel", pendingCancel)
             .setContentIntent(
                 PendingIntent.getActivity(
                     this, 0,
                     Intent(this, MainActivity::class.java).apply {
                         flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
                         action = "com.example.medianest.ACTION_NAVIGATE_DOWNLOADS"
                     },
                     PendingIntent.FLAG_IMMUTABLE
                 )
             )
             .build()

         try {
             NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification)
         } catch (_: SecurityException) {}
     }

     private suspend fun mergeAudioVideoNative(videoFile: File, audioFile: File, outputFile: File): Boolean {
         var videoExtractor: MediaExtractor? = null
         var audioExtractor: MediaExtractor? = null
         var muxer: MediaMuxer? = null
         try {
             videoExtractor = MediaExtractor().apply { setDataSource(videoFile.absolutePath) }
             audioExtractor = MediaExtractor().apply { setDataSource(audioFile.absolutePath) }

             var videoTrackIndex = -1
             var videoFormat: MediaFormat? = null
             for (i in 0 until videoExtractor.trackCount) {
                 val format = videoExtractor.getTrackFormat(i)
                 val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                 if (mime.startsWith("video/")) {
                     videoTrackIndex = i
                     videoFormat = format
                     break
                 }
             }

             var audioTrackIndex = -1
             var audioFormat: MediaFormat? = null
             for (i in 0 until audioExtractor.trackCount) {
                 val format = audioExtractor.getTrackFormat(i)
                 val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                 if (mime.startsWith("audio/")) {
                     audioTrackIndex = i
                     audioFormat = format
                     break
                 }
             }

             if (videoTrackIndex == -1 || audioTrackIndex == -1 || videoFormat == null || audioFormat == null) {
                 android.util.Log.e("DownloadService", "Native merge: Missing video or audio track. Video track index: $videoTrackIndex, Audio track index: $audioTrackIndex")
                 return false
             }

             videoExtractor.selectTrack(videoTrackIndex)
             audioExtractor.selectTrack(audioTrackIndex)

             val outputFormat = if (outputFile.name.endsWith(".webm", ignoreCase = true)) {
                 MediaMuxer.OutputFormat.MUXER_OUTPUT_WEBM
             } else {
                 MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
             }

             muxer = MediaMuxer(outputFile.absolutePath, outputFormat)
             val muxerVideoTrack = muxer.addTrack(videoFormat)
             val muxerAudioTrack = muxer.addTrack(audioFormat)
             muxer.start()

             val bufferSize = 1024 * 1024
             val byteBuffer = ByteBuffer.allocate(bufferSize)
             val bufferInfo = android.media.MediaCodec.BufferInfo()

             // Mux video track
             while (true) {
                 // 4. Merge Cancellation: Add ensureActive() inside MediaMuxer sample muxing loops
                 coroutineContext.ensureActive()

                 byteBuffer.clear()
                 val sampleSize = videoExtractor.readSampleData(byteBuffer, 0)
                 if (sampleSize < 0) break

                 bufferInfo.offset = 0
                 bufferInfo.size = sampleSize
                 bufferInfo.presentationTimeUs = videoExtractor.sampleTime
                 bufferInfo.flags = videoExtractor.sampleFlags

                 muxer.writeSampleData(muxerVideoTrack, byteBuffer, bufferInfo)
                 videoExtractor.advance()
             }

             // Mux audio track
             while (true) {
                 // 4. Merge Cancellation: Add ensureActive() inside MediaMuxer sample muxing loops
                 coroutineContext.ensureActive()

                 byteBuffer.clear()
                 val sampleSize = audioExtractor.readSampleData(byteBuffer, 0)
                 if (sampleSize < 0) break

                 bufferInfo.offset = 0
                 bufferInfo.size = sampleSize
                 bufferInfo.presentationTimeUs = audioExtractor.sampleTime
                 bufferInfo.flags = audioExtractor.sampleFlags

                 muxer.writeSampleData(muxerAudioTrack, byteBuffer, bufferInfo)
                 audioExtractor.advance()
             }

             muxer.stop()
             android.util.Log.d("DownloadService", "Native MediaMuxer merge completed successfully")
             return true
         } catch (e: Exception) {
             android.util.Log.e("DownloadService", "Native MediaMuxer merge failed with exception", e)
             return false
         } finally {
             runCatching { videoExtractor?.release() }
             runCatching { audioExtractor?.release() }
             runCatching { muxer?.release() }
         }
     }
 
      override fun onBind(intent: Intent?): IBinder? = null
 
      override fun onDestroy() {
          serviceScope.cancel()
          isForeground = false
          super.onDestroy()
      }
  }
