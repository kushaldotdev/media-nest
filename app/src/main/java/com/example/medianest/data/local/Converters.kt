package com.example.medianest.data.local

import androidx.room.TypeConverter
import com.example.medianest.data.local.entity.BulkDownloadItemStatus
import com.example.medianest.data.local.entity.BulkDownloadJobStatus
import com.example.medianest.data.local.entity.DownloadStatus

class Converters {
    @TypeConverter
    fun fromDownloadStatus(status: DownloadStatus): String {
        return status.name
    }

    @TypeConverter
    fun toDownloadStatus(statusStr: String): DownloadStatus {
        return try {
            DownloadStatus.valueOf(statusStr)
        } catch (e: IllegalArgumentException) {
            DownloadStatus.QUEUED
        }
    }

    @TypeConverter
    fun fromBulkDownloadJobStatus(status: BulkDownloadJobStatus): String = status.name

    @TypeConverter
    fun toBulkDownloadJobStatus(statusStr: String): BulkDownloadJobStatus {
        return try {
            BulkDownloadJobStatus.valueOf(statusStr)
        } catch (e: IllegalArgumentException) {
            BulkDownloadJobStatus.PENDING
        }
    }

    @TypeConverter
    fun fromBulkDownloadItemStatus(status: BulkDownloadItemStatus): String = status.name

    @TypeConverter
    fun toBulkDownloadItemStatus(statusStr: String): BulkDownloadItemStatus {
        return try {
            BulkDownloadItemStatus.valueOf(statusStr)
        } catch (e: IllegalArgumentException) {
            BulkDownloadItemStatus.PENDING
        }
    }
}
