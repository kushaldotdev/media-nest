package com.example.medianest.data.repository

import com.example.medianest.data.local.dao.VideoDao
import com.example.medianest.data.local.entity.VideoEntity
import com.example.medianest.data.mapper.toVideoEntity
import com.example.medianest.data.model.ChannelInfo
import com.example.medianest.data.model.ExtractedPlaylistInfo
import com.example.medianest.data.model.ExtractedVideoInfo
import com.example.medianest.extraction.YouTubeExtractor
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VideoRepository @Inject constructor(
    private val videoDao: VideoDao,
    private val youTubeExtractor: YouTubeExtractor
) {
    fun getAllVideos(): Flow<List<VideoEntity>> = videoDao.getAllVideos()

    suspend fun getVideoById(videoId: String): VideoEntity? = videoDao.getVideoById(videoId)

    suspend fun searchAndSave(url: String): ExtractedVideoInfo {
        val info = youTubeExtractor.extractVideo(url)
        val existing = videoDao.getVideoById(info.videoId)
        if (existing != null) {
            val updated = info.toVideoEntity().copy(
                addedAt = existing.addedAt,
                favorite = existing.favorite,
                localFilePath = existing.localFilePath,
                lastPlayedAt = existing.lastPlayedAt,
                downloadedAt = existing.downloadedAt
            )
            videoDao.update(updated)
        } else {
            videoDao.insert(info.toVideoEntity())
        }
        return info
    }

    suspend fun extractPlaylist(url: String): ExtractedPlaylistInfo =
        youTubeExtractor.extractPlaylist(url)

    suspend fun extractChannel(url: String): ChannelInfo =
        youTubeExtractor.extractChannel(url)

    suspend fun deleteVideo(video: VideoEntity) = videoDao.delete(video)

    suspend fun setFavorite(videoId: String, favorite: Boolean) =
        videoDao.setFavorite(videoId, favorite)

    suspend fun insertVideo(video: VideoEntity) = videoDao.insert(video)

    suspend fun updateVideo(video: VideoEntity) = videoDao.update(video)
}
