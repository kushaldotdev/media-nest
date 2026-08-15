package com.example.medianest.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.subscriptionsStore: DataStore<Preferences> by preferencesDataStore(name = "subscriptions_prefs")

/**
 * Preferences for Subscriptions (feed filtering and display options).
 */
class SubscriptionsPreferences(private val context: Context) {
    companion object {
        private val KEY_SHOW_SHORTS = booleanPreferencesKey("show_shorts")
        const val DEFAULT_SHOW_SHORTS = false
    }

    val showShorts: Flow<Boolean> = context.subscriptionsStore.data.map { prefs ->
        prefs[KEY_SHOW_SHORTS] ?: DEFAULT_SHOW_SHORTS
    }

    suspend fun setShowShorts(show: Boolean) {
        context.subscriptionsStore.edit { prefs ->
            prefs[KEY_SHOW_SHORTS] = show
        }
    }
}
