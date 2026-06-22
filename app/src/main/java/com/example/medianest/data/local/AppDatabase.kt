package com.example.medianest.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.medianest.data.local.dao.SubscriptionDao
import com.example.medianest.data.local.dao.VideoDao
import com.example.medianest.data.local.dao.DownloadDao
import com.example.medianest.data.local.dao.FolderDao
import com.example.medianest.data.local.dao.HistoryDao
import com.example.medianest.data.local.dao.PlaylistDao
import com.example.medianest.data.local.dao.VideoFolderDao
import com.example.medianest.data.local.entity.SubscriptionEntity
import com.example.medianest.data.local.entity.VideoEntity
import com.example.medianest.data.local.entity.DownloadEntity
import com.example.medianest.data.local.entity.FolderEntity
import com.example.medianest.data.local.entity.HistoryEntity
import com.example.medianest.data.local.entity.PlaylistEntity
import com.example.medianest.data.local.entity.VideoFolderJoin

@Database(
    entities = [
        VideoEntity::class,
        DownloadEntity::class,
        HistoryEntity::class,
        PlaylistEntity::class,
        FolderEntity::class,
        VideoFolderJoin::class,
        SubscriptionEntity::class
    ],
    version = 7,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun videoDao(): VideoDao
    abstract fun downloadDao(): DownloadDao
    abstract fun historyDao(): HistoryDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun folderDao(): FolderDao
    abstract fun videoFolderDao(): VideoFolderDao
    abstract fun subscriptionDao(): SubscriptionDao
}
