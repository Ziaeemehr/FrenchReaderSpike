package com.ziaee.frenchreader.content

import android.content.Context
import android.net.Uri
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.ziaee.frenchreader.util.MAX_TEXT_IMPORT_BYTES
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PdfTextExtractor(private val context: Context) {
    suspend fun extract(uri: Uri): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            PDFBoxResourceLoader.init(context.applicationContext)
            val input = context.contentResolver.openInputStream(uri)
                ?: error("Unable to open PDF")
            input.use { stream ->
                PDDocument.load(stream).use { document ->
                    require(!document.isEncrypted) { "Encrypted PDFs are not supported" }
                    require(document.numberOfPages <= MAX_PDF_PAGES) {
                        "PDF exceeds the $MAX_PDF_PAGES page limit"
                    }
                    val stripper = PDFTextStripper().apply {
                        sortByPosition = true
                        addMoreFormatting = true
                        // Default paragraph markers are empty, which makes
                        // paragraph breaks indistinguishable from line wraps.
                        paragraphEnd = lineSeparator
                    }
                    val pages = ArrayList<String>(document.numberOfPages)
                    var characterCount = 0L
                    for (page in 1..document.numberOfPages) {
                        stripper.startPage = page
                        stripper.endPage = page
                        val text = stripper.getText(document)
                        characterCount += text.length
                        require(characterCount <= MAX_TEXT_IMPORT_BYTES) { "PDF text is too large" }
                        pages += text
                    }
                    PdfTextCleaner.cleanPages(pages)
                }
            }
        }
    }

    private companion object {
        const val MAX_PDF_PAGES = 300
    }
}
