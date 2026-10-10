package com.cytsai.urlclean.core

import com.cytsai.urlclean.data.FilterRule
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Host scoping. The TLD-wildcard cases are the interesting ones: `domain=shopee.*` is how the
 * shipped lists write "this brand on every country domain", and it used to match nothing.
 */
class UrlCleanerTest {

    private val wildcard = listOf(FilterRule(domains = listOf("shopee.*"), param = "pid"))

    @Test
    fun tldWildcardMatchesCountryDomain() {
        assertEquals(
            "https://shopee.tw/product/1?id=2",
            UrlCleaner.clean("https://shopee.tw/product/1?pid=x&id=2", wildcard),
        )
    }

    @Test
    fun tldWildcardMatchesSubdomain() {
        assertEquals(
            "https://s.shopee.com.br/product/1",
            UrlCleaner.clean("https://s.shopee.com.br/product/1?pid=x", wildcard),
        )
    }

    @Test
    fun tldWildcardDoesNotMatchSuffixImposter() {
        val url = "https://myshopee.tw/product/1?pid=x"

        assertEquals(url, UrlCleaner.clean(url, wildcard))
    }

    @Test
    fun plainDomainStillScopesToItselfAndSubdomains() {
        val rules = listOf(FilterRule(domains = listOf("shopee.tw"), param = "pid"))

        assertEquals("https://shopee.tw/a", UrlCleaner.clean("https://shopee.tw/a?pid=x", rules))
        assertEquals("https://s.shopee.tw/a", UrlCleaner.clean("https://s.shopee.tw/a?pid=x", rules))
        assertEquals(
            "https://shopee.com/a?pid=x",
            UrlCleaner.clean("https://shopee.com/a?pid=x", rules),
        )
    }

    /** Regex rules match `name=value`, as AdGuard does; plain rules keep matching by name. */
    @Test
    fun regexRuleMatchesNameAndValue() {
        val rules = listOf(
            FilterRule(domains = null, param = "/^utm_/"),
            FilterRule(domains = null, param = "/^id=AB/i"),
        )

        assertEquals(
            "https://a.com/?id=xy",
            UrlCleaner.clean("https://a.com/?utm_source=x&id=xy", rules),
        )
        assertEquals("https://a.com/", UrlCleaner.clean("https://a.com/?id=ab12", rules))
    }
}
