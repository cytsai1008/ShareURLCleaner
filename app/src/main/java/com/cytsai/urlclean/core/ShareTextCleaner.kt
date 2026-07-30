package com.cytsai.urlclean.core

import com.cytsai.urlclean.data.FilterRule

object ShareTextCleaner {

    private val httpUrlRegex = Regex("""https?://\S+""")

    data class Result(
        val text: String,
        val foundUrl: Boolean,
        val cleaned: Boolean,
    )

    /** Whether there is anything here worth cleaning, without doing the work. */
    fun hasUrl(text: String): Boolean = httpUrlRegex.containsMatchIn(text)

    /**
     * Cleans every URL in [text] — a shared caption often carries several, and the one the user
     * cares about is not always the first.
     *
     * @param resolve optional aggressive-mode redirect resolver. When supplied, each cleaned URL is
     * followed to its destination and the destination is cleaned in turn. Blocking — the caller is
     * responsible for running this off the main thread. Return null to skip (not applicable / failed).
     */
    fun cleanUrls(
        text: String,
        rules: List<FilterRule>,
        resolve: ((String) -> String?)? = null,
    ): Result {
        var foundUrl = false
        // ponytail: URLs are resolved one after another. Parallelising would need a scope in
        // here; worth it only if sharing captions with several shortened links gets slow.
        val cleanedText = httpUrlRegex.replace(text) { match ->
            foundUrl = true
            cleanUrl(match.value, rules, resolve)
        }

        return Result(
            text = cleanedText,
            foundUrl = foundUrl,
            cleaned = cleanedText != text,
        )
    }

    private fun cleanUrl(
        url: String,
        rules: List<FilterRule>,
        resolve: ((String) -> String?)?,
    ): String {
        val cleaned = UrlCleaner.clean(url, rules)
        val resolved = resolve?.invoke(cleaned) ?: return cleaned
        return if (resolved != cleaned) UrlCleaner.clean(resolved, rules) else cleaned
    }
}
