package com.cytsai.urlclean.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/** [domains] is null for a global rule; otherwise the hosts (and their subdomains) it applies to. */
data class FilterRule(val domains: List<String>?, val param: String)

class FilterRepository(private val context: Context) {

    companion object {
        private val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

        private const val RULES_FILE = "filter_rules.txt"
        private const val RULES_TMP = "filter_rules.tmp"

        private val GITHUB_HOSTS = setOf(
            "github.com",
            "raw.githubusercontent.com",
            "gist.githubusercontent.com",
            "objects.githubusercontent.com",
        )

        /** Above this many GitHub-hosted lists in one run, space the requests out. */
        private const val GITHUB_COOLDOWN_THRESHOLD = 3
        private const val GITHUB_COOLDOWN_MS = 1_500L

        /** Backoff after GitHub actually says no (429, or 403 with a rate-limit body). */
        private const val RATE_LIMITED_BACKOFF_MS = 5_000L

        private fun isGitHub(url: String): Boolean {
            val host = url.toHttpUrlOrNull()?.host?.lowercase() ?: return false
            return host in GITHUB_HOSTS || host.endsWith(".githubusercontent.com")
        }

        /**
         * Parses one AdGuard `$removeparam` rule into a [FilterRule], or null if it is a
         * comment, an exception, or a form this app does not support.
         *
         * A rule is `pattern$option,option,option`. Scope can arrive three different ways and
         * all three must be honoured — anything treated as global gets stripped from every site
         * the user ever shares:
         *
         * ```
         * ||facebook.com^$removeparam=rdid                → facebook.com
         * $removeparam=id,domain=skimlinks.com|hanes.com  → those two hosts
         * ://www.bilibili.com/video/$removeparam=mid      → www.bilibili.com
         * ```
         *
         * `removeparam` is not always the first option (`$doc,removeparam=ref`), so the option
         * list is split before looking for it rather than string-matching `"\$removeparam="`.
         */
        internal fun parseLine(line: String): FilterRule? {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith('!') || trimmed.startsWith("@@")) return null

            val optionsStart = trimmed.lastIndexOf('$')
            if (optionsStart == -1) return null
            val pattern = trimmed.substring(0, optionsStart)
            val options = trimmed.substring(optionsStart + 1).split(',')

            val paramOption = options.firstOrNull {
                it == "removeparam" || it.startsWith("removeparam=")
            } ?: return null
            val param = paramOption.substringAfter('=', "").trim()
            // Bare `$removeparam` (strip everything), regex params and inverted params are all
            // out of scope — better to skip than to guess.
            if (param.isEmpty() || param.startsWith('/') || param.startsWith('~')) return null

            val domainOption = options.firstOrNull { it.startsWith("domain=") }
            if (domainOption != null) {
                val domains = domainOption.removePrefix("domain=")
                    .split('|')
                    // `~host` excludes a host. A list of nothing but exclusions means "everywhere
                    // but these", which is a global rule as far as this app is concerned.
                    .filter { it.isNotBlank() && !it.startsWith('~') }
                    .map { it.trim().lowercase() }
                return FilterRule(domains.ifEmpty { null }, param)
            }

            hostFromPattern(pattern)?.let { return FilterRule(listOf(it), param) }

            // No host anywhere. An empty pattern (`$removeparam=fbclid`) really is global, but a
            // non-empty one scopes by URL substring (`ref=shadcn.com^$removeparam=ref`) — a match
            // mode this app doesn't implement. Skipping is the conservative read: applying it
            // globally would strip `ref` from every site to satisfy a rule about one blog.
            if (pattern.isNotEmpty()) return null

            return FilterRule(null, param)
        }

        /** The host a rule pattern is anchored to, or null when it matches any host. */
        private fun hostFromPattern(pattern: String): String? {
            val afterAnchor = when {
                pattern.startsWith("||") -> pattern.substring(2)
                pattern.contains("://") -> pattern.substringAfter("://")
                else -> return null
            }
            return afterAnchor
                .takeWhile { it != '^' && it != '/' && it != '*' && it != '?' }
                .lowercase()
                .ifEmpty { null }
        }
    }

    /**
     * Downloads every [urls] entry and writes the merged, de-duplicated rule set.
     *
     * All-or-nothing on purpose: a single failed list aborts the run and leaves the previous
     * `filter_rules.txt` in place, rather than silently shrinking the user's protection.
     */
    suspend fun downloadAndUpdate(urls: List<String>): Result<Int> = withContext(Dispatchers.IO) {
        if (urls.isEmpty()) return@withContext Result.failure(IOException("No filter lists enabled"))
        try {
            val gitHubCount = urls.count { isGitHub(it) }
            val spaceOutGitHub = gitHubCount > GITHUB_COOLDOWN_THRESHOLD
            var gitHubFetched = 0

            val rules = LinkedHashSet<FilterRule>()
            for (url in urls) {
                if (isGitHub(url)) {
                    if (spaceOutGitHub && gitHubFetched > 0) delay(GITHUB_COOLDOWN_MS)
                    gitHubFetched++
                }
                fetch(url).lineSequence().mapNotNullTo(rules) { parseLine(it) }
            }

            val tmpFile = File(context.filesDir, RULES_TMP)
            tmpFile.bufferedWriter().use { writer ->
                rules.forEach { rule ->
                    // `a.com|b.com<TAB>param`, or bare `param` when global. A file written by
                    // an older build has one domain and no '|', so it still reads back correctly.
                    if (rule.domains != null) {
                        writer.write("${rule.domains.joinToString("|")}\t${rule.param}\n")
                    } else {
                        writer.write("${rule.param}\n")
                    }
                }
            }
            tmpFile.renameTo(File(context.filesDir, RULES_FILE))

            Result.success(rules.size)
        } catch (e: IOException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Retries once after a pause when GitHub reports rate limiting. */
    private suspend fun fetch(url: String): String {
        repeat(2) { attempt ->
            val response = client.newCall(Request.Builder().url(url).build()).execute()
            response.use {
                if (it.isSuccessful) return it.body.string()
                val rateLimited = it.code == 429 || (it.code == 403 && isGitHub(url))
                if (!rateLimited || attempt == 1) throw IOException("HTTP ${it.code} for $url")
            }
            delay(RATE_LIMITED_BACKOFF_MS)
        }
        throw IOException("Rate limited: $url")
    }

    fun loadRules(): List<FilterRule> {
        val file = File(context.filesDir, RULES_FILE)
        if (!file.exists()) return emptyList()
        return file.bufferedReader().lineSequence()
            .mapNotNull { line ->
                val parts = line.split('\t', limit = 2)
                when (parts.size) {
                    1 -> if (parts[0].isNotBlank()) FilterRule(null, parts[0]) else null
                    2 -> FilterRule(parts[0].split('|'), parts[1])
                    else -> null
                }
            }
            .toList()
    }
}
