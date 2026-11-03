package com.ziaee.frenchreader.content.pdf

/** Display coordinates, normalized to the visible page, with the origin at top left. */
data class PdfRect(val left: Float, val top: Float, val right: Float, val bottom: Float)
/** textIndex is the character offset in the page text formed by joining words with spaces. */
data class PdfWord(val text: String, val textIndex: Int, val bounds: PdfRect)

fun hitTestWord(words: List<PdfWord>, x: Float, y: Float, slop: Float = 0f): PdfWord? =
    words.filter { word ->
        val b = word.bounds
        x >= b.left - slop && x <= b.right + slop && y >= b.top - slop && y <= b.bottom + slop
    }.minByOrNull { word ->
        val b = word.bounds
        val dx = (b.left + b.right) / 2 - x
        val dy = (b.top + b.bottom) / 2 - y
        dx * dx + dy * dy
    }

fun sentenceAround(words: List<PdfWord>, index: Int): String {
    if (index !in words.indices) return ""
    fun endsSentence(word: PdfWord) = word.text.trimEnd('"', '\'', '»', '”', ')', ']').lastOrNull() in listOf('.', '!', '?', '…')
    var first = index
    var last = index
    while (first > 0 && !endsSentence(words[first - 1])) first--
    while (last < words.lastIndex && !endsSentence(words[last])) last++
    return words.subList(first, last + 1).joinToString(" ") { it.text }
}

fun wordsInRect(words: List<PdfWord>, rect: PdfRect): List<PdfWord> = words.filter {
    it.bounds.left <= rect.right && it.bounds.right >= rect.left &&
        it.bounds.top <= rect.bottom && it.bounds.bottom >= rect.top
}

/** PDFBox DirAdj values follow glyph direction. Apply only the remaining page rotation. */
fun normalizePdfBounds(bounds: PdfRect, pageWidth: Float, pageHeight: Float, textDirection: Int, pageRotation: Int): PdfRect {
    val direction = ((textDirection % 360) + 360) % 360
    val width = if (direction == 90 || direction == 270) pageHeight else pageWidth
    val height = if (direction == 90 || direction == 270) pageWidth else pageHeight
    val rotation = ((pageRotation - direction) % 360 + 360) % 360
    fun point(x: Float, y: Float): Pair<Float, Float> {
        val nx = x / width
        val ny = y / height
        return when (rotation) {
            90 -> 1f - ny to nx
            180 -> 1f - nx to 1f - ny
            270 -> ny to 1f - nx
            else -> nx to ny
        }
    }
    val corners = listOf(point(bounds.left, bounds.top), point(bounds.right, bounds.top),
        point(bounds.left, bounds.bottom), point(bounds.right, bounds.bottom))
    return PdfRect(corners.minOf { it.first }.coerceIn(0f, 1f), corners.minOf { it.second }.coerceIn(0f, 1f),
        corners.maxOf { it.first }.coerceIn(0f, 1f), corners.maxOf { it.second }.coerceIn(0f, 1f))
}
