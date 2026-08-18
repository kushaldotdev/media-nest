package com.example.medianest.data.repository

import com.example.medianest.data.local.dao.NotificationDao
import com.example.medianest.data.local.entity.AppNotificationEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationRepository @Inject constructor(
    private val notificationDao: NotificationDao
) {
    fun getAllNotificationsFlow(): Flow<List<AppNotificationEntity>> =
        notificationDao.getAllNotificationsFlow()

    fun getNotificationsByTypeFlow(type: String): Flow<List<AppNotificationEntity>> =
        notificationDao.getNotificationsByTypeFlow(type)

    fun getUnreadCountFlow(): Flow<Int> =
        notificationDao.getUnreadCountFlow()

    suspend fun getAllNotificationsOnce(): List<AppNotificationEntity> =
        notificationDao.getAllNotificationsOnce()

    suspend fun insert(notification: AppNotificationEntity): Long =
        notificationDao.insert(notification)

    suspend fun insertAll(notifications: List<AppNotificationEntity>) =
        notificationDao.insertAll(notifications)

    suspend fun markAsRead(id: Long) =
        notificationDao.markAsRead(id)

    suspend fun markAllAsRead() =
        notificationDao.markAllAsRead()

    suspend fun deleteById(id: Long) =
        notificationDao.deleteById(id)

    suspend fun clearAll() =
        notificationDao.clearAll()

    suspend fun deleteOlderThan(cutoff: Long) =
        notificationDao.deleteOlderThan(cutoff)
}
