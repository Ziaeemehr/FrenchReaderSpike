package com.ziaee.frenchreader.content.pdf

/** All coordinates use the same space (normally normalized full-page coordinates). */
object OcrRegionFilter {
    fun expanded(box: PdfRect) = PdfRect((box.left - .01f).coerceAtLeast(0f),
        (box.top - .025f).coerceAtLeast(0f), (box.right + .01f).coerceAtMost(1f),
        (box.bottom + .025f).coerceAtMost(1f))

    fun renderWidth(box: PdfRect, pageAspect: Float, screenWidth: Int): Int {
        require(pageAspect.isFinite() && pageAspect > 0)
        val longest = maxOf(box.right - box.left, (box.bottom - box.top) * pageAspect)
        require(longest > 0)
        return maxOf(screenWidth.coerceIn(1, 4096) * 2, kotlin.math.ceil(1600 / longest).toInt()).coerceAtMost(8192)
    }

    fun contains(point: PdfPoint, polygon: List<PdfPoint>): Boolean {
        if (polygon.size < 3) return false
        var inside = false
        var previous = polygon.last()
        for (current in polygon) {
            val cross = (point.x - previous.x) * (current.y - previous.y) -
                (point.y - previous.y) * (current.x - previous.x)
            if (kotlin.math.abs(cross) < 1e-7f && point.x >= minOf(previous.x, current.x) &&
                point.x <= maxOf(previous.x, current.x) && point.y >= minOf(previous.y, current.y) &&
                point.y <= maxOf(previous.y, current.y)) return true
            if ((current.y > point.y) != (previous.y > point.y) &&
                point.x < (previous.x - current.x) * (point.y - current.y) / (previous.y - current.y) + current.x) inside = !inside
            previous = current
        }
        return inside
    }

    fun overlap(box: PdfRect, polygon: List<PdfPoint>): Float {
        if (box.right <= box.left || box.bottom <= box.top) return 0f
        var count = 0
        for (x in 0 until 5) for (y in 0 until 3) {
            if (contains(PdfPoint(box.left + (x + .5f) / 5 * (box.right - box.left),
                    box.top + (y + .5f) / 3 * (box.bottom - box.top)), polygon)) count++
        }
        return count / 15f
    }

    fun keepLine(box: PdfRect, polygon: List<PdfPoint>) = overlap(box, polygon) >= .5f

    /**
     * Trims words at either end of a line that the lasso leaves out, and words the crop edge
     * cut in half ([truncated]) — OCR of half a word is noise like a lone "m".
     */
    fun wordRange(words: List<PdfRect?>, polygon: List<PdfPoint>, truncated: (Int) -> Boolean = { false }): IntRange {
        val bounds = regionBounds(polygon) ?: return words.indices
        fun excluded(index: Int): Boolean {
            if (truncated(index)) return true
            val box = words[index] ?: return false
            val centreX = (box.left + box.right) / 2
            return (centreX < bounds.left || centreX > bounds.right) &&
                !contains(PdfPoint(centreX, (box.top + box.bottom) / 2), polygon)
        }
        var first = 0
        var last = words.lastIndex
        while (first <= last && excluded(first)) first++
        while (last >= first && excluded(last)) last--
        return first..last
    }
}
