package com.example.medianest.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.updateStore: DataStore<Preferences> by preferencesDataStore(name = "update_prefs")

/**
 * App-scoped persistence for the update flow.
 *
 * This is the layer that survives both navigation (ViewModel death) and
 * WorkManager pruning, so the Settings screen can always rehydrate the
 * correct update state on re-entry.
 */
class UpdatePreferences(private val context: Context) {

    companion object {
        const val STATE_DOWNLOADING = "downloading"
        const val STATE_READY = "ready"
        const val STATE_FAILED = "failed"
        const val STATE_UPDATE_AVAILABLE = "update_available"

        private val KEY_LATEST_VERSION = stringPreferencesKey("latest_version")
        private val KEY_CHANGELOG = stringPreferencesKey("changelog")
        private val KEY_DOWNLOAD_URL = stringPreferencesKey("download_url")
        private val KEY_STATE = stringPreferencesKey("update_state")
        private val KEY_ERROR = stringPreferencesKey("update_error")
        private val KEY_PROGRESS = stringPreferencesKey("update_progress")
        private val KEY_LAST_CHECK_AT = longPreferencesKey("last_check_at")
        private val KEY_AUTO_CHECK_INTERVAL_HOURS = intPreferencesKey("auto_check_interval_hours")

        /** Default: daily, on by default. */
        const val DEFAULT_AUTO_CHECK_INTERVAL_HOURS = 24
    }

    val latestVersion: Flow<String> = context.updateStore.data.map { it[KEY_LATEST_VERSION] ?: "" }
    val changelog: Flow<String> = context.updateStore.data.map { it[KEY_CHANGELOG] ?: "" }
    val downloadUrl: Flow<String> = context.updateStore.data.map { it[KEY_DOWNLOAD_URL] ?: "" }

    /** One of [STATE_DOWNLOADING], [STATE_READY], [STATE_FAILED], [STATE_UPDATE_AVAILABLE], or "" when none. */
    val state: Flow<String> = context.updateStore.data.map { it[KEY_STATE] ?: "" }
    val error: Flow<String> = context.updateStore.data.map { it[KEY_ERROR] ?: "" }
    val progress: Flow<Float> = context.updateStore.data.map { it[KEY_PROGRESS]?.toFloatOrNull() ?: 0f }
    val lastCheckAt: Flow<Long> = context.updateStore.data.map { it[KEY_LAST_CHECK_AT] ?: 0L }
    val autoCheckIntervalHours: Flow<Int> = context.updateStore.data.map {
        it[KEY_AUTO_CHECK_INTERVAL_HOURS] ?: DEFAULT_AUTO_CHECK_INTERVAL_HOURS
    }

    suspend fun setUpdateInfo(latestVersion: String, changelog: String, downloadUrl: String) {
        context.updateStore.edit { prefs ->
            prefs[KEY_LATEST_VERSION] = latestVersion
            prefs[KEY_CHANGELOG] = changelog
            prefs[KEY_DOWNLOAD_URL] = downloadUrl
            prefs[KEY_LAST_CHECK_AT] = System.currentTimeMillis()
        }
    }

    suspend fun setDownloadUrl(downloadUrl: String) {
        context.updateStore.edit { prefs ->
            prefs[KEY_DOWNLOAD_URL] = downloadUrl
        }
    }

    suspend fun setState(state: String, error: String? = null, progress: Float? = null) {
        context.updateStore.edit { prefs ->
            prefs[KEY_STATE] = state
            if (error != null) prefs[KEY_ERROR] = error else prefs.remove(KEY_ERROR)
            if (progress != null) {
                prefs[KEY_PROGRESS] = progress.toString()
            }
        }
    }

    suspend fun setProgress(progress: Float) {
        context.updateStore.edit { prefs ->
            prefs[KEY_PROGRESS] = progress.toString()
        }
    }

    suspend fun setLastCheckAt(timestamp: Long) {
        context.updateStore.edit { prefs ->
            prefs[KEY_LAST_CHECK_AT] = timestamp
        }
    }

    suspend fun setAutoCheckIntervalHours(hours: Int) {
        context.updateStore.edit { prefs ->
            prefs[KEY_AUTO_CHECK_INTERVAL_HOURS] = hours
        }
    }

    suspend fun clearState() {
        context.updateStore.edit { prefs ->
            prefs.remove(KEY_STATE)
            prefs.remove(KEY_ERROR)
            prefs.remove(KEY_PROGRESS)
            prefs.remove(KEY_LATEST_VERSION)
            prefs.remove(KEY_CHANGELOG)
            prefs.remove(KEY_DOWNLOAD_URL)
        }
    }

    suspend fun stateSnapshot(): String = state.first()
}
