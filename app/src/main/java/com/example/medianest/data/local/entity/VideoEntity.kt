package com.example.medianest.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "videos")
data class VideoEntity(
    @PrimaryKey val id: String,
    val title: String,
    val channelName: String,
    val channelId: String? = null,
    val durationSeconds: Long = 0,
    val thumbnailUrl: String? = null,
    val description: String? = null,
    val uploadDate: String? = null,
    val addedAt: Long = System.currentTimeMillis()
)
