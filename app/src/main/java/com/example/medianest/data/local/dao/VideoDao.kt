package com.example.medianest.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.medianest.data.local.entity.VideoEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VideoDao {
    @Query("SELECT * FROM videos ORDER BY max(addedAt, COALESCE(lastPlayedAt, 0), COALESCE(downloadedAt, 0)) DESC")
    fun getAllVideos(): Flow<List<VideoEntity>>

    @Query("SELECT * FROM videos WHERE id = :videoId")
    suspend fun getVideoById(videoId: String): VideoEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(video: VideoEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(videos: List<VideoEntity>)

    @Update
    suspend fun update(video: VideoEntity)

    @Query("SELECT * FROM videos WHERE title LIKE '%' || :query || '%' OR channelName LIKE '%' || :query || '%' ORDER BY max(addedAt, COALESCE(lastPlayedAt, 0), COALESCE(downloadedAt, 0)) DESC")
    fun searchVideos(query: String): Flow<List<VideoEntity>>

    @Query("SELECT * FROM videos WHERE favorite = 1 ORDER BY max(addedAt, COALESCE(lastPlayedAt, 0), COALESCE(downloadedAt, 0)) DESC")
    fun getFavoriteVideos(): Flow<List<VideoEntity>>

    @Query("SELECT * FROM videos ORDER BY max(addedAt, COALESCE(lastPlayedAt, 0), COALESCE(downloadedAt, 0)) DESC")
    fun getAllVideosSortedByDate(): Flow<List<VideoEntity>>

    @Query("UPDATE videos SET favorite = :favorite WHERE id = :videoId")
    suspend fun setFavorite(videoId: String, favorite: Boolean)

    @Delete
    suspend fun delete(video: VideoEntity)

    @Query("DELETE FROM videos WHERE id = :videoId")
    suspend fun deleteById(videoId: String)

    @Query("SELECT * FROM videos WHERE addedAt > :since")
    suspend fun getVideosSince(since: Long): List<VideoEntity>

    @Query("UPDATE videos SET lastPlayedAt = NULL")
    suspend fun clearAllLastPlayed()

    @Query("DELETE FROM videos WHERE favorite = 0 AND (localFilePath = '' OR localFilePath IS NULL) AND id NOT IN (SELECT videoId FROM video_folder_join)")
    suspend fun deleteOrphanHistoryVideos()
}
