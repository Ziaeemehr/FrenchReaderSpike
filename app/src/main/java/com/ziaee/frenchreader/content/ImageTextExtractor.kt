package com.ziaee.frenchreader.content

import android.graphics.Bitmap
import kotlinx.coroutines.CancellationException
import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ImageTextExtractor(private val context: Context) {
    suspend fun extract(uris: List<Uri>): Result<String> = withContext(Dispatchers.IO) { runCatching {
        require(uris.isNotEmpty()) { "No images selected" }
        require(uris.size <= MAX_IMAGES) { "Too many images selected" }
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            val pages = uris.map { uri ->
                val result = recognizer.process(InputImage.fromFilePath(context, uri)).await()
                result.textBlocks.joinToString("\n\n") { block ->
                    block.lines.joinToString("\n") { it.text }
                }
            }
            pages.joinToString("\n\n") { PdfTextCleaner.clean(it) }.trim()
        } finally {
            recognizer.close()
        }
    } }

    suspend fun extract(bitmap: Bitmap): Result<String> = withContext(Dispatchers.IO) {
        try {
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            try {
                // ML Kit's task cannot be cancelled. Keep the caller's bitmap alive until
                // recognition completes, even when the reader is dismissed meanwhile.
                val result = withContext(kotlinx.coroutines.NonCancellable) {
                    recognizer.process(InputImage.fromBitmap(bitmap, 0)).await()
                }
                Result.success(PdfTextCleaner.clean(result.textBlocks.joinToString("\n\n") { block ->
                    block.lines.joinToString("\n") { it.text }
                }).trim())
            } finally {
                recognizer.close()
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    companion object {
        const val MAX_IMAGES = 10
    }
}
