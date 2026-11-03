package com.ziaee.frenchreader.content

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

data class PdfImport(val text: String, val pdfPath: String)

class TextExtractionController(private val context: Context) {
    private val pdfExtractor = PdfTextExtractor(context)
    private val imageExtractor = ImageTextExtractor(context)

    var isExtracting by mutableStateOf(false)
        private set

    suspend fun extractPdf(uri: Uri): Result<PdfImport> {
        if (isExtracting) return Result.failure(IllegalStateException("An import is already running"))
        isExtracting = true
        return try {
            val text = pdfExtractor.extract(uri).getOrThrow()
            val path = com.ziaee.frenchreader.content.pdf.PdfFileStore.copy(context, uri)
            Result.success(PdfImport(text, path))
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Result.failure(error)
        } finally {
            isExtracting = false
        }
    }

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
