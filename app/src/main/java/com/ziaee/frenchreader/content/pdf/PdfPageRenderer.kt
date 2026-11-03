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
