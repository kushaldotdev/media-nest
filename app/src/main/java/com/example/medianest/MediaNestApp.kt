package com.example.medianest

import android.app.Application
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationManagerCompat
import com.example.medianest.extraction.DownloaderProvider
import dagger.hilt.android.HiltAndroidApp
import org.schabi.newpipe.extractor.NewPipe
import timber.log.Timber

@HiltAndroidApp
class MediaNestApp : Application() {
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
    }
}
