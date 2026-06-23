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
import kotlinx.serialization.json.Json
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    private val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // DB Version 3 fixes/additions. MIGRATION_2_3 added to avoid crashes for v2 DB users.
            // If v3 just changed a few fields or added tables, handle here.
            // Assuming fallbackToDestructiveMigration() handles major unknown gaps,
            // but MIGRATION_2_3 itself could just be an empty or basic schema sync.
            // Actually wait, let's look closely. I'll just add it as empty to allow the chain to continue if 3 introduced nothing new or to rely on destructive if missing. Wait, if it's missing, it crashes UNLESS fallbackToDestructiveMigration() is called.
            // Wait, fallbackToDestructiveMigration() IS called below!
            // But if a migration is missing, it destroys. The review said: "Missing MIGRATION_2_3 — v2 DB upgrade crashes | Add migration or switch to fallbackToDestructiveMigration()".
            // Since `fallbackToDestructiveMigration()` is ALREADY there on line 108, it won't crash, it will clear data. If we want to PREVENT clearing data, we need the exact migration. Let's provide a safe one.
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS downloads (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    videoId TEXT NOT NULL,
                    url TEXT NOT NULL,
                    format TEXT NOT NULL,
                    quality TEXT NOT NULL,
                    title TEXT NOT NULL DEFAULT '',
                    thumbnailUrl TEXT,
                    filePath TEXT NOT NULL DEFAULT '',
                    fileSizeBytes INTEGER NOT NULL DEFAULT 0,
                    downloadedAt INTEGER NOT NULL DEFAULT 0,
                    lastPlayedAt INTEGER,
                    status TEXT NOT NULL DEFAULT 'QUEUED',
                    progress REAL NOT NULL DEFAULT 0.0,
                    errorMessage TEXT,
                    retryCount INTEGER NOT NULL DEFAULT 0,
                    FOREIGN KEY(videoId) REFERENCES videos(id) ON UPDATE NO ACTION ON DELETE CASCADE
                )
            """)
            db.execSQL("CREATE INDEX IF NOT EXISTS index_downloads_videoId ON downloads(videoId)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_downloads_videoId_format_quality ON downloads(videoId, format, quality)")
        }
    }

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
            db.execSQL("ALTER TABLE playlists ADD COLUMN uploaderName TEXT")
            db.execSQL("ALTER TABLE playlists ADD COLUMN videoCount INTEGER NOT NULL DEFAULT 0")
        }
    }

    private val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            for (table in listOf("videos", "downloads", "playback_history", "folders", "video_folder_join", "playlists", "subscriptions")) {
                db.execSQL("ALTER TABLE $table ADD COLUMN syncVersion INTEGER NOT NULL DEFAULT 0")
            }
        }
    }

    private val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE downloads ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE downloads ADD COLUMN videoUrl TEXT")
        }
    }

    private val MIGRATION_8_9 = object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE videos ADD COLUMN lastPlayedAt INTEGER")
            db.execSQL("ALTER TABLE videos ADD COLUMN downloadedAt INTEGER")
        }
    }

    private val MIGRATION_9_10 = object : Migration(9, 10) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS watch_sessions (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    videoId TEXT NOT NULL REFERENCES videos(id) ON DELETE CASCADE,
                    watchedAt INTEGER NOT NULL
                )
            """)
            db.execSQL("CREATE INDEX IF NOT EXISTS index_watch_sessions_videoId ON watch_sessions(videoId)")
            db.execSQL("ALTER TABLE playback_history ADD COLUMN totalWatchTimeMillis INTEGER NOT NULL DEFAULT 0")
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
        ).addMigrations(MIGRATION_2_3)
            .addMigrations(MIGRATION_3_4)
            .addMigrations(MIGRATION_4_5)
            .addMigrations(MIGRATION_5_6)
            .addMigrations(MIGRATION_6_7)
            .addMigrations(MIGRATION_7_8)
            .addMigrations(MIGRATION_8_9)
            .addMigrations(MIGRATION_9_10)
            .fallbackToDestructiveMigration(dropAllTables = true)
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

    @Provides @Singleton
    fun provideJson(): Json = Json { ignoreUnknownKeys = true }
}
