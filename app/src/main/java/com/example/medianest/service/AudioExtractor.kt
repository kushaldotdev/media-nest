package com.example.medianest.service

import android.content.Context
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

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
        inputFilePath: String,
        videoId: String,
        quality: String
    ): ExtractionResult = withContext(Dispatchers.IO) {
        val outputDir = File(context.filesDir, "MediaNest/audio")
        outputDir.mkdirs()

        val outputFileName = "${videoId}_${quality}_audio.mp3"
        val outputFile = File(outputDir, outputFileName)

        if (outputFile.exists()) outputFile.delete()

        val command = "-i \"$inputFilePath\" -vn -acodec libmp3lame -q:a 2 \"${outputFile.absolutePath}\""

        val session = FFmpegKit.execute(command)
        val returnCode = session.returnCode

        if (ReturnCode.isSuccess(returnCode)) {
            ExtractionResult(outputFile.absolutePath, true)
        } else {
            val logs = session.allLogsAsString
            ExtractionResult("", false, logs.ifEmpty { "ffmpeg extraction failed" })
        }
    }
}
