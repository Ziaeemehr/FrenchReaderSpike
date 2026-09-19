package com.ziaee.frenchreader.content

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import org.jsoup.parser.Parser
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.zip.ZipInputStream

data class EpubImage(val name: String, val bytes: ByteArray, val mimeType: String)
data class EpubChapter(val title: String, val text: String, val images: List<EpubImage> = emptyList())
data class EpubBook(
    val title: String,
    val identifier: String,
    val chapters: List<EpubChapter>,
    val cover: EpubImage? = null
)

class EpubFormatException(message: String) : Exception(message)

object EpubReader {
    private const val MAX_UNCOMPRESSED_BYTES = 100L * 1024L * 1024L
    private const val MIN_IMAGE_BYTES = 2 * 1024
    private val markerRegex = Regex("!\\[[^]]*]\\(epubimg:[^)]+\\)")
    private val blockTags = setOf("p", "li", "blockquote", "div", "h1", "h2", "h3", "h4", "h5", "h6")
    private val imageTypes = setOf("image/jpeg", "image/png", "image/gif", "image/webp")

    private data class ManifestItem(
        val id: String,
        val path: String,
        val mediaType: String,
        val properties: Set<String>
    )

    fun read(input: InputStream, splitLimit: Int? = null): EpubBook {
        try {
            require(splitLimit == null || splitLimit > 0) { "splitLimit must be positive" }
            val entries = readEntries(input)
            val container = entries["META-INF/container.xml"]
                ?: throw EpubFormatException("missing META-INF/container.xml")
            val containerDocument = Jsoup.parse(container.toString(StandardCharsets.UTF_8), "", Parser.xmlParser())
            val opfPath = containerDocument.getElementsByTag("rootfile").firstOrNull()
                ?.attr("full-path")?.takeIf(String::isNotBlank)?.let(::normalizePath)
                ?: throw EpubFormatException("missing rootfile")
            val opfBytes = entries[opfPath] ?: throw EpubFormatException("missing package document")
            val opf = Jsoup.parse(opfBytes.toString(StandardCharsets.UTF_8), "", Parser.xmlParser())
            val title = firstElementByLocalName(opf, "title")?.text()?.cleanWhitespace()
                ?.takeIf(String::isNotEmpty) ?: "EPUB"
            val identifier = firstElementByLocalName(opf, "identifier")?.text()?.cleanWhitespace()
                ?.takeIf(String::isNotEmpty) ?: title
            val opfDirectory = opfPath.substringBeforeLast('/', "")
            val manifest = opf.getElementsByTag("item").mapNotNull { item ->
                val id = item.attr("id").takeIf(String::isNotBlank) ?: return@mapNotNull null
                val href = item.attr("href").takeIf(String::isNotBlank) ?: return@mapNotNull null
                id to ManifestItem(
                    id,
                    resolvePath(opfDirectory, href),
                    item.attr("media-type").lowercase(),
                    item.attr("properties").split(Regex("\\s+")).filter(String::isNotBlank).toSet()
                )
            }.toMap()
            val mediaTypes = manifest.values.associate { it.path to it.mediaType }
            val spineItems = opf.getElementsByTag("itemref").mapNotNull { manifest[it.attr("idref")] }
            val readableItems = spineItems.filter {
                "nav" !in it.properties && it.mediaType in setOf("application/xhtml+xml", "text/html")
            }
            val extracted = readableItems.mapIndexedNotNull { index, item ->
                entries[item.path]?.let { extractChapter(it, item.path, index + 1, entries, mediaTypes) }
            }.flatMap(::splitAtHeadingBoundaries)
            val substantial = if (extracted.size == 1) extracted else extracted.filter { visibleLength(it.text) >= 200 }
            val withoutLicense = substantial.filterIndexed { index, chapter ->
                index != substantial.lastIndex || !chapter.title.matches(Regex("project gutenberg.*license", RegexOption.IGNORE_CASE))
            }
            val chapters = withoutLicense
                .flatMap { chapter -> splitLimit?.let { splitChapter(chapter, it) } ?: listOf(chapter) }
                .mapIndexed(::renumberImages)
            if (chapters.isEmpty()) throw EpubFormatException("no readable text")

            val propertyCover = manifest.values.firstOrNull { "cover-image" in it.properties }
            val epub2CoverId = opf.getAllElements().firstOrNull {
                it.localName().equals("meta", ignoreCase = true) &&
                    it.attr("name").equals("cover", ignoreCase = true)
            }?.attr("content")
            val declaredCover = propertyCover ?: epub2CoverId?.let(manifest::get)
            val cover = declaredCover?.let { imageAt(it.path, entries, mediaTypes, "cover.jpg") }
                ?: readableItems.firstOrNull()?.let { firstImageOnPage(it.path, entries, mediaTypes) }
            return EpubBook(title, identifier, chapters, cover)
        } catch (error: EpubFormatException) {
            throw error
        } catch (error: Exception) {
            throw EpubFormatException(error.message ?: "invalid EPUB")
        }
    }

    private fun readEntries(input: InputStream): Map<String, ByteArray> {
        val entries = linkedMapOf<String, ByteArray>()
        var total = 0L
        ZipInputStream(input).use { zip ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val entry = zip.nextEntry ?: break
                if (!entry.isDirectory) {
                    val output = ByteArrayOutputStream()
                    while (true) {
                        val count = zip.read(buffer)
                        if (count < 0) break
                        total += count
                        if (total > MAX_UNCOMPRESSED_BYTES) throw EpubFormatException("uncompressed EPUB exceeds 100 MB")
                        output.write(buffer, 0, count)
                    }
                    entries[normalizePath(entry.name)] = output.toByteArray()
                }
                zip.closeEntry()
            }
        }
        return entries
    }

    private fun extractChapter(
        bytes: ByteArray,
        pagePath: String,
        number: Int,
        entries: Map<String, ByteArray>,
        mediaTypes: Map<String, String>
    ): EpubChapter? {
        val document = Jsoup.parse(bytes.toString(StandardCharsets.UTF_8))
        document.select("script, style, nav").remove()
        val title = document.selectFirst("h1, h2, h3, h4, h5, h6")?.text()?.cleanWhitespace()
            ?.takeIf(String::isNotEmpty)
            ?: document.title().cleanWhitespace().takeIf(String::isNotEmpty)
            ?: "Chapter $number"
        val directory = pagePath.substringBeforeLast('/', "")
        val images = mutableListOf<EpubImage>()
        val blocks = document.select("p, li, blockquote, div, h1, h2, h3, h4, h5, h6, img, image")
            .filter { element ->
                element.parents().none { it.tagName() in blockTags - "div" } &&
                    !(element.tagName() == "div" && element.parents().any { it.tagName() == "div" }) &&
                    !(element.localName() in setOf("img", "image") && element.parents().any { it.tagName() == "div" })
            }
        val paragraphs = blocks.flatMap { element ->
            val chunks = mutableListOf<String>()
            val text = StringBuilder()
            fun flush() {
                text.toString().cleanWhitespace().takeIf(String::isNotEmpty)?.let(chunks::add)
                text.clear()
            }
            fun walk(node: Node, insideDiv: Boolean) {
                when (node) {
                    is TextNode -> text.append(' ').append(node.text())
                    is Element -> {
                        val tag = node.localName()
                        if (tag == "img" || tag == "image") {
                            val image = resolveElementImage(node, directory, entries, mediaTypes, "tmp_${images.size}.jpg")
                            if (image != null) {
                                flush()
                                val alt = node.attr("alt").replace(Regex("[\\[\\]()]"), "").cleanWhitespace()
                                chunks += "![$alt](epubimg:${image.name})"
                                images += image
                            }
                        } else if (!insideDiv || tag !in blockTags) {
                            node.childNodes().forEach { walk(it, insideDiv) }
                        }
                    }
                }
            }
            walk(element, element.tagName() == "div")
            flush()
            if (element.tagName().matches(Regex("h[1-6]")) && chunks.firstOrNull()?.startsWith("![") == false) {
                chunks[0] = "# ${chunks[0]}"
            }
            chunks
        }
        val text = paragraphs.joinToString("\n\n")
        if (visibleLength(text) == 0) return null
        return EpubChapter(title, text, images.filter { text.contains("epubimg:${it.name}") })
    }

    private fun splitAtHeadingBoundaries(chapter: EpubChapter): List<EpubChapter> {
        val blocks = chapter.text.split("\n\n")
        val headingIndexes = blocks.indices.filter { blocks[it].startsWith("# ") }
        if (headingIndexes.size < 2) return listOf(chapter)
        val leadIn = blocks.subList(0, headingIndexes.first())
        return headingIndexes.mapIndexedNotNull { sectionIndex, start ->
            val end = headingIndexes.getOrNull(sectionIndex + 1) ?: blocks.size
            val heading = blocks[start].removePrefix("# ").trim()
            val sectionBlocks = buildList {
                if (sectionIndex == 0) addAll(leadIn)
                addAll(blocks.subList(start, end))
            }
            val text = sectionBlocks.joinToString("\n\n").trim()
            EpubChapter(
                title = heading,
                text = text,
                images = chapter.images.filter { text.contains("epubimg:${it.name}") }
            ).takeIf { visibleLength(it.text) >= 200 }
        }
    }

    private fun splitChapter(chapter: EpubChapter, splitLimit: Int): List<EpubChapter> {
        if (chapter.text.length <= splitLimit) return listOf(chapter)
        val units = chapter.text.split("\n\n").flatMap { splitLongParagraph(it, splitLimit) }
        val parts = mutableListOf<String>()
        var current = StringBuilder()
        for (unit in units) {
            val separatorLength = if (current.isEmpty()) 0 else 2
            if (current.isNotEmpty() && current.length + separatorLength + unit.length > splitLimit) {
                parts += current.toString()
                current = StringBuilder()
            }
            if (current.isNotEmpty()) current.append("\n\n")
            current.append(unit)
        }
        if (current.isNotEmpty()) parts += current.toString()
        return parts.mapIndexed { index, text ->
            EpubChapter(
                "${chapter.title} (${index + 1}/${parts.size})",
                text,
                chapter.images.filter { text.contains("epubimg:${it.name}") }
            )
        }
    }

    private fun renumberImages(chapterIndex: Int, chapter: EpubChapter): EpubChapter {
        var text = chapter.text
        val images = chapter.images.mapIndexed { imageIndex, image ->
            val name = "${chapterIndex}_${imageIndex}.jpg"
            text = text.replace("epubimg:${image.name}", "epubimg:$name")
            image.copy(name = name)
        }
        return chapter.copy(text = text, images = images)
    }

    private fun firstImageOnPage(
        pagePath: String,
        entries: Map<String, ByteArray>,
        mediaTypes: Map<String, String>
    ): EpubImage? {
        val bytes = entries[pagePath] ?: return null
        val document = Jsoup.parse(bytes.toString(StandardCharsets.UTF_8))
        val directory = pagePath.substringBeforeLast('/', "")
        return document.select("img, image").firstNotNullOfOrNull {
            resolveElementImage(it, directory, entries, mediaTypes, "cover.jpg")
        }
    }

    private fun resolveElementImage(
        element: Element,
        directory: String,
        entries: Map<String, ByteArray>,
        mediaTypes: Map<String, String>,
        name: String
    ): EpubImage? {
        val href = element.attr("src").ifBlank { element.attr("xlink:href") }.ifBlank { element.attr("href") }
        return href.takeIf(String::isNotBlank)?.let { imageAt(resolvePath(directory, it), entries, mediaTypes, name) }
    }

    private fun imageAt(
        path: String,
        entries: Map<String, ByteArray>,
        mediaTypes: Map<String, String>,
        name: String
    ): EpubImage? {
        val bytes = entries[path]?.takeIf { it.size >= MIN_IMAGE_BYTES } ?: return null
        val declaredMime = mediaTypes[path]
        val mime = if (declaredMime != null) declaredMime.takeIf { it in imageTypes }
        else mimeTypeForPath(path)
        mime ?: return null
        return EpubImage(name, bytes, mime)
    }

    private fun mimeTypeForPath(path: String): String? = when (path.substringAfterLast('.', "").lowercase()) {
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        else -> null
    }

    private fun visibleLength(text: String) = text.replace(markerRegex, "").cleanWhitespace().length

    private fun splitLongParagraph(paragraph: String, splitLimit: Int): List<String> {
        if (paragraph.length <= splitLimit) return listOf(paragraph)
        val result = mutableListOf<String>()
        var remaining = paragraph
        while (remaining.length > splitLimit) {
            val sentenceEnd = remaining.lastIndexOf(". ", splitLimit - 1)
            val cut = if (sentenceEnd >= 0) sentenceEnd + 1 else splitLimit
            result += remaining.substring(0, cut).trim()
            remaining = remaining.substring(cut).trimStart()
        }
        if (remaining.isNotEmpty()) result += remaining
        return result
    }

    private fun resolvePath(directory: String, href: String): String {
        val pathOnly = href.substringBefore('#').substringBefore('?')
        val decoded = URLDecoder.decode(pathOnly.replace("+", "%2B"), StandardCharsets.UTF_8.name())
        return normalizePath(if (directory.isEmpty()) decoded else "$directory/$decoded")
    }

    private fun normalizePath(path: String): String {
        val components = mutableListOf<String>()
        for (component in path.replace('\\', '/').split('/')) {
            when (component) {
                "", "." -> Unit
                ".." -> if (components.isNotEmpty()) components.removeAt(components.lastIndex)
                else -> components += component
            }
        }
        return components.joinToString("/")
    }

    private fun firstElementByLocalName(root: Element, localName: String): Element? =
        root.getAllElements().firstOrNull { it.localName().equals(localName, ignoreCase = true) }

    private fun Element.localName() = tagName().substringAfterLast(':').lowercase()

    private fun String.cleanWhitespace() = replace('\u00a0', ' ').replace(Regex("\\s+"), " ").trim()
}
