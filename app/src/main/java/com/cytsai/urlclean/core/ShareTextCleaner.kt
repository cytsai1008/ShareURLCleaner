package com.cytsai.urlclean.core

import com.cytsai.urlclean.data.FilterRule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

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
     * Every distinct URL is resolved concurrently, so a caption with three shorteners waits for
     * the slowest one rather than the sum of all three.
     *
     * @param resolve optional aggressive-mode redirect resolver. When supplied, each cleaned URL is
     * followed to its destination and the destination is cleaned in turn. Blocking — it is called
     * on [Dispatchers.IO]. Return null to skip (not applicable / failed).
     */
    suspend fun cleanUrls(
        text: String,
        rules: List<FilterRule>,
        resolve: ((String) -> String?)? = null,
    ): Result = coroutineScope {
        val cleanedUrls = httpUrlRegex.findAll(text)
            .map { it.value }
            .distinct()
            .associateWith { UrlCleaner.clean(it, rules) }

        // Keyed by the cleaned URL, so the same link twice in one caption costs one fetch.
        val resolved: Map<String, String?> = if (resolve == null) {
            emptyMap()
        } else {
            cleanedUrls.values.distinct()
                .map { url -> async(Dispatchers.IO) { url to resolve(url) } }
                .awaitAll()
                .toMap()
        }

        var foundUrl = false
        val cleanedText = httpUrlRegex.replace(text) { match ->
            foundUrl = true
            val cleaned = cleanedUrls.getValue(match.value)
            val destination = resolved[cleaned]
            if (destination != null && destination != cleaned) {
                UrlCleaner.clean(destination, rules)
            } else {
                cleaned
            }
        }

        Result(
            text = cleanedText,
            foundUrl = foundUrl,
            cleaned = cleanedText != text,
        )
    }
}
