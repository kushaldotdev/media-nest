package com.example.medianest.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.medianest.data.local.AppDatabase
import com.example.medianest.data.local.dao.SubscriptionDao
import com.example.medianest.data.local.dao.VideoDao
import com.example.medianest.data.local.dao.VideoFolderDao
import com.example.medianest.data.local.dao.DownloadDao
import com.example.medianest.data.local.dao.FolderDao
import com.example.medianest.data.local.dao.HistoryDao
import com.example.medianest.data.local.dao.PlaylistDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    private val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE videos ADD COLUMN localFilePath TEXT NOT NULL DEFAULT ''")
        }
    }

    private val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS subscriptions (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    sourceType TEXT NOT NULL,
                    sourceId TEXT NOT NULL,
                    name TEXT NOT NULL,
                    thumbnailUrl TEXT,
                    uploaderName TEXT,
                    autoDownload INTEGER NOT NULL DEFAULT 0,
                    audioOnly INTEGER NOT NULL DEFAULT 0,
                    lastCheckedAt INTEGER NOT NULL DEFAULT 0,
                    createdAt INTEGER NOT NULL,
                    updatedAt INTEGER NOT NULL
                )
            """)
            db.execSQL("ALTER TABLE playlists ADD COLUMN youtubePlaylistId TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE playlists ADD COLUMN uploaderName TEXT DEFAULT ''")
            db.execSQL("ALTER TABLE playlists ADD COLUMN videoCount INTEGER NOT NULL DEFAULT 0")
        }
    }

    private val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE videos ADD COLUMN favorite INTEGER NOT NULL DEFAULT 0")
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS folders (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    name TEXT NOT NULL,
                    parentId INTEGER REFERENCES folders(id) ON DELETE SET NULL,
                    createdAt INTEGER NOT NULL,
                    updatedAt INTEGER NOT NULL
                )
            """)
            db.execSQL("CREATE INDEX IF NOT EXISTS index_folders_parentId ON folders(parentId)")
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS video_folder_join (
                    videoId TEXT NOT NULL REFERENCES videos(id) ON DELETE CASCADE,
                    folderId INTEGER NOT NULL REFERENCES folders(id) ON DELETE CASCADE,
                    addedAt INTEGER NOT NULL,
                    PRIMARY KEY (videoId, folderId)
                )
            """)
            db.execSQL("CREATE INDEX IF NOT EXISTS index_vfj_videoId ON video_folder_join(videoId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_vfj_folderId ON video_folder_join(folderId)")
        }
    }

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "media_nest.db"
        ).addMigrations(MIGRATION_3_4)
            .addMigrations(MIGRATION_4_5)
            .addMigrations(MIGRATION_5_6)
            .fallbackToDestructiveMigration(false)
            .build()
    }

    @Provides
    fun provideVideoDao(database: AppDatabase): VideoDao = database.videoDao()

    @Provides
    fun provideDownloadDao(database: AppDatabase): DownloadDao = database.downloadDao()

    @Provides
    fun provideHistoryDao(database: AppDatabase): HistoryDao = database.historyDao()

    @Provides
    fun providePlaylistDao(database: AppDatabase): PlaylistDao = database.playlistDao()

    @Provides
    fun provideFolderDao(database: AppDatabase): FolderDao = database.folderDao()

    @Provides
    fun provideVideoFolderDao(database: AppDatabase): VideoFolderDao = database.videoFolderDao()

    @Provides
    fun provideSubscriptionDao(database: AppDatabase): SubscriptionDao = database.subscriptionDao()
}
