package com.example.medianest

import android.app.Application
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.example.medianest.data.preferences.DownloadPreferences
import com.example.medianest.extraction.DownloaderProvider
import com.example.medianest.worker.WorkScheduler
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.NewPipe
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class MediaNestApp : Application(), Configuration.Provider {
    @Inject lateinit var hiltWorkerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(hiltWorkerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        NewPipe.init(DownloaderProvider.getDownloader())
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        val downloadChannel = NotificationChannelCompat.Builder("downloads", NotificationManagerCompat.IMPORTANCE_LOW)
            .setName("Downloads")
            .build()
        NotificationManagerCompat.from(this).createNotificationChannel(downloadChannel)

        WorkScheduler.scheduleSubscriptionCheck(this)

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
