package com.ziaee.frenchreader.content.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import com.ziaee.frenchreader.content.ImageTextExtractor
import com.ziaee.frenchreader.comprehension.LemmaLexicon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class PdfOcrResult(val original: String, val corrected: OcrCorrection)

class PdfRegionOcr(context: Context) {
    private val extractor = ImageTextExtractor(context.applicationContext)
    private val lexicon = LemmaLexicon.get(context)

    suspend fun extract(renderer: PdfPageRenderer, page: Int, screenWidth: Int, path: List<PdfPoint>): String =
        extractRegion(renderer, page, screenWidth, path).original

    suspend fun extractRegion(renderer: PdfPageRenderer, page: Int, screenWidth: Int, path: List<PdfPoint>): PdfOcrResult =
        withContext(Dispatchers.Default) {
            val bounds = OcrRegionFilter.expanded(requireNotNull(regionBounds(path)) { "Empty region" })
            val width = OcrRegionFilter.renderWidth(bounds, renderer.pageAspects[page], screenWidth)
            // Only the region is rendered; map crop pixels back to page-normalised coordinates.
            val source = renderer.renderRegionForOcr(page, bounds, width)
            try {
                val cropped = ocrReadyCopy(source)
                val regionWidth = bounds.right - bounds.left
                val regionHeight = bounds.bottom - bounds.top
                try {
                    fun pageBox(box: PdfRect?) = box?.let {
                        PdfRect(bounds.left + it.left / source.width * regionWidth, bounds.top + it.top / source.height * regionHeight,
                            bounds.left + it.right / source.width * regionWidth, bounds.top + it.bottom / source.height * regionHeight)
                    }
                    val recognized = extractor.extractStructured(cropped).getOrThrow()
                    val text = StringBuilder()
                    val confidence = mutableListOf<OcrTokenConfidence>()
                    for (block in recognized.blocks) {
                        val blockStart = text.length
                        for (line in block) {
                            val box = pageBox(line.box ?: regionBounds(line.cornerPoints)) ?: continue
                            if (!OcrRegionFilter.keepLine(box, path)) continue
                            // The crop extends 1% of the page past the lasso, so a word wholly inside
                            // the lasso stays that far from the crop edge; one reaching within half of
                            // that margin runs past the edge and was cut ("mais" read as "m").
                            val edge = 0.005f * source.width / regionWidth
                            val range = OcrRegionFilter.wordRange(line.words.map { pageBox(it.box ?: regionBounds(it.cornerPoints)) }, path) { index ->
                                // Box in crop pixels touching the crop's left/right edge: the word was cut.
                                val box = line.words[index].box ?: return@wordRange false
                                box.left <= edge || box.right >= cropped.width - edge
                            }
                            if (line.words.isNotEmpty() && range.isEmpty()) continue
                            if (text.length > blockStart) text.append('\n')
                            if (line.words.isEmpty()) text.append(line.text)
                            else for (index in range) {
                                if (index > range.first) text.append(' ')
                                val word = line.words[index]
                                val start = text.length
                                text.append(word.text)
                                (word.confidence ?: line.confidence)?.let {
                                    confidence += OcrTokenConfidence(start, text.length, it)
                                }
                            }
                        }
                        if (text.length > blockStart) text.append("\n\n")
                    }
                    val raw = text.toString().trim()
                    PdfOcrResult(raw, lexicon.getOcrCorrector().correct(raw, confidence))
                } finally { cropped.recycle() }
            } finally { source.recycle() }
        }
}

/** Grayscale, mildly contrast-stretched copy of a rendered page area: ML Kit reads it more reliably. */
internal fun ocrReadyCopy(source: Bitmap): Bitmap {
    val copy = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
    val grayscale = ColorMatrix().apply { setSaturation(0f) }
    val contrast = ColorMatrix(floatArrayOf(
        1.12f,0f,0f,0f,-15f, 0f,1.12f,0f,0f,-15f,
        0f,0f,1.12f,0f,-15f, 0f,0f,0f,1f,0f))
    contrast.postConcat(grayscale)
    Canvas(copy).drawBitmap(source, 0f, 0f, Paint().apply { colorFilter = ColorMatrixColorFilter(contrast) })
    return copy
}
