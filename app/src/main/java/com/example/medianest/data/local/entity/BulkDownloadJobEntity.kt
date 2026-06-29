package com.example.medianest.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class BulkDownloadJobStatus {
    PENDING,
    RUNNING,
    READY,
    CONFIRMED,
    FAILED,
    CANCELLED
}

@Entity(
    tableName = "bulk_download_jobs",
    indices = [Index(value = ["status"], name = "index_bulk_download_jobs_status")]
)
data class BulkDownloadJobEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceType: String,
    val sourceId: String,
    val sourceUrl: String,
    val sourceName: String,
    val quality: String,
    @ColumnInfo(defaultValue = "0") val totalVideos: Int = 0,
    @ColumnInfo(defaultValue = "0") val processedVideos: Int = 0,
    @ColumnInfo(defaultValue = "''") val currentTitle: String = "",
    @ColumnInfo(defaultValue = "0") val downloadableVideos: Int = 0,
    @ColumnInfo(defaultValue = "0") val unavailableVideos: Int = 0,
    @ColumnInfo(defaultValue = "0") val failedVideos: Int = 0,
    @ColumnInfo(defaultValue = "0") val totalSizeBytes: Long = 0,
    @ColumnInfo(defaultValue = "0") val usableSpaceBytes: Long = 0,
    @ColumnInfo(defaultValue = "'PENDING'") val status: BulkDownloadJobStatus = BulkDownloadJobStatus.PENDING,
    val errorMessage: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
