package com.example.medianest

import android.app.Application
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.example.medianest.data.preferences.DownloadPreferences
import com.example.medianest.data.preferences.UpdatePreferences
import com.example.medianest.extraction.DownloaderProvider
import com.example.medianest.worker.WorkScheduler
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.NewPipe
import timber.log.Timber
import javax.inject.Inject
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import okhttp3.OkHttpClient
import com.example.medianest.data.local.dao.VideoDao
import kotlinx.coroutines.Dispatchers

@HiltAndroidApp
class MediaNestApp : Application(), Configuration.Provider, ImageLoaderFactory {
    @Inject lateinit var hiltWorkerFactory: HiltWorkerFactory
    @Inject lateinit var okHttpClient: OkHttpClient
    @Inject lateinit var videoDao: VideoDao
    @Inject lateinit var updatePreferences: UpdatePreferences

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(hiltWorkerFactory)
            .build()

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .okHttpClient(okHttpClient)
            .respectCacheHeaders(false)
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(250 * 1024 * 1024)
                    .build()
            }
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .crossfade(true)
            .build()
    }

    override fun onCreate() {
        super.onCreate()
        MainScope().launch(Dispatchers.IO) {
            runCatching {
                videoDao.deleteSearchedOrphans()
            }.onFailure {
                Timber.e(it, "Failed to prune searched orphans on startup")
            }
        }
        NewPipe.init(DownloaderProvider.getDownloader())
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        val downloadChannel = NotificationChannelCompat.Builder("downloads", NotificationManagerCompat.IMPORTANCE_LOW)
            .setName("Downloads")
            .build()
        NotificationManagerCompat.from(this).createNotificationChannel(downloadChannel)

        val bulkDownloadChannel = NotificationChannelCompat.Builder("bulk_downloads", NotificationManagerCompat.IMPORTANCE_LOW)
            .setName("Bulk Downloads")
            .build()
        NotificationManagerCompat.from(this).createNotificationChannel(bulkDownloadChannel)

        val updateChannel = NotificationChannelCompat.Builder("app_updates", NotificationManagerCompat.IMPORTANCE_LOW)
            .setName("App Updates")
            .build()
        NotificationManagerCompat.from(this).createNotificationChannel(updateChannel)

        WorkScheduler.scheduleSubscriptionCheck(this)

        // Schedule auto update-check on app startup if enabled
        MainScope().launch {
            val interval = updatePreferences.autoCheckIntervalHours.first()
            if (interval > 0) {
                WorkScheduler.scheduleUpdateCheck(this@MediaNestApp, interval.toLong())
            }
        }

        // Schedule auto-backup on app startup if enabled
        val downloadPreferences = DownloadPreferences(this)
        MainScope().launch {
            val interval = downloadPreferences.autoBackupIntervalHours.first()
            if (interval > 0) {
                WorkScheduler.scheduleAutoBackup(this@MediaNestApp, interval.toLong())
            }
        }
    }
}
