package com.cytsai.urlclean.data

import com.cytsai.urlclean.core.UrlCleaner
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the list shipped in `Filters/filter.txt`. A rule with a typo parses to null and then
 * silently removes nothing, which is invisible at runtime — this is what catches it.
 */
class PersonalFilterListTest {

    private val listFile = File("../Filters/filter.txt")

    private val rules = listFile.readLines()
        .filterNot { it.isBlank() || it.startsWith('!') || it.startsWith('[') }
        .map { it to FilterRepository.parseLine(it) }

    @Test
    fun everyRuleParses() {
        val broken = rules.filter { (_, parsed) -> parsed == null }.map { (line, _) -> line }

        assertTrue("unparseable rules: $broken", broken.isEmpty())
    }

    @Test
    fun isReferencedAsABuiltInList() {
        assertTrue(
            SettingsDataStore.BUILT_IN_FILTERS.any {
                it.url.endsWith("/Filters/filter.txt") && it.name == "Kenichi's URL Cleaner List"
            },
        )
    }

    /** The link this list exists for: nothing but the product path should survive. */
    @Test
    fun stripsShopeeAffiliatePayload() {
        val parsed = rules.mapNotNull { (_, rule) -> rule }
        val url = "https://shopee.tw/opaanlp/13611963/56806577794?__mobile__=1&credential_token=8w" +
            "&exp_group=rollout&gads_t_sig=gq&mmp_pid=an_16332660010&uls_trackid=568rh1lr00m7"

        assertEquals(
            "https://shopee.tw/opaanlp/13611963/56806577794",
            UrlCleaner.clean(url, parsed),
        )
    }

    /** The param name is random on every share, so these names are made up on purpose. */
    @Test
    fun stripsInstagramShareTokenWhateverItsName() {
        val parsed = rules.mapNotNull { (_, rule) -> rule }
        val post = "https://www.instagram.com/p/DeT_D6TmZMw/"
        val reel = "https://www.instagram.com/reel/DeOUzR4zJpt/"

        assertEquals(post, UrlCleaner.clean("$post?qzvk=dGVzdHRva2VuMTIz", parsed))
        assertEquals(reel, UrlCleaner.clean("$reel?wbfo=cmFuZG9tdGV4dDQ1Ng==", parsed))
        assertEquals(
            "$post?img_index=2",
            UrlCleaner.clean("$post?img_index=2&hjtr=YW5vdGhlcm9uZTc4OQ==", parsed),
        )
    }
}
