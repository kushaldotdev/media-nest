package com.example.medianest.service

import com.example.medianest.data.local.entity.DownloadEntity
import kotlinx.coroutines.sync.Mutex
import java.io.File
import java.util.UUID

object DownloadPathResolver {

    // FFmpegKit exposes process-global callbacks. Serialize invocations until each operation
    // can own an isolated callback and session through the library API.
    val ffmpegMutex = Mutex()

    fun resolveOutputDir(format: String, customFolder: String, filesDir: File): File {
        val dir = if (format == "audio" || format == "audio_extracted") "audio" else "video"
        return if (customFolder.isNotEmpty()) {
            File(File(customFolder), dir)
        } else {
            File(filesDir, "MediaNest/$dir")
        }
    }

    fun resolveOutputDir(entity: DownloadEntity, filesDir: File): File {
        requireValidUuid(entity.downloadUuid)
        return resolveOutputDir(entity.format, entity.outputRoot, filesDir)
    }

    fun tempVideoFile(entity: DownloadEntity, outputDir: File): File =
        ownedFile(entity, outputDir, "_video.tmp")

    fun tempAudioFile(entity: DownloadEntity, outputDir: File): File =
        ownedFile(entity, outputDir, "_audio.tmp")

    fun outputFile(entity: DownloadEntity, outputDir: File, ext: String): File =
        ownedFile(entity, outputDir, ".${ext.lowercase()}")

    fun extractTempFile(entity: DownloadEntity, outputDir: File, ext: String): File =
        ownedFile(entity, outputDir, "_extract.${ext.lowercase()}")

    fun deleteOwnedFiles(entity: DownloadEntity, outputDir: File, extensions: Iterable<String>) {
        tempVideoFile(entity, outputDir).delete()
        tempAudioFile(entity, outputDir).delete()
        extensions.forEach { extension ->
            outputFile(entity, outputDir, extension).delete()
            extractTempFile(entity, outputDir, extension).delete()
        }
    }

    fun validatePathUnderRoot(file: File, root: File): File {
        val canonicalFile = file.canonicalFile
        val canonicalRoot = root.canonicalFile
        val filePath = canonicalFile.absolutePath
        val rootPath = canonicalRoot.absolutePath

        if (!filePath.startsWith(rootPath + File.separator) && filePath != rootPath) {
            throw SecurityException("Path escape: $filePath not under $rootPath")
        }
        return canonicalFile
    }

    private fun ownedFile(entity: DownloadEntity, outputDir: File, suffix: String): File {
        val uuid = requireValidUuid(entity.downloadUuid)
        val name = buildString {
            append(sanitizeFileName(entity.title))
            if (isNotEmpty()) append(" - ")
            append(uuid.take(8))
            append(" - ")
            append(sanitizeFileName(entity.quality))
            append(suffix)
        }
        return validatePathUnderRoot(File(outputDir, name), outputDir)
    }

    private fun sanitizeFileName(value: String): String {
        val cleaned = value
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .replace(Regex("[\\p{Cntrl}]"), "_")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(80)
        return cleaned.ifEmpty { "video" }
    }

    private fun requireValidUuid(value: String): String {
        val uuid = runCatching { UUID.fromString(value) }.getOrElse {
            throw SecurityException("Invalid download UUID")
        }
        require(uuid.toString().equals(value, ignoreCase = true)) { "Invalid download UUID" }
        return uuid.toString()
    }
}
