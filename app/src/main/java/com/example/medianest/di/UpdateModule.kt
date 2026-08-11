package com.example.medianest.di

import android.content.Context
import com.example.medianest.data.preferences.UpdatePreferences
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object UpdateModule {
    @Provides
    @Singleton
    fun provideUpdatePreferences(@ApplicationContext context: Context): UpdatePreferences =
        UpdatePreferences(context)
}
