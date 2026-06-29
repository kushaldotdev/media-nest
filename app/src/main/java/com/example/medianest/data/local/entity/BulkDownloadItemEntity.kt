package com.example.medianest.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class BulkDownloadItemStatus {
    PENDING,
    READY,
    UNAVAILABLE,
    FAILED
}

@Entity(
    tableName = "bulk_download_items",
    foreignKeys = [
        ForeignKey(
            entity = BulkDownloadJobEntity::class,
            parentColumns = ["id"],
            childColumns = ["jobId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["jobId"], name = "index_bulk_download_items_jobId"),
        Index(value = ["jobId", "videoId"], unique = true, name = "index_bulk_download_items_jobId_videoId")
    ]
)
data class BulkDownloadItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val jobId: Long,
    val videoId: String,
    val title: String,
    val thumbnailUrl: String? = null,
    val channelName: String? = null,
    val channelId: String? = null,
    val quality: String,
    @ColumnInfo(defaultValue = "''") val format: String = "",
    @ColumnInfo(defaultValue = "''") val codec: String = "",
    @ColumnInfo(defaultValue = "''") val url: String = "",
    @ColumnInfo(defaultValue = "0") val contentLengthBytes: Long = 0,
    @ColumnInfo(defaultValue = "'PENDING'") val status: BulkDownloadItemStatus = BulkDownloadItemStatus.PENDING,
    val errorMessage: String? = null,
    @ColumnInfo(defaultValue = "0") val displayOrder: Int = 0
)
