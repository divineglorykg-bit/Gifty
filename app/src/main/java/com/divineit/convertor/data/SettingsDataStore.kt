package com.divineit.convertor.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by
    preferencesDataStore(name = "settings")

/** App settings. localOnly defaults to ON: no cloud processing of any kind
 *  while it is on, and Phase 2 makes no network calls at all. */
class SettingsDataStore(private val context: Context) {
    private object Keys {
        val LOCAL_ONLY = booleanPreferencesKey("local_only")
        val THEME = stringPreferencesKey("theme")
        val TEXT_SCALE = floatPreferencesKey("text_scale")
    }

    val localOnly: Flow<Boolean> = context.settingsDataStore.data
        .map { it[Keys.LOCAL_ONLY] ?: true }

    val theme: Flow<String> = context.settingsDataStore.data
        .map { it[Keys.THEME] ?: "system" }

    val textScale: Flow<Float> = context.settingsDataStore.data
        .map { it[Keys.TEXT_SCALE] ?: 1.0f }

    suspend fun setLocalOnly(value: Boolean) {
        context.settingsDataStore.edit { it[Keys.LOCAL_ONLY] = value }
    }

    suspend fun setTheme(value: String) {
        context.settingsDataStore.edit { it[Keys.THEME] = value }
    }

    suspend fun setTextScale(value: Float) {
        context.settingsDataStore.edit { it[Keys.TEXT_SCALE] = value }
    }
}
