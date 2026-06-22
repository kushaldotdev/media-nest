package com.example.medianest.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.syncStore: DataStore<Preferences> by preferencesDataStore(name = "sync_prefs")

class DevicePreferences(private val context: Context) {
    companion object {
        private val KEY_SERVER_URL = stringPreferencesKey("server_url")
        private val KEY_API_KEY = stringPreferencesKey("api_key")
        private val KEY_DEVICE_ID = stringPreferencesKey("device_id")
        private val KEY_LAST_SYNC_VERSION = stringPreferencesKey("last_sync_version")
        private val KEY_LAST_SYNC_AT = stringPreferencesKey("last_sync_at")
        private val KEY_SYNC_INTERVAL_HOURS = stringPreferencesKey("sync_interval_hours")
    }

    val serverUrl: Flow<String> = context.syncStore.data.map { it[KEY_SERVER_URL] ?: "" }
    val apiKey: Flow<String> = context.syncStore.data.map { it[KEY_API_KEY] ?: "" }
    val deviceId: Flow<String> = context.syncStore.data.map { it[KEY_DEVICE_ID] ?: "" }
    val lastSyncVersion: Flow<Long> = context.syncStore.data.map { it[KEY_LAST_SYNC_VERSION]?.toLongOrNull() ?: 0L }
    val lastSyncAt: Flow<Long> = context.syncStore.data.map { it[KEY_LAST_SYNC_AT]?.toLongOrNull() ?: 0L }
    val syncIntervalHours: Flow<Int> = context.syncStore.data.map { it[KEY_SYNC_INTERVAL_HOURS]?.toIntOrNull() ?: 6 }

    suspend fun setServerUrl(url: String) { context.syncStore.edit { it[KEY_SERVER_URL] = url } }
    suspend fun setApiKey(key: String) { context.syncStore.edit { it[KEY_API_KEY] = key } }
    suspend fun setDeviceId(id: String) { context.syncStore.edit { it[KEY_DEVICE_ID] = id } }
    suspend fun setLastSyncVersion(version: Long) { context.syncStore.edit { it[KEY_LAST_SYNC_VERSION] = version.toString() } }
    suspend fun setLastSyncAt(timestamp: Long) { context.syncStore.edit { it[KEY_LAST_SYNC_AT] = timestamp.toString() } }
    suspend fun setSyncIntervalHours(hours: Int) { context.syncStore.edit { it[KEY_SYNC_INTERVAL_HOURS] = hours.toString() } }

    suspend fun clear() {
        context.syncStore.edit { it.clear() }
    }
}
