package com.ziaee.frenchreader.news

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NewsFetcherTest {
    // Trimmed real response shape from https://www.franceinfo.fr/titres.rss
    // (redirects to franceinfo.fr) -- two items, in feed order.
    private val sampleFeedXml = """
        <?xml version="1.0"?>
        <rss xmlns:atom="http://www.w3.org/2005/Atom" version="2.0"><channel>
        <title>franceinfo - Les Titres</title>
        <item>
        <title>Discours sur l'&#xE9;tat de l'Union&#xA0;: Ursula von der Leyen</title>
        <description>La pr&#xE9;sidente de la Commission europ&#xE9;enne s'exprime &#xE0; 9 heures.</description>
        <pubDate>Wed, 16 Sep 2026 09:37:29 +0200</pubDate>
        <link>https://www.franceinfo.fr/monde/direct-discours_8193647.html#xtor=RSS-3-[lestitres]</link>
        <guid>https://www.franceinfo.fr/monde/direct-discours_8193647.html#xtor=RSS-3-[lestitres]</guid>
        </item>
        <item>
        <title>Six questions sur le proc&#xE8;s de Rachida Dati</title>
        <description>L'ancienne ministre compara&#xEE;t &#xE0; partir de mercredi.</description>
        <pubDate>Wed, 16 Sep 2026 08:47:58 +0200</pubDate>
        <link>https://www.franceinfo.fr/politique/six-questions_8193776.html#xtor=RSS-3-[lestitres]</link>
        <guid>https://www.franceinfo.fr/politique/six-questions_8193776.html#xtor=RSS-3-[lestitres]</guid>
        </item>
        </channel></rss>
    """.trimIndent()

    @Test
    fun `parses multiple items in feed order`() {
        val items = NewsFetcher.parseItems(sampleFeedXml, limit = 25)

        assertEquals(2, items.size)
        assertEquals("Discours sur l'état de l'Union : Ursula von der Leyen", items[0].title)
        assertEquals(
            "https://www.franceinfo.fr/monde/direct-discours_8193647.html#xtor=RSS-3-[lestitres]",
            items[0].link
        )
        assertEquals("Six questions sur le procès de Rachida Dati", items[1].title)
    }

    @Test
    fun `respects the limit`() {
        val items = NewsFetcher.parseItems(sampleFeedXml, limit = 1)
        assertEquals(1, items.size)
    }

    @Test
    fun `parses RFC822 pubDate into epoch ms`() {
        val items = NewsFetcher.parseItems(sampleFeedXml, limit = 25)
        // 2026-09-16T09:37:29+02:00
        assertEquals(1789544249000L, items[0].publishedAtMs)
    }

    @Test
    fun `prefers content encoded over description when present`() {
        val xml = """
            <rss><channel><item>
            <title>Titre</title>
            <description>Court résumé.</description>
            <content:encoded>Contenu plus long.</content:encoded>
            <link>https://example.com/a</link>
            <guid>https://example.com/a</guid>
            </item></channel></rss>
        """.trimIndent()

        val items = NewsFetcher.parseItems(xml, limit = 25)
        assertEquals("Contenu plus long.", items[0].snippet)
    }

    @Test
    fun `parses media content image and upgrades http`() {
        val xml = """
            <rss xmlns:media="http://search.yahoo.com/mrss/"><channel><item>
            <title>Titre</title><description>Résumé.</description>
            <media:content url="http://img.test/a.jpg" type="image/jpeg" />
            <link>https://example.com/a</link>
            </item></channel></rss>
        """.trimIndent()

        val item = NewsFetcher.parseItems(xml, limit = 25).single()

        assertEquals("https://img.test/a.jpg", item.imageUrl)
    }

    @Test
    fun `parses image enclosure`() {
        val xml = """
            <rss><channel><item>
            <title>Titre</title><description>Résumé.</description>
            <enclosure url="https://img.test/b.jpg" type="image/jpeg" />
            <link>https://example.com/b</link>
            </item></channel></rss>
        """.trimIndent()

        val item = NewsFetcher.parseItems(xml, limit = 25).single()

        assertEquals("https://img.test/b.jpg", item.imageUrl)
    }

    @Test
    fun `parses first description image`() {
        val xml = """
            <rss><channel><item>
            <title>Titre</title>
            <description><![CDATA[<p>Résumé.</p><img src="http://img.test/c.jpg"><img src="https://img.test/d.jpg">]]></description>
            <link>https://example.com/c</link>
            </item></channel></rss>
        """.trimIndent()

        val item = NewsFetcher.parseItems(xml, limit = 25).single()

        assertEquals("https://img.test/c.jpg", item.imageUrl)
    }

    @Test
    fun `missing image returns null`() {
        val xml = """
            <rss><channel><item>
            <title>Titre</title><description>Résumé.</description>
            <link>https://example.com/no-image</link>
            </item></channel></rss>
        """.trimIndent()

        val item = NewsFetcher.parseItems(xml, limit = 25).single()

        assertNull(item.imageUrl)
    }

    @Test
    fun `ignores non-image media content in favor of thumbnail`() {
        val xml = """
            <rss xmlns:media="http://search.yahoo.com/mrss/"><channel><item>
            <title>Titre</title><description>Résumé.</description>
            <media:content url="https://media.test/audio.mp3" type="audio/mpeg" />
            <media:thumbnail url="https://img.test/thumb.jpg" />
            <link>https://example.com/audio</link>
            </item></channel></rss>
        """.trimIndent()

        val item = NewsFetcher.parseItems(xml, limit = 25).single()

        assertEquals("https://img.test/thumb.jpg", item.imageUrl)
    }

    @Test
    fun `ignores non-image media content in favor of image enclosure`() {
        val xml = """
            <rss xmlns:media="http://search.yahoo.com/mrss/"><channel><item>
            <title>Titre</title><description>Résumé.</description>
            <media:content url="https://media.test/video.mp4" type="video/mp4" />
            <enclosure url="https://img.test/enclosure.jpg" type="image/jpeg" />
            <link>https://example.com/video</link>
            </item></channel></rss>
        """.trimIndent()

        val item = NewsFetcher.parseItems(xml, limit = 25).single()

        assertEquals("https://img.test/enclosure.jpg", item.imageUrl)
    }

    @Test
    fun `upgrades uppercase http image schemes`() {
        val xml = """
            <rss xmlns:media="http://search.yahoo.com/mrss/"><channel><item>
            <title>Titre</title><description>Résumé.</description>
            <media:content url="HTTP://IMG.TEST/uppercase.jpg" type="image/jpeg" />
            <link>https://example.com/uppercase</link>
            </item></channel></rss>
        """.trimIndent()

        val item = NewsFetcher.parseItems(xml, limit = 25).single()

        assertEquals("https://IMG.TEST/uppercase.jpg", item.imageUrl)
    }
}
