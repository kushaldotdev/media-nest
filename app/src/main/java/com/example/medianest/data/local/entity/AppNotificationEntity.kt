package com.example.medianest.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "app_notifications")
data class AppNotificationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val type: String,
    val title: String,
    val message: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isRead: Boolean = false,
    val targetVideoId: String? = null,
    val targetDownloadId: Long? = null
) {
    companion object {
        const val TYPE_DOWNLOAD = "DOWNLOAD"
        const val TYPE_SUBSCRIPTION = "SUBSCRIPTION"
        const val TYPE_SYSTEM = "SYSTEM"
        const val TYPE_SYNC = "SYNC"
    }
}
