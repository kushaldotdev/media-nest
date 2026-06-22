package com.example.medianest.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.medianest.data.local.dao.VideoDao
import com.example.medianest.data.local.dao.DownloadDao
import com.example.medianest.data.local.dao.HistoryDao
import com.example.medianest.data.local.dao.PlaylistDao
import com.example.medianest.data.local.entity.VideoEntity
import com.example.medianest.data.local.entity.DownloadEntity
import com.example.medianest.data.local.entity.HistoryEntity
import com.example.medianest.data.local.entity.PlaylistEntity

@Database(
    entities = [
        VideoEntity::class,
        DownloadEntity::class,
        HistoryEntity::class,
        PlaylistEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun videoDao(): VideoDao
    abstract fun downloadDao(): DownloadDao
    abstract fun historyDao(): HistoryDao
    abstract fun playlistDao(): PlaylistDao
}
