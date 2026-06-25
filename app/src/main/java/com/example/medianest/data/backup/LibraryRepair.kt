package com.example.medianest.data.backup

import android.content.Context
import com.example.medianest.data.local.dao.DownloadDao
import com.example.medianest.data.local.dao.VideoDao
import com.example.medianest.data.preferences.DownloadPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class RepairResult(
    val filesFound: Int = 0,
    val pathsFixed: Int = 0,
    val pathsCleared: Int = 0,
    val orphansRemoved: Int = 0,
    val errors: List<String> = emptyList(),
    val details: List<String> = emptyList()
)

@Singleton
class LibraryRepair @Inject constructor(
    @ApplicationContext private val context: Context,
    private val videoDao: VideoDao,
    private val downloadDao: DownloadDao,
    private val downloadPreferences: DownloadPreferences
) {
    suspend fun repair(progress: (Float) -> Unit): RepairResult {
        return withContext(Dispatchers.IO) {
            val errors = mutableListOf<String>()
            val details = mutableListOf<String>()
            val repairActions = mutableListOf<String>()
            
            val customFolder = downloadPreferences.downloadFolder.first()
            val dirs = mutableListOf<File>()
            
            if (customFolder.isNotEmpty()) {
                dirs.add(File(File(customFolder), "video"))
                dirs.add(File(File(customFolder), "audio"))
            } else {
                dirs.add(File(context.filesDir, "MediaNest/video"))
                dirs.add(File(context.filesDir, "MediaNest/audio"))
            }

            // Log Directories Scanned
            details.add("Directories Scanned:")
            val distinctDirs = dirs.distinct()
            distinctDirs.forEach { dir ->
                if (dir.exists()) {
                    details.add("  • ${dir.absolutePath}")
                } else {
                    details.add("  • ${dir.absolutePath} (not found on disk)")
                }
            }
            details.add("") // Spacer

            val mediaFiles = mutableMapOf<String, File>()
            for (dir in distinctDirs) {
                if (dir.exists()) {
                    dir.listFiles()?.forEach { file ->
                        mediaFiles[file.name] = file
                    }
                }
            }
            progress(0.3f)

            // Log Files Found
            val filesFound = mediaFiles.size
            details.add("Files Found on Disk ($filesFound):")
            if (filesFound > 0) {
                val listToLog = mediaFiles.keys.sorted()
                val limit = 50
                val loggedFiles = listToLog.take(limit)
                loggedFiles.forEach { fileName ->
                    details.add("  • $fileName")
                }
                if (filesFound > limit) {
                    details.add("  • ... and ${filesFound - limit} more files.")
                }
            } else {
                details.add("  • No media files found on disk.")
            }
            details.add("") // Spacer

            var pathsFixed = 0
            var pathsCleared = 0

            // Fix video entities
            try {
                val videos = videoDao.getAllVideos().first()
                for (video in videos) {
                    val matchingFile = mediaFiles.entries.find { (name, _) ->
                        name.startsWith("${video.id}_")
                    }?.value
                    if (matchingFile != null) {
                        if (video.localFilePath != matchingFile.absolutePath) {
                            val oldPath = video.localFilePath
                            videoDao.update(video.copy(localFilePath = matchingFile.absolutePath))
                            pathsFixed++
                            repairActions.add("Fixed path for video '${video.title}': found file at '${matchingFile.absolutePath}' (was '$oldPath')")
                        }
                    } else if (video.localFilePath.isNotEmpty()) {
                        val oldPath = video.localFilePath
                        videoDao.update(video.copy(localFilePath = ""))
                        pathsCleared++
                        repairActions.add("Cleared missing path for video '${video.title}': file not found at '$oldPath'")
                    }
                }
            } catch (e: Exception) {
                errors.add("Video repair failed: ${e.message}")
            }
            progress(0.6f)

            // Fix download entities
            try {
                val downloads = downloadDao.getAllDownloads().first()
                for (download in downloads) {
                    if (download.filePath.isNotEmpty() && !File(download.filePath).exists()) {
                        val oldPath = download.filePath
                        downloadDao.update(download.copy(filePath = ""))
                        repairActions.add("Cleared missing path for download '${download.title}': file not found at '$oldPath'")
                    }
                }
            } catch (e: Exception) {
                errors.add("Download repair failed: ${e.message}")
            }
            progress(0.8f)

            val registeredPaths = mutableSetOf<String>()
            try {
                videoDao.getAllVideos().first().forEach { video ->
                    if (video.localFilePath.isNotEmpty()) {
                        registeredPaths.add(File(video.localFilePath).absolutePath)
                    }
                }
            } catch (_: Exception) {}
            try {
                downloadDao.getAllDownloads().first().forEach { download ->
                    if (download.filePath.isNotEmpty()) {
                        registeredPaths.add(File(download.filePath).absolutePath)
                    }
                }
            } catch (_: Exception) {}

            // Remove orphan media files (no matching video)
            var orphansRemoved = 0
            for ((_, file) in mediaFiles) {
                if (file.absolutePath !in registeredPaths) {
                    val fileName = file.name
                    file.delete()
                    orphansRemoved++
                    repairActions.add("Removed orphan media file: '$fileName'")
                }
            }
            progress(1.0f)

            // Log Repairs Performed
            details.add("Repairs Performed:")
            if (repairActions.isNotEmpty()) {
                repairActions.forEach { action ->
                    details.add("  • $action")
                }
            } else {
                details.add("  • No repairs were needed. Library is in sync.")
            }

            RepairResult(
                filesFound = filesFound,
                pathsFixed = pathsFixed,
                pathsCleared = pathsCleared,
                orphansRemoved = orphansRemoved,
                errors = errors,
                details = details
            )
        }
    }
}
