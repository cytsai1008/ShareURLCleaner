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
import com.cytsai.urlclean.data.SettingsDataStore.Companion.parseDomains
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** Whether to follow redirects to the real destination before cleaning. */
enum class AggressiveMode { OFF, SELECTED, ALL }

/** One filter list the user can tick on or off. [name] is blank for user-added lists. */
data class FilterSource(val url: String, val enabled: Boolean, val name: String = "")

class SettingsDataStore(private val context: Context) {

    companion object {
        const val DEFAULT_FILTER_URL =
            "https://raw.githubusercontent.com/AdguardTeam/FiltersRegistry/master/filters/filter_17_TrackParam/filter.txt"

        /**
         * Lists shipped with the app, all on by default. Names are product names, deliberately
         * not translated.
         *
         * Together they clean the three share links this was tested against down to zero
         * tracking params — AdGuard alone leaves 3 on Bilibili, and Facebook needs uBO. Each
         * list covers params the others miss, and overlap is de-duplicated on download.
         */
        val BUILT_IN_FILTERS = listOf(
            FilterSource(DEFAULT_FILTER_URL, enabled = true, name = "AdGuard URL Tracking"),
            FilterSource(
                "https://raw.githubusercontent.com/DandelionSprout/adfilt/master/LegitimateURLShortener.txt",
                enabled = true,
                name = "Legitimate URL Shortener",
            ),
            FilterSource(
                "https://raw.githubusercontent.com/uBlockOrigin/uAssets/master/filters/privacy.txt",
                enabled = true,
                name = "uBlock Origin Privacy",
            ),
        )

        private fun builtInName(url: String): String =
            BUILT_IN_FILTERS.firstOrNull { it.url == url }?.name ?: ""

        fun isBuiltIn(url: String): Boolean = BUILT_IN_FILTERS.any { it.url == url }

        private fun serializeSources(sources: List<FilterSource>): String =
            sources.joinToString("\n") { "${if (it.enabled) 1 else 0}\t${it.url}" }

        private fun parseSources(raw: String): List<FilterSource> = raw
            .lineSequence()
            .mapNotNull { line ->
                val parts = line.split('\t', limit = 2)
                if (parts.size != 2 || parts[1].isBlank()) return@mapNotNull null
                FilterSource(parts[1].trim(), parts[0] == "1", builtInName(parts[1].trim()))
            }
            .toList()

        /**
         * Hosts where following a redirect actually pays off: link shorteners, plus the
         * social/shopping apps that wrap shares in a tracking hop. Sorted alphabetically.
         *
         * Note the entries for whole domains (facebook.com, instagram.com, threads.com): their
         * share links live on the main domain (`/share/…`), so scoping to a subdomain would miss
         * them. The cost is a HEAD request on every link to those sites — trim them if that
         * bothers you.
         */
        val DEFAULT_AGGRESSIVE_DOMAINS = listOf(
            "3.cn", "a.co", "amzn.asia", "amzn.eu",
            "amzn.to", "b23.tv", "bit.ly", "buff.ly",
            "cutt.ly", "dlvr.it", "facebook.com", "fb.me",
            "fb.watch", "goo.gl", "ift.tt", "ig.me",
            "instagram.com", "is.gd", "l.facebook.com", "l.instagram.com",
            "l.threads.net", "lihi.cc", "lihi1.cc", "lihi2.cc",
            "lihi3.cc", "lm.facebook.com", "lnkd.in", "m.me",
            "m.tb.cn", "momo.dm", "ow.ly", "pin.it",
            "pse.is", "rb.gy", "rebrand.ly", "redd.it",
            "reurl.cc", "s.id", "s.shopee.tw", "shope.ee",
            "shorturl.at", "spoti.fi", "t.cn", "t.co",
            "t.ly", "t.snapchat.com", "tb.cn", "threads.com",
            "threads.net", "tiny.cc", "tinyurl.com", "trib.al",
            "u.jd.com", "url.cn", "v.douyin.com", "v.gd",
            "v.kuaishou.com", "vm.tiktok.com", "vt.tiktok.com", "xhslink.com",
            "z.kuaishou.com",
        ).joinToString("\n")

        private val FILTER_URL = stringPreferencesKey("filter_url")
        private val AUTO_UPDATE = booleanPreferencesKey("auto_update")
        private val LAST_UPDATED = longPreferencesKey("last_updated")
        private val RULE_COUNT = intPreferencesKey("rule_count")
        private val AGGRESSIVE_MODE = stringPreferencesKey("aggressive_mode")
        private val AGGRESSIVE_DOMAINS = stringPreferencesKey("aggressive_domains")
        private val FILTER_SOURCES = stringPreferencesKey("filter_sources")

        private val domainSeparators = Regex("""[\s,;]+""")

        /** Splits the user-editable domain list on whitespace, commas or semicolons. */
        fun parseDomains(raw: String): Set<String> = raw
            .split(domainSeparators)
            .mapNotNull { it.trim().removePrefix("*.").lowercase().ifEmpty { null } }
            .toSet()
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

    /**
     * Never empty. Falls back to the built-ins, carrying over a custom `filter_url` from the
     * single-list version of the app so an upgrade doesn't silently drop it.
     */
    val filterSources: Flow<List<FilterSource>> = context.dataStore.data.map { prefs ->
        val stored = prefs[FILTER_SOURCES]?.let { parseSources(it) }
        if (!stored.isNullOrEmpty()) return@map stored

        val legacy = prefs[FILTER_URL]
        if (legacy != null && legacy.isNotBlank() && !isBuiltIn(legacy)) {
            BUILT_IN_FILTERS + FilterSource(legacy, enabled = true)
        } else {
            BUILT_IN_FILTERS
        }
    }

    suspend fun setFilterSources(sources: List<FilterSource>) {
        context.dataStore.edit { it[FILTER_SOURCES] = serializeSources(sources) }
    }

    val aggressiveMode: Flow<AggressiveMode> = context.dataStore.data.map { prefs ->
        val stored = prefs[AGGRESSIVE_MODE]
        AggressiveMode.entries.firstOrNull { it.name == stored } ?: AggressiveMode.OFF
    }

    /** Raw, user-editable text. Use [parseDomains] to turn it into matchable hosts. */
    val aggressiveDomains: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[AGGRESSIVE_DOMAINS] ?: DEFAULT_AGGRESSIVE_DOMAINS
    }

    suspend fun setAggressiveMode(mode: AggressiveMode) {
        context.dataStore.edit { it[AGGRESSIVE_MODE] = mode.name }
    }

    suspend fun setAggressiveDomains(raw: String) {
        context.dataStore.edit { it[AGGRESSIVE_DOMAINS] = raw }
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
