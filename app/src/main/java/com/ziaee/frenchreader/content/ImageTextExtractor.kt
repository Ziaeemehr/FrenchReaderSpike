package com.ziaee.frenchreader.content

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

    companion object {
        const val MAX_IMAGES = 10
    }
}
