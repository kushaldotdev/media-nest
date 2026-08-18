package com.example.medianest.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.playbackStore: DataStore<Preferences> by preferencesDataStore(name = "playback_prefs")

class PlaybackPreferences(private val context: Context) {
    companion object {
        private val PLAYBACK_SPEED = floatPreferencesKey("playback_speed")
        private val AUTO_MARK_WATCHED = booleanPreferencesKey("auto_mark_watched")
        private val BACKGROUND_PLAYBACK = booleanPreferencesKey("background_playback")

        const val DEFAULT_SPEED = 1.0f
        const val DEFAULT_AUTO_MARK_WATCHED = true
        const val DEFAULT_BACKGROUND_PLAYBACK = false
    }

    val playbackSpeed: Flow<Float> = context.playbackStore.data.map { prefs ->
        prefs[PLAYBACK_SPEED] ?: DEFAULT_SPEED
    }

    val autoMarkWatched: Flow<Boolean> = context.playbackStore.data.map { prefs ->
        prefs[AUTO_MARK_WATCHED] ?: DEFAULT_AUTO_MARK_WATCHED
    }

    val backgroundPlayback: Flow<Boolean> = context.playbackStore.data.map { prefs ->
        prefs[BACKGROUND_PLAYBACK] ?: DEFAULT_BACKGROUND_PLAYBACK
    }

    suspend fun setPlaybackSpeed(speed: Float) {
        context.playbackStore.edit { prefs ->
            prefs[PLAYBACK_SPEED] = speed
        }
    }

    suspend fun setAutoMarkWatched(enabled: Boolean) {
        context.playbackStore.edit { prefs ->
            prefs[AUTO_MARK_WATCHED] = enabled
        }
    }

    suspend fun setBackgroundPlayback(enabled: Boolean) {
        context.playbackStore.edit { prefs ->
            prefs[BACKGROUND_PLAYBACK] = enabled
        }
    }
}

