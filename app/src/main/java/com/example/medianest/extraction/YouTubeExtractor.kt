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

    private fun extractChannelIdFromUrl(url: String?): String? {
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

    suspend fun extractVideo(url: String): ExtractedVideoInfo = withContext(Dispatchers.IO) {
        val info = NewPipeStreamInfo.getInfo(service, url)

        val streams = mutableListOf<StreamSource>()

        runCatching {
            info.audioStreams?.forEach { track ->
                val codec = track.format?.name ?: track.format?.mimeType?.substringAfter("/") ?: "audio"
                val bitrate = if (track.averageBitrate > 0) track.averageBitrate else track.bitrate
                val qualityStr = if (bitrate > 0) "${bitrate / 1000}kbps" else "Unknown bitrate"
                streams.add(
                    StreamSource(
                        url = track.content,
                        format = "audio",
                        quality = qualityStr,
                        mimeType = track.format?.mimeType ?: "audio/mpeg",
                        codec = codec,
                        contentLength = if (track.itagItem?.contentLength ?: 0L > 0L) track.itagItem?.contentLength else null
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

    suspend fun extractPlaylist(url: String): ExtractedPlaylistInfo = withContext(Dispatchers.IO) {
        val info = NewPipePlaylistInfo.getInfo(service, url)

        val videos = info.relatedItems?.mapNotNull { item ->
            runCatching {
                ExtractedVideoInfo(
                    videoId = extractVideoIdFromUrl(item.url),
                    title = item.name ?: "Unknown",
                    channelName = item.uploaderName ?: "Unknown",
                    channelId = extractChannelIdFromUrl(item.uploaderUrl) ?: "",
                    durationSeconds = item.duration,
                    thumbnailUrl = item.thumbnails?.firstOrNull()?.url ?: "",
                    description = null,
                    uploadDate = null,
                    isShort = item.url.contains("/shorts/") || item.streamType.name.contains("SHORT") || item.duration <= 180
                )
            }.getOrNull()
        } ?: emptyList()

        ExtractedPlaylistInfo(
            playlistId = info.id,
            name = info.name ?: "Unknown",
            thumbnailUrl = info.thumbnails?.firstOrNull()?.url ?: "",
            uploaderName = info.uploaderName,
            videos = videos
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

    suspend fun extractChannel(url: String): ModelChannelInfo = withContext(Dispatchers.IO) {
        val cleanChannelUrl = stripChannelTab(url)
        val info = NewPipeChannelInfo.getInfo(service, cleanChannelUrl)
        
        val uploads = runCatching {
            val sanitizedUrl = sanitizeChannelUrl(info.url ?: cleanChannelUrl)
            val tabLinkHandler = service.getChannelTabLHFactory().fromUrl(sanitizedUrl)
            val tabInfo = ChannelTabInfo.getInfo(service, tabLinkHandler)
            tabInfo.relatedItems?.mapNotNull { item ->
                if (item !is StreamInfoItem) return@mapNotNull null
                runCatching {
                    ExtractedVideoInfo(
                        videoId = extractVideoIdFromUrl(item.url),
                        title = item.name ?: "Unknown",
                        channelName = info.name ?: "Unknown",
                        channelId = extractChannelIdFromUrl(info.url) ?: "",
                        durationSeconds = item.duration,
                        thumbnailUrl = item.thumbnails?.firstOrNull()?.url ?: "",
                        description = null,
                        uploadDate = item.textualUploadDate,
                        isShort = item.url.contains("/shorts/") || item.streamType.name.contains("SHORT") || item.duration <= 180
                    )
                }.getOrNull()
            }
        }.getOrElse { tabError ->
            runCatching {
                val uploadsPlaylistId = if (info.id.startsWith("UC")) {
                    "UU" + info.id.substring(2)
                } else {
                    info.id
                }
                val playlistUrl = "https://www.youtube.com/playlist?list=$uploadsPlaylistId"
                val playlistInfo = NewPipePlaylistInfo.getInfo(service, playlistUrl)
                playlistInfo.relatedItems?.mapNotNull { item ->
                    if (item !is StreamInfoItem) return@mapNotNull null
                    runCatching {
                        ExtractedVideoInfo(
                            videoId = extractVideoIdFromUrl(item.url),
                            title = item.name ?: "Unknown",
                            channelName = info.name ?: "Unknown",
                            channelId = extractChannelIdFromUrl(info.url) ?: "",
                            durationSeconds = item.duration,
                            thumbnailUrl = item.thumbnails?.firstOrNull()?.url ?: "",
                            description = null,
                            uploadDate = item.textualUploadDate,
                            isShort = item.url.contains("/shorts/") || item.streamType.name.contains("SHORT") || item.duration <= 180
                        )
                    }.getOrNull()
                }
            }.getOrNull()
        } ?: emptyList()

        ModelChannelInfo(
            channelId = info.id,
            url = info.url,
            name = info.name ?: "Unknown",
            avatarUrl = info.avatars?.firstOrNull()?.url ?: "",
            subscriberCount = info.subscriberCount,
            description = info.description?.take(500),
            uploads = uploads
        )
    }
}
