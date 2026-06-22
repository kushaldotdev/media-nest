package com.example.medianest.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "subscriptions")
data class SubscriptionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceType: String,
    val sourceId: String,
    val name: String,
    val thumbnailUrl: String? = null,
    val uploaderName: String? = null,
    val autoDownload: Boolean = false,
    val audioOnly: Boolean = false,
    val lastCheckedAt: Long = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val syncVersion: Long = 0
)
