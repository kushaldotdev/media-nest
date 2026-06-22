package com.example.medianest.data.backup

import android.content.Context
import androidx.room.withTransaction
import com.example.medianest.data.local.AppDatabase
import com.example.medianest.data.local.dao.DownloadDao
import com.example.medianest.data.local.dao.FolderDao
import com.example.medianest.data.local.dao.HistoryDao
import com.example.medianest.data.local.dao.PlaylistDao
import com.example.medianest.data.local.dao.SubscriptionDao
import com.example.medianest.data.local.dao.VideoDao
import com.example.medianest.data.local.dao.VideoFolderDao
import com.example.medianest.data.local.entity.DownloadEntity
import com.example.medianest.data.local.entity.DownloadStatus
import com.example.medianest.data.local.entity.FolderEntity
import com.example.medianest.data.local.entity.HistoryEntity
import com.example.medianest.data.local.entity.PlaylistEntity
import com.example.medianest.data.local.entity.SubscriptionEntity
import com.example.medianest.data.local.entity.VideoEntity
import com.example.medianest.data.local.entity.VideoFolderJoin
import com.example.medianest.data.preferences.DownloadPreferences
import com.example.medianest.data.preferences.PlaybackPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.io.InputStream
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RestoreRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val json: Json,
    private val database: AppDatabase,
    private val videoDao: VideoDao,
    private val downloadDao: DownloadDao,
    private val historyDao: HistoryDao,
    private val folderDao: FolderDao,
    private val videoFolderDao: VideoFolderDao,
    private val playlistDao: PlaylistDao,
    private val subscriptionDao: SubscriptionDao,
    private val downloadPreferences: DownloadPreferences,
    private val playbackPreferences: PlaybackPreferences
) {
    suspend fun restoreFromZip(inputStream: InputStream, progress: (Float) -> Unit) {
        withContext(Dispatchers.IO) {
            var databaseJson: String? = null
            var preferencesJson: String? = null
            
            val videoDir = File(context.filesDir, "MediaNest/video").also { it.mkdirs() }
            val audioDir = File(context.filesDir, "MediaNest/audio").also { it.mkdirs() }

            ZipInputStream(inputStream).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    when {
                        entry.name == "database.json" -> {
                            databaseJson = zip.readBytes().toString(Charsets.UTF_8)
                        }
                        entry.name == "preferences.json" -> {
                            preferencesJson = zip.readBytes().toString(Charsets.UTF_8)
                        }
                        entry.name.startsWith("media/") -> {
                            val name = entry.name.removePrefix("media/")
                            val target = if (name.contains("_audio")) audioDir else videoDir
                            val file = File(target, name)
                            if (!file.canonicalPath.startsWith(target.canonicalPath + File.separator)) {
                                throw SecurityException("Zip Slip detected: entry path is outside destination directory")
                            }
                            file.outputStream().use { fos ->
                                zip.copyTo(fos)
                            }
                        }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
            progress(0.2f)

            // Restore database
            if (databaseJson != null) {
                val data = json.decodeFromString<BackupData>(databaseJson)
                progress(0.3f)

                database.withTransaction {
                    database.clearAllTables()
                    for (video in data.videos) {
                        videoDao.insert(video.toEntity())
                    }
                    progress(0.4f)

                    for (download in data.downloads) {
                        downloadDao.insert(download.toEntity())
                    }
                    progress(0.5f)

                    for (hist in data.history) {
                        historyDao.upsert(hist.toEntity())
                    }
                    progress(0.55f)

                    for (folder in data.folders) {
                        folderDao.insert(folder.toEntity())
                    }
                    progress(0.6f)

                    for (join in data.videoFolderJoins) {
                        videoFolderDao.addVideoToFolder(join.toEntity())
                    }
                    progress(0.65f)

                    for (playlist in data.playlists) {
                        playlistDao.insert(playlist.toEntity())
                    }
                    progress(0.7f)

                    for (sub in data.subscriptions) {
                        subscriptionDao.insert(sub.toEntity())
                    }
                }
                progress(0.75f)
            }

            // Restore preferences
            if (preferencesJson != null) {
                try {
                    val prefs = json.decodeFromString<BackupPreferences>(preferencesJson)
                    prefs.downloads["max_concurrent"]?.toIntOrNull()?.let {
                        downloadPreferences.setMaxConcurrentDownloads(it)
                    }
                    prefs.playback["playback_speed"]?.toFloatOrNull()?.let {
                        playbackPreferences.setPlaybackSpeed(it)
                    }
                } catch (_: Exception) {
                    // Skip preference restore on parse failure
                }
            } else if (databaseJson != null) {
                try {
                    val data = json.decodeFromString<BackupData>(databaseJson)
                    data.preferences.downloads["max_concurrent"]?.toIntOrNull()?.let {
                        downloadPreferences.setMaxConcurrentDownloads(it)
                    }
                    data.preferences.playback["playback_speed"]?.toFloatOrNull()?.let {
                        playbackPreferences.setPlaybackSpeed(it)
                    }
                } catch (_: Exception) {}
            }
            progress(0.85f)

            progress(1.0f)
        }
    }

    private fun BackupVideo.toEntity() = VideoEntity(
        id = id, title = title, channelName = channelName, channelId = channelId,
        durationSeconds = durationSeconds, thumbnailUrl = thumbnailUrl,
        description = description, uploadDate = uploadDate,
        localFilePath = localFilePath, favorite = favorite, addedAt = addedAt
    )

    private fun BackupDownload.toEntity() = DownloadEntity(
        id = id, videoId = videoId, url = url, format = format, quality = quality,
        title = title, thumbnailUrl = thumbnailUrl, filePath = filePath,
        fileSizeBytes = fileSizeBytes, downloadedAt = downloadedAt,
        lastPlayedAt = lastPlayedAt,
        status = try { DownloadStatus.valueOf(status) } catch (_: Exception) { DownloadStatus.COMPLETED },
        progress = progress, errorMessage = errorMessage, retryCount = retryCount
    )

    private fun BackupHistory.toEntity() = HistoryEntity(
        videoId = videoId, positionMillis = positionMillis, playedAt = playedAt
    )

    private fun BackupFolder.toEntity() = FolderEntity(
        id = id, name = name, parentId = parentId, createdAt = createdAt, updatedAt = updatedAt
    )

    private fun BackupVideoFolderJoin.toEntity() = VideoFolderJoin(
        videoId = videoId, folderId = folderId, addedAt = addedAt
    )

    private fun BackupPlaylist.toEntity() = PlaylistEntity(
        id = id, name = name, description = description, thumbnailUrl = thumbnailUrl,
        youtubePlaylistId = youtubePlaylistId, uploaderName = uploaderName,
        videoCount = videoCount, createdAt = createdAt, updatedAt = updatedAt
    )

    private fun BackupSubscription.toEntity() = SubscriptionEntity(
        id = id, sourceType = sourceType, sourceId = sourceId, name = name,
        thumbnailUrl = thumbnailUrl, uploaderName = uploaderName,
        autoDownload = autoDownload, audioOnly = audioOnly,
        lastCheckedAt = lastCheckedAt, createdAt = createdAt, updatedAt = updatedAt
    )
}
