package com.ziaee.frenchreader.resources

import com.ziaee.frenchreader.data.ResourceLink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ResourceMetadataTest {
    @Test
    fun normalizeResourceUrl_addsHttpsAndRejectsNonWebSchemes() {
        assertEquals("https://fabulang.com/en/fr/", normalizeResourceUrl(" fabulang.com/en/fr/ "))
        assertEquals("http://example.com", normalizeResourceUrl("http://example.com"))
        assertNull(normalizeResourceUrl("javascript:alert(1)"))
        assertNull(normalizeResourceUrl("ftp://example.com/file"))
        assertNull(normalizeResourceUrl("not a url"))
    }

    @Test
    fun parseResourceMetadata_prefersOpenGraphAndResolvesRelativeImage() {
        val html = """
            <html><head>
              <title>Fallback title</title>
              <meta property="og:title" content="French Stories" />
              <meta property="og:image" content="/images/cover.jpg" />
            </head></html>
        """.trimIndent()

        assertEquals(
            ResourceMetadata("French Stories", "https://example.com/images/cover.jpg"),
            parseResourceMetadata(html, "https://example.com/lessons/start")
        )
    }

    @Test
    fun parseResourceMetadata_fallsBackToDocumentTitleAndNoImage() {
        val html = "<html><head><title>  Learn French  </title></head></html>"

        assertEquals(
            ResourceMetadata("Learn French", null),
            parseResourceMetadata(html, "https://example.com")
        )
    }

    @Test
    fun filterResources_matchesTitleAndDomainIgnoringCase() {
        val items = listOf(
            ResourceLink(id = 1, title = "Fabulang", url = "https://fabulang.com", createdAtMs = 1_000_000),
            ResourceLink(id = 2, title = "RFI Savoirs", url = "https://francaisfacile.rfi.fr", createdAtMs = 2_000_000)
        )

        assertEquals(listOf(items[0]), filterResources(items, "FABU"))
        assertEquals(listOf(items[1]), filterResources(items, "francaisfacile"))
        // The user's own links come newest first.
        assertEquals(listOf(items[1], items[0]), filterResources(items, "  "))
    }

    @Test
    fun filterResources_searchesBuiltInDescriptionsAndListsBuiltInsAfterOwnLinksInCatalogOrder() {
        val own = ResourceLink(id = 9, title = "Mine", url = "https://example.org", createdAtMs = 5_000_000)
        val rfi = ResourceLink(id = 2, title = "RFI", url = DEFAULT_RESOURCES[1].url, createdAtMs = DEFAULT_RESOURCES[1].createdAtMs)
        val inner = ResourceLink(id = 3, title = "Inner", url = DEFAULT_RESOURCES[0].url, createdAtMs = DEFAULT_RESOURCES[0].createdAtMs)
        assertEquals(listOf(own, inner, rfi), filterResources(listOf(rfi, own, inner), ""))
        // Matches the catalog description ("transcripts"), not just title or URL.
        assertEquals(listOf(rfi), filterResources(listOf(rfi, own, inner), "transcripts"))
        assertEquals("A2–B1", resourceLevel(rfi))
        assertEquals(DEFAULT_RESOURCES[1].description["fa"], resourceDescription(rfi, "fa"))
        assertEquals(DEFAULT_RESOURCES[1].description["en"], resourceDescription(rfi, "de"))
    }

    @Test
    fun filterResources_combinesCategoryAndQuery() {
        val podcast = ResourceLink(
            id = 1,
            title = "InnerFrench",
            url = "https://innerfrench.com/podcast/",
            category = ResourceCategory.PODCASTS.key
        )
        val reading = ResourceLink(
            id = 2,
            title = "Fabulang",
            url = "https://fabulang.com",
            category = ResourceCategory.READING.key
        )
        val rfiPodcast = ResourceLink(
            id = 3,
            title = "Journal facile",
            url = "https://francaisfacile.rfi.fr",
            category = ResourceCategory.PODCASTS.key
        )
        val items = listOf(podcast, reading, rfiPodcast)

        assertEquals(listOf(podcast, rfiPodcast), filterResources(items, "", ResourceCategory.PODCASTS))
        assertEquals(listOf(rfiPodcast), filterResources(items, "RFI", ResourceCategory.PODCASTS))
        assertEquals(emptyList<ResourceLink>(), filterResources(items, "Fabulang", ResourceCategory.PODCASTS))
    }

    @Test
    fun resourceCategoryFromKey_fallsBackToOther() {
        assertEquals(ResourceCategory.PODCASTS, ResourceCategory.fromKey("podcasts"))
        assertEquals(ResourceCategory.OTHER, ResourceCategory.fromKey("future-category"))
        assertEquals(ResourceCategory.OTHER, ResourceCategory.fromKey(null))
    }
}
