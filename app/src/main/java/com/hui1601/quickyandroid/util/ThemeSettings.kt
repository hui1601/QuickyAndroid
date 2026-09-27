package com.hui1601.quickyandroid.util

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "theme_settings")

class ThemeSettings(private val context: Context) {

    private val darkModeKey = booleanPreferencesKey("dark_mode")
    private val followSystemKey = booleanPreferencesKey("follow_system")
    private val dynamicColorKey = booleanPreferencesKey("dynamic_color")

    val darkMode: Flow<Boolean?> = context.dataStore.data.map { preferences ->
        preferences[darkModeKey]
    }

    val followSystem: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[followSystemKey] ?: true
    }

    val dynamicColor: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[dynamicColorKey] ?: false
    }

    suspend fun setDarkMode(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[darkModeKey] = enabled
            preferences[followSystemKey] = false
        }
    }

    suspend fun setFollowSystem(follow: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[followSystemKey] = follow
        }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[dynamicColorKey] = enabled
        }
    }
}
