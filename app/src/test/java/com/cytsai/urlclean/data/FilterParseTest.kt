package com.cytsai.urlclean.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Every case here is a real line from AdGuard filter 17, uBO privacy.txt or
 * LegitimateURLShortener. The scoped ones used to parse as global, which stripped their params
 * from every site the user shared — that is what these guard against.
 */
class FilterParseTest {

    private fun parse(line: String) = FilterRepository.parseLine(line)

    @Test
    fun ignoresCommentsBlanksAndExceptions() {
        assertNull(parse("! Title: AdGuard URL Tracking filter"))
        assertNull(parse(""))
        assertNull(parse("   "))
        assertNull(parse("@@||example.com^\$removeparam=utm_source"))
    }

    @Test
    fun ignoresLinesWithoutRemoveparam() {
        assertNull(parse("||ads.example.com^\$third-party"))
        assertNull(parse("||example.com^"))
    }

    @Test
    fun ignoresBareRemoveparamThatWouldStripEverything() {
        assertNull(parse("||example.com^\$removeparam"))
    }

    @Test
    fun ignoresInvertedParams() {
        assertNull(parse("||example.com^\$removeparam=~keep"))
    }

    @Test
    fun readsRegexParam() {
        assertEquals(
            FilterRule(listOf("example.com"), "/^utm_/"),
            parse("||example.com^\$removeparam=/^utm_/"),
        )
    }

    /** Real lines: AdGuard escapes `,` and `$` inside a regex so they don't end the option. */
    @Test
    fun unescapesCommaAndDollarInRegex() {
        assertEquals(
            FilterRule(null, "/^__s=[A-Za-z0-9]{6,}/"),
            parse("\$removeparam=/^__s=[A-Za-z0-9]{6\\,}/,domain=~univis.uni-erlangen.de"),
        )
        assertEquals(
            FilterRule(listOf("instagram.com"), "/^[a-z]{4}=x\$/"),
            parse("||instagram.com^\$removeparam=/^[a-z]{4}=x\\\$/"),
        )
    }

    /** LegitimateURLShortener writes a regex's end anchor bare; it must not start the options. */
    @Test
    fun bareDollarAnchorInRegexDoesNotSplitTheRule() {
        assertEquals(
            FilterRule(listOf("blue-tomato.com"), "/^.*_(\$)ja.*\$/"),
            parse("||blue-tomato.com^\$removeparam=/^.*_(\\\$)ja.*\$/"),
        )
    }

    @Test
    fun skipsRegexJavaCannotCompile() {
        assertNull(parse("||example.com^\$removeparam=/(unclosed/"))
        assertNull(parse("||example.com^\$removeparam=/^utm_/g"))
    }

    @Test
    fun readsGlobalRule() {
        assertEquals(FilterRule(null, "fbclid"), parse("\$removeparam=fbclid"))
    }

    @Test
    fun readsHostAnchoredRule() {
        assertEquals(
            FilterRule(listOf("facebook.com"), "rdid"),
            parse("||facebook.com^\$removeparam=rdid"),
        )
    }

    @Test
    fun readsHostAnchoredRuleWithPath() {
        assertEquals(
            FilterRule(listOf("facebook.com"), "tracking"),
            parse("||facebook.com/marketplace/\$removeparam=tracking"),
        )
    }

    /** Was global before the fix, so `mid` vanished from every site. */
    @Test
    fun readsSchemePrefixedRuleAsScoped() {
        assertEquals(
            FilterRule(listOf("www.bilibili.com"), "mid"),
            parse("://www.bilibili.com/video/\$removeparam=mid"),
        )
    }

    /** Was global before the fix, so `id` vanished from every site. */
    @Test
    fun readsDomainModifierAsScoped() {
        assertEquals(
            FilterRule(listOf("go.skimlinks.com", "karkkainen.com"), "id"),
            parse("\$removeparam=id,domain=go.skimlinks.com|karkkainen.com"),
        )
    }

    /** Was global before the fix, so `p` vanished from every site. */
    @Test
    fun readsDomainModifierWithPatternPrefix() {
        assertEquals(
            FilterRule(listOf("bilibili.com"), "p"),
            parse("^p=1^\$removeparam=p,domain=bilibili.com"),
        )
    }

    /** Was dropped entirely before the fix: the option list does not start with removeparam. */
    @Test
    fun readsRemoveparamThatIsNotTheFirstOption() {
        assertEquals(
            FilterRule(listOf("reddit.com"), "ref"),
            parse("||reddit.com^\$doc,removeparam=ref"),
        )
    }

    @Test
    fun readsRemoveparamBeforeOtherOptions() {
        assertEquals(
            FilterRule(listOf("404media.co", "adobe.com"), "ref"),
            parse("\$removeparam=ref,doc,domain=404media.co|adobe.com"),
        )
    }

    @Test
    fun dropsNegatedDomainsButKeepsThePositiveOnes() {
        assertEquals(
            FilterRule(listOf("example.com"), "x"),
            parse("\$removeparam=x,domain=example.com|~keep.example.com"),
        )
    }

    @Test
    fun lowercasesHosts() {
        assertEquals(
            FilterRule(listOf("example.com"), "utm_source"),
            parse("||EXAMPLE.COM^\$removeparam=utm_source"),
        )
    }

    /**
     * `ref=shadcn.com^` scopes by URL substring, not by host. Applying it globally stripped
     * `ref` from every site to satisfy a rule about one blog, so it is skipped instead.
     */
    @Test
    fun skipsSubstringScopedRuleRatherThanTreatingItAsGlobal() {
        assertNull(parse("ref=shadcn.com^\$removeparam=ref"))
        assertNull(parse("?ref=upstract.com^\$removeparam=ref"))
        assertNull(parse("/landing.\$removeparam=ref"))
        assertNull(parse("ref=awin^\$doc,removeparam=ref"))
    }

    /** `domain=~a|~b` means "everywhere except a and b", which is global for our purposes. */
    @Test
    fun treatsAllNegatedDomainListAsGlobal() {
        assertEquals(
            FilterRule(null, "MID"),
            parse("\$removeparam=MID,domain=~media-cdn.costco.*|~community.arubanetworks.com"),
        )
    }

    /** A list added in a later release has to reach installs that already stored their sources. */
    @Test
    fun withMissingBuiltInsAppendsNewlyShippedLists() {
        val stored = listOf(
            SettingsDataStore.BUILT_IN_FILTERS.first().copy(enabled = false),
            FilterSource("https://example.com/mine.txt", enabled = true),
        )

        val merged = SettingsDataStore.withMissingBuiltIns(stored)

        assertEquals(stored, merged.take(stored.size))
        assertEquals(
            SettingsDataStore.BUILT_IN_FILTERS.drop(1),
            merged.drop(stored.size),
        )
    }

    /**
     * "Did the user actually edit the domain list" is decided by comparing parsed sets, so
     * reformatting alone — the field hands back whatever the user's keyboard produced — must
     * still read as untouched, or the list stops following the shipped default.
     */
    @Test
    fun parseDomainsIgnoresFormatting() {
        val default = SettingsDataStore.parseDomains(SettingsDataStore.DEFAULT_AGGRESSIVE_DOMAINS)

        assertEquals(
            default,
            SettingsDataStore.parseDomains(default.reversed().joinToString(", ") { "*.$it" }),
        )
    }
}
