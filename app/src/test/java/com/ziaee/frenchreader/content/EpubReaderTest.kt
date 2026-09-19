package com.ziaee.frenchreader.content

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class EpubReaderTest {
    @Test
    fun `parses metadata and follows spine rather than manifest order`() {
        val book = readBook(
            manifest = """
                <item id="second" href="second.xhtml" media-type="application/xhtml+xml"/>
                <item id="first" href="first.xhtml" media-type="application/xhtml+xml"/>
            """,
            spine = "<itemref idref=\"first\"/><itemref idref=\"second\"/>",
            files = mapOf(
                "OPS/first.xhtml" to chapter("Premier", longText("un")),
                "OPS/second.xhtml" to chapter("Deuxième", longText("deux"))
            ),
            title = "Mon livre",
            identifier = "urn:test:123"
        )

        assertEquals("Mon livre", book.title)
        assertEquals("urn:test:123", book.identifier)
        assertEquals(listOf("Premier", "Deuxième"), book.chapters.map { it.title })
        assertTrue(book.chapters.first().text.startsWith("# Premier\n\n"))
    }

    @Test
    fun `resolves encoded relative hrefs from the OPF directory`() {
        val book = readBook(
            opfPath = "EPUB/package/content.opf",
            manifest = "<item id=\"chapter\" href=\"../Text/My%20Chapter.xhtml\" media-type=\"application/xhtml+xml\"/>",
            spine = "<itemref idref=\"chapter\"/>",
            files = mapOf("EPUB/Text/My Chapter.xhtml" to chapter("Chemin", longText("texte")))
        )

        assertEquals("Chemin", book.chapters.single().title)
    }

    @Test
    fun `skips nav document and short cover chapter`() {
        val book = readBook(
            manifest = """
                <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="landmarks nav"/>
                <item id="cover" href="cover.xhtml" media-type="application/xhtml+xml"/>
                <item id="story" href="story.xhtml" media-type="application/xhtml+xml"/>
            """,
            spine = "<itemref idref=\"nav\"/><itemref idref=\"cover\"/><itemref idref=\"story\"/>",
            files = mapOf(
                "OPS/nav.xhtml" to chapter("Contents", "Navigation"),
                "OPS/cover.xhtml" to chapter("Cover", "Short cover"),
                "OPS/story.xhtml" to chapter("Story", longText("histoire"))
            )
        )

        assertEquals(listOf("Story"), book.chapters.map { it.title })
    }

    @Test
    fun `splits long chapters at bounded sizes and numbers every part`() {
        val paragraph = ("Une phrase assez longue. ".repeat(1500)).trim()
        val book = readBook(
            manifest = "<item id=\"long\" href=\"long.xhtml\" media-type=\"application/xhtml+xml\"/>",
            spine = "<itemref idref=\"long\"/>",
            files = mapOf("OPS/long.xhtml" to chapter("Long", paragraph)),
            splitLimit = 30_000
        )

        assertTrue(book.chapters.size > 1)
        val count = book.chapters.size
        book.chapters.forEachIndexed { index, part ->
            assertEquals("Long (${index + 1}/$count)", part.title)
            assertTrue(part.text.length <= 30_000)
        }
    }

    @Test
    fun `does not split long chapters unless a limit is requested`() {
        val paragraph = ("Une phrase assez longue. ".repeat(1500)).trim()
        val book = readBook(
            manifest = "<item id=\"long\" href=\"long.xhtml\" media-type=\"application/xhtml+xml\"/>",
            spine = "<itemref idref=\"long\"/>",
            files = mapOf("OPS/long.xhtml" to chapter("Long", paragraph))
        )

        assertEquals(1, book.chapters.size)
        assertEquals("Long", book.chapters.single().title)
    }

    @Test
    fun `splits one spine file into substantial heading sections`() {
        val lead = "Préface sans titre. ".repeat(12)
        val first = "Première histoire. ".repeat(20)
        val second = "Deuxième histoire. ".repeat(20)
        val license = "License text. ".repeat(30)
        val html = "<html><body><p>$lead</p><h1>Conte un</h1><p>$first</p>" +
            "<h2>Conte deux</h2><p>$second</p><h2>Project Gutenberg License</h2><p>$license</p></body></html>"
        val book = readBook(
            manifest = "<item id=\"stories\" href=\"stories.xhtml\" media-type=\"application/xhtml+xml\"/>",
            spine = "<itemref idref=\"stories\"/>",
            files = mapOf("OPS/stories.xhtml" to html)
        )

        assertEquals(listOf("Conte un", "Conte deux"), book.chapters.map { it.title })
        assertTrue(book.chapters.first().text.contains("Préface sans titre"))
    }

    @Test
    fun `keeps supported images in position and numbers them`() {
        val image = ByteArray(2048) { 7 }
        val book = readBook(
            manifest = """
                <item id="chapter" href="Text/ch.xhtml" media-type="application/xhtml+xml"/>
                <item id="picture" href="Images/pic%201.png" media-type="image/png"/>
            """,
            spine = "<itemref idref=\"chapter\"/>",
            files = mapOf("OPS/Text/ch.xhtml" to "<html><body><h1>Titre</h1><p>Avant<img src=\"../Images/pic%201.png\" alt=\"une [image](x)\"/>Après</p></body></html>"),
            binaryFiles = mapOf("OPS/Images/pic 1.png" to image)
        )

        val chapter = book.chapters.single()
        assertEquals("# Titre\n\nAvant\n\n![une imagex](epubimg:0_0.jpg)\n\nAprès", chapter.text)
        assertEquals("0_0.jpg", chapter.images.single().name)
        assertTrue(chapter.images.single().bytes.contentEquals(image))
    }

    @Test
    fun `ignores tiny and missing images`() {
        val book = readBook(
            manifest = "<item id=\"chapter\" href=\"chapter.xhtml\" media-type=\"application/xhtml+xml\"/>",
            spine = "<itemref idref=\"chapter\"/>",
            files = mapOf("OPS/chapter.xhtml" to "<html><body><h1>T</h1><p>${longText("texte")}</p><img src=\"tiny.jpg\"/><img src=\"missing.jpg\"/></body></html>"),
            binaryFiles = mapOf("OPS/tiny.jpg" to ByteArray(2047))
        )

        assertTrue(book.chapters.single().images.isEmpty())
        assertTrue("epubimg:" !in book.chapters.single().text)
    }

    @Test
    fun `finds cover from epub3 property`() {
        val cover = ByteArray(2048) { 1 }
        val book = readBook(
            manifest = """
                <item id="chapter" href="chapter.xhtml" media-type="application/xhtml+xml"/>
                <item id="cover" href="cover.jpg" media-type="image/jpeg" properties="cover-image"/>
            """,
            spine = "<itemref idref=\"chapter\"/>",
            files = mapOf("OPS/chapter.xhtml" to chapter("T", longText("texte"))),
            binaryFiles = mapOf("OPS/cover.jpg" to cover)
        )
        assertTrue(book.cover!!.bytes.contentEquals(cover))
    }

    @Test
    fun `finds cover from epub2 metadata`() {
        val cover = ByteArray(2048) { 2 }
        val book = readBook(
            manifest = """
                <item id="chapter" href="chapter.xhtml" media-type="application/xhtml+xml"/>
                <item id="legacy-cover" href="legacy.png" media-type="image/png"/>
            """,
            spine = "<itemref idref=\"chapter\"/>",
            files = mapOf("OPS/chapter.xhtml" to chapter("T", longText("texte"))),
            binaryFiles = mapOf("OPS/legacy.png" to cover),
            metadataExtra = "<meta name=\"cover\" content=\"legacy-cover\"/>"
        )
        assertTrue(book.cover!!.bytes.contentEquals(cover))
    }

    @Test
    fun `uses first image on first spine page as cover`() {
        val cover = ByteArray(2048) { 3 }
        val book = readBook(
            manifest = """
                <item id="cover-page" href="cover.xhtml" media-type="application/xhtml+xml"/>
                <item id="chapter" href="chapter.xhtml" media-type="application/xhtml+xml"/>
            """,
            spine = "<itemref idref=\"cover-page\"/><itemref idref=\"chapter\"/>",
            files = mapOf(
                "OPS/cover.xhtml" to "<html><body><img src=\"fallback.webp\"/></body></html>",
                "OPS/chapter.xhtml" to chapter("T", longText("texte"))
            ),
            binaryFiles = mapOf("OPS/fallback.webp" to cover)
        )
        assertTrue(book.cover!!.bytes.contentEquals(cover))
        assertEquals(listOf("T"), book.chapters.map { it.title })
    }

    @Test
    fun `split chapters keep image markers with final chapter indexes`() {
        val image = ByteArray(2048) { 4 }
        val body = "<p>${"phrase. ".repeat(4500)}</p><p><img src=\"pic.jpg\"/></p>"
        val book = readBook(
            manifest = "<item id=\"chapter\" href=\"chapter.xhtml\" media-type=\"application/xhtml+xml\"/>",
            spine = "<itemref idref=\"chapter\"/>",
            files = mapOf("OPS/chapter.xhtml" to "<html><body><h1>T</h1>$body</body></html>"),
            binaryFiles = mapOf("OPS/pic.jpg" to image),
            splitLimit = 30_000
        )
        val imagePartIndex = book.chapters.indexOfFirst { it.images.isNotEmpty() }
        assertTrue(imagePartIndex > 0)
        assertEquals("${imagePartIndex}_0.jpg", book.chapters[imagePartIndex].images.single().name)
        assertTrue("epubimg:${imagePartIndex}_0.jpg" in book.chapters[imagePartIndex].text)
    }

    @Test
    fun `invalid zip throws format exception`() {
        assertFormatException { EpubReader.read(ByteArrayInputStream("not a zip".toByteArray())) }
    }

    @Test
    fun `missing container throws format exception`() {
        assertFormatException { EpubReader.read(ByteArrayInputStream(zip(mapOf("other.txt" to "x")))) }
    }

    @Test
    fun `book with no readable text throws`() {
        val epub = epub(
            manifest = "<item id=\"empty\" href=\"empty.xhtml\" media-type=\"application/xhtml+xml\"/>",
            spine = "<itemref idref=\"empty\"/>",
            files = mapOf("OPS/empty.xhtml" to "<html><head><title>Empty</title></head><body><script>x</script></body></html>")
        )

        val error = assertFormatException { EpubReader.read(ByteArrayInputStream(epub)) }
        assertEquals("no readable text", error.message)
    }

    private fun readBook(
        manifest: String,
        spine: String,
        files: Map<String, String>,
        title: String = "Book",
        identifier: String = "book-id",
        opfPath: String = "OPS/content.opf",
        binaryFiles: Map<String, ByteArray> = emptyMap(),
        metadataExtra: String = "",
        splitLimit: Int? = null
    ): EpubBook = EpubReader.read(
        ByteArrayInputStream(epub(manifest, spine, files, title, identifier, opfPath, binaryFiles, metadataExtra)),
        splitLimit
    )

    private fun epub(
        manifest: String,
        spine: String,
        files: Map<String, String>,
        title: String = "Book",
        identifier: String = "book-id",
        opfPath: String = "OPS/content.opf",
        binaryFiles: Map<String, ByteArray> = emptyMap(),
        metadataExtra: String = ""
    ): ByteArray {
        val container = """
            <?xml version="1.0"?>
            <container xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
              <rootfiles><rootfile full-path="$opfPath" media-type="application/oebps-package+xml"/></rootfiles>
            </container>
        """.trimIndent()
        val opf = """
            <?xml version="1.0"?>
            <package xmlns="http://www.idpf.org/2007/opf" xmlns:dc="http://purl.org/dc/elements/1.1/">
              <metadata><dc:title>$title</dc:title><dc:identifier>$identifier</dc:identifier>$metadataExtra</metadata>
              <manifest>$manifest</manifest>
              <spine>$spine</spine>
            </package>
        """.trimIndent()
        return zipBytes((mapOf("META-INF/container.xml" to container, opfPath to opf) + files).mapValues { it.value.toByteArray() } + binaryFiles)
    }

    private fun zip(files: Map<String, String>): ByteArray {
        return zipBytes(files.mapValues { it.value.toByteArray() })
    }

    private fun zipBytes(files: Map<String, ByteArray>): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            files.forEach { (name, contents) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(contents)
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }

    private fun chapter(title: String, body: String): String =
        "<html><head><title>Fallback</title></head><body><h1>$title</h1><p>$body</p></body></html>"

    private fun longText(word: String): String = "$word ".repeat(120).trim()

    private fun assertFormatException(block: () -> Unit): EpubFormatException {
        try {
            block()
            fail("Expected EpubFormatException")
        } catch (error: EpubFormatException) {
            return error
        }
        throw AssertionError("unreachable")
    }
}
