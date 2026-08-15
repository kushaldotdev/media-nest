package com.example.medianest.di

import android.content.Context
import com.example.medianest.data.preferences.CollectionsPreferences
import com.example.medianest.data.preferences.DevicePreferences
import com.example.medianest.data.preferences.SubscriptionsPreferences
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

    @Provides
    @Singleton
    fun provideSubscriptionsPreferences(@ApplicationContext context: Context): SubscriptionsPreferences =
        SubscriptionsPreferences(context)

    @Provides
    @Singleton
    fun provideCollectionsPreferences(@ApplicationContext context: Context): CollectionsPreferences =
        CollectionsPreferences(context)
}
