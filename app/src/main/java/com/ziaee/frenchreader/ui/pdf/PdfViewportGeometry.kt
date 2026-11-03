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

/** Where a pinch wants the view: zoom, top page, viewport top inside that page, sideways scroll (px). */
internal data class PdfPinchTarget(val zoom: Float, val page: Int, val pageOffset: Float, val horizontal: Float)

/**
 * One pinch event applied to the target, not to the laid-out view (which lags a few frames):
 * the content point under the previous centroid ends up under the moved centroid.
 * [pageHeight] gives a page's height plus the gap after it at the given zoom.
 */
internal fun pinchTarget(
    target: PdfPinchTarget, factor: Float, centroid: androidx.compose.ui.geometry.Offset, pan: androidx.compose.ui.geometry.Offset,
    viewportWidth: Float, pageCount: Int, pageHeight: (page: Int, zoom: Float) -> Float
): PdfPinchTarget {
    val zoom = (target.zoom * factor).coerceIn(1f, 4f)
    val ratio = zoom / target.zoom
    val horizontal = ((target.horizontal + centroid.x) * ratio - centroid.x - pan.x).coerceIn(0f, viewportWidth * (zoom - 1f))
    var page = target.page
    var offset = (target.pageOffset + centroid.y) * ratio - centroid.y - pan.y
    while (offset < 0f && page > 0) { page--; offset += pageHeight(page, zoom) }
    while (page < pageCount - 1 && offset >= pageHeight(page, zoom)) { offset -= pageHeight(page, zoom); page++ }
    return PdfPinchTarget(zoom, page, offset.coerceAtLeast(0f), horizontal)
}
