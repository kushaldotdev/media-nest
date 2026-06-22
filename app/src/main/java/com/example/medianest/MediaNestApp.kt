package com.example.medianest

import android.app.Application
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
    }
}
