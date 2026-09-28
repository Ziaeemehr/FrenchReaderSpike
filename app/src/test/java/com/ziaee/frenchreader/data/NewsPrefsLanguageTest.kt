package com.ziaee.frenchreader.data

import com.ziaee.frenchreader.news.newsSourcesFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NewsPrefsLanguageTest {
    @Test fun `fresh install uses each language's defaults`() {
        assertEquals(NewsPrefs.DEFAULT_ENABLED_SOURCE_IDS, NewsPrefs.enabledForLanguage(null, "fr"))
        assertEquals(setOf("dw_langsam", "tagesschau", "deutschlandfunk"), NewsPrefs.enabledForLanguage(null, "de"))
    }

    @Test fun `legacy french customisation is kept and german gets its defaults`() {
        val legacy = setOf("rfi", "lemonde_culture")
        assertEquals(legacy, NewsPrefs.enabledForLanguage(legacy, "fr"))
        assertEquals(setOf("dw_langsam", "tagesschau", "deutschlandfunk"), NewsPrefs.enabledForLanguage(legacy, "de"))
    }

    @Test fun `saving german keeps french and an empty german choice sticks`() {
        val stored = NewsPrefs.withLanguageChoice(setOf("rfi"), "de", emptySet())
        assertEquals(setOf("rfi"), NewsPrefs.enabledForLanguage(stored, "fr"))
        assertTrue(NewsPrefs.enabledForLanguage(stored, "de").isEmpty())
    }

    @Test fun `sources are split by language`() {
        assertTrue(newsSourcesFor("de").none { it.feedUrl.contains(".fr/") })
        assertTrue(newsSourcesFor("fr").none { it.language == "de" })
    }
}
