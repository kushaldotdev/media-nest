package com.example.medianest.service

import android.content.Context
import android.media.MediaCodec
import com.example.medianest.data.local.entity.DownloadEntity
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

@Singleton
class AudioExtractor @Inject constructor(
    @ApplicationContext private val context: Context
) {
    data class ExtractionResult(
        val outputPath: String,
        val success: Boolean,
        val errorMessage: String? = null
    )

    suspend fun extractAudio(
        entity: DownloadEntity,
        inputFilePath: String,
        onProgress: (suspend (Float) -> Unit)? = null
    ): ExtractionResult = withContext(Dispatchers.IO) {
        val outputDir = DownloadPathResolver.resolveOutputDir(entity, context.filesDir)
        outputDir.mkdirs()

        try {
            // Try native demuxing first (generates .m4a container, extremely fast, 100% crash-free)
            val m4aOutputFile = DownloadPathResolver.extractTempFile(entity, outputDir, "m4a")
            if (m4aOutputFile.exists()) m4aOutputFile.delete()

            val nativeSuccess = extractAudioNatively(inputFilePath, m4aOutputFile)
            if (nativeSuccess && m4aOutputFile.exists() && m4aOutputFile.length() > 0) {
                return@withContext ExtractionResult(m4aOutputFile.absolutePath, true)
            }
            m4aOutputFile.delete()

            // Try fast FFmpeg stream copy of the audio track (extremely fast, under 1 second)
            val copyExt = if (inputFilePath.endsWith(".webm", ignoreCase = true)) "ogg" else "m4a"
            val copyOutputFile = DownloadPathResolver.extractTempFile(entity, outputDir, copyExt)
            if (copyOutputFile.exists()) copyOutputFile.delete()

            try {
                val copyCommand = "-y -i \"$inputFilePath\" -vn -c:a copy \"${copyOutputFile.absolutePath}\""
                val session = DownloadPathResolver.ffmpegMutex.withLock {
                    executeFfmpeg(copyCommand)
                }
                if (com.arthenica.ffmpegkit.ReturnCode.isSuccess(session.returnCode)) {
                    return@withContext ExtractionResult(copyOutputFile.absolutePath, true)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                copyOutputFile.delete()
                throw e
            } catch (t: Throwable) {
                // Fall through to transcoding
            }
            copyOutputFile.delete()

            // Fallback to FFmpegKit transcoding if copy fails (produces .mp3, wrapped in try-catch)
            val mp3OutputFile = DownloadPathResolver.extractTempFile(entity, outputDir, "mp3")
            if (mp3OutputFile.exists()) mp3OutputFile.delete()

            try {
                val command = "-y -i \"$inputFilePath\" -vn -acodec libmp3lame -q:a 2 \"${mp3OutputFile.absolutePath}\""
                
                val mediaMetadataRetriever = android.media.MediaMetadataRetriever()
                var durationMs = 0L
                try {
                    mediaMetadataRetriever.setDataSource(inputFilePath)
                    durationMs = mediaMetadataRetriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                } catch (e: Exception) {
                    // Ignore
                } finally {
                    try {
                        mediaMetadataRetriever.release()
                    } catch (e: Exception) {}
                }
                val durationSeconds = durationMs / 1000L

                val session = DownloadPathResolver.ffmpegMutex.withLock {
                    if (onProgress != null && durationSeconds > 0) {
                        com.arthenica.ffmpegkit.FFmpegKitConfig.enableStatisticsCallback { stats ->
                            val progress = ((stats.time / 1000.0) / durationSeconds).coerceIn(0.0, 1.0).toFloat()
                            // Do not block FFmpeg's callback thread on database work.
                            kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                                onProgress(progress)
                            }
                        }
                    }
                    try {
                        executeFfmpeg(command)
                    } finally {
                        com.arthenica.ffmpegkit.FFmpegKitConfig.enableStatisticsCallback(null)
                    }
                }
                val returnCode = session.returnCode

                if (com.arthenica.ffmpegkit.ReturnCode.isSuccess(returnCode)) {
                    ExtractionResult(mp3OutputFile.absolutePath, true)
                } else {
                    mp3OutputFile.delete()
                    val logs = session.allLogsAsString ?: "ffmpeg extraction failed"
                    ExtractionResult("", false, logs.ifEmpty { "ffmpeg extraction failed" })
                }
            } catch (t: Throwable) {
                mp3OutputFile.delete()
                throw t
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (t: Throwable) {
            ExtractionResult(
                outputPath = "",
                success = false,
                errorMessage = "Extraction failed: native extractor failed and FFmpeg could not be initialized (${t.message})"
            )
        }
    }

    private suspend fun extractAudioNatively(inputFilePath: String, outputFile: File): Boolean {
        var extractor: MediaExtractor? = null
        var muxer: MediaMuxer? = null
        try {
            extractor = MediaExtractor()
            extractor.setDataSource(inputFilePath)

            var audioTrackIndex = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val trackFormat = extractor.getTrackFormat(i)
                val mime = trackFormat.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    format = trackFormat
                    break
                }
            }

            if (audioTrackIndex == -1 || format == null) return false
            if (format.getString(MediaFormat.KEY_MIME) != "audio/mp4a-latm") return false

            extractor.selectTrack(audioTrackIndex)

            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val writeTrackIndex = muxer.addTrack(format)
            muxer.start()

            val maxBufferSize = if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE)
            } else {
                64 * 1024
            }
            val buffer = ByteBuffer.allocate(maxBufferSize)
            val bufferInfo = MediaCodec.BufferInfo()

            while (true) {
                coroutineContext.ensureActive()
                bufferInfo.offset = 0
                bufferInfo.size = extractor.readSampleData(buffer, 0)
                if (bufferInfo.size < 0) {
                    break
                }
                bufferInfo.presentationTimeUs = extractor.sampleTime
                bufferInfo.flags = extractor.sampleFlags
                muxer.writeSampleData(writeTrackIndex, buffer, bufferInfo)
                extractor.advance()
            }

            return true
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        } finally {
            runCatching { extractor?.release() }
            runCatching {
                muxer?.stop()
                muxer?.release()
            }
        }
    }

    private suspend fun executeFfmpeg(command: String): com.arthenica.ffmpegkit.FFmpegSession {
        val completedSession = CompletableDeferred<com.arthenica.ffmpegkit.FFmpegSession>()
        val session = com.arthenica.ffmpegkit.FFmpegKit.executeAsync(command) {
            completedSession.complete(it)
        }
        try {
            return completedSession.await()
        } finally {
            if (!completedSession.isCompleted) {
                com.arthenica.ffmpegkit.FFmpegKit.cancel(session.sessionId)
            }
        }
    }
}
