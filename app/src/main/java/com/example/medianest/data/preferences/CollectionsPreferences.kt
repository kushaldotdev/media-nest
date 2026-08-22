package com.example.medianest.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.collectionsStore: DataStore<Preferences> by preferencesDataStore(name = "collections_prefs")

/**
 * Preferences for Collections hub (view mode and per-tab sorting state).
 *
 * Reconciliation Note:
 * DevicePreferences historically stored `libraryViewMode` in sync_prefs (VPS sync DataStore).
 * CollectionsPreferences.viewMode is now the dedicated preference for the redesigned Collections
 * Hub stored within collections_prefs. DevicePreferences.libraryViewMode is preserved for backward
 * compatibility until full migration of legacy screens.
 */
@Suppress("PropertyName", "FunctionNaming")
class CollectionsPreferences(private val context: Context) {
    companion object {
        private val KEY_VIEW_MODE = stringPreferencesKey("view_mode")
        private val KEY_SORT_MODE_HISTORY = stringPreferencesKey("sort_mode_history")
        private val KEY_SORT_MODE_WATCHED = stringPreferencesKey("sort_mode_watched")
        private val KEY_SORT_MODE_FOLDERS = stringPreferencesKey("sort_mode_folders")
        private val KEY_SORT_MODE_FAVORITES = stringPreferencesKey("sort_mode_favorites")
        private val KEY_SORT_MODE_PLAYLISTS = stringPreferencesKey("sort_mode_playlists")
        private val KEY_SORT_MODE_CHANNELS = stringPreferencesKey("sort_mode_channels")
        private val KEY_FULL_TITLES = booleanPreferencesKey("full_titles")

        const val LIST_HOME = "home"
        const val LIST_HOME_LINKS = "home_links"
        const val LIST_HISTORY = "history"
        const val LIST_FAVORITES = "favorites"
        const val LIST_WATCHED = "watched"
        const val LIST_FOLDERS = "folders"
        const val LIST_PLAYLISTS = "playlists"
        const val LIST_CHANNELS = "channels"
        const val LIST_DOWNLOADS = "downloads"
        const val LIST_QUEUE = "queue"

        const val DEFAULT_VIEW_MODE = "GRID"
        const val DEFAULT_SORT_MODE_HISTORY = "DATE_DESC"
        const val DEFAULT_SORT_MODE_WATCHED = "DATE_DESC"
        const val DEFAULT_SORT_MODE_FOLDERS = "NAME_ASC"
        const val DEFAULT_SORT_MODE_FAVORITES = "DATE_DESC"
        const val DEFAULT_SORT_MODE_PLAYLISTS = "DATE_DESC"
        const val DEFAULT_SORT_MODE_CHANNELS = "NAME_ASC"
        const val DEFAULT_FULL_TITLES = false
    }

    val viewMode: Flow<String> = context.collectionsStore.data.map { prefs ->
        prefs[KEY_VIEW_MODE] ?: DEFAULT_VIEW_MODE
    }
    val viewModeFlow: Flow<String> get() = viewMode


    val sortMode_history: Flow<String> = context.collectionsStore.data.map { prefs ->
        prefs[KEY_SORT_MODE_HISTORY] ?: DEFAULT_SORT_MODE_HISTORY
    }
    val sortModeHistory: Flow<String> get() = sortMode_history

    val sortMode_watched: Flow<String> = context.collectionsStore.data.map { prefs ->
        prefs[KEY_SORT_MODE_WATCHED] ?: DEFAULT_SORT_MODE_WATCHED
    }
    val sortModeWatched: Flow<String> get() = sortMode_watched

    val sortMode_folders: Flow<String> = context.collectionsStore.data.map { prefs ->
        prefs[KEY_SORT_MODE_FOLDERS] ?: DEFAULT_SORT_MODE_FOLDERS
    }
    val sortModeFolders: Flow<String> get() = sortMode_folders

    val sortMode_favorites: Flow<String> = context.collectionsStore.data.map { prefs ->
        prefs[KEY_SORT_MODE_FAVORITES] ?: DEFAULT_SORT_MODE_FAVORITES
    }
    val sortModeFavorites: Flow<String> get() = sortMode_favorites

    val sortMode_playlists: Flow<String> = context.collectionsStore.data.map { prefs ->
        prefs[KEY_SORT_MODE_PLAYLISTS] ?: DEFAULT_SORT_MODE_PLAYLISTS
    }
    val sortModePlaylists: Flow<String> get() = sortMode_playlists

    val sortMode_channels: Flow<String> = context.collectionsStore.data.map { prefs ->
        prefs[KEY_SORT_MODE_CHANNELS] ?: DEFAULT_SORT_MODE_CHANNELS
    }
    val sortModeChannels: Flow<String> get() = sortMode_channels

    val fullTitles: Flow<Boolean> = context.collectionsStore.data.map { prefs ->
        prefs[KEY_FULL_TITLES] ?: DEFAULT_FULL_TITLES
    }

    suspend fun setFullTitles(enabled: Boolean) {
        context.collectionsStore.edit { prefs ->
            prefs[KEY_FULL_TITLES] = enabled
        }
    }

    /**
     * Returns the current global default. List toggles are intentionally transient UI state;
     * leaving a list must not persist an override that can mask the Settings value.
     */
    fun fullTitlesFor(listKey: String): Flow<Boolean> = fullTitles

    /** Kept for source compatibility; per-list title choices are not persisted. */
    suspend fun setFullTitlesFor(listKey: String, enabled: Boolean) = Unit

    suspend fun setViewMode(mode: String) {
        context.collectionsStore.edit { prefs ->
            prefs[KEY_VIEW_MODE] = mode
        }
    }

    suspend fun setSortModeHistory(mode: String) {
        context.collectionsStore.edit { prefs ->
            prefs[KEY_SORT_MODE_HISTORY] = mode
        }
    }

    suspend fun setSortMode_history(mode: String) = setSortModeHistory(mode)

    suspend fun setSortModeWatched(mode: String) {
        context.collectionsStore.edit { prefs ->
            prefs[KEY_SORT_MODE_WATCHED] = mode
        }
    }

    suspend fun setSortMode_watched(mode: String) = setSortModeWatched(mode)

    suspend fun setSortModeFolders(mode: String) {
        context.collectionsStore.edit { prefs ->
            prefs[KEY_SORT_MODE_FOLDERS] = mode
        }
    }

    suspend fun setSortMode_folders(mode: String) = setSortModeFolders(mode)

    suspend fun setSortModeFavorites(mode: String) {
        context.collectionsStore.edit { prefs ->
            prefs[KEY_SORT_MODE_FAVORITES] = mode
        }
    }

    suspend fun setSortMode_favorites(mode: String) = setSortModeFavorites(mode)

    suspend fun setSortModePlaylists(mode: String) {
        context.collectionsStore.edit { prefs ->
            prefs[KEY_SORT_MODE_PLAYLISTS] = mode
        }
    }

    suspend fun setSortMode_playlists(mode: String) = setSortModePlaylists(mode)

    suspend fun setSortModeChannels(mode: String) {
        context.collectionsStore.edit { prefs ->
            prefs[KEY_SORT_MODE_CHANNELS] = mode
        }
    }

    suspend fun setSortMode_channels(mode: String) = setSortModeChannels(mode)
}
