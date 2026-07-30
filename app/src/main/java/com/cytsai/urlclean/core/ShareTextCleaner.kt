package com.cytsai.urlclean.core

import com.cytsai.urlclean.data.FilterRule

object ShareTextCleaner {

    private val httpUrlRegex = Regex("""https?://\S+""")

    data class Result(
        val text: String,
        val foundUrl: Boolean,
        val cleaned: Boolean,
    )

    /**
     * @param resolve optional aggressive-mode redirect resolver. When supplied, the cleaned URL is
     * followed to its destination and the destination is cleaned in turn. Blocking — the caller is
     * responsible for running this off the main thread. Return null to skip (not applicable / failed).
     */
    fun cleanFirstUrl(
        text: String,
        rules: List<FilterRule>,
        resolve: ((String) -> String?)? = null,
    ): Result {
        val urlMatch = httpUrlRegex.find(text) ?: return Result(
            text = text,
            foundUrl = false,
            cleaned = false,
        )

        var cleanedUrl = UrlCleaner.clean(urlMatch.value, rules)
        resolve?.invoke(cleanedUrl)?.let { resolved ->
            if (resolved != cleanedUrl) cleanedUrl = UrlCleaner.clean(resolved, rules)
        }

        val cleanedText = text.replaceRange(urlMatch.range, cleanedUrl)

        return Result(
            text = cleanedText,
            foundUrl = true,
            cleaned = cleanedText != text,
        )
    }
}
