package com.example.medianest.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.playbackStore: DataStore<Preferences> by preferencesDataStore(name = "playback_prefs")

class PlaybackPreferences(private val context: Context) {
    companion object {
        private val PLAYBACK_SPEED = floatPreferencesKey("playback_speed")
        const val DEFAULT_SPEED = 1.0f
    }

    val playbackSpeed: Flow<Float> = context.playbackStore.data.map { prefs ->
        prefs[PLAYBACK_SPEED] ?: DEFAULT_SPEED
    }

    suspend fun setPlaybackSpeed(speed: Float) {
        context.playbackStore.edit { prefs ->
            prefs[PLAYBACK_SPEED] = speed
        }
    }
}
