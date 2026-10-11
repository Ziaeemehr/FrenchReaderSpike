package com.ziaee.frenchreader.share

/**
 * Text with bold ranges (UTF-16 indices). [rtl] fixes the paragraph direction; left null, the text
 * is right-to-left as soon as it holds any Persian letter, because a Persian sentence often starts
 * with a French term.
 */
data class PdfText(val text: String, val bold: List<IntRange> = emptyList(), val rtl: Boolean? = null) {
    val isRtl: Boolean get() = rtl ?: text.any {
        val direction = Character.getDirectionality(it)
        direction == Character.DIRECTIONALITY_RIGHT_TO_LEFT || direction == Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC
    }
}

/** What a PDF export is made of. Pure data, so building it from content is unit-testable. */
sealed interface PdfBlock {
    data class Heading(val text: String, val level: Int) : PdfBlock
    data class Paragraph(val text: PdfText, val quote: Boolean = false) : PdfBlock
    data class Table(val header: List<PdfText>, val rows: List<List<PdfText>>) : PdfBlock
}

/**
 * Turns `code` spans into left-to-right isolates, so a French span inside a Persian line keeps its
 * word order, and drops the backticks.
 */
fun pdfInline(text: String): String = buildString {
    var i = 0
    while (i < text.length) {
        val end = if (text[i] == '`') text.indexOf('`', i + 1) else -1
        if (end > i) {
            append('⁦').append(text, i + 1, end).append('⁩')
            i = end + 1
        } else append(text[i++])
    }
}
