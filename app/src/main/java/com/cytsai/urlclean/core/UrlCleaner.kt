package com.cytsai.urlclean.core

import com.cytsai.urlclean.data.FilterRule
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

object UrlCleaner {

    fun clean(rawUrl: String, rules: List<FilterRule>): String {
        val httpUrl = rawUrl.toHttpUrlOrNull() ?: return rawUrl
        val host = httpUrl.host.lowercase()

        val paramsToRemove = buildSet {
            for (rule in rules) {
                if (rule.domain == null || host == rule.domain || host.endsWith(".${rule.domain}")) {
                    add(rule.param.lowercase())
                }
            }
        }

        if (paramsToRemove.isEmpty()) return rawUrl

        val keptParams = (0 until httpUrl.querySize)
            .map { httpUrl.queryParameterName(it) to httpUrl.queryParameterValue(it) }
            .filter { (name, _) -> name.lowercase() !in paramsToRemove }

        if (keptParams.size == httpUrl.querySize) return rawUrl

        val builder = httpUrl.newBuilder()
        builder.query(null)
        keptParams.forEach { (name, value) -> builder.addQueryParameter(name, value) }

        return builder.build().toString()
    }
}
