package com.ziaee.frenchreader.content

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class TextExtractionController(context: Context) {
    private val pdfExtractor = PdfTextExtractor(context)
    private val imageExtractor = ImageTextExtractor(context)

    var isExtracting by mutableStateOf(false)
        private set

    suspend fun extractPdf(uri: Uri): Result<String> = extract { pdfExtractor.extract(uri) }

    suspend fun extractImages(uris: List<Uri>): Result<String> = extract { imageExtractor.extract(uris) }

    private suspend fun extract(block: suspend () -> Result<String>): Result<String> {
        if (isExtracting) return Result.failure(IllegalStateException("An import is already running"))
        isExtracting = true
        return try {
            block()
        } finally {
            isExtracting = false
        }
    }
}
