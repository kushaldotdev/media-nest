package com.example.medianest.data.backup

import android.content.Context
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
import com.example.medianest.data.local.entity.PlaylistEntity
import com.example.medianest.data.local.entity.SubscriptionEntity
import com.example.medianest.data.local.entity.VideoEntity
import com.example.medianest.data.local.entity.VideoFolderJoin
import com.example.medianest.data.preferences.DownloadPreferences
import com.example.medianest.data.preferences.PlaybackPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val json: Json,
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
    suspend fun exportToZip(outputStream: FileOutputStream, progress: (Float) -> Unit) {
        withContext(Dispatchers.IO) {
            ZipOutputStream(outputStream).use { zos ->
                progress(0.05f)

                val videos = videoDao.getAllVideos().first()
                progress(0.15f)
                val downloads = downloadDao.getAllDownloads().first()
                progress(0.2f)
                val history = historyDao.getAllHistory().first()
                progress(0.25f)
                val folders = folderDao.getAllFolders().first()
                progress(0.3f)
                val subscriptions = subscriptionDao.getAllSubscriptions().first()
                progress(0.35f)
                val playlists = playlistDao.getAllPlaylists().first()
                progress(0.4f)
                val joins = videoFolderDao.getAllJoins()
                progress(0.45f)

                val preferences = BackupPreferences(
                    downloads = mapOf("max_concurrent" to downloadPreferences.maxConcurrentDownloads.first().toString()),
                    playback = mapOf("playback_speed" to playbackPreferences.playbackSpeed.first().toString())
                )

                val backupData = BackupData(
                    metadata = BackupMetadata(
                        videoCount = videos.size,
                        downloadCount = downloads.size,
                        mediaFileCount = countMediaFiles()
                    ),
                    videos = videos.map { it.toBackup() },
                    downloads = downloads.map { it.toBackup() },
                    history = history.map { it.toBackup() },
                    folders = folders.map { it.toBackup() },
                    playlists = playlists.map { it.toBackup() },
                    subscriptions = subscriptions.map { it.toBackup() },
                    videoFolderJoins = joins.map { it.toBackup() },
                    preferences = preferences
                )

                // Write database.json
                zos.putNextEntry(ZipEntry("database.json"))
                zos.write(json.encodeToString(backupData).toByteArray())
                zos.closeEntry()
                progress(0.6f)

                // Write media files
                val mediaFiles = listOf(
                    File(context.filesDir, "MediaNest/video"),
                    File(context.filesDir, "MediaNest/audio")
                ).flatMap { dir ->
                    if (dir.exists()) dir.listFiles()?.toList() ?: emptyList() else emptyList()
                }
                var written = 0
                val total = mediaFiles.size.coerceAtLeast(1)
                for (file in mediaFiles) {
                    zos.putNextEntry(ZipEntry("media/${file.name}"))
                    file.inputStream().use { it.copyTo(zos) }
                    zos.closeEntry()
                    written++
                    progress(0.6f + 0.4f * written / total)
                }
            }
        }
    }

    private fun countMediaFiles(): Int {
        return listOf(
            File(context.filesDir, "MediaNest/video"),
            File(context.filesDir, "MediaNest/audio")
        ).sumOf { dir -> if (dir.exists()) dir.listFiles()?.size ?: 0 else 0 }
    }

    private fun VideoEntity.toBackup() = BackupVideo(
        id = id, title = title, channelName = channelName, channelId = channelId,
        durationSeconds = durationSeconds, thumbnailUrl = thumbnailUrl,
        description = description, uploadDate = uploadDate,
        localFilePath = localFilePath, favorite = favorite, addedAt = addedAt
    )

    private fun DownloadEntity.toBackup() = BackupDownload(
        id = id, videoId = videoId, url = url, format = format, quality = quality,
        title = title, thumbnailUrl = thumbnailUrl, filePath = filePath,
        fileSizeBytes = fileSizeBytes, downloadedAt = downloadedAt,
        lastPlayedAt = lastPlayedAt, status = status.name, progress = progress,
        errorMessage = errorMessage, retryCount = retryCount
    )

    private fun com.example.medianest.data.local.entity.HistoryEntity.toBackup() = BackupHistory(
        videoId = videoId, positionMillis = positionMillis, playedAt = playedAt
    )

    private fun FolderEntity.toBackup() = BackupFolder(
        id = id, name = name, parentId = parentId, createdAt = createdAt, updatedAt = updatedAt
    )

    private fun VideoFolderJoin.toBackup() = BackupVideoFolderJoin(
        videoId = videoId, folderId = folderId, addedAt = addedAt
    )

    private fun PlaylistEntity.toBackup() = BackupPlaylist(
        id = id, name = name, description = description, thumbnailUrl = thumbnailUrl,
        youtubePlaylistId = youtubePlaylistId, uploaderName = uploaderName,
        videoCount = videoCount, createdAt = createdAt, updatedAt = updatedAt
    )

    private fun SubscriptionEntity.toBackup() = BackupSubscription(
        id = id, sourceType = sourceType, sourceId = sourceId, name = name,
        thumbnailUrl = thumbnailUrl, uploaderName = uploaderName,
        autoDownload = autoDownload, audioOnly = audioOnly,
        lastCheckedAt = lastCheckedAt, createdAt = createdAt, updatedAt = updatedAt
    )
}
