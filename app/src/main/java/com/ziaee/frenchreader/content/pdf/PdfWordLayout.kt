package com.ziaee.frenchreader.content.pdf

import android.content.Context
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/** Reads one page lazily. No PDFBox document is retained after extraction. */
class PdfWordLayout(private val context: Context, private val file: File) {
    private val mutex = Mutex()
    private val cache = object : LinkedHashMap<Int, List<PdfWord>>(16, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, List<PdfWord>>?): Boolean = size > 12
    }

    suspend fun words(pageIndex: Int): List<PdfWord> = withContext(Dispatchers.IO) {
        mutex.withLock {
            cache[pageIndex] ?: extract(pageIndex).also { cache[pageIndex] = it }
        }
    }

    suspend fun pageText(pageIndex: Int): String = words(pageIndex).joinToString(" ") { it.text }

    private fun extract(pageIndex: Int): List<PdfWord> {
        PDFBoxResourceLoader.init(context.applicationContext)
        return PDDocument.load(file).use { document ->
            val page = document.getPage(pageIndex)
            val box = page.cropBox ?: page.mediaBox
            val result = mutableListOf<PdfWord>()
            var textIndex = 0
            val stripper = object : PDFTextStripper() {
                override fun writeString(text: String, textPositions: MutableList<TextPosition>) {
                    var token = StringBuilder()
                    var bounds: PdfRect? = null
                    var previous: TextPosition? = null
                    fun flush() {
                        if (token.isNotEmpty() && bounds != null) {
                            result += PdfWord(token.toString(), textIndex, bounds!!)
                            textIndex += token.length + 1
                        }
                        token = StringBuilder()
                        bounds = null
                    }
                    textPositions.forEach { position ->
                        val value = position.unicode.orEmpty()
                        val old = previous
                        val gap = old != null && (kotlin.math.abs(position.yDirAdj - old.yDirAdj) > old.heightDir * .6f ||
                            position.xDirAdj - (old.xDirAdj + old.widthDirAdj) > position.widthOfSpace * .45f)
                        if (gap || value.isBlank()) flush()
                        if (value.isNotBlank()) {
                            // DirAdj is already oriented along the glyph baseline. Normalize
                            // with only the remaining page rotation (zero when they match).
                            val b = normalizePdfBounds(PdfRect(position.xDirAdj,
                                position.yDirAdj - position.heightDir,
                                position.xDirAdj + position.widthDirAdj, position.yDirAdj),
                                box.width, box.height, position.dir.toInt(), page.rotation)
                            val prior = bounds
                            bounds = if (prior == null) b else PdfRect(minOf(prior.left, b.left), minOf(prior.top, b.top), maxOf(prior.right, b.right), maxOf(prior.bottom, b.bottom))
                            token.append(value)
                        }
                        previous = position
                    }
                    flush()
                }
            }.apply { sortByPosition = false; startPage = pageIndex + 1; endPage = pageIndex + 1 }
            stripper.getText(document)
            result
        }
    }
}
