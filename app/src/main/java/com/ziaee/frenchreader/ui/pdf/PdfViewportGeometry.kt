package com.ziaee.frenchreader.ui.pdf

import kotlin.math.abs

internal data class PdfVisiblePage(val index: Int, val offset: Int, val size: Int)
internal data class PdfZoomAnchor(val page: Int, val pageScrollOffset: Float, val horizontal: Float)

/** Scroll after scaling so the content under the focal point stays under it. */
internal fun focalScrollOffset(scroll: Float, focal: Float, oldZoom: Float, newZoom: Float): Float =
    (scroll + focal) * (newZoom / oldZoom) - focal

/** Offset within one page, independent of the heights of all preceding pages. */
internal fun focalPageZoomOffset(pageTop: Float, focal: Float, oldZoom: Float, newZoom: Float): Float =
    (focal - pageTop) * (newZoom / oldZoom) - focal

internal fun pageAtCentre(pages: List<PdfVisiblePage>, viewportStart: Int, viewportEnd: Int): Int? {
    val centre = (viewportStart + viewportEnd) / 2f
    return pages.minByOrNull {
        when {
            centre < it.offset -> it.offset - centre
            centre > it.offset + it.size -> centre - it.offset - it.size
            else -> 0f
        }
    }?.index
}
