package com.example.medianest.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.medianest.data.local.entity.AppNotificationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NotificationDao {
    @Query("SELECT * FROM app_notifications ORDER BY timestamp DESC")
    fun getAllNotificationsFlow(): Flow<List<AppNotificationEntity>>

    @Query("SELECT * FROM app_notifications WHERE type = :type ORDER BY timestamp DESC")
    fun getNotificationsByTypeFlow(type: String): Flow<List<AppNotificationEntity>>

    @Query("SELECT COUNT(*) FROM app_notifications WHERE isRead = 0")
    fun getUnreadCountFlow(): Flow<Int>

    @Query("SELECT * FROM app_notifications ORDER BY timestamp DESC")
    suspend fun getAllNotificationsOnce(): List<AppNotificationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(notification: AppNotificationEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(notifications: List<AppNotificationEntity>)

    @Query("UPDATE app_notifications SET isRead = 1 WHERE id = :id")
    suspend fun markAsRead(id: Long)

    @Query("UPDATE app_notifications SET isRead = 1")
    suspend fun markAllAsRead()

    @Query("DELETE FROM app_notifications WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM app_notifications")
    suspend fun clearAll()

    @Query("DELETE FROM app_notifications WHERE timestamp < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long)
}
