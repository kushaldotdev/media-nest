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
    val errors: List<String> = emptyList()
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
            
            val customFolder = downloadPreferences.downloadFolder.first()
            val baseDirs = mutableListOf(context.filesDir)
            if (customFolder.isNotEmpty()) {
                baseDirs.add(File(customFolder))
            }
            context.getExternalFilesDir(null)?.let { baseDirs.add(it) }

            val mediaFiles = mutableMapOf<String, File>()
            for (baseDir in baseDirs) {
                val videoDir = File(baseDir, "MediaNest/video")
                val audioDir = File(baseDir, "MediaNest/audio")
                for (dir in listOf(videoDir, audioDir)) {
                    if (dir.exists()) {
                        dir.listFiles()?.forEach { file ->
                            mediaFiles[file.name] = file
                        }
                    }
                }
            }
            progress(0.3f)

            val filesFound = mediaFiles.size
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
                            videoDao.update(video.copy(localFilePath = matchingFile.absolutePath))
                            pathsFixed++
                        }
                    } else if (video.localFilePath.isNotEmpty()) {
                        videoDao.update(video.copy(localFilePath = ""))
                        pathsCleared++
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
                        downloadDao.update(download.copy(filePath = ""))
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
                    file.delete()
                    orphansRemoved++
                }
            }
            progress(1.0f)

            RepairResult(
                filesFound = filesFound,
                pathsFixed = pathsFixed,
                pathsCleared = pathsCleared,
                orphansRemoved = orphansRemoved,
                errors = errors
            )
        }
    }
}
