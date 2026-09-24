package com.ziaee.frenchreader.util

enum class IncomingContentKind { PDF, IMAGE, EPUB, TEXT }

fun classifyIncomingContent(mimeType: String?, displayName: String?): IncomingContentKind {
    val normalizedMime = mimeType?.lowercase()
    return when {
        normalizedMime == "application/pdf" -> IncomingContentKind.PDF
        normalizedMime?.startsWith("image/") == true -> IncomingContentKind.IMAGE
        normalizedMime == "application/epub+zip" -> IncomingContentKind.EPUB
        normalizedMime?.startsWith("text/") == true -> IncomingContentKind.TEXT
        displayName?.endsWith(".pdf", ignoreCase = true) == true -> IncomingContentKind.PDF
        displayName?.endsWith(".epub", ignoreCase = true) == true -> IncomingContentKind.EPUB
        else -> IncomingContentKind.TEXT
    }
}
