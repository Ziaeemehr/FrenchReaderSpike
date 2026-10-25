package com.ziaee.frenchreader.translate

/** Keep the request small while retaining the previous paragraph's final two sentences. */
internal fun paragraphTranslationContext(text: String, before: String?): String? {
    val sentences = before?.trim()?.takeIf { it.isNotEmpty() }
        ?.split(Regex("(?<=[.!?؟。！？])\\s+")) ?: return null
    val context = sentences.takeLast(2).joinToString(" ")
    return context.takeIf { text.length + it.length + 1 <= 1800 }
}

/** Auto detection can follow the context's language instead of the target paragraph's. */
internal fun shouldFallbackFromParagraphContext(text: String, translated: String): Boolean =
    TranslationService.isSameText(translated, text)
