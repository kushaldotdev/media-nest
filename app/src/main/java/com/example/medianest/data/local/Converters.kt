package com.example.medianest.data.local

import androidx.room.TypeConverter
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
}
