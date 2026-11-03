package com.ziaee.frenchreader.content.pdf

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/** All renderer access, including close, shares one lock: only one page may be open. */
class PdfPageRenderer(file: File) {
    private val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    private val renderer = try { PdfRenderer(descriptor) } catch (error: Exception) { descriptor.close(); throw error }
    private val mutex = Mutex()
    private var closed = false
    val pageCount: Int = renderer.pageCount
    /** Height / width of every page, so unrendered pages already take their final size. */
    val pageAspects: List<Float> = (0 until pageCount).map { index ->
        renderer.openPage(index).use { page -> page.height.toFloat() / page.width.coerceAtLeast(1) }
    }
    private val cache = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount
    }

    suspend fun render(page: Int, width: Int): Bitmap = withContext(Dispatchers.IO) {
        mutex.withLock {
            check(!closed)
            val safeWidth = width.coerceIn(1, 4096)
            val key = "$page:$safeWidth"
            cache.get(key) ?: renderer.openPage(page).use { opened ->
                // Bound allocation on unusually tall pages as well as zoomed pages.
                val ratio = opened.height.toDouble() / opened.width
                val actualWidth = minOf(safeWidth, kotlin.math.sqrt(4_000_000.0 / ratio).toInt().coerceAtLeast(1))
                val height = (actualWidth * ratio).toInt().coerceAtLeast(1)
                Bitmap.createBitmap(actualWidth, height, Bitmap.Config.ARGB_8888).also {
                    it.eraseColor(Color.WHITE)
                    opened.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    cache.put(key, it)
                }
            }
        }
    }

    /**
     * Renders only [left, top, right, bottom] (page-normalised) of a page as if the whole page
     * were [pageWidth] pixels wide. Caller owns the uncached bitmap; memory is the region's,
     * not the page's, so OCR can use high detail on any phone.
     */
    suspend fun renderRegionForOcr(page: Int, region: PdfRect, pageWidth: Int): Bitmap = withContext(Dispatchers.IO) {
        mutex.withLock {
            check(!closed)
            renderer.openPage(page).use { opened ->
                val scale = pageWidth.coerceIn(1, 8192).toFloat() / opened.width
                val fullWidth = opened.width * scale
                val fullHeight = opened.height * scale
                val left = kotlin.math.floor(region.left * fullWidth)
                val top = kotlin.math.floor(region.top * fullHeight)
                var width = (kotlin.math.ceil(region.right * fullWidth) - left).toInt().coerceAtLeast(1)
                var height = (kotlin.math.ceil(region.bottom * fullHeight) - top).toInt().coerceAtLeast(1)
                // Hard cap of ~6 MP (24 MB) for very large lassos.
                val shrink = minOf(1.0, kotlin.math.sqrt(6_000_000.0 / (width.toDouble() * height)))
                val finalScale = (scale * shrink).toFloat()
                width = (width * shrink).toInt().coerceAtLeast(1)
                height = (height * shrink).toInt().coerceAtLeast(1)
                Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
                    try {
                        it.eraseColor(Color.WHITE)
                        val matrix = android.graphics.Matrix().apply {
                            setScale(finalScale, finalScale)
                            postTranslate((-left * shrink).toFloat(), (-top * shrink).toFloat())
                        }
                        opened.render(it, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    } catch (error: Throwable) { it.recycle(); throw error }
                }
            }
        }
    }

    suspend fun close() = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (!closed) {
                closed = true
                cache.evictAll() // Never recycle: Compose may still hold an evicted bitmap.
                renderer.close()
                descriptor.close()
            }
        }
    }
}
