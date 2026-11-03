package com.ziaee.frenchreader.content.pdf

import android.content.Context
import com.ziaee.frenchreader.comprehension.LemmaLexicon
import com.ziaee.frenchreader.content.ImageTextExtractor
import com.ziaee.frenchreader.content.OcrDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Recognizes a whole scanned page so its words can be tapped on the page image itself.
 * Results are cached on disk per PDF file and page; the cache is safe to lose.
 */
class PdfPageOcr(context: Context, private val renderer: PdfPageRenderer, pdf: File) {
    private val app = context.applicationContext
    private val extractor = ImageTextExtractor(app)
    private val cacheDir = File(app.cacheDir, "pdf-ocr")
    private val key = pdf.nameWithoutExtension

    suspend fun words(page: Int): List<PdfWord> = withContext(Dispatchers.Default) {
        val cached = File(cacheDir, "v1-$key-$page.tsv")
        if (cached.isFile) return@withContext runCatching { decodeOcrWords(cached.readText()) }.getOrNull() ?: recognize(page, cached)
        recognize(page, cached)
    }

    private suspend fun recognize(page: Int, cached: File): List<PdfWord> {
        val corrector = runCatching { LemmaLexicon.get(app).getOcrCorrector() }.getOrNull()
        // ~2200 px wide reads body text well; renderRegionForOcr caps the bitmap at ~6 MP.
        val source = renderer.renderRegionForOcr(page, PdfRect(0f, 0f, 1f, 1f), 2200)
        val words = try {
            val ready = ocrReadyCopy(source)
            try {
                val document = extractor.extractStructured(ready).getOrThrow()
                ocrPageWords(document, ready.width.toFloat(), ready.height.toFloat()) { text, confidence ->
                    corrector?.correct(text, listOfNotNull(confidence?.let { OcrTokenConfidence(0, text.length, it) }))?.text ?: text
                }
            } finally { ready.recycle() }
        } finally { source.recycle() }
        withContext(Dispatchers.IO) {
            runCatching { cacheDir.mkdirs(); cached.writeText(encodeOcrWords(words)) }
        }
        return words
    }
}

/** Maps ML Kit word boxes (bitmap pixels) to page-normalised boxes in reading order. */
internal fun ocrPageWords(
    document: OcrDocument, width: Float, height: Float,
    correct: (String, Float?) -> String = { text, _ -> text }
): List<PdfWord> {
    val result = mutableListOf<PdfWord>()
    var textIndex = 0
    for (block in document.blocks) for (line in block) for (word in line.words) {
        val box = word.box ?: regionBounds(word.cornerPoints) ?: continue
        val text = correct(word.text, word.confidence ?: line.confidence).trim()
        if (text.isEmpty()) continue
        result += PdfWord(text, textIndex, PdfRect(
            (box.left / width).coerceIn(0f, 1f), (box.top / height).coerceIn(0f, 1f),
            (box.right / width).coerceIn(0f, 1f), (box.bottom / height).coerceIn(0f, 1f)))
        textIndex += text.length + 1
    }
    return result
}

internal fun encodeOcrWords(words: List<PdfWord>): String = words.joinToString("\n") { word ->
    val b = word.bounds
    "${word.text.replace('\t', ' ').replace('\n', ' ')}\t${b.left}\t${b.top}\t${b.right}\t${b.bottom}"
}

internal fun decodeOcrWords(encoded: String): List<PdfWord> {
    var textIndex = 0
    return encoded.lineSequence().filter { it.isNotEmpty() }.map { line ->
        val parts = line.split('\t')
        require(parts.size == 5) { "Bad OCR cache line" }
        PdfWord(parts[0], textIndex, PdfRect(parts[1].toFloat(), parts[2].toFloat(), parts[3].toFloat(), parts[4].toFloat()))
            .also { textIndex += it.text.length + 1 }
    }.toList()
}
