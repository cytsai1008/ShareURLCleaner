package com.cytsai.urlclean.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsDataStore(private val context: Context) {

    companion object {
        const val DEFAULT_FILTER_URL =
            "https://raw.githubusercontent.com/AdguardTeam/FiltersRegistry/master/filters/filter_17_TrackParam/filter.txt"

        private val FILTER_URL = stringPreferencesKey("filter_url")
        private val AUTO_UPDATE = booleanPreferencesKey("auto_update")
        private val LAST_UPDATED = longPreferencesKey("last_updated")
        private val RULE_COUNT = intPreferencesKey("rule_count")
    }

    val filterUrl: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[FILTER_URL] ?: DEFAULT_FILTER_URL
    }

    val autoUpdate: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[AUTO_UPDATE] ?: true
    }

    val lastUpdated: Flow<Long> = context.dataStore.data.map { prefs ->
        prefs[LAST_UPDATED] ?: 0L
    }

    val ruleCount: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[RULE_COUNT] ?: 0
    }

    suspend fun setFilterUrl(url: String) {
        context.dataStore.edit { it[FILTER_URL] = url }
    }

    suspend fun setAutoUpdate(enabled: Boolean) {
        context.dataStore.edit { it[AUTO_UPDATE] = enabled }
    }

    suspend fun setLastUpdated(ts: Long) {
        context.dataStore.edit { it[LAST_UPDATED] = ts }
    }

    suspend fun setRuleCount(count: Int) {
        context.dataStore.edit { it[RULE_COUNT] = count }
    }
}
