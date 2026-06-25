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
import kotlinx.coroutines.flow.map
import java.io.File

private val Context.downloadStore: DataStore<Preferences> by preferencesDataStore(name = "download_prefs")

class DownloadPreferences(private val context: Context) {
    companion object {
        private val MAX_CONCURRENT = intPreferencesKey("max_concurrent")
        private val KEY_DOWNLOAD_FOLDER = stringPreferencesKey("download_folder")
        private val KEY_AUTO_BACKUP_INTERVAL_HOURS = intPreferencesKey("auto_backup_interval_hours")
        private val KEY_AUTO_BACKUP_SCHEDULED_AT = longPreferencesKey("auto_backup_scheduled_at")
        const val DEFAULT_MAX = 2
    }

    val maxConcurrentDownloads: Flow<Int> = context.downloadStore.data.map { prefs ->
        prefs[MAX_CONCURRENT] ?: DEFAULT_MAX
    }

    val downloadFolder: Flow<String> = context.downloadStore.data.map { prefs ->
        prefs[KEY_DOWNLOAD_FOLDER] ?: try {
            File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS), "MediaNest").absolutePath
        } catch (_: Exception) {
            File(context.getExternalFilesDir(null) ?: context.filesDir, "MediaNest").absolutePath
        }
    }

    val autoBackupIntervalHours: Flow<Int> = context.downloadStore.data.map { prefs ->
        prefs[KEY_AUTO_BACKUP_INTERVAL_HOURS] ?: 0
    }

    val autoBackupScheduledAt: Flow<Long> = context.downloadStore.data.map { prefs ->
        prefs[KEY_AUTO_BACKUP_SCHEDULED_AT] ?: 0L
    }

    suspend fun setMaxConcurrentDownloads(max: Int) {
        context.downloadStore.edit { prefs ->
            prefs[MAX_CONCURRENT] = max.coerceIn(1, 5)
        }
    }

    suspend fun setDownloadFolder(path: String) {
        context.downloadStore.edit { prefs ->
            prefs[KEY_DOWNLOAD_FOLDER] = path
        }
    }

    suspend fun setAutoBackupIntervalHours(hours: Int) {
        context.downloadStore.edit { prefs ->
            prefs[KEY_AUTO_BACKUP_INTERVAL_HOURS] = hours
            if (hours > 0) {
                prefs[KEY_AUTO_BACKUP_SCHEDULED_AT] = System.currentTimeMillis()
            } else {
                prefs[KEY_AUTO_BACKUP_SCHEDULED_AT] = 0L
            }
        }
    }
}
