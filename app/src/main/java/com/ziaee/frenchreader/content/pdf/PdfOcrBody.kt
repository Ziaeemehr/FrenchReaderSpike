package com.ziaee.frenchreader.content.pdf

// Exact legacy import placeholders in every supported locale. Do not match substrings:
// a user may have edited or extended their body since importing the PDF.
private val scannedPdfPlaceholders = setOf(
    "Scanned PDF. Switch to PDF view to read the pages.",
    "PDF numérisé. Passez à la vue PDF pour lire les pages.",
    "پی‌دی‌اف اسکن‌شده. برای خواندن صفحه‌ها نمای پی‌دی‌اف را باز کنید."
)

fun isScannedPdfPlaceholder(body: String): Boolean = body in scannedPdfPlaceholders

fun appendPdfOcrText(body: String, recognized: String): String = when {
    recognized.isBlank() -> body
    body.isBlank() || isScannedPdfPlaceholder(body) -> recognized.trim()
    else -> body + "\n\n" + recognized.trim()
}
