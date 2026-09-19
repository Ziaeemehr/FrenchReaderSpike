package com.ziaee.frenchreader.images

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

private const val MAX_IMAGE_BYTES = 8L * 1024 * 1024
private const val MAX_IMAGE_WIDTH = 1080
private const val JPEG_QUALITY = 82
internal const val IMAGES_DIR_NAME = "text_images"

/** Downloads, bounds/type-checks, scales, and stores one document's lead
 * image. Testable in isolation via the [ArticleImageStorage] seam --
 * [ArticleImportRepository] never touches Android APIs directly. */
interface ArticleImageStorage {
    /** Downloads [imageUrl], scales it to [MAX_IMAGE_WIDTH], and writes it as
     * a JPEG under `filesDir/text_images/<documentId>.jpg`. Returns the
     * relative path on success, or null (deleting any partial file) if the
     * URL isn't reachable, isn't an image, exceeds the size bound, or fails
     * to decode -- a missing image is never fatal to an import. */
    suspend fun downloadAndStore(documentId: Long, imageUrl: String): String?

    /** Decodes, scales, and stores image bytes at an owned relative path. */
    suspend fun storeBytes(relativePath: String, bytes: ByteArray): String?

    /** Deletes an image at a relative path this store previously returned.
     * Refuses (returns false) any path outside its own images directory. */
    suspend fun delete(relativePath: String): Boolean
}

class ArticleImageStore(private val context: Context) : ArticleImageStorage {

    override suspend fun downloadAndStore(documentId: Long, imageUrl: String): String? = withContext(Dispatchers.IO) {
        try {
            val bytes = downloadBounded(imageUrl) ?: return@withContext null
            storeBytes("$IMAGES_DIR_NAME/$documentId.jpg", bytes)
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun storeBytes(relativePath: String, bytes: ByteArray): String? = withContext(Dispatchers.IO) {
        var file: File? = null
        try {
            val ownedFile = resolveOwned(relativePath) ?: return@withContext null
            file = ownedFile
            if (bytes.size > MAX_IMAGE_BYTES) return@withContext null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null
            val (targetWidth, targetHeight) = scaledDimensions(bounds.outWidth, bounds.outHeight, MAX_IMAGE_WIDTH)
            val options = BitmapFactory.Options().apply { inSampleSize = sampleSizeFor(bounds.outWidth, targetWidth) }
            val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return@withContext null
            val scaled = if (decoded.width != targetWidth || decoded.height != targetHeight) {
                Bitmap.createScaledBitmap(decoded, targetWidth, targetHeight, true)
            } else decoded
            ownedFile.parentFile?.mkdirs()
            val written = FileOutputStream(ownedFile).use { scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
            if (scaled !== decoded) decoded.recycle()
            scaled.recycle()
            if (!written) {
                ownedFile.delete()
                null
            } else relativePath
        } catch (e: Exception) {
            file?.delete()
            null
        }
    }

    override suspend fun delete(relativePath: String): Boolean = withContext(Dispatchers.IO) {
        resolveOwned(relativePath)?.delete() ?: false
    }

    /** Refuses to touch any path outside `filesDir/text_images/` -- guards
     * against a malformed or malicious relative path escaping via `..`. */
    private fun resolveOwned(relativePath: String): File? {
        val dir = imagesDir().canonicalFile
        val file = File(context.filesDir, relativePath).canonicalFile
        return file.takeIf { it.path.startsWith(dir.path + File.separator) }
    }

    private fun imagesDir(): File = File(context.filesDir, IMAGES_DIR_NAME)

    private fun downloadBounded(urlString: String): ByteArray? {
        val connection = URL(urlString).openConnection() as HttpURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 15_000
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Android; FrenchReaderApp)")
        try {
            val code = connection.responseCode
            if (code !in 200..299) return null
            if (!connection.contentType.orEmpty().startsWith("image/", ignoreCase = true)) return null
            val declaredLength = connection.contentLengthLong
            if (declaredLength > MAX_IMAGE_BYTES) return null

            val buffer = ByteArrayOutputStream()
            val chunk = ByteArray(8192)
            var total = 0L
            connection.inputStream.use { input ->
                while (true) {
                    val read = input.read(chunk)
                    if (read == -1) break
                    total += read
                    if (total > MAX_IMAGE_BYTES) return null
                    buffer.write(chunk, 0, read)
                }
            }
            return buffer.toByteArray()
        } finally {
            connection.disconnect()
        }
    }
}

/** Pure scaling math, split out so it's testable on the plain JVM without
 * Robolectric/instrumentation (unlike the bitmap decode/download above). */
internal fun scaledDimensions(width: Int, height: Int, maxWidth: Int): Pair<Int, Int> {
    if (width <= maxWidth || width <= 0) return width to height
    val scaledHeight = ((height.toLong() * maxWidth) / width).toInt()
    return maxWidth to scaledHeight
}

private fun sampleSizeFor(sourceWidth: Int, targetWidth: Int): Int {
    if (targetWidth <= 0) return 1
    var sample = 1
    while (sourceWidth / (sample * 2) >= targetWidth) sample *= 2
    return sample
}
