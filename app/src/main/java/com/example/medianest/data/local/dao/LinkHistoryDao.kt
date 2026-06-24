package com.example.medianest.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.medianest.data.local.entity.LinkHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LinkHistoryDao {
    @Query("SELECT * FROM link_history ORDER BY extractedAt DESC")
    fun getAllLinkHistory(): Flow<List<LinkHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(linkHistory: LinkHistoryEntity)

    @Query("DELETE FROM link_history WHERE url = :url")
    suspend fun deleteByUrl(url: String)

    @Query("DELETE FROM link_history")
    suspend fun clearAll()
}
