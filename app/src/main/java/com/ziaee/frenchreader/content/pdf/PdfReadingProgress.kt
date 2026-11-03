package com.ziaee.frenchreader.content.pdf

/** Unknown progress is distinct from the first page of an opened PDF. */
data class PdfReadingProgress(val page: Int, val pageCount: Int) {
    val fraction: Float
        get() = if (pageCount <= 0 || page < 0) 0f
        else ((page.toLong() + 1).toFloat() / pageCount).coerceIn(0f, 1f)
    val finished: Boolean get() = pageCount > 0 && page >= 0 && fraction >= 1f
}
