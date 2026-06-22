package com.example.medianest.data.backup

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class BackupMetadata(
    @EncodeDefault(EncodeDefault.Mode.NEVER) val appVersion: String = "1.0",
    @EncodeDefault(EncodeDefault.Mode.NEVER) val schemaVersion: Int = 7,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val exportedAt: Long = System.currentTimeMillis(),
    @EncodeDefault(EncodeDefault.Mode.NEVER) val videoCount: Int = 0,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val downloadCount: Int = 0,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val mediaFileCount: Int = 0
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class BackupVideo(
    val id: String,
    val title: String,
    val channelName: String,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val channelId: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val durationSeconds: Long = 0,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val thumbnailUrl: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val description: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val uploadDate: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val localFilePath: String = "",
    @EncodeDefault(EncodeDefault.Mode.NEVER) val favorite: Boolean = false,
    val addedAt: Long
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class BackupDownload(
    val id: Long,
    val videoId: String,
    val url: String,
    val format: String,
    val quality: String,
    val title: String,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val thumbnailUrl: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val filePath: String = "",
    @EncodeDefault(EncodeDefault.Mode.NEVER) val fileSizeBytes: Long = 0,
    val downloadedAt: Long,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val lastPlayedAt: Long? = null,
    val status: String,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val progress: Float = 0f,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val errorMessage: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val retryCount: Int = 0
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class BackupHistory(
    val videoId: String,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val positionMillis: Long = 0,
    val playedAt: Long
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class BackupFolder(
    val id: Long,
    val name: String,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val parentId: Long? = null,
    val createdAt: Long,
    val updatedAt: Long
)

@Serializable
data class BackupVideoFolderJoin(
    val videoId: String,
    val folderId: Long,
    val addedAt: Long
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class BackupPlaylist(
    val id: Long,
    val name: String,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val description: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val thumbnailUrl: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val youtubePlaylistId: String = "",
    @EncodeDefault(EncodeDefault.Mode.NEVER) val uploaderName: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val videoCount: Int = 0,
    val createdAt: Long,
    val updatedAt: Long
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class BackupSubscription(
    val id: Long,
    val sourceType: String,
    val sourceId: String,
    val name: String,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val thumbnailUrl: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val uploaderName: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val autoDownload: Boolean = false,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val audioOnly: Boolean = false,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val lastCheckedAt: Long = 0,
    val createdAt: Long,
    val updatedAt: Long
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class BackupPreferences(
    @EncodeDefault(EncodeDefault.Mode.NEVER) val downloads: Map<String, String> = emptyMap(),
    @EncodeDefault(EncodeDefault.Mode.NEVER) val playback: Map<String, String> = emptyMap()
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class BackupData(
    val metadata: BackupMetadata,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val videos: List<BackupVideo> = emptyList(),
    @EncodeDefault(EncodeDefault.Mode.NEVER) val downloads: List<BackupDownload> = emptyList(),
    @EncodeDefault(EncodeDefault.Mode.NEVER) val history: List<BackupHistory> = emptyList(),
    @EncodeDefault(EncodeDefault.Mode.NEVER) val folders: List<BackupFolder> = emptyList(),
    @EncodeDefault(EncodeDefault.Mode.NEVER) val videoFolderJoins: List<BackupVideoFolderJoin> = emptyList(),
    @EncodeDefault(EncodeDefault.Mode.NEVER) val playlists: List<BackupPlaylist> = emptyList(),
    @EncodeDefault(EncodeDefault.Mode.NEVER) val subscriptions: List<BackupSubscription> = emptyList(),
    @EncodeDefault(EncodeDefault.Mode.NEVER) val preferences: BackupPreferences = BackupPreferences()
)
