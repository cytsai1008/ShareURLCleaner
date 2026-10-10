package com.cytsai.urlclean.core

import com.cytsai.urlclean.data.FilterRule
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

object UrlCleaner {

    fun clean(rawUrl: String, rules: List<FilterRule>): String {
        val httpUrl = rawUrl.toHttpUrlOrNull() ?: return rawUrl
        val host = httpUrl.host.lowercase()

        val paramsToRemove = mutableSetOf<String>()
        val patternsToRemove = mutableListOf<Regex>()
        for (rule in rules) {
            val scoped = rule.domains
            if (scoped == null || scoped.any { hostMatches(host, it) }) {
                val regex = rule.regex
                if (regex != null) patternsToRemove += regex else paramsToRemove += rule.param.lowercase()
            }
        }

        if (paramsToRemove.isEmpty() && patternsToRemove.isEmpty()) return rawUrl

        val keptParams = (0 until httpUrl.querySize)
            .map { httpUrl.queryParameterName(it) to httpUrl.queryParameterValue(it) }
            .filter { (name, value) ->
                name.lowercase() !in paramsToRemove &&
                    patternsToRemove.none { it.containsMatchIn("$name=${value.orEmpty()}") }
            }

        if (keptParams.size == httpUrl.querySize) return rawUrl

        val builder = httpUrl.newBuilder()
        builder.query(null)
        keptParams.forEach { (name, value) -> builder.addQueryParameter(name, value) }

        return builder.build().toString()
    }

    /**
     * Whether [host] falls under a rule's `domain=` entry, including AdGuard's TLD wildcard
     * (`shopee.*` — the same brand on every country domain). Over 100 rules in the shipped
     * lists are written that way, and all of them used to match nothing.
     */
    private fun hostMatches(host: String, domain: String): Boolean =
        if (domain.endsWith(".*")) {
            // "shopee." — matches shopee.tw and s.shopee.tw, not myshopee.tw.
            val base = domain.dropLast(1)
            host.startsWith(base) || host.contains(".$base")
        } else {
            host == domain || host.endsWith(".$domain")
        }
}
