package com.example.medianest.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.medianest.data.local.entity.SubscriptionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SubscriptionDao {
    @Query("SELECT * FROM subscriptions ORDER BY name ASC")
    fun getAllSubscriptions(): Flow<List<SubscriptionEntity>>

    @Query("SELECT * FROM subscriptions ORDER BY name ASC")
    suspend fun getAllSubscriptionsOnce(): List<SubscriptionEntity>

    @Query("SELECT * FROM subscriptions WHERE id = :id")
    suspend fun getById(id: Long): SubscriptionEntity?

    @Query("SELECT * FROM subscriptions WHERE sourceType = :sourceType AND sourceId = :sourceId LIMIT 1")
    suspend fun getBySource(sourceType: String, sourceId: String): SubscriptionEntity?

    @Query("SELECT * FROM subscriptions WHERE sourceType = :sourceType ORDER BY name ASC")
    fun getByType(sourceType: String): Flow<List<SubscriptionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(subscription: SubscriptionEntity): Long

    @Update
    suspend fun update(subscription: SubscriptionEntity)

    @Delete
    suspend fun delete(subscription: SubscriptionEntity)

    @Query("UPDATE subscriptions SET lastCheckedAt = :timestamp WHERE id = :id")
    suspend fun updateLastChecked(id: Long, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE subscriptions SET autoDownload = :autoDownload, audioOnly = :audioOnly WHERE id = :id")
    suspend fun updateAutoDownload(id: Long, autoDownload: Boolean, audioOnly: Boolean)

    @Query("DELETE FROM subscriptions WHERE sourceId = :sourceId")
    suspend fun deleteBySourceId(sourceId: String)

    @Query("SELECT * FROM subscriptions WHERE updatedAt > :since")
    suspend fun getSubscriptionsSince(since: Long): List<SubscriptionEntity>
}
