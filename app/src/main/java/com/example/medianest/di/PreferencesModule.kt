package com.example.medianest.di

import android.content.Context
import com.example.medianest.data.preferences.DevicePreferences
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object PreferencesModule {
    @Provides
    @Singleton
    fun provideDevicePreferences(@ApplicationContext context: Context): DevicePreferences =
        DevicePreferences(context)
}
