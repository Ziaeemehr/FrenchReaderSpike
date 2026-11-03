package com.ziaee.frenchreader.content.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Path
import com.ziaee.frenchreader.content.ImageTextExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.ceil
import kotlin.math.floor

class PdfRegionOcr(context: Context) {
    private val extractor = ImageTextExtractor(context.applicationContext)

    suspend fun extract(renderer: PdfPageRenderer, page: Int, screenWidth: Int, path: List<PdfPoint>): String =
        withContext(Dispatchers.Default) {
            val bounds = requireNotNull(regionBounds(path)) { "Empty region" }
            val source = renderer.render(page, (screenWidth.coerceIn(1, 4096) * 2).coerceAtMost(4096))
            val left = floor(bounds.left * source.width).toInt().coerceIn(0, source.width - 1)
            val top = floor(bounds.top * source.height).toInt().coerceIn(0, source.height - 1)
            val right = ceil(bounds.right * source.width).toInt().coerceIn(left + 1, source.width)
            val bottom = ceil(bounds.bottom * source.height).toInt().coerceIn(top + 1, source.height)
            val cropped = Bitmap.createBitmap(right - left, bottom - top, Bitmap.Config.ARGB_8888)
            try {
                val mask = Path().apply {
                    path.forEachIndexed { index, point ->
                        val x = point.x * source.width - left
                        val y = point.y * source.height - top
                        if (index == 0) moveTo(x, y) else lineTo(x, y)
                    }
                    close()
                }
                Canvas(cropped).apply {
                    drawColor(Color.WHITE)
                    save()
                    clipPath(mask)
                    drawBitmap(source, -left.toFloat(), -top.toFloat(), null)
                    restore()
                }
                extractor.extract(cropped).getOrThrow()
            } finally {
                // The full-page bitmap belongs to the renderer cache.
                cropped.recycle()
            }
        }
}
