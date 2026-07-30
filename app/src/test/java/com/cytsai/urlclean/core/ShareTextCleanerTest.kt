package com.cytsai.urlclean.core

import com.cytsai.urlclean.data.FilterRule
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger

class ShareTextCleanerTest {

    @Test
    fun cleanUrls_removesMatchingParamFromPlainUrl() = runBlocking {
        val result = ShareTextCleaner.cleanUrls(
            text = "https://example.com/page?utm_source=newsletter&id=123",
            rules = listOf(FilterRule(domains = null, param = "utm_source")),
        )

        assertEquals("https://example.com/page?id=123", result.text)
        assertTrue(result.foundUrl)
        assertTrue(result.cleaned)
    }

    @Test
    fun cleanUrls_removesMatchingParamFromUrlInsideCaption() = runBlocking {
        val result = ShareTextCleaner.cleanUrls(
            text = "Look https://example.com/page?utm_source=newsletter&id=123 for details",
            rules = listOf(FilterRule(domains = null, param = "utm_source")),
        )

        assertEquals("Look https://example.com/page?id=123 for details", result.text)
        assertTrue(result.foundUrl)
        assertTrue(result.cleaned)
    }

    @Test
    fun cleanUrls_reportsNoUrl() = runBlocking {
        val result = ShareTextCleaner.cleanUrls(
            text = "just a caption",
            rules = listOf(FilterRule(domains = null, param = "utm_source")),
        )

        assertEquals("just a caption", result.text)
        assertFalse(result.foundUrl)
        assertFalse(result.cleaned)
    }

    @Test
    fun cleanUrls_keepsTextWhenRulesAreEmpty() = runBlocking {
        val text = "https://example.com/page?utm_source=newsletter&id=123"

        val result = ShareTextCleaner.cleanUrls(text = text, rules = emptyList())

        assertEquals(text, result.text)
        assertTrue(result.foundUrl)
        assertFalse(result.cleaned)
    }

    @Test
    fun cleanUrls_cleansResolvedUrlWhenRedirected() = runBlocking {
        val result = ShareTextCleaner.cleanUrls(
            text = "See https://bit.ly/abc for details",
            rules = listOf(FilterRule(domains = null, param = "utm_source")),
            resolve = { "https://example.com/page?utm_source=twitter&id=1" },
        )

        assertEquals("See https://example.com/page?id=1 for details", result.text)
        assertTrue(result.cleaned)
    }

    @Test
    fun cleanUrls_keepsCleanedUrlWhenResolverDeclines() = runBlocking {
        val result = ShareTextCleaner.cleanUrls(
            text = "https://example.com/page?utm_source=x&id=1",
            rules = listOf(FilterRule(domains = null, param = "utm_source")),
            resolve = { null },
        )

        assertEquals("https://example.com/page?id=1", result.text)
        assertTrue(result.cleaned)
    }

    @Test
    fun cleanUrls_resolverSeesAlreadyCleanedUrl() = runBlocking {
        var seen: String? = null
        ShareTextCleaner.cleanUrls(
            text = "https://example.com/page?utm_source=x&id=1",
            rules = listOf(FilterRule(domains = null, param = "utm_source")),
            resolve = { seen = it; null },
        )

        assertEquals("https://example.com/page?id=1", seen)
    }

    /** A shared caption regularly carries several links; every one of them gets cleaned. */
    @Test
    fun cleanUrls_cleansEveryUrlInTheText() = runBlocking {
        val result = ShareTextCleaner.cleanUrls(
            text = "https://a.example/1?utm_source=x\nsth https://b23.tv/abc and https://c.example/3",
            rules = listOf(FilterRule(domains = null, param = "utm_source")),
            resolve = { if (it == "https://b23.tv/abc") "https://bilibili.com/v/1?utm_source=y" else null },
        )

        assertEquals(
            "https://a.example/1\nsth https://bilibili.com/v/1 and https://c.example/3",
            result.text,
        )
        assertTrue(result.cleaned)
    }

    /**
     * Two links, each blocking until the other has started. Sequential resolution deadlocks and
     * the test times out; concurrent resolution passes.
     */
    @Test(timeout = 5_000)
    fun cleanUrls_resolvesUrlsConcurrently() = runBlocking {
        val bothStarted = CountDownLatch(2)

        val result = ShareTextCleaner.cleanUrls(
            text = "https://a.example/1 https://b.example/2",
            rules = emptyList(),
            resolve = {
                bothStarted.countDown()
                bothStarted.await()
                "$it?resolved"
            },
        )

        assertEquals("https://a.example/1?resolved https://b.example/2?resolved", result.text)
    }

    /** The same link twice in one caption is one network call, not two. */
    @Test
    fun cleanUrls_resolvesEachDistinctUrlOnce() = runBlocking {
        val calls = AtomicInteger()

        ShareTextCleaner.cleanUrls(
            text = "https://bit.ly/x and again https://bit.ly/x",
            rules = emptyList(),
            resolve = { calls.incrementAndGet(); null },
        )

        assertEquals(1, calls.get())
    }

    @Test
    fun cleanUrls_keepsUrlWhenNoRuleMatches() = runBlocking {
        val text = "https://example.com/page?utm_source=newsletter&id=123"

        val result = ShareTextCleaner.cleanUrls(
            text = text,
            rules = listOf(FilterRule(domains = listOf("other.example"), param = "utm_source")),
        )

        assertEquals(text, result.text)
        assertTrue(result.foundUrl)
        assertFalse(result.cleaned)
    }
}
