package com.cytsai.urlclean.core

import com.cytsai.urlclean.data.FilterRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShareTextCleanerTest {

    @Test
    fun cleanFirstUrl_removesMatchingParamFromPlainUrl() {
        val result = ShareTextCleaner.cleanFirstUrl(
            text = "https://example.com/page?utm_source=newsletter&id=123",
            rules = listOf(FilterRule(domains = null, param = "utm_source")),
        )

        assertEquals("https://example.com/page?id=123", result.text)
        assertTrue(result.foundUrl)
        assertTrue(result.cleaned)
    }

    @Test
    fun cleanFirstUrl_removesMatchingParamFromUrlInsideCaption() {
        val result = ShareTextCleaner.cleanFirstUrl(
            text = "Look https://example.com/page?utm_source=newsletter&id=123 for details",
            rules = listOf(FilterRule(domains = null, param = "utm_source")),
        )

        assertEquals("Look https://example.com/page?id=123 for details", result.text)
        assertTrue(result.foundUrl)
        assertTrue(result.cleaned)
    }

    @Test
    fun cleanFirstUrl_reportsNoUrl() {
        val result = ShareTextCleaner.cleanFirstUrl(
            text = "just a caption",
            rules = listOf(FilterRule(domains = null, param = "utm_source")),
        )

        assertEquals("just a caption", result.text)
        assertFalse(result.foundUrl)
        assertFalse(result.cleaned)
    }

    @Test
    fun cleanFirstUrl_keepsTextWhenRulesAreEmpty() {
        val text = "https://example.com/page?utm_source=newsletter&id=123"

        val result = ShareTextCleaner.cleanFirstUrl(text = text, rules = emptyList())

        assertEquals(text, result.text)
        assertTrue(result.foundUrl)
        assertFalse(result.cleaned)
    }

    @Test
    fun cleanFirstUrl_cleansResolvedUrlWhenRedirected() {
        val result = ShareTextCleaner.cleanFirstUrl(
            text = "See https://bit.ly/abc for details",
            rules = listOf(FilterRule(domains = null, param = "utm_source")),
            resolve = { "https://example.com/page?utm_source=twitter&id=1" },
        )

        assertEquals("See https://example.com/page?id=1 for details", result.text)
        assertTrue(result.cleaned)
    }

    @Test
    fun cleanFirstUrl_keepsCleanedUrlWhenResolverDeclines() {
        val result = ShareTextCleaner.cleanFirstUrl(
            text = "https://example.com/page?utm_source=x&id=1",
            rules = listOf(FilterRule(domains = null, param = "utm_source")),
            resolve = { null },
        )

        assertEquals("https://example.com/page?id=1", result.text)
        assertTrue(result.cleaned)
    }

    @Test
    fun cleanFirstUrl_resolverSeesAlreadyCleanedUrl() {
        var seen: String? = null
        ShareTextCleaner.cleanFirstUrl(
            text = "https://example.com/page?utm_source=x&id=1",
            rules = listOf(FilterRule(domains = null, param = "utm_source")),
            resolve = { seen = it; null },
        )

        assertEquals("https://example.com/page?id=1", seen)
    }

    @Test
    fun cleanFirstUrl_keepsUrlWhenNoRuleMatches() {
        val text = "https://example.com/page?utm_source=newsletter&id=123"

        val result = ShareTextCleaner.cleanFirstUrl(
            text = text,
            rules = listOf(FilterRule(domains = listOf("other.example"), param = "utm_source")),
        )

        assertEquals(text, result.text)
        assertTrue(result.foundUrl)
        assertFalse(result.cleaned)
    }
}
