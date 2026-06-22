package com.example.medianest.di

import android.content.Context
import com.example.medianest.data.preferences.DownloadPreferences
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DownloadModule {
    @Provides
    @Singleton
    fun provideDownloadPreferences(@ApplicationContext context: Context): DownloadPreferences =
        DownloadPreferences(context)
}
