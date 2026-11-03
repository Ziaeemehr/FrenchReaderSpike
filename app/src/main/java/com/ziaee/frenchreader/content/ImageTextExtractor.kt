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

    suspend fun extractStructured(bitmap: Bitmap): Result<OcrDocument> = withContext(Dispatchers.IO) {
        try {
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            try {
                // Await the non-cancellable ML Kit task before the caller recycles the crop.
                val result = withContext(kotlinx.coroutines.NonCancellable) {
                    recognizer.process(InputImage.fromBitmap(bitmap, 0)).await()
                }
                fun box(rect: android.graphics.Rect?) = rect?.let {
                    com.ziaee.frenchreader.content.pdf.PdfRect(it.left.toFloat(), it.top.toFloat(), it.right.toFloat(), it.bottom.toFloat())
                }
                fun corners(points: Array<android.graphics.Point>?) = points.orEmpty().map {
                    com.ziaee.frenchreader.content.pdf.PdfPoint(it.x.toFloat(), it.y.toFloat())
                }
                Result.success(OcrDocument(result.textBlocks.map { block ->
                    block.lines.map { line ->
                        OcrLine(line.text, box(line.boundingBox), corners(line.cornerPoints),
                            line.confidence.takeIf { it in 0f..1f }, line.elements.map { word ->
                                OcrWord(word.text, box(word.boundingBox), corners(word.cornerPoints),
                                    word.confidence.takeIf { it in 0f..1f })
                            })
                    }
                }))
            } finally { recognizer.close() }
        } catch (cancelled: CancellationException) { throw cancelled
        } catch (error: Exception) { Result.failure(error) }
    }

    suspend fun extract(bitmap: Bitmap): Result<String> = extractStructured(bitmap).map { result ->
        PdfTextCleaner.clean(result.blocks.joinToString("\n\n") { block -> block.joinToString("\n") { it.text } }).trim()
    }

    companion object {
        const val MAX_IMAGES = 10
    }
}
