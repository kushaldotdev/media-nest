package com.example.medianest.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "link_history")
data class LinkHistoryEntity(
    @PrimaryKey val url: String,
    val title: String,
    val extractedAt: Long = System.currentTimeMillis()
)
