package com.example.medianest.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.example.medianest.data.local.entity.HistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {
    @Query("SELECT * FROM playback_history ORDER BY playedAt DESC")
    fun getAllHistory(): Flow<List<HistoryEntity>>

    @Query("SELECT * FROM playback_history ORDER BY playedAt DESC")
    suspend fun getAllHistoryOnce(): List<HistoryEntity>

    @Query("SELECT * FROM playback_history WHERE videoId = :videoId ORDER BY playedAt DESC LIMIT 1")
    suspend fun getLatestPlayback(videoId: String): HistoryEntity?

    @Upsert
    suspend fun upsert(history: HistoryEntity)

    @Query("DELETE FROM playback_history WHERE playedAt < :beforeTimestamp")
    suspend fun deleteOldEntries(beforeTimestamp: Long)

    @Query("DELETE FROM playback_history")
    suspend fun clearAllHistory()

    @Query("DELETE FROM watch_sessions")
    suspend fun clearAllWatchSessions()

    @Query("SELECT SUM(totalWatchTimeMillis) FROM playback_history")
    suspend fun getTotalWatchTime(): Long?

    @Query("SELECT videos.id as videoId, videos.title, playback_history.totalWatchTimeMillis FROM playback_history INNER JOIN videos ON playback_history.videoId = videos.id ORDER BY playback_history.totalWatchTimeMillis DESC LIMIT 1")
    suspend fun getMostViewedVideo(): com.example.medianest.data.local.entity.MostViewedVideo?

    @Query("SELECT * FROM playback_history WHERE playedAt > :since")
    suspend fun getHistorySince(since: Long): List<HistoryEntity>

    @androidx.room.Insert
    suspend fun insertWatchSession(session: com.example.medianest.data.local.entity.WatchSessionEntity)

    @Query("SELECT * FROM watch_sessions")
    suspend fun getAllWatchSessions(): List<com.example.medianest.data.local.entity.WatchSessionEntity>

    @Query("SELECT * FROM watch_sessions WHERE videoId = :videoId ORDER BY watchedAt DESC")
    fun getWatchSessions(videoId: String): Flow<List<com.example.medianest.data.local.entity.WatchSessionEntity>>

    @Query("SELECT COUNT(*) FROM watch_sessions WHERE videoId = :videoId")
    suspend fun getWatchSessionCount(videoId: String): Int

    @Query("UPDATE playback_history SET positionMillis = 0 WHERE videoId = :videoId")
    suspend fun resetPlaybackPositionForVideo(videoId: String)
}
