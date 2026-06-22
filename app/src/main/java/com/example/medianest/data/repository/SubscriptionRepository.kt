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
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SubscriptionRepository @Inject constructor(
    private val subscriptionDao: SubscriptionDao,
    private val playlistDao: PlaylistDao,
    private val videoDao: VideoDao,
    private val youTubeExtractor: YouTubeExtractor
) {
    fun getAllSubscriptions(): Flow<List<SubscriptionEntity>> = subscriptionDao.getAllSubscriptions()

    fun getChannels(): Flow<List<SubscriptionEntity>> = subscriptionDao.getByType("channel")

    fun getPlaylistSubscriptions(): Flow<List<SubscriptionEntity>> = subscriptionDao.getByType("playlist")

    suspend fun getById(id: Long): SubscriptionEntity? = subscriptionDao.getById(id)

    suspend fun isSubscribed(sourceType: String, sourceId: String): Boolean =
        subscriptionDao.getBySource(sourceType, sourceId) != null

    suspend fun subscribe(
        sourceType: String,
        sourceId: String,
        name: String,
        thumbnailUrl: String? = null,
        uploaderName: String? = null
    ): Long {
        val existing = subscriptionDao.getBySource(sourceType, sourceId)
        if (existing != null) return existing.id
        return subscriptionDao.insert(
            SubscriptionEntity(
                sourceType = sourceType,
                sourceId = sourceId,
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
