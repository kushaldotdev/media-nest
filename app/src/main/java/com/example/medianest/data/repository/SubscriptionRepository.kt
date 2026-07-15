package com.example.medianest.data.repository

import com.example.medianest.data.local.dao.PlaylistDao
import com.example.medianest.data.local.dao.SubscriptionDao
import com.example.medianest.data.local.dao.VideoDao
import com.example.medianest.data.local.entity.PlaylistEntity
import com.example.medianest.data.local.entity.SubscriptionEntity
import com.example.medianest.data.local.entity.VideoEntity
import com.example.medianest.data.mapper.toVideoEntity
import com.example.medianest.extraction.YouTubeExtractor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SubscriptionRepository @Inject constructor(
    private val subscriptionDao: SubscriptionDao,
    private val playlistDao: PlaylistDao,
    private val videoDao: VideoDao,
    private val youTubeExtractor: YouTubeExtractor
) {
    private fun cleanSourceId(sourceType: String, sourceId: String): String {
        return if (sourceType == "channel" && sourceId.startsWith("http")) {
            youTubeExtractor.extractChannelIdFromUrl(sourceId) ?: sourceId
        } else if (sourceType == "playlist" && sourceId.startsWith("http")) {
            sourceId.substringAfter("list=").substringBefore("&")
        } else {
            sourceId
        }
    }

    init {
        @OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
        GlobalScope.launch(Dispatchers.IO) {
            runCatching {
                val all = subscriptionDao.getAllSubscriptionsOnce()
                for (sub in all) {
                    if (sub.sourceType == "channel") {
                        val cleaned = cleanSourceId(sub.sourceType, sub.sourceId)
                        if (!cleaned.startsWith("UC")) {
                            runCatching {
                                val channelInfo = youTubeExtractor.extractChannel(sub.sourceId)
                                val resolvedId = channelInfo.channelId
                                if (resolvedId.isNotEmpty() && resolvedId.startsWith("UC")) {
                                    subscriptionDao.update(sub.copy(sourceId = resolvedId))
                                }
                            }
                        } else if (cleaned != sub.sourceId) {
                            subscriptionDao.update(sub.copy(sourceId = cleaned))
                        }
                    } else if (sub.sourceType == "playlist") {
                        val cleaned = cleanSourceId(sub.sourceType, sub.sourceId)
                        if (cleaned != sub.sourceId) {
                            subscriptionDao.update(sub.copy(sourceId = cleaned))
                        }
                    }
                }
            }
        }
    }

    fun getAllSubscriptions(): Flow<List<SubscriptionEntity>> = subscriptionDao.getAllSubscriptions()

    suspend fun getAllSubscriptionsOnce(): List<SubscriptionEntity> = subscriptionDao.getAllSubscriptionsOnce()

    fun getChannels(): Flow<List<SubscriptionEntity>> = subscriptionDao.getByType("channel")

    fun getPlaylistSubscriptions(): Flow<List<SubscriptionEntity>> = subscriptionDao.getByType("playlist")

    suspend fun getById(id: Long): SubscriptionEntity? = subscriptionDao.getById(id)

    suspend fun getBySource(sourceType: String, sourceId: String): SubscriptionEntity? {
        val clean = cleanSourceId(sourceType, sourceId)
        return subscriptionDao.getBySource(sourceType, clean)
    }

    suspend fun isSubscribed(sourceType: String, sourceId: String): Boolean {
        if (getBySource(sourceType, sourceId) != null) return true
        if (sourceType == "channel") {
            val clean = cleanSourceId(sourceType, sourceId)
            val all = subscriptionDao.getAllSubscriptionsOnce()
            return all.any { sub ->
                sub.sourceType == "channel" && (
                    sub.sourceId == clean ||
                    sub.sourceId.contains(clean) ||
                    clean.contains(sub.sourceId.removePrefix("https://").removePrefix("www.youtube.com/").removePrefix("@").removePrefix("channel/").removePrefix("c/").trim())
                )
            }
        }
        return false
    }

    suspend fun subscribe(
        sourceType: String,
        sourceId: String,
        name: String,
        thumbnailUrl: String? = null,
        uploaderName: String? = null
    ): Long {
        val clean = cleanSourceId(sourceType, sourceId)
        val existing = subscriptionDao.getBySource(sourceType, clean)
        if (existing != null) return existing.id
        return subscriptionDao.insert(
            SubscriptionEntity(
                sourceType = sourceType,
                sourceId = clean,
                name = name,
                thumbnailUrl = thumbnailUrl,
                uploaderName = uploaderName
            )
        )
    }

    suspend fun unsubscribe(id: Long) {
        val sub = subscriptionDao.getById(id) ?: return
        subscriptionDao.delete(sub)
    }

    suspend fun unsubscribeBySourceId(sourceId: String) {
        // Since we don't know the sourceType here, we'll try channel cleaning if it's not a playlist
        val clean = if (sourceId.contains("list=")) {
            cleanSourceId("playlist", sourceId)
        } else {
            cleanSourceId("channel", sourceId)
        }
        subscriptionDao.deleteBySourceId(clean)
    }

    suspend fun updateAutoDownload(id: Long, autoDownload: Boolean, audioOnly: Boolean) {
        subscriptionDao.updateAutoDownload(id, autoDownload, audioOnly)
    }

    suspend fun checkForUpdates(subscription: SubscriptionEntity): List<VideoEntity> {
        return if (subscription.sourceType == "channel") {
            checkChannel(subscription)
        } else {
            checkPlaylist(subscription)
        }
    }

    private suspend fun checkChannel(subscription: SubscriptionEntity): List<VideoEntity> {
        val channel = youTubeExtractor.extractChannel(subscription.sourceId)
        val newVideos = mutableListOf<VideoEntity>()
        for (video in channel.uploads) {
            val existing = videoDao.getVideoById(video.videoId)
            if (existing == null) {
                val entity = video.toVideoEntity()
                videoDao.insert(entity)
                newVideos.add(entity)
            }
        }
        subscriptionDao.updateLastChecked(subscription.id)
        return newVideos
    }

    private suspend fun checkPlaylist(subscription: SubscriptionEntity): List<VideoEntity> {
        val playlist = youTubeExtractor.extractPlaylist(subscription.sourceId)
        val newVideos = mutableListOf<VideoEntity>()

        val existingPlaylist = playlistDao.getByYoutubePlaylistId(playlist.playlistId)
        if (existingPlaylist == null) {
            playlistDao.insert(
                PlaylistEntity(
                    name = playlist.name,
                    thumbnailUrl = playlist.thumbnailUrl,
                    youtubePlaylistId = playlist.playlistId,
                    uploaderName = playlist.uploaderName,
                    videoCount = playlist.videos.size
                )
            )
        }

        for (video in playlist.videos) {
            val existing = videoDao.getVideoById(video.videoId)
            if (existing == null) {
                val entity = video.toVideoEntity()
                videoDao.insert(entity)
                newVideos.add(entity)
            }
        }
        subscriptionDao.updateLastChecked(subscription.id)
        return newVideos
    }
}
