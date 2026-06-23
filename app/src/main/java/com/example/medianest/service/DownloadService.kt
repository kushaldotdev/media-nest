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
    private var isFirstStart = true
    private var isForeground = false
    private lateinit var initJob: Job

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        initJob = serviceScope.launch {
            repository.resetStaleDownloads()
        }
        serviceScope.launch {
            initJob.join()
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
            ACTION_PAUSE_ALL -> pauseAllDownloads()
            ACTION_RESUME_ALL -> resumeAllDownloads()
            ACTION_CANCEL_ALL -> cancelAllDownloads()
            else -> processQueue()
        }
        return START_STICKY
    }

    private fun processQueue() {
        serviceScope.launch {
            if (::initJob.isInitialized) {
                initJob.join()
            }
            queueMutex.withLock {
                val maxConcurrent = preferences.maxConcurrentDownloads.first()
                val queue = repository.getDownloadsByStatus(DownloadStatus.QUEUED).first()
                    .filter { it.format != "audio_extracted" && !activeJobs.containsKey(it.id) }
                val active = activeJobs.size
                val paused = repository.getDownloadsByStatus(DownloadStatus.PAUSED).first()
                    .filter { it.format != "audio_extracted" }

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
                        repository.updateStatus(download.id, DownloadStatus.QUEUED, download.progress)
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
                repository.updateStatus(download.id, DownloadStatus.PAUSED, download.progress)
                updateNotification()
            }
            return
        }
        if (isCancelled(download.id)) {
            serviceScope.launch {
                repository.updateStatus(download.id, DownloadStatus.CANCELED, download.progress)
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
                        repository.updateStatus(download.id, DownloadStatus.PAUSED, progress)
                    } else if (isCancelled(download.id)) {
                        repository.updateStatus(download.id, DownloadStatus.CANCELED, progress)
                    } else if (isPutToQueue(download.id)) {
                        repository.updateStatus(download.id, DownloadStatus.QUEUED, progress)
                    }

                    activeJobs.remove(download.id)
                    activeCalls.remove(download.id)
                    // We purposefully do not remove pauseFlags, cancelFlags, etc. here
                    // to ensure they survive until the DB is fully updated and processQueue completes.
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
        while (retries <= maxRetries) {
            if (isPaused(download.id) || isCancelled(download.id) || isPutToQueue(download.id)) {
                return false
            }
            try {
                val existingBytes = if (tmpFile.exists()) tmpFile.length() else 0L
                val requestBuilder = Request.Builder().url(currentUrl)
                // Always send the Range header to bypass YouTube's throttling on non-range requests
                requestBuilder.header("Range", "bytes=$existingBytes-")
                requestBuilder.header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                requestBuilder.header("Accept-Encoding", "identity")
                val request = requestBuilder.build()

                val call = okHttpClient.newCall(request)
                activeCalls[download.id] = call
                val response = withContext(Dispatchers.IO) {
                    call.execute()
                }

                if (!response.isSuccessful) {
                    if (response.code == 416) {
                        tmpFile.delete()
                        retries++
                        continue
                    }
                    if ((response.code == 403 || response.code == 410) && retries < maxRetries) {
                        retries++
                        repository.updateRetryCount(download.id, retries)
                        val freshUrl = onUrlExpired()
                        if (freshUrl != null) {
                            currentUrl = freshUrl
                            continue
                        }
                    }
                    if (isAudioStream) {
                        throw IOException("HTTP ${response.code}")
                    } else {
                        repository.markFailed(download.id, "HTTP ${response.code}", retries)
                        return false
                    }
                }

                val isRange = response.code == 206
                val actualExistingBytes = if (isRange) existingBytes else 0L
                if (!isRange && tmpFile.exists()) {
                    tmpFile.delete()
                }

                val responseLength = response.body?.contentLength() ?: -1L
                val resolvedTotalLength = if (responseLength > 0) actualExistingBytes + responseLength else -1L

                if (resolvedTotalLength > 0 && !isAudioStream && download.fileSizeBytes != resolvedTotalLength) {
                    repository.updateFileSize(download.id, resolvedTotalLength)
                }

                response.body?.byteStream()?.use { input ->
                    FileOutputStream(tmpFile, isRange).use { output ->
                        val buffer = ByteArray(128 * 1024) // 128 KB buffer to maximize throughput
                        var bytesRead = actualExistingBytes
                        var lastProgressUpdate = 0L
                        var lastProgressTime = System.currentTimeMillis()
                        var lastProgressSent = startProgress + (if (resolvedTotalLength > 0) (bytesRead.toFloat() / resolvedTotalLength) else 0f) * (endProgress - startProgress)

                        var speedLastTime = System.currentTimeMillis()
                        var speedLastBytes = bytesRead
                        var currentSpeedString: String? = null

                        while (true) {
                            if (isPaused(download.id)) {
                                val currentPct = if (resolvedTotalLength > 0) bytesRead.toFloat() / resolvedTotalLength else 0f
                                val savedProgress = startProgress + currentPct * (endProgress - startProgress)
                                repository.updateStatus(download.id, DownloadStatus.PAUSED, savedProgress)
                                activeJobs.remove(download.id)
                                return false
                            }
                            if (isCancelled(download.id)) {
                                tmpFile.delete()
                                repository.updateStatus(download.id, DownloadStatus.CANCELED, download.progress)
                                return false
                            }
                            if (isPutToQueue(download.id)) {
                                val currentPct = if (resolvedTotalLength > 0) bytesRead.toFloat() / resolvedTotalLength else 0f
                                val savedProgress = startProgress + currentPct * (endProgress - startProgress)
                                repository.updateStatus(download.id, DownloadStatus.QUEUED, savedProgress)
                                activeJobs.remove(download.id)
                                return false
                            }

                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            bytesRead += read

                            val currentPct = if (resolvedTotalLength > 0) bytesRead.toFloat() / resolvedTotalLength else 0f
                            val currentProgress = startProgress + currentPct * (endProgress - startProgress)

                            val currentTime = System.currentTimeMillis()
                            val timeElapsed = currentTime - lastProgressTime >= 250

                            // Calculate download speed every 1 second
                            if (currentTime - speedLastTime >= 1000) {
                                val bytesDiff = bytesRead - speedLastBytes
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
                                speedLastBytes = bytesRead
                            }

                            val shouldUpdate = if (resolvedTotalLength > 0) {
                                (currentProgress - lastProgressSent >= 0.01f && timeElapsed) || (bytesRead == resolvedTotalLength)
                            } else {
                                (bytesRead - lastProgressUpdate > 1024 * 1024) && timeElapsed
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
                                    bytesDownloaded = (currentProgress * (if (resolvedTotalLength > 0) resolvedTotalLength else bytesRead)).toLong(),
                                    totalBytes = if (resolvedTotalLength > 0) resolvedTotalLength else bytesRead
                                )
                                updateNotification()
                                lastProgressSent = currentProgress
                                lastProgressUpdate = bytesRead
                                lastProgressTime = currentTime
                            }
                        }
                    }
                }
                return true
            } catch (e: CancellationException) {
                return false
            } catch (e: Exception) {
                if (isPaused(download.id) || isCancelled(download.id) || isPutToQueue(download.id)) {
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
                    repository.markFailed(download.id, e.message ?: "Download failed", retries)
                    return false
                }
            }
        }
        return false
    }

    private suspend fun downloadFile(download: DownloadEntity) {
        if (isPaused(download.id)) {
            repository.updateStatus(download.id, DownloadStatus.PAUSED, download.progress)
            updateNotification()
            return
        }
        repository.updateStatus(download.id, DownloadStatus.DOWNLOADING, download.progress)
        if (isPaused(download.id)) {
            repository.updateStatus(download.id, DownloadStatus.PAUSED, download.progress)
            updateNotification()
            return
        }
        
        val dir = if (download.format == "audio") "audio" else "video"
        val customFolder = preferences.downloadFolder.first()
        val baseDir = if (customFolder.isNotEmpty()) File(customFolder) else filesDir
        val outputDir = File(baseDir, "MediaNest/$dir")
        outputDir.mkdirs()

        val tmpFile = File(outputDir, "${download.videoId}_${download.quality}.tmp")
        val audioFile = File(outputDir, "${download.videoId}_${download.quality}_audio.tmp")
        
        var ext = if (download.url.contains("webm", ignoreCase = true) || download.quality.contains("webm", ignoreCase = true)) "webm" else "mp4"
        var videoDownloadCompleted = false

        if (download.format == "video_only" && tmpFile.exists() && download.fileSizeBytes > 0L && tmpFile.length() >= download.fileSizeBytes) {
            videoDownloadCompleted = true
        }

        // Step 1: Download video (or primary stream)
        val videoSuccess = if (videoDownloadCompleted) true else {
            downloadUrlToFile(
                download = download,
                url = download.url,
                tmpFile = tmpFile,
                startProgress = 0.0f,
                endProgress = if (download.format == "video_only") 0.90f else 1.0f,
                progressMessage = null,
                isAudioStream = false
            ) {
                val freshInfo = extractor.extractVideo(download.videoUrl ?: download.url)
                val matchingStream = freshInfo.streamSources.find {
                    it.format == download.format && it.quality == download.quality
                }
                matchingStream?.url
            }
        }

        if (!videoSuccess) {
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

                    if (!downloadAudioSuccess) {
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
                        if (audioFile.exists()) audioFile.delete()
                        tmpFile.delete()
                        repository.markFailed(download.id, e.message ?: "Audio download/merge failed", audioRetries)
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
                    repository.markFailed(download.id, "Failed to move temp file: ${e.message}", 0)
                    return
                }
            }
        }

        repository.markCompleted(download.id, outputFile.length(), outputFile.absolutePath)
        activeProgress.remove(download.id)
        updateNotification()

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
  
      private fun pauseDownload(id: Long) {
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
          serviceScope.launch {
              val download = repository.getDownloadById(id) ?: return@launch
              val effectiveProgress = progressAtPause ?: download.progress
              if (!activeProgress.containsKey(id)) {
                  showPausedNotification(
                      downloadId = id,
                      title = download.title.ifEmpty { download.quality },
                      progress = effectiveProgress,
                      totalBytes = download.fileSizeBytes
                  )
              }
              repository.updateStatus(id, DownloadStatus.PAUSED, effectiveProgress)
              updateNotification()
              processQueue()
          }
      }

      private fun putDownloadingToQueue(id: Long) {
          putToQueueFlags[id] = true
          val job = activeJobs[id]
          if (job != null) {
              activeCalls[id]?.cancel()
              job.cancel()
          } else {
              serviceScope.launch {
                  val download = repository.getDownloadById(id) ?: return@launch
                  repository.updateStatus(id, DownloadStatus.QUEUED, download.progress)
                  withContext(Dispatchers.Main) {
                      putToQueueFlags.remove(id)
                      processQueue()
                  }
              }
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
                 withContext(Dispatchers.Main) {
                     processQueue()
                 }
             }
         }
     }
 
      private fun cancelDownload(id: Long) {
          cancelFlags[id] = true
          pauseFlags.remove(id)
          val job = activeJobs[id]
          if (job != null) {
              activeCalls[id]?.cancel()
              job.cancel()
          }
          serviceScope.launch {
              val download = repository.getDownloadById(id) ?: return@launch
              if (download.filePath.isNotEmpty()) {
                  File(download.filePath).delete()
              }
              val dir = if (download.format == "audio") "audio" else "video"
              val customFolder = preferences.downloadFolder.first()
              val baseDir = if (customFolder.isNotEmpty()) File(customFolder) else filesDir
              val outputDir = File(baseDir, "MediaNest/$dir")
              val tmpFile = File(outputDir, "${download.videoId}_${download.quality}.tmp")
              if (tmpFile.exists()) {
                  tmpFile.delete()
              }
              val audioFile = File(outputDir, "${download.videoId}_${download.quality}_audio.tmp")
              if (audioFile.exists()) {
                  audioFile.delete()
              }
              // Idempotent update
              repository.updateStatus(id, DownloadStatus.CANCELED, download.progress)
              updateNotification()
              processQueue()
          }
      }

      private fun restartDownload(id: Long) {
           serviceScope.launch {
               val download = repository.getDownloadById(id) ?: return@launch
               try {
                    val dir = if (download.format == "audio" || download.format == "audio_extracted") "audio" else "video"
                    val customFolder = preferences.downloadFolder.first()
                    val baseDir = if (customFolder.isNotEmpty()) File(customFolder) else filesDir
                    val outputDir = File(baseDir, "MediaNest/$dir")
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
                   retryCount = 0
               )
               repository.update(reset)
               resumeDownload(id)
           }
       }

      private fun pauseAllDownloads() {
          serviceScope.launch {
              val downloading = repository.getDownloadsByStatus(DownloadStatus.DOWNLOADING).first()
                  .filter { it.format != "audio_extracted" }
              val queued = repository.getDownloadsByStatus(DownloadStatus.QUEUED).first()
                  .filter { it.format != "audio_extracted" }
              
              downloading.forEach { pauseFlags[it.id] = true }
              queued.forEach { pauseFlags[it.id] = true }
              
              withContext(Dispatchers.Main) {
                  downloading.forEach {
                      activeCalls[it.id]?.cancel()
                      activeJobs[it.id]?.cancel()
                  }
              }
              
              downloading.forEach { repository.updateStatus(it.id, DownloadStatus.PAUSED, currentProgress(it.id) ?: it.progress) }
              queued.forEach { repository.updateStatus(it.id, DownloadStatus.PAUSED, it.progress) }
              
              updateNotification()
              withContext(Dispatchers.Main) {
                  processQueue()
              }
          }
      }

      private fun resumeAllDownloads() {
          serviceScope.launch {
              val paused = repository.getDownloadsByStatus(DownloadStatus.PAUSED).first()
                  .filter { it.format != "audio_extracted" }
              paused.forEach { download ->
                  pauseFlags.remove(download.id)
                  repository.updateStatus(download.id, DownloadStatus.QUEUED, download.progress)
              }
              withContext(Dispatchers.Main) {
                  processQueue()
              }
          }
      }

      private fun cancelAllDownloads() {
          serviceScope.launch {
              val downloading = repository.getDownloadsByStatus(DownloadStatus.DOWNLOADING).first()
                  .filter { it.format != "audio_extracted" }
              val queued = repository.getDownloadsByStatus(DownloadStatus.QUEUED).first()
                  .filter { it.format != "audio_extracted" }
              val paused = repository.getDownloadsByStatus(DownloadStatus.PAUSED).first()
                  .filter { it.format != "audio_extracted" }
              
              val all = downloading + queued + paused
              all.forEach { download ->
                  cancelFlags[download.id] = true
                  pauseFlags.remove(download.id)
              }
              
              withContext(Dispatchers.Main) {
                  all.forEach {
                      activeCalls[it.id]?.cancel()
                      activeJobs[it.id]?.cancel()
                  }
              }
              
              val customFolder = preferences.downloadFolder.first()
              val baseDir = if (customFolder.isNotEmpty()) File(customFolder) else filesDir
              all.forEach { download ->
                  if (download.filePath.isNotEmpty()) {
                      File(download.filePath).delete()
                  }
                  val dir = if (download.format == "audio") "audio" else "video"
                  val outputDir = File(baseDir, "MediaNest/$dir")
                  val tmpFile = File(outputDir, "${download.videoId}_${download.quality}.tmp")
                  if (tmpFile.exists()) {
                      tmpFile.delete()
                  }
                  val audioFile = File(outputDir, "${download.videoId}_${download.quality}_audio.tmp")
                  if (audioFile.exists()) {
                      audioFile.delete()
                  }
                  repository.updateStatus(download.id, DownloadStatus.CANCELED, download.progress)
              }
              
              withContext(Dispatchers.Main) {
                  processQueue()
              }
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
                        action = "com.example.medianest.ACTION_NAVIGATE_DOWNLOADS"
                    },
                    PendingIntent.FLAG_IMMUTABLE
                )
            )
            .build()
    }

    private suspend fun updateNotification() {
        val paused = repository.getDownloadsByStatus(DownloadStatus.PAUSED).first()

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

    private fun mergeAudioVideoNative(videoFile: File, audioFile: File, outputFile: File): Boolean {
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
