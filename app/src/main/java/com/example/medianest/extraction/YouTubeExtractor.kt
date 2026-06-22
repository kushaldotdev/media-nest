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
import org.schabi.newpipe.extractor.playlist.PlaylistInfo as NewPipePlaylistInfo
import org.schabi.newpipe.extractor.stream.StreamInfo as NewPipeStreamInfo
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

    suspend fun extractVideo(url: String): ExtractedVideoInfo = withContext(Dispatchers.IO) {
        val info = NewPipeStreamInfo.getInfo(service, url)

        val streams = mutableListOf<StreamSource>()

        runCatching {
            info.audioStreams?.forEach { track ->
                streams.add(
                    StreamSource(
                        url = track.content,
                        format = "audio",
                        quality = "${track.averageBitrate / 1000}kbps",
                        mimeType = track.format?.mimeType ?: "audio/mpeg",
                        contentLength = 0
                    )
                )
            }
        }

        runCatching {
            info.videoStreams?.forEach { stream ->
                streams.add(
                    StreamSource(
                        url = stream.content,
                        format = "video",
                        quality = stream.getResolution(),
                        mimeType = stream.format?.mimeType ?: "video/mp4",
                        contentLength = 0
                    )
                )
            }
        }

        runCatching {
            info.videoOnlyStreams?.forEach { stream ->
                streams.add(
                    StreamSource(
                        url = stream.content,
                        format = "video_only",
                        quality = stream.getResolution(),
                        mimeType = stream.format?.mimeType ?: "video/mp4",
                        contentLength = 0
                    )
                )
            }
        }

        ExtractedVideoInfo(
            videoId = info.id,
            title = info.name ?: "",
            channelName = info.uploaderName ?: "Unknown",
            channelId = info.uploaderUrl ?: "",
            durationSeconds = info.duration,
            thumbnailUrl = info.thumbnails?.firstOrNull()?.url ?: "",
            description = info.description?.content?.take(1000),
            uploadDate = info.textualUploadDate,
            streamSources = streams
        )
    }

    suspend fun extractPlaylist(url: String): ExtractedPlaylistInfo = withContext(Dispatchers.IO) {
        val info = NewPipePlaylistInfo.getInfo(service, url)

        val videos = info.relatedItems?.mapNotNull { item ->
            runCatching {
                ExtractedVideoInfo(
                    videoId = item.url,
                    title = item.name ?: "Unknown",
                    channelName = item.uploaderName ?: "Unknown",
                    channelId = item.uploaderUrl ?: "",
                    durationSeconds = item.duration,
                    thumbnailUrl = item.thumbnails?.firstOrNull()?.url ?: "",
                    description = null,
                    uploadDate = null
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

    suspend fun extractChannel(url: String): ModelChannelInfo = withContext(Dispatchers.IO) {
        val info = NewPipeChannelInfo.getInfo(service, url)

        ModelChannelInfo(
            channelId = info.id,
            name = info.name ?: "Unknown",
            avatarUrl = info.avatars?.firstOrNull()?.url ?: "",
            subscriberCount = info.subscriberCount,
            description = info.description?.take(500),
            uploads = emptyList()
        )
    }
}
