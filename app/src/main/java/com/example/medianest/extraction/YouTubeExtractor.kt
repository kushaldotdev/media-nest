package com.example.medianest.extraction

import com.example.medianest.data.model.ChannelInfo as ModelChannelInfo
import com.example.medianest.data.model.ExtractedPlaylistInfo
import com.example.medianest.data.model.ExtractedVideoInfo
import com.example.medianest.data.model.StreamSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.StreamingService
import org.schabi.newpipe.extractor.channel.ChannelInfo as NewPipeChannelInfo
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabInfo
import org.schabi.newpipe.extractor.playlist.PlaylistInfo as NewPipePlaylistInfo
import org.schabi.newpipe.extractor.stream.StreamInfo as NewPipeStreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class YouTubeExtractor @Inject constructor() {

    companion object {
        private const val SERVICE_ID = 0
    }

    private val service: StreamingService by lazy {
        NewPipe.getService(SERVICE_ID)
    }

    fun extractChannelIdFromUrl(url: String?): String? {
        if (url.isNullOrBlank()) return null
        return runCatching {
            service.getChannelLHFactory().fromUrl(url).id
        }.getOrNull() ?: url
    }

    private fun extractVideoIdFromUrl(url: String?): String {
        if (url == null) return ""
        return runCatching {
            android.net.Uri.parse(url).getQueryParameter("v")
                ?: url.substringAfterLast("/")
                    .substringBefore("?")
                    .substringBefore("&")
        }.getOrDefault(url)
    }

    private fun StreamInfoItem.toExtractedVideoInfo(channelNameOverride: String? = null, channelIdOverride: String? = null): ExtractedVideoInfo {
        return ExtractedVideoInfo(
            videoId = extractVideoIdFromUrl(url),
            title = name ?: "Unknown",
            channelName = channelNameOverride ?: uploaderName ?: "Unknown",
            channelId = channelIdOverride ?: extractChannelIdFromUrl(uploaderUrl) ?: "",
            durationSeconds = duration,
            thumbnailUrl = thumbnails?.firstOrNull()?.url ?: "",
            description = null,
            uploadDate = textualUploadDate,
            isShort = url.contains("/shorts/") || streamType.name.contains("SHORT") || duration <= 180
        )
    }

    private fun addUniqueVideo(target: LinkedHashMap<String, ExtractedVideoInfo>, video: ExtractedVideoInfo): Boolean {
        if (video.videoId.isBlank() || target.containsKey(video.videoId)) return false
        target[video.videoId] = video
        return true
    }

    suspend fun extractVideo(url: String): ExtractedVideoInfo = withContext(Dispatchers.IO) {
        val info = NewPipeStreamInfo.getInfo(service, url)

        val streams = mutableListOf<StreamSource>()

        runCatching {
            val hasNonDubbed = info.audioStreams?.any { it.audioTrackType != org.schabi.newpipe.extractor.stream.AudioTrackType.DUBBED } ?: false
            info.audioStreams?.forEach { track ->
                if (hasNonDubbed && track.audioTrackType == org.schabi.newpipe.extractor.stream.AudioTrackType.DUBBED) {
                    return@forEach
                }
                val codec = track.format?.name ?: track.format?.mimeType?.substringAfter("/") ?: "audio"
                val rawBitrate = if (track.averageBitrate > 0) track.averageBitrate else track.bitrate
                val bitrateKbps = if (rawBitrate > 0) {
                    if (rawBitrate < 1000) rawBitrate else rawBitrate / 1000
                } else {
                    0
                }
                val qualityStr = if (bitrateKbps > 0) "${bitrateKbps}kbps" else "Unknown bitrate"
                streams.add(
                    StreamSource(
                        url = track.content,
                        format = "audio",
                        quality = qualityStr,
                        mimeType = track.format?.mimeType ?: "audio/mpeg",
                        codec = codec,
                        contentLength = if (track.itagItem?.contentLength ?: 0L > 0L) track.itagItem?.contentLength else null,
                        language = if (!track.audioTrackName.isNullOrBlank()) track.audioTrackName else track.audioLocale?.displayLanguage
                    )
                )
            }
        }

        runCatching {
            info.videoStreams?.forEach { stream ->
                val codec = stream.format?.name ?: stream.format?.mimeType?.substringAfter("/") ?: "mp4"
                streams.add(
                    StreamSource(
                        url = stream.content,
                        format = "video",
                        quality = stream.getResolution(),
                        mimeType = stream.format?.mimeType ?: "video/mp4",
                        codec = codec,
                        contentLength = if (stream.itagItem?.contentLength ?: 0L > 0L) stream.itagItem?.contentLength else null
                    )
                )
            }
        }

        runCatching {
            info.videoOnlyStreams?.forEach { stream ->
                val codec = stream.format?.name ?: stream.format?.mimeType?.substringAfter("/") ?: "mp4"
                streams.add(
                    StreamSource(
                        url = stream.content,
                        format = "video_only",
                        quality = stream.getResolution(),
                        mimeType = stream.format?.mimeType ?: "video/mp4",
                        codec = codec,
                        contentLength = if (stream.itagItem?.contentLength ?: 0L > 0L) stream.itagItem?.contentLength else null
                    )
                )
            }
        }

        ExtractedVideoInfo(
            videoId = extractVideoIdFromUrl(info.id).ifBlank { extractVideoIdFromUrl(url) },
            title = info.name ?: "",
            channelName = info.uploaderName ?: "Unknown",
            channelId = extractChannelIdFromUrl(info.uploaderUrl) ?: "",
            durationSeconds = info.duration,
            thumbnailUrl = info.thumbnails?.firstOrNull()?.url ?: "",
            description = info.description?.content?.take(1000),
            uploadDate = info.textualUploadDate,
            isShort = info.url?.contains("/shorts/") == true || info.duration <= 180,
            streamSources = streams
        )
    }

    data class ChannelPage(
        val videos: List<ExtractedVideoInfo>,
        val nextPage: Any?,
        val pageType: String,
        val tabLinkHandler: Any?,
        val playlistUrl: String?
    )

    data class PlaylistPage(
        val videos: List<ExtractedVideoInfo>,
        val nextPage: Any?,
        val playlistUrl: String
    )

    suspend fun extractPlaylistFirstPage(url: String): Pair<ExtractedPlaylistInfo, PlaylistPage> = withContext(Dispatchers.IO) {
        val canonicalUrl = if (!url.startsWith("http") && !url.contains("youtube.com")) {
            "https://www.youtube.com/playlist?list=$url"
        } else {
            url
        }
        val info = NewPipePlaylistInfo.getInfo(service, canonicalUrl)

        val videosById = linkedMapOf<String, ExtractedVideoInfo>()
        info.relatedItems?.forEach { item ->
            runCatching { item.toExtractedVideoInfo() }
                .getOrNull()
                ?.let { addUniqueVideo(videosById, it) }
        }

        val videos = videosById.values.toList()
        val reportedCount = info.streamCount
        val videoCount = if (reportedCount > 0 && reportedCount <= Int.MAX_VALUE) reportedCount.toInt() else videos.size

        val playlistInfo = ExtractedPlaylistInfo(
            playlistId = info.id,
            name = info.name ?: "Unknown",
            thumbnailUrl = info.thumbnails?.firstOrNull()?.url ?: "",
            uploaderName = info.uploaderName,
            videoCount = videoCount,
            videos = videos
        )
        val playlistPage = PlaylistPage(
            videos = videos,
            nextPage = info.nextPage,
            playlistUrl = canonicalUrl
        )
        Pair(playlistInfo, playlistPage)
    }

    suspend fun extractPlaylistNextPage(currentPage: PlaylistPage): PlaylistPage = withContext(Dispatchers.IO) {
        val nextPageToken = currentPage.nextPage as? org.schabi.newpipe.extractor.Page
        if (nextPageToken == null) {
            return@withContext PlaylistPage(emptyList(), null, currentPage.playlistUrl)
        }

        val page = runCatching {
            NewPipePlaylistInfo.getMoreItems(service, currentPage.playlistUrl, nextPageToken)
        }.getOrNull()

        if (page == null) {
            return@withContext PlaylistPage(emptyList(), null, currentPage.playlistUrl)
        }

        val videosById = linkedMapOf<String, ExtractedVideoInfo>()
        page.items?.forEach { item ->
            runCatching { item.toExtractedVideoInfo() }
                .getOrNull()
                ?.let { addUniqueVideo(videosById, it) }
        }

        PlaylistPage(
            videos = videosById.values.toList(),
            nextPage = if (page.hasNextPage()) page.nextPage else null,
            playlistUrl = currentPage.playlistUrl
        )
    }

    suspend fun extractPlaylist(url: String): ExtractedPlaylistInfo = withContext(Dispatchers.IO) {
        val (firstInfo, firstPage) = extractPlaylistFirstPage(url)
        val allVideos = firstInfo.videos.toMutableList()
        val videosById = linkedMapOf<String, ExtractedVideoInfo>()
        allVideos.forEach { addUniqueVideo(videosById, it) }

        var currentPage = firstPage
        while (currentPage.nextPage != null) {
            val nextPageData = extractPlaylistNextPage(currentPage)
            if (nextPageData.videos.isEmpty()) break
            var addedFromPage = 0
            nextPageData.videos.forEach { video ->
                if (addUniqueVideo(videosById, video)) {
                    addedFromPage++
                }
            }
            if (addedFromPage == 0 || nextPageData.nextPage == null) break
            currentPage = nextPageData
        }

        val videos = videosById.values.toList()
        firstInfo.copy(
            videos = videos,
            videoCount = if (firstInfo.videoCount > videos.size) firstInfo.videoCount else videos.size
        )
    }

    private fun stripChannelTab(url: String): String {
        val baseWithoutQuery = url.substringBefore("?")
        val trimmed = baseWithoutQuery.trim().removeSuffix("/")
        val lastSegment = trimmed.substringAfterLast("/")
        val knownTabs = listOf("videos", "playlists", "shorts", "streams", "community", "featured", "about", "store")
        if (lastSegment in knownTabs) {
            val stripped = trimmed.substringBeforeLast("/")
            val query = url.substringAfter("?", "")
            return if (query.isEmpty()) stripped else "$stripped?$query"
        }
        return url
    }

    private fun sanitizeChannelUrl(url: String): String {
        val baseWithoutQuery = url.substringBefore("?")
        val trimmed = baseWithoutQuery.trim().removeSuffix("/")
        val isChannel = trimmed.contains("/channel/") || trimmed.contains("/c/") || trimmed.contains("/user/") || trimmed.contains("/@")
        if (!isChannel) return url
        
        val lastSegment = trimmed.substringAfterLast("/")
        val knownTabs = listOf("videos", "playlists", "shorts", "streams", "community", "featured", "about", "store")
        if (lastSegment in knownTabs) {
            return url
        }
        
        return "$trimmed/videos"
    }

    suspend fun extractChannelFirstPage(url: String): Pair<ModelChannelInfo, ChannelPage> = withContext(Dispatchers.IO) {
        val canonicalUrl = if (!url.startsWith("http") && !url.contains("youtube.com")) {
            "https://www.youtube.com/channel/$url"
        } else {
            url
        }
        val cleanChannelUrl = stripChannelTab(canonicalUrl)
        val info = NewPipeChannelInfo.getInfo(service, cleanChannelUrl)
        val channelId = extractChannelIdFromUrl(info.url) ?: ""

        var tabLinkHandler: org.schabi.newpipe.extractor.linkhandler.ListLinkHandler? = null
        var playlistUrl: String? = null
        var pageType = "tab"
        var nextPage: Any? = null

        val uploads = runCatching {
            val sanitizedUrl = sanitizeChannelUrl(info.url ?: cleanChannelUrl)
            val handler = service.getChannelTabLHFactory().fromUrl(sanitizedUrl)
            tabLinkHandler = handler
            val tabInfo = ChannelTabInfo.getInfo(service, handler)
            val videosById = linkedMapOf<String, ExtractedVideoInfo>()
            tabInfo.relatedItems?.forEach { item ->
                if (item is StreamInfoItem) {
                    runCatching { item.toExtractedVideoInfo(info.name ?: "Unknown", channelId) }
                        .getOrNull()
                        ?.let { addUniqueVideo(videosById, it) }
                }
            }
            nextPage = tabInfo.nextPage
            pageType = "tab"
            videosById.values.toList()
        }.getOrElse { tabError ->
            runCatching {
                val uploadsPlaylistId = if (info.id.startsWith("UC")) {
                    "UU" + info.id.substring(2)
                } else {
                    info.id
                }
                val pUrl = "https://www.youtube.com/playlist?list=$uploadsPlaylistId"
                playlistUrl = pUrl
                val playlistInfo = NewPipePlaylistInfo.getInfo(service, pUrl)
                val videosById = linkedMapOf<String, ExtractedVideoInfo>()
                playlistInfo.relatedItems?.forEach { item ->
                    runCatching { item.toExtractedVideoInfo(info.name ?: "Unknown", channelId) }
                        .getOrNull()
                        ?.let { addUniqueVideo(videosById, it) }
                }
                nextPage = playlistInfo.nextPage
                pageType = "playlist"
                videosById.values.toList()
            }.getOrNull() ?: emptyList()
        }

        val channelInfo = ModelChannelInfo(
            channelId = info.id,
            url = info.url ?: cleanChannelUrl,
            name = info.name ?: "Unknown",
            avatarUrl = info.avatars?.firstOrNull()?.url ?: "",
            subscriberCount = info.subscriberCount,
            description = info.description?.take(500),
            videoCount = uploads.size,
            uploads = uploads
        )

        val channelPage = ChannelPage(
            videos = uploads,
            nextPage = nextPage,
            pageType = pageType,
            tabLinkHandler = tabLinkHandler,
            playlistUrl = playlistUrl
        )

        Pair(channelInfo, channelPage)
    }

    suspend fun extractChannelNextPage(currentPage: ChannelPage): ChannelPage = withContext(Dispatchers.IO) {
        val nextPageToken = currentPage.nextPage as? org.schabi.newpipe.extractor.Page
        if (nextPageToken == null) {
            return@withContext ChannelPage(
                videos = emptyList(),
                nextPage = null,
                pageType = currentPage.pageType,
                tabLinkHandler = currentPage.tabLinkHandler,
                playlistUrl = currentPage.playlistUrl
            )
        }

        val channelName = currentPage.videos.firstOrNull()?.channelName ?: "Unknown"
        val channelId = currentPage.videos.firstOrNull()?.channelId ?: ""

        val videosById = linkedMapOf<String, ExtractedVideoInfo>()
        var nextNextPage: Any? = null

        if (currentPage.pageType == "tab") {
            val handler = currentPage.tabLinkHandler as? org.schabi.newpipe.extractor.linkhandler.ListLinkHandler
            if (handler != null) {
                runCatching {
                    val page = ChannelTabInfo.getMoreItems(service, handler, nextPageToken)
                    page?.items?.forEach { item ->
                        if (item is StreamInfoItem) {
                            runCatching { item.toExtractedVideoInfo(channelName, channelId) }
                                .getOrNull()
                                ?.let { addUniqueVideo(videosById, it) }
                        }
                    }
                    if (page != null && page.hasNextPage()) {
                        nextNextPage = page.nextPage
                    }
                }
            }
        } else {
            val pUrl = currentPage.playlistUrl
            if (pUrl != null) {
                runCatching {
                    val page = NewPipePlaylistInfo.getMoreItems(service, pUrl, nextPageToken)
                    page?.items?.forEach { item ->
                        runCatching { item.toExtractedVideoInfo(channelName, channelId) }
                            .getOrNull()
                            ?.let { addUniqueVideo(videosById, it) }
                    }
                    if (page != null && page.hasNextPage()) {
                        nextNextPage = page.nextPage
                    }
                }
            }
        }

        ChannelPage(
            videos = videosById.values.toList(),
            nextPage = nextNextPage,
            pageType = currentPage.pageType,
            tabLinkHandler = currentPage.tabLinkHandler,
            playlistUrl = currentPage.playlistUrl
        )
    }

    suspend fun extractChannel(url: String): ModelChannelInfo = withContext(Dispatchers.IO) {
        val (channelInfo, firstPage) = extractChannelFirstPage(url)
        val allUploads = channelInfo.uploads.toMutableList()
        val videosById = linkedMapOf<String, ExtractedVideoInfo>()
        allUploads.forEach { addUniqueVideo(videosById, it) }

        var currentPage = firstPage
        while (currentPage.nextPage != null) {
            val nextPageData = extractChannelNextPage(currentPage)
            if (nextPageData.videos.isEmpty()) break
            var addedFromPage = 0
            nextPageData.videos.forEach { video ->
                if (addUniqueVideo(videosById, video)) {
                    addedFromPage++
                }
            }
            if (addedFromPage == 0 || nextPageData.nextPage == null) break
            currentPage = nextPageData
        }

        val uploads = videosById.values.toList()
        channelInfo.copy(
            uploads = uploads,
            videoCount = uploads.size
        )
    }
}
