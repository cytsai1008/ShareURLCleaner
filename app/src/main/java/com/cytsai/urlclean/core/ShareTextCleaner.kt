package com.cytsai.urlclean.core

import com.cytsai.urlclean.data.FilterRule

object ShareTextCleaner {

    private val httpUrlRegex = Regex("""https?://\S+""")

    data class Result(
        val text: String,
        val foundUrl: Boolean,
        val cleaned: Boolean,
    )

    fun cleanFirstUrl(text: String, rules: List<FilterRule>): Result {
        val urlMatch = httpUrlRegex.find(text) ?: return Result(
            text = text,
            foundUrl = false,
            cleaned = false,
        )

        if (rules.isEmpty()) {
            return Result(
                text = text,
                foundUrl = true,
                cleaned = false,
            )
        }

        val cleanedUrl = UrlCleaner.clean(urlMatch.value, rules)
        val cleanedText = text.replaceRange(urlMatch.range, cleanedUrl)

        return Result(
            text = cleanedText,
            foundUrl = true,
            cleaned = cleanedText != text,
        )
    }
}
