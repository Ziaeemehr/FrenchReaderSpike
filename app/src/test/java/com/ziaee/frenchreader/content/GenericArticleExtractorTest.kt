package com.ziaee.frenchreader.content

import com.ziaee.frenchreader.vikidia.VikidiaClient
import com.ziaee.frenchreader.wikisource.WikisourceClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Shapes taken from live Tagesschau, Spiegel, Deutschlandfunk and DW pages (2026-09-28). */
class GenericArticleExtractorTest {
    private fun sentence(n: Int) =
        "Dies ist der Satz Nummer $n eines langen Artikels über das Wetter in Berlin und München heute."

    private val teasers = (1..6).joinToString("") {
        "<div class=\"teaser\"><p><a href=\"/a$it\">Kurzer Teaser $it mit einigen Wörtern, der zu einem anderen Artikel führt</a> mehr</p></div>"
    }

    @Test fun `picks the article body over a teaser grid`() {
        val body = (1..8).joinToString("") { "<p>${sentence(it)}</p>" }
        val html = "<html><body><nav>Menü</nav><div class=\"grid\">$teasers</div>" +
            "<article><h2>Das Wetter</h2>$body</article><footer><p>${sentence(99)}</p></footer></body></html>"
        val text = ArticleExtractor.extractGenericArticle(html)!!
        assertTrue(text.startsWith("## Das Wetter"))
        assertTrue(text.contains("Nummer 8"))
        assertFalse(text.contains("Teaser"))
        assertFalse(text.contains("Nummer 99"))
    }

    @Test fun `joins paragraphs wrapped one by one in sibling divs`() {
        val wrapped = (1..8).joinToString("<div class=\"embed\">Instagram</div>") {
            "<div class=\"w\"><div class=\"rich\"><p>${sentence(it)}</p></div></div>"
        }
        val html = "<html><body><div class=\"grid\">$teasers</div><section>$wrapped</section></body></html>"
        val text = ArticleExtractor.extractGenericArticle(html)!!
        assertTrue(text.contains("Nummer 1 ") && text.contains("Nummer 8 "))
        assertFalse(text.contains("Teaser"))
    }

    @Test fun `reads leaf divs used as paragraphs`() {
        val body = (1..6).joinToString("") { "<div class=\"article-details-text\">${sentence(it)}</div>" }
        val html = "<html><body><div class=\"article\">$body</div></body></html>"
        assertEquals(6, ArticleExtractor.extractGenericArticle(html)!!.split("\n\n").size)
    }

    @Test fun `falls back to article html escaped inside page json`() {
        val inner = (1..6).joinToString("") { "\\u003cp>${sentence(it)}\\u003c/p>" }
        val html = "<html><body><div id=\"app\"></div><script>window.__STATE__ = {\"text\":\"" +
            "\\u003ch2>Nachrichten\\u003c/h2>$inner\"};</script></body></html>"
        val text = ArticleExtractor.extractGenericArticle(html)!!
        assertTrue(text.startsWith("## Nachrichten"))
        assertTrue(text.contains("Nummer 6"))
    }

    @Test fun `wiki urls use the language edition`() {
        assertEquals("https://de.vikidia.org/wiki/Stra%C3%9Fe", VikidiaClient.articleUrl("Straße", "de"))
        assertEquals("https://de.wikisource.org/wiki/Der_Erlk%C3%B6nig", WikisourceClient.articleUrl("Der Erlkönig", "de"))
    }
}
