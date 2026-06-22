package com.example.medianest.data.sync

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
import com.example.medianest.data.preferences.DevicePreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

data class SyncLogEntry(
    val timestamp: Long,
    val type: String,
    val table: String? = null,
    val rowCount: Int = 0,
    val summary: String
) {
    val formattedTime: String
        get() = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(timestamp))
}

sealed class SyncState {
    data object Idle : SyncState()
    data object Syncing : SyncState()
    data class Success(val message: String) : SyncState()
    data class Error(val message: String) : SyncState()
}

@Singleton
class SyncManager @Inject constructor(
    private val syncRepository: SyncRepository,
    private val devicePreferences: DevicePreferences,
    private val videoDao: VideoDao,
    private val downloadDao: DownloadDao,
    private val historyDao: HistoryDao,
    private val folderDao: FolderDao,
    private val videoFolderDao: VideoFolderDao,
    private val playlistDao: PlaylistDao,
    private val subscriptionDao: SubscriptionDao
) {
    private val _state = MutableStateFlow<SyncState>(SyncState.Idle)
    val state: StateFlow<SyncState> = _state

    suspend fun sync(): SyncState {
        if (_state.value is SyncState.Syncing) return _state.value
        _state.value = SyncState.Syncing
        return try {
            val state = withContext(Dispatchers.IO) {
                val serverUrl = devicePreferences.serverUrl.first()
                val apiKey = devicePreferences.apiKey.first()
                val deviceId = devicePreferences.deviceId.first()

                if (serverUrl.isBlank() || apiKey.isBlank() || deviceId.isBlank()) {
                    val err = SyncState.Error("Sync not configured. Set server URL and API key in Settings.")
                    _state.value = err
                    return@withContext err
                }

                val localChanges = collectLocalChanges()
                if (localChanges.isNotEmpty()) {
                    addLogEntry(SyncLogEntry(System.currentTimeMillis(), "push", null,
                        localChanges.size, "Pushing ${localChanges.size} local changes"))
                    val pushResult = syncRepository.pushChanges(serverUrl, apiKey, deviceId, localChanges)
                    if (pushResult.isFailure) {
                        val err = pushResult.exceptionOrNull()?.message ?: "Unknown error"
                        addLogEntry(SyncLogEntry(System.currentTimeMillis(), "error", null, 0, "Push failed: $err"))
                        val syncErr = SyncState.Error("Push failed: $err")
                        _state.value = syncErr
                        return@withContext syncErr
                    }
                    val accepted = pushResult.getOrThrow().accepted
                    addLogEntry(SyncLogEntry(System.currentTimeMillis(), "push", null,
                        accepted, "Accepted $accepted/$accepted changes"))
                } else {
                    addLogEntry(SyncLogEntry(System.currentTimeMillis(), "push", null, 0, "No local changes to push"))
                }

                val lastVersion = devicePreferences.lastSyncVersion.first()
                addLogEntry(SyncLogEntry(System.currentTimeMillis(), "pull", null, 0,
                    "Fetching changes since version $lastVersion"))
                val pullResult = syncRepository.pullChanges(serverUrl, apiKey, deviceId, lastVersion)
                if (pullResult.isFailure) {
                    val err = pullResult.exceptionOrNull()?.message ?: "Unknown error"
                    addLogEntry(SyncLogEntry(System.currentTimeMillis(), "error", null, 0, "Pull failed: $err"))
                    val syncErr = SyncState.Error("Pull failed: $err")
                    _state.value = syncErr
                    return@withContext syncErr
                }

                val pull = pullResult.getOrThrow()
                if (pull.changes.isNotEmpty()) {
                    val perTable = mutableMapOf<String, Int>()
                    pull.changes.forEach { c ->
                        val t = (c["table_name"] as? JsonPrimitive)?.content ?: "unknown"
                        perTable[t] = (perTable[t] ?: 0) + 1
                    }
                    perTable.forEach { (t, c) ->
                        addLogEntry(SyncLogEntry(System.currentTimeMillis(), "apply", t, c,
                            "Applied $c $t ${
                                if (c == 1) "change" else "changes"
                            }"))
                    }
                }
                applyRemoteChanges(pull.changes)
                devicePreferences.setLastSyncVersion(pull.version)
                devicePreferences.setLastSyncAt(System.currentTimeMillis())

                val msg = if (pull.changes.isEmpty()) "Synced (no new changes)" else "Synced ${pull.changes.size} changes"
                addLogEntry(SyncLogEntry(System.currentTimeMillis(), "pull", null,
                    pull.changes.size, msg))
                val syncSuccess = SyncState.Success(msg)
                _state.value = syncSuccess
                syncSuccess
            }
            state
        } catch (e: Exception) {
            val syncErr = SyncState.Error(e.message ?: "Sync failed")
            _state.value = syncErr
            syncErr
        }
    }

    fun resetState() { _state.value = SyncState.Idle }

    private val _log = MutableStateFlow<List<SyncLogEntry>>(emptyList())
    val log: StateFlow<List<SyncLogEntry>> = _log
    private val maxLogEntries = 100

    fun clearLog() { _log.value = emptyList() }

    private fun addLogEntry(entry: SyncLogEntry) {
        val current = _log.value.toMutableList()
        current.add(0, entry)
        if (current.size > maxLogEntries) current.removeAt(current.size - 1)
        _log.value = current
    }

    private suspend fun collectLocalChanges(): List<SyncPushItem> {
        val since = devicePreferences.lastSyncAt.first()
        val changes = mutableListOf<SyncPushItem>()
        val useIncremental = since > 0

        videoDao.getAllVideos().first().forEach { v ->
            if (!useIncremental || v.addedAt > since) {
                changes.add(SyncPushItem("videos", v.id, "upsert", mapOf(
                "id" to JsonPrimitive(v.id), "title" to JsonPrimitive(v.title),
                "channelName" to JsonPrimitive(v.channelName),
                "channelId" to JsonPrimitive(v.channelId ?: ""),
                "durationSeconds" to JsonPrimitive(v.durationSeconds),
                "thumbnailUrl" to JsonPrimitive(v.thumbnailUrl ?: ""),
                "description" to JsonPrimitive(v.description ?: ""),
                "uploadDate" to JsonPrimitive(v.uploadDate ?: ""),
                "localFilePath" to JsonPrimitive(""), // Clear absolute path on push
                "favorite" to JsonPrimitive(v.favorite),
                "addedAt" to JsonPrimitive(v.addedAt),
                "updatedAt" to JsonPrimitive(v.addedAt),
                "createdAt" to JsonPrimitive(v.addedAt),
                "syncVersion" to JsonPrimitive(v.syncVersion)
            )))
            }
        }
        downloadDao.getAllDownloadsOnce().forEach { d ->
            if (!useIncremental || d.updatedAt > since) {
                changes.add(SyncPushItem("downloads", d.id.toString(), "upsert", mapOf(
                "id" to JsonPrimitive(d.id), "videoId" to JsonPrimitive(d.videoId),
                "url" to JsonPrimitive(d.url), "videoUrl" to JsonPrimitive(d.videoUrl ?: ""), "format" to JsonPrimitive(d.format),
                "quality" to JsonPrimitive(d.quality), "title" to JsonPrimitive(d.title),
                "thumbnailUrl" to JsonPrimitive(d.thumbnailUrl ?: ""),
                "filePath" to JsonPrimitive(""), // Clear absolute path on push
                "fileSizeBytes" to JsonPrimitive(d.fileSizeBytes),
                "downloadedAt" to JsonPrimitive(d.downloadedAt),
                "lastPlayedAt" to JsonPrimitive(d.lastPlayedAt ?: 0L),
                "status" to JsonPrimitive(d.status.name),
                "progress" to JsonPrimitive(d.progress),
                "errorMessage" to JsonPrimitive(d.errorMessage ?: ""),
                "retryCount" to JsonPrimitive(d.retryCount),
                "updatedAt" to JsonPrimitive(d.updatedAt),
                "createdAt" to JsonPrimitive(d.downloadedAt), // Missing fields needed by other tables? 
                "syncVersion" to JsonPrimitive(d.syncVersion)
            )))
            }
        }
        historyDao.getAllHistoryOnce().forEach { h ->
            if (!useIncremental || h.playedAt > since) {
                changes.add(SyncPushItem("playback_history", h.videoId, "upsert", mapOf(
                "videoId" to JsonPrimitive(h.videoId),
                "positionMillis" to JsonPrimitive(h.positionMillis),
                "playedAt" to JsonPrimitive(h.playedAt),
                "createdAt" to JsonPrimitive(h.playedAt),
                "updatedAt" to JsonPrimitive(h.playedAt),
                "syncVersion" to JsonPrimitive(h.syncVersion)
            )))
            }
        }
        folderDao.getAllFolders().first().forEach { f ->
            if (!useIncremental || f.updatedAt > since) {
                changes.add(SyncPushItem("folders", f.id.toString(), "upsert", mapOf(
                "id" to JsonPrimitive(f.id), "name" to JsonPrimitive(f.name),
                "parentId" to JsonPrimitive(f.parentId ?: -1L),
                "createdAt" to JsonPrimitive(f.createdAt),
                "updatedAt" to JsonPrimitive(f.updatedAt),
                "syncVersion" to JsonPrimitive(f.syncVersion)
            )))
            }
        }
        videoFolderDao.getAllJoins().forEach { j ->
            if (!useIncremental || j.addedAt > since) {
                changes.add(SyncPushItem("video_folder_join", "${j.videoId}_${j.folderId}", "upsert", mapOf(
                "videoId" to JsonPrimitive(j.videoId),
                "folderId" to JsonPrimitive(j.folderId),
                "addedAt" to JsonPrimitive(j.addedAt),
                "createdAt" to JsonPrimitive(j.addedAt),
                "updatedAt" to JsonPrimitive(j.addedAt),
                "syncVersion" to JsonPrimitive(j.syncVersion)
            )))
            }
        }
        playlistDao.getAllPlaylistsOnce().forEach { p ->
            if (!useIncremental || p.updatedAt > since) {
                changes.add(SyncPushItem("playlists", p.id.toString(), "upsert", mapOf(
                "id" to JsonPrimitive(p.id), "name" to JsonPrimitive(p.name),
                "description" to JsonPrimitive(p.description ?: ""),
                "thumbnailUrl" to JsonPrimitive(p.thumbnailUrl ?: ""),
                "youtubePlaylistId" to JsonPrimitive(p.youtubePlaylistId),
                "uploaderName" to JsonPrimitive(p.uploaderName ?: ""),
                "videoCount" to JsonPrimitive(p.videoCount),
                "createdAt" to JsonPrimitive(p.createdAt),
                "updatedAt" to JsonPrimitive(p.updatedAt),
                "syncVersion" to JsonPrimitive(p.syncVersion)
            )))
            }
        }
        subscriptionDao.getAllSubscriptionsOnce().forEach { s ->
            if (!useIncremental || s.updatedAt > since) {
                changes.add(SyncPushItem("subscriptions", s.sourceId, "upsert", mapOf(
                "id" to JsonPrimitive(s.id), "sourceId" to JsonPrimitive(s.sourceId),
                "sourceType" to JsonPrimitive(s.sourceType),
                "name" to JsonPrimitive(s.name),
                "thumbnailUrl" to JsonPrimitive(s.thumbnailUrl ?: ""),
                "uploaderName" to JsonPrimitive(s.uploaderName ?: ""),
                "autoDownload" to JsonPrimitive(s.autoDownload),
                "audioOnly" to JsonPrimitive(s.audioOnly),
                "lastCheckedAt" to JsonPrimitive(s.lastCheckedAt),
                "createdAt" to JsonPrimitive(s.createdAt),
                "updatedAt" to JsonPrimitive(s.updatedAt),
                "syncVersion" to JsonPrimitive(s.syncVersion)
            )))
            }
        }
        return changes
    }

    private suspend fun applyRemoteChanges(changes: List<Map<String, JsonElement?>>) {
        for (change in changes) {
            try {
                val table = (change["table_name"] as? JsonPrimitive)?.content ?: continue
                val operation = (change["operation"] as? JsonPrimitive)?.content ?: continue
                val payloadEl = change["payload"] as? JsonElement ?: continue
                when (operation) {
                    "upsert" -> applyUpsert(table, payloadEl)
                    "delete" -> applyDelete(table, payloadEl)
                }
            } catch (e: Exception) {
                // Prevent a single DB constraint error from permanently blocking the sync loop
                addLogEntry(SyncLogEntry(System.currentTimeMillis(), "error", null, 0, "Failed to apply change: ${e.message}"))
            }
        }
    }

    private fun jsonString(el: JsonElement?, fallback: String = ""): String =
        (el as? JsonPrimitive)?.content ?: fallback

    private fun jsonLong(el: JsonElement?, fallback: Long = 0L): Long =
        (el as? JsonPrimitive)?.longOrNull ?: fallback

    private fun jsonInt(el: JsonElement?, fallback: Int = 0): Int =
        (el as? JsonPrimitive)?.let { it.content.toIntOrNull() } ?: fallback

    private fun jsonBoolean(el: JsonElement?, fallback: Boolean = false): Boolean =
        (el as? JsonPrimitive)?.let { it.content.toBooleanStrictOrNull() } ?: fallback

    private suspend fun applyUpsert(table: String, payload: JsonElement) {
        val obj = payload as? JsonObject ?: return
        when (table) {
            "videos" -> {
                val id = jsonString(obj["id"])
                if (id.isBlank()) return
                val existing = videoDao.getVideoById(id)
                val favorite = jsonBoolean(obj["favorite"])
                val title = jsonString(obj["title"])
                val channelName = jsonString(obj["channelName"])
                val channelId = jsonString(obj["channelId"]).ifBlank { null }
                val durationSeconds = jsonLong(obj["durationSeconds"])
                val thumbnailUrl = jsonString(obj["thumbnailUrl"]).ifBlank { null }
                val description = jsonString(obj["description"]).ifBlank { null }
                val uploadDate = jsonString(obj["uploadDate"]).ifBlank { null }
                val syncVersion = jsonLong(obj["syncVersion"])
                
                if (existing == null) {
                    videoDao.insert(VideoEntity(
                        id = id,
                        title = title,
                        channelName = channelName,
                        channelId = channelId,
                        durationSeconds = durationSeconds,
                        thumbnailUrl = thumbnailUrl,
                        description = description,
                        uploadDate = uploadDate,
                        localFilePath = "", // Clear absolute path on pull
                        favorite = favorite,
                        addedAt = jsonLong(obj["addedAt"], System.currentTimeMillis()),
                        syncVersion = syncVersion
                    ))
                } else {
                    videoDao.update(existing.copy(
                        title = title,
                        channelName = channelName,
                        channelId = channelId,
                        durationSeconds = durationSeconds,
                        thumbnailUrl = thumbnailUrl,
                        description = description,
                        uploadDate = uploadDate,
                        favorite = favorite,
                        syncVersion = syncVersion
                    ))
                }
            }
            "folders" -> {
                val id = obj["id"]?.let { it.jsonPrimitive.longOrNull } ?: return
                val parentId = obj["parentId"]?.let { it.jsonPrimitive.longOrNull }
                folderDao.insert(FolderEntity(
                    id = id, name = jsonString(obj["name"]),
                    parentId = if (parentId == -1L) null else parentId,
                    createdAt = jsonLong(obj["createdAt"], System.currentTimeMillis()),
                    updatedAt = jsonLong(obj["updatedAt"], System.currentTimeMillis()),
                    syncVersion = jsonLong(obj["syncVersion"])
                ))
            }
            "video_folder_join" -> {
                val videoId = jsonString(obj["videoId"])
                val folderId = obj["folderId"]?.let { it.jsonPrimitive.longOrNull } ?: return
                videoFolderDao.addVideoToFolder(VideoFolderJoin(
                    videoId = videoId, folderId = folderId,
                    addedAt = jsonLong(obj["addedAt"], System.currentTimeMillis()),
                    syncVersion = jsonLong(obj["syncVersion"])
                ))
            }
            "playlists" -> {
                val id = obj["id"]?.let { it.jsonPrimitive.longOrNull } ?: return
                playlistDao.insert(PlaylistEntity(
                    id = id,
                    name = jsonString(obj["name"]),
                    description = jsonString(obj["description"]).ifBlank { null },
                    thumbnailUrl = jsonString(obj["thumbnailUrl"]).ifBlank { null },
                    youtubePlaylistId = jsonString(obj["youtubePlaylistId"]),
                    uploaderName = jsonString(obj["uploaderName"]).ifBlank { null },
                    videoCount = jsonInt(obj["videoCount"]),
                    createdAt = jsonLong(obj["createdAt"], System.currentTimeMillis()),
                    updatedAt = jsonLong(obj["updatedAt"], System.currentTimeMillis()),
                    syncVersion = jsonLong(obj["syncVersion"])
                ))
            }
            "subscriptions" -> {
                subscriptionDao.insert(SubscriptionEntity(
                    sourceType = jsonString(obj["sourceType"], "CHANNEL"),
                    sourceId = jsonString(obj["sourceId"]).takeIf { it.isNotBlank() } ?: return,
                    name = jsonString(obj["name"]),
                    thumbnailUrl = jsonString(obj["thumbnailUrl"]).ifBlank { null },
                    uploaderName = jsonString(obj["uploaderName"]).ifBlank { null },
                    autoDownload = jsonBoolean(obj["autoDownload"]),
                    audioOnly = jsonBoolean(obj["audioOnly"]),
                    lastCheckedAt = jsonLong(obj["lastCheckedAt"]),
                    createdAt = jsonLong(obj["createdAt"], System.currentTimeMillis()),
                    updatedAt = jsonLong(obj["updatedAt"], System.currentTimeMillis()),
                    syncVersion = jsonLong(obj["syncVersion"])
                ))
            }
            "downloads" -> {
                val downloadId = jsonLong(obj["id"], 0L)
                val videoId = jsonString(obj["videoId"])
                if (videoId.isBlank()) return
                val existing = downloadDao.getDownloadById(downloadId)
                    ?: downloadDao.getDownload(videoId, jsonString(obj["format"]), jsonString(obj["quality"]))
                val entity = DownloadEntity(
                    id = existing?.id ?: downloadId,
                    videoId = videoId,
                    url = jsonString(obj["url"]),
                    videoUrl = jsonString(obj["videoUrl"]).ifBlank { null },
                    format = jsonString(obj["format"]),
                    quality = jsonString(obj["quality"]),
                    title = jsonString(obj["title"]),
                    thumbnailUrl = jsonString(obj["thumbnailUrl"]).ifBlank { null },
                    filePath = existing?.filePath ?: "", // Clear/ignore absolute path on pull (preserve if exists locally)
                    fileSizeBytes = jsonLong(obj["fileSizeBytes"]),
                    downloadedAt = jsonLong(obj["downloadedAt"], System.currentTimeMillis()),
                    lastPlayedAt = jsonLong(obj["lastPlayedAt"]).let { if (it == 0L) null else it },
                    status = try { DownloadStatus.valueOf(jsonString(obj["status"], "QUEUED")) } catch (_: Exception) { DownloadStatus.QUEUED },
                    progress = (obj["progress"] as? JsonPrimitive)?.let { it.content.toFloatOrNull() } ?: 0f,
                    errorMessage = jsonString(obj["errorMessage"]).ifBlank { null },
                    retryCount = jsonInt(obj["retryCount"]),
                    updatedAt = jsonLong(obj["updatedAt"], System.currentTimeMillis()),
                    syncVersion = jsonLong(obj["syncVersion"])
                )
                if (existing == null) {
                    downloadDao.insert(entity)
                } else {
                    downloadDao.update(entity)
                }
            }
            "playback_history" -> {
                val hVideoId = jsonString(obj["videoId"])
                if (hVideoId.isBlank()) return
                historyDao.upsert(HistoryEntity(
                    videoId = hVideoId,
                    positionMillis = jsonLong(obj["positionMillis"]),
                    playedAt = jsonLong(obj["playedAt"], System.currentTimeMillis()),
                    syncVersion = jsonLong(obj["syncVersion"])
                ))
            }
        }
    }

    private suspend fun applyDelete(table: String, payload: JsonElement) {
        val obj = payload as? JsonObject ?: return
        when (table) {
            "videos" -> videoDao.deleteById(jsonString(obj["rowId"]))
            "folders" -> {
                val id = obj["rowId"]?.let { it.jsonPrimitive.longOrNull } ?: return
                folderDao.deleteById(id)
            }
            "subscriptions" -> subscriptionDao.deleteBySourceId(jsonString(obj["rowId"]))
        }
    }
}
