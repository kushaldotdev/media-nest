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
}
