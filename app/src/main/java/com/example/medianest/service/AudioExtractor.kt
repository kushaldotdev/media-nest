package com.example.medianest.service

import android.content.Context
import android.media.MediaCodec
import com.example.medianest.data.preferences.DownloadPreferences
import kotlinx.coroutines.flow.first
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudioExtractor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloadPreferences: DownloadPreferences
) {
    data class ExtractionResult(
        val outputPath: String,
        val success: Boolean,
        val errorMessage: String? = null
    )

    suspend fun extractAudio(
        inputFilePath: String,
        videoId: String,
        quality: String
    ): ExtractionResult = withContext(Dispatchers.IO) {
        val customFolder = downloadPreferences.downloadFolder.first()
        val outputDir = if (customFolder.isNotEmpty()) {
            File(File(customFolder), "audio")
        } else {
            File(context.filesDir, "MediaNest/audio")
        }
        outputDir.mkdirs()

        // Try native demuxing first (generates .m4a container, extremely fast, 100% crash-free)
        val m4aFileName = "${videoId}_${quality}_audio.m4a"
        val m4aOutputFile = File(outputDir, m4aFileName)
        if (m4aOutputFile.exists()) m4aOutputFile.delete()

        val nativeSuccess = extractAudioNatively(inputFilePath, m4aOutputFile)
        if (nativeSuccess && m4aOutputFile.exists() && m4aOutputFile.length() > 0) {
            return@withContext ExtractionResult(m4aOutputFile.absolutePath, true)
        }

        // Fallback to FFmpegKit if native demuxing fails (produces .mp3, wrapped in try-catch to avoid native library link crashes)
        val mp3FileName = "${videoId}_${quality}_audio.mp3"
        val mp3OutputFile = File(outputDir, mp3FileName)
        if (mp3OutputFile.exists()) mp3OutputFile.delete()

        try {
            val command = "-i \"$inputFilePath\" -vn -acodec libmp3lame -q:a 2 \"${mp3OutputFile.absolutePath}\""
            val session = com.arthenica.ffmpegkit.FFmpegKit.execute(command)
            val returnCode = session.returnCode

            if (com.arthenica.ffmpegkit.ReturnCode.isSuccess(returnCode)) {
                ExtractionResult(mp3OutputFile.absolutePath, true)
            } else {
                val logs = session.allLogsAsString ?: "ffmpeg extraction failed"
                ExtractionResult("", false, logs.ifEmpty { "ffmpeg extraction failed" })
            }
        } catch (t: Throwable) {
            ExtractionResult(
                outputPath = "",
                success = false,
                errorMessage = "Extraction failed: native extractor failed and FFmpeg could not be initialized (${t.message})"
            )
        }
    }

    private fun extractAudioNatively(inputFilePath: String, outputFile: File): Boolean {
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
}
