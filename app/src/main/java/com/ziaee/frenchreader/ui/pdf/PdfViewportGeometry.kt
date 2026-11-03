package com.ziaee.frenchreader.ui.pdf

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import kotlin.math.abs

internal data class PdfVisiblePage(val index: Int, val offset: Int, val size: Int)
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

/**
 * Zoom is a transform: pages stay laid out at screen width and their drawing is scaled by [zoom]
 * from the top-left corner and moved by [pan] (screen px, never positive), like a photo viewer.
 */
internal data class PdfView(val zoom: Float, val pan: Offset)

/** Keeps the scaled drawing covering the viewport. */
internal fun clampPan(zoom: Float, pan: Offset, viewport: Size): Offset = Offset(
    pan.x.coerceIn(-(zoom - 1f) * viewport.width, 0f), pan.y.coerceIn(-(zoom - 1f) * viewport.height, 0f))

/**
 * One pinch event: the content under the previous centroid ends up under the moved centroid.
 * Returns the new view and the vertical screen px the pan could not absorb; the caller scrolls
 * the page list by that amount instead.
 */
internal fun pinchView(view: PdfView, factor: Float, centroid: Offset, move: Offset, viewport: Size): Pair<PdfView, Float> {
    val zoom = (view.zoom * factor).coerceIn(1f, 4f)
    val wanted = centroid + move - (centroid - view.pan) * (zoom / view.zoom)
    val pan = clampPan(zoom, wanted, viewport)
    return PdfView(zoom, pan) to (wanted.y - pan.y)
}
