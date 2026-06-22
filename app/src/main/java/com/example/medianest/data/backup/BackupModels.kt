package com.example.medianest.data.backup

import kotlinx.serialization.Serializable

@Serializable
data class BackupMetadata(
    val appVersion: String = "1.0",
    val schemaVersion: Int = 7,
    val exportedAt: Long = System.currentTimeMillis(),
    val videoCount: Int = 0,
    val downloadCount: Int = 0,
    val mediaFileCount: Int = 0
)

@Serializable
data class BackupVideo(
    val id: String,
    val title: String,
    val channelName: String,
    val channelId: String? = null,
    val durationSeconds: Long = 0,
    val thumbnailUrl: String? = null,
    val description: String? = null,
    val uploadDate: String? = null,
    val localFilePath: String = "",
    val favorite: Boolean = false,
    val addedAt: Long
)

@Serializable
data class BackupDownload(
    val id: Long,
    val videoId: String,
    val url: String,
    val format: String,
    val quality: String,
    val title: String,
    val thumbnailUrl: String? = null,
    val filePath: String = "",
    val fileSizeBytes: Long = 0,
    val downloadedAt: Long,
    val lastPlayedAt: Long? = null,
    val status: String,
    val progress: Float = 0f,
    val errorMessage: String? = null,
    val retryCount: Int = 0
)

@Serializable
data class BackupHistory(
    val videoId: String,
    val positionMillis: Long = 0,
    val playedAt: Long
)

@Serializable
data class BackupFolder(
    val id: Long,
    val name: String,
    val parentId: Long? = null,
    val createdAt: Long,
    val updatedAt: Long
)

@Serializable
data class BackupVideoFolderJoin(
    val videoId: String,
    val folderId: Long,
    val addedAt: Long
)

@Serializable
data class BackupPlaylist(
    val id: Long,
    val name: String,
    val description: String? = null,
    val thumbnailUrl: String? = null,
    val youtubePlaylistId: String = "",
    val uploaderName: String? = null,
    val videoCount: Int = 0,
    val createdAt: Long,
    val updatedAt: Long
)

@Serializable
data class BackupSubscription(
    val id: Long,
    val sourceType: String,
    val sourceId: String,
    val name: String,
    val thumbnailUrl: String? = null,
    val uploaderName: String? = null,
    val autoDownload: Boolean = false,
    val audioOnly: Boolean = false,
    val lastCheckedAt: Long = 0,
    val createdAt: Long,
    val updatedAt: Long
)

@Serializable
data class BackupPreferences(
    val downloads: Map<String, String> = emptyMap(),
    val playback: Map<String, String> = emptyMap()
)

@Serializable
data class BackupData(
    val metadata: BackupMetadata,
    val videos: List<BackupVideo> = emptyList(),
    val downloads: List<BackupDownload> = emptyList(),
    val history: List<BackupHistory> = emptyList(),
    val folders: List<BackupFolder> = emptyList(),
    val videoFolderJoins: List<BackupVideoFolderJoin> = emptyList(),
    val playlists: List<BackupPlaylist> = emptyList(),
    val subscriptions: List<BackupSubscription> = emptyList(),
    val preferences: BackupPreferences = BackupPreferences()
)
