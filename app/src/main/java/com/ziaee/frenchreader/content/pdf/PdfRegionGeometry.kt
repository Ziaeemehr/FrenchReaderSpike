package com.ziaee.frenchreader.content.pdf

data class PdfPoint(val x: Float, val y: Float)

/** Coordinates are local to the page, including when Compose scales or pans it. */
fun normalizeRegionPath(points: List<PdfPoint>, width: Float, height: Float): List<PdfPoint> {
    require(width.isFinite() && height.isFinite() && width > 0f && height > 0f)
    require(points.all { it.x.isFinite() && it.y.isFinite() })
    return points.map { PdfPoint((it.x / width).coerceIn(0f, 1f), (it.y / height).coerceIn(0f, 1f)) }
}

fun regionBounds(points: List<PdfPoint>): PdfRect? {
    if (points.size < 3 || points.any { !it.x.isFinite() || !it.y.isFinite() }) return null
    val bounds = PdfRect(points.minOf { it.x }, points.minOf { it.y }, points.maxOf { it.x }, points.maxOf { it.y })
    return bounds.takeIf { it.right > it.left && it.bottom > it.top }
}
