package com.example.medianest.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.downloadStore: DataStore<Preferences> by preferencesDataStore(name = "download_prefs")

class DownloadPreferences(private val context: Context) {
    companion object {
        private val MAX_CONCURRENT = intPreferencesKey("max_concurrent")
        const val DEFAULT_MAX = 2
    }

    val maxConcurrentDownloads: Flow<Int> = context.downloadStore.data.map { prefs ->
        prefs[MAX_CONCURRENT] ?: DEFAULT_MAX
    }

    suspend fun setMaxConcurrentDownloads(max: Int) {
        context.downloadStore.edit { prefs ->
            prefs[MAX_CONCURRENT] = max.coerceIn(1, 5)
        }
    }
}
