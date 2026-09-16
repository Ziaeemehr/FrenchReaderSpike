package com.ziaee.frenchreader.content

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ArticleExtractorTest {
    // Trimmed but structurally real shape of a franceinfo.fr article page:
    // the body text lives in div.c-body, which also embeds an "À lire aussi"
    // related-article card inside a nested .media-embed div that must NOT
    // end up in the extracted text.
    private val franceInfoHtml = """
        <html><body>
        <div class="c-body"><p>Première phrase de l'article.</p>
        <div class="media-embed">
          <span class="media-embed__title">À lire aussi</span>
          <div class="media-embed__wrapper">
            <article class="card-article-list-xs"><a href="/x"><p class="card-article-list-xs__title">Un article lié</p></a></article>
          </div>
        </div>
        <p>Deuxième phrase, après le lien.</p><h2 class="number"><span>1</span> <span>Une question ?</span></h2><p>Réponse à la question.</p>
        </div>
        </body></html>
    """.trimIndent()

    @Test
    fun `extracts France Info article paragraphs and headings, excluding related-article embeds`() {
        val text = ArticleExtractor.extractFranceInfoArticle(franceInfoHtml)

        assertEquals(
            "Première phrase de l'article.\n\nDeuxième phrase, après le lien.\n\n1 Une question ?\n\nRéponse à la question.",
            text
        )
    }

    @Test
    fun `returns null for France Info when c-body is missing`() {
        assertNull(ArticleExtractor.extractFranceInfoArticle("<html><body><p>no body div here</p></body></html>"))
    }

    // Trimmed but structurally real shape of a francaisfacile.rfi.fr episode
    // page: the synced transcript lives in div.m-transcription__content,
    // as plain <p> tags -- already clean, no embeds to strip.
    private val rfiHtml = """
        <html><body>
        <div class="m-transcription__content">
          <div class="m-box-expand">
            <div class="m-box-expand__content">
              <p>
                Bonjour  à  toutes  et  à  tous.
              </p><p>
                Le  Journal en  français  facile.
              </p>
            </div>
          </div>
        </div>
        </body></html>
    """.trimIndent()

    @Test
    fun `extracts RFI Facile transcript paragraphs`() {
        val text = ArticleExtractor.extractRfiFacileTranscript(rfiHtml)

        assertEquals("Bonjour à toutes et à tous.\n\nLe Journal en français facile.", text)
    }

    @Test
    fun `returns null for RFI Facile when transcription content is missing`() {
        assertNull(ArticleExtractor.extractRfiFacileTranscript("<html><body><p>no transcript here</p></body></html>"))
    }
}
