package com.example.medianest.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.medianest.data.local.dao.DownloadDao
import com.example.medianest.data.local.dao.FolderDao
import com.example.medianest.data.local.dao.HistoryDao
import com.example.medianest.data.local.dao.LinkHistoryDao
import com.example.medianest.data.local.dao.SubscriptionDao
import com.example.medianest.data.local.dao.VideoDao
import com.example.medianest.data.local.dao.VideoFolderDao
import com.example.medianest.data.local.entity.DownloadStatus
import com.example.medianest.data.local.entity.MostViewedVideo
import com.example.medianest.data.local.entity.VideoEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TopVideoStat(
    val id: String,
    val title: String,
    val channelName: String,
    val thumbnailUrl: String?,
    val watchCount: Int,
    val durationSeconds: Long,
    val totalWatchTimeMillis: Long,
    val mediaType: String = "VIDEO"
)

data class StatisticsUiState(
    // 1. Overall Engagement & Library Overview
    val totalTracked: Int = 0,
    val audioTrackCount: Int = 0,
    val videoTrackCount: Int = 0,
    val videoRatioPct: Int = 0,
    val audioRatioPct: Int = 0,
    val watchedVideos: Int = 0,
    val completionPct: Int = 0,
    val totalPlayCount: Int = 0,
    val favorites: Int = 0,
    val subCount: Int = 0,
    val autoDownloadCount: Int = 0,
    val totalFolders: Int = 0,

    // 2. Engagement & Watch Metrics
    val totalWatchTimeMillis: Long = 0L,
    val sessionsThisWeek: Int = 0,
    val weekWatchTimeMillis: Long = 0L,
    val avgSessionMillis: Long = 0L,
    val longestSessionMillis: Long = 0L,

    // 3. Storage Details
    val totalDownloadsCount: Int = 0,
    val totalDownloadBytes: Long = 0L,
    val completedBytes: Long = 0L,
    val videoBytes: Long = 0L,
    val audioBytes: Long = 0L,
    val avgFileSizeBytes: Long = 0L,
    val audioExtractionCount: Int = 0,

    // 4. Download Health
    val downloadSuccessRate: Int = 100,
    val completedDownloads: Int = 0,
    val activeDownloads: Int = 0,
    val failedDownloads: Int = 0,
    val canceledDownloads: Int = 0,

    // 5. Link Extraction Stats
    val totalExtractedLinks: Int = 0,
    val videoLinksCount: Int = 0,
    val playlistLinksCount: Int = 0,
    val channelLinksCount: Int = 0,
    val unknownLinksCount: Int = 0,

    // 6. Top Content & Breakdowns
    val topVideos: List<TopVideoStat> = emptyList(),
    val resolutionMap: Map<String, Int> = emptyMap(),
    val channelMap: Map<String, Int> = emptyMap(),
    val folderMap: Map<String, Int> = emptyMap(),

    // Backwards compatibility legacy fields
    val totalDownloadedFiles: Int = 0,
    val totalDatabaseEntries: Int = 0,
    val totalWatchedVideos: Int = 0,
    val mostViewedVideo: MostViewedVideo? = null,

    val isLoading: Boolean = true
)

@HiltViewModel
class StatisticsViewModel @Inject constructor(
    private val videoDao: VideoDao,
    private val downloadDao: DownloadDao,
    private val historyDao: HistoryDao,
    private val linkHistoryDao: LinkHistoryDao,
    private val folderDao: FolderDao,
    private val subscriptionDao: SubscriptionDao,
    private val videoFolderDao: VideoFolderDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(StatisticsUiState())
    val uiState: StateFlow<StatisticsUiState> = _uiState.asStateFlow()

    init {
        loadStatistics()
    }

    fun refresh() {
        loadStatistics()
    }

    private fun loadStatistics() {
        viewModelScope.launch {
            try {
                val videos = videoDao.getAllVideosList()
                val downloads = downloadDao.getAllDownloadsOnce()
                val history = historyDao.getAllHistoryOnce()
                val watchSessions = historyDao.getAllWatchSessions()
                val folders = folderDao.getAllFoldersOnce()
                val subscriptions = subscriptionDao.getAllSubscriptionsOnce()
                val linkHistory = linkHistoryDao.getAllLinkHistoryOnce()
                val joins = videoFolderDao.getAllJoins()

                // 1. Library & Ratios
                val totalTracked = videos.size
                val audioTrackCount = videos.count {
                    it.mediaType.equals("AUDIO", ignoreCase = true) ||
                    it.localFilePath.endsWith(".m4a", ignoreCase = true) ||
                    it.localFilePath.endsWith(".mp3", ignoreCase = true) ||
                    it.localFilePath.endsWith(".aac", ignoreCase = true) ||
                    it.localFilePath.endsWith(".opus", ignoreCase = true)
                }
                val videoTrackCount = (totalTracked - audioTrackCount).coerceAtLeast(0)
                val videoRatioPct = if (totalTracked > 0) ((videoTrackCount * 100) / totalTracked) else 0
                val audioRatioPct = if (totalTracked > 0) ((audioTrackCount * 100) / totalTracked) else 0

                val watchedVideos = videos.count { it.watchCount > 0 }
                val completionPct = if (totalTracked > 0) ((watchedVideos * 100) / totalTracked) else 0

                val totalPlayCount = videos.sumOf { it.watchCount }
                val favorites = videos.count { it.favorite }
                val subCount = subscriptions.size
                val autoDownloadCount = subscriptions.count { it.autoDownload }
                val totalFolders = folders.size

                // 2. Engagement & Watch Metrics
                val totalWatchTimeFromHistory = history.sumOf { it.totalWatchTimeMillis }
                val totalWatchTimeMillis = if (totalWatchTimeFromHistory > 0) {
                    totalWatchTimeFromHistory
                } else {
                    videos.sumOf { (it.watchCount * it.durationSeconds * 1000 * 0.7).toLong() }
                }

                val longestSessionMillis = history.maxOfOrNull { it.totalWatchTimeMillis } ?: 0L
                val avgSessionMillis = if (history.isNotEmpty()) (totalWatchTimeMillis / history.size) else 0L

                val now = System.currentTimeMillis()
                val weekCutoff = now - 7 * 24 * 60 * 60 * 1000L
                val sessionsThisWeek = if (watchSessions.isNotEmpty()) {
                    watchSessions.count { it.watchedAt >= weekCutoff }
                } else {
                    history.count { it.playedAt >= weekCutoff }
                }
                val weekWatchTimeMillis = history.filter { it.playedAt >= weekCutoff }.sumOf { it.totalWatchTimeMillis }

                // 3. Downloads & Storage Details
                val totalDownloadsCount = downloads.size
                val completedDownloads = downloads.count { it.status == DownloadStatus.COMPLETED }
                val activeDownloads = downloads.count {
                    it.status == DownloadStatus.DOWNLOADING ||
                    it.status == DownloadStatus.QUEUED ||
                    it.status == DownloadStatus.PAUSED
                }
                val failedDownloads = downloads.count { it.status == DownloadStatus.FAILED }
                val canceledDownloads = downloads.count { it.status == DownloadStatus.CANCELED }

                val totalFinished = completedDownloads + failedDownloads + canceledDownloads
                val downloadSuccessRate = if (totalFinished > 0) {
                    ((completedDownloads * 100) / totalFinished).coerceIn(0, 100)
                } else if (totalDownloadsCount > 0) {
                    ((completedDownloads * 100) / totalDownloadsCount).coerceIn(0, 100)
                } else {
                    100
                }

                val totalDownloadBytes = downloads.sumOf { it.fileSizeBytes }
                val completedBytes = downloads.filter { it.status == DownloadStatus.COMPLETED }.sumOf { it.fileSizeBytes }

                val audioDownloads = downloads.filter {
                    it.format == "audio" ||
                    it.format == "audio_extracted" ||
                    it.quality.equals("audio", ignoreCase = true) ||
                    it.filePath.endsWith(".m4a", ignoreCase = true) ||
                    it.filePath.endsWith(".mp3", ignoreCase = true) ||
                    it.filePath.endsWith(".opus", ignoreCase = true) ||
                    it.filePath.endsWith(".aac", ignoreCase = true) ||
                    (it.filePath.endsWith(".webm", ignoreCase = true) && (it.format == "audio" || it.quality.equals("audio", ignoreCase = true)))
                }
                val audioBytes = audioDownloads.sumOf { it.fileSizeBytes }
                val videoBytes = (totalDownloadBytes - audioBytes).coerceAtLeast(0L)
                val avgFileSizeBytes = if (totalDownloadsCount > 0) (totalDownloadBytes / totalDownloadsCount) else 0L

                val audioExtractionCount = downloads.count { it.format == "audio_extracted" } +
                    videos.count { it.mediaType.equals("AUDIO", ignoreCase = true) && it.localFilePath.isNotEmpty() }

                // 4. Link Extraction Stats
                var videoLinksCount = 0
                var playlistLinksCount = 0
                var channelLinksCount = 0
                var unknownLinksCount = 0

                linkHistory.forEach { item ->
                    val type = item.linkType.uppercase()
                    val url = item.url.lowercase()
                    when {
                        type == "PLAYLIST" || url.contains("list=") -> playlistLinksCount++
                        type == "CHANNEL" || url.contains("/@") || url.contains("/channel/") || url.contains("/c/") -> channelLinksCount++
                        type == "VIDEO" || url.contains("watch?v=") || url.contains("youtu.be/") || url.contains("/shorts/") -> videoLinksCount++
                        else -> unknownLinksCount++
                    }
                }

                // 5. Top Content
                val historyMap = history.associateBy { it.videoId }
                val topVideos = videos
                    .filter { it.watchCount > 0 || (historyMap[it.id]?.totalWatchTimeMillis ?: 0L) > 0L }
                    .sortedWith(compareByDescending<VideoEntity> { it.watchCount }
                        .thenByDescending { historyMap[it.id]?.totalWatchTimeMillis ?: 0L }
                        .thenByDescending { it.durationSeconds })
                    .map { video ->
                        val timeSpent = historyMap[video.id]?.totalWatchTimeMillis
                            ?: (video.watchCount * video.durationSeconds * 1000L)
                        TopVideoStat(
                            id = video.id,
                            title = video.title,
                            channelName = video.channelName,
                            thumbnailUrl = video.thumbnailUrl,
                            watchCount = video.watchCount,
                            durationSeconds = video.durationSeconds,
                            totalWatchTimeMillis = timeSpent,
                            mediaType = video.mediaType
                        )
                    }

                val mostViewed = historyDao.getMostViewedVideo() ?: topVideos.firstOrNull()?.let {
                    MostViewedVideo(it.id, it.title, it.totalWatchTimeMillis)
                }

                // 6. Content Breakdowns
                val resMap = mutableMapOf<String, Int>()
                if (downloads.isNotEmpty()) {
                    downloads.forEach { d ->
                        val q = when {
                            d.format == "audio" || d.format == "audio_extracted" || d.quality.equals("audio", ignoreCase = true) -> "Audio"
                            d.quality.contains("1080", ignoreCase = true) -> "1080p Full HD"
                            d.quality.contains("720", ignoreCase = true) -> "720p HD"
                            d.quality.contains("480", ignoreCase = true) -> "480p SD"
                            d.quality.contains("360", ignoreCase = true) -> "360p SD"
                            d.quality.isNotBlank() -> d.quality
                            else -> "Standard Quality"
                        }
                        resMap[q] = (resMap[q] ?: 0) + 1
                    }
                } else if (totalTracked > 0) {
                    if (videoTrackCount > 0) resMap["Video Streams"] = videoTrackCount
                    if (audioTrackCount > 0) resMap["Audio Streams"] = audioTrackCount
                }

                val channelMap = mutableMapOf<String, Int>()
                videos.forEach { v ->
                    val ch = if (v.channelName.isNotBlank()) v.channelName else "Unknown Channel"
                    channelMap[ch] = (channelMap[ch] ?: 0) + 1
                }

                val folderCounts = mutableMapOf<Long, Int>()
                joins.forEach { join ->
                    folderCounts[join.folderId] = (folderCounts[join.folderId] ?: 0) + 1
                }
                val folderMap = mutableMapOf<String, Int>()
                folders.forEach { f ->
                    folderMap[f.name] = folderCounts[f.id] ?: 0
                }

                _uiState.value = StatisticsUiState(
                    totalTracked = totalTracked,
                    audioTrackCount = audioTrackCount,
                    videoTrackCount = videoTrackCount,
                    videoRatioPct = videoRatioPct,
                    audioRatioPct = audioRatioPct,
                    watchedVideos = watchedVideos,
                    completionPct = completionPct,
                    totalPlayCount = totalPlayCount,
                    favorites = favorites,
                    subCount = subCount,
                    autoDownloadCount = autoDownloadCount,
                    totalFolders = totalFolders,
                    totalWatchTimeMillis = totalWatchTimeMillis,
                    sessionsThisWeek = sessionsThisWeek,
                    weekWatchTimeMillis = weekWatchTimeMillis,
                    avgSessionMillis = avgSessionMillis,
                    longestSessionMillis = longestSessionMillis,
                    totalDownloadsCount = totalDownloadsCount,
                    totalDownloadBytes = totalDownloadBytes,
                    completedBytes = completedBytes,
                    videoBytes = videoBytes,
                    audioBytes = audioBytes,
                    avgFileSizeBytes = avgFileSizeBytes,
                    audioExtractionCount = audioExtractionCount,
                    downloadSuccessRate = downloadSuccessRate,
                    completedDownloads = completedDownloads,
                    activeDownloads = activeDownloads,
                    failedDownloads = failedDownloads,
                    canceledDownloads = canceledDownloads,
                    totalExtractedLinks = linkHistory.size,
                    videoLinksCount = videoLinksCount,
                    playlistLinksCount = playlistLinksCount,
                    channelLinksCount = channelLinksCount,
                    unknownLinksCount = unknownLinksCount,
                    topVideos = topVideos,
                    resolutionMap = resMap,
                    channelMap = channelMap,
                    folderMap = folderMap,
                    totalDownloadedFiles = completedDownloads,
                    totalDatabaseEntries = totalTracked,
                    totalWatchedVideos = watchedVideos,
                    mostViewedVideo = mostViewed,
                    isLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }
}
