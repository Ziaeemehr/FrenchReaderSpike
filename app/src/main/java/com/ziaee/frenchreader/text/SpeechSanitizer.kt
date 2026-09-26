package com.ziaee.frenchreader.text

/**
 * Reduces text to code-point categories that are useful to speech and
 * translation while retaining natural-language scripts, punctuation, and a
 * small set of commonly spoken symbols. Emoji presentation selectors,
 * keycap enclosers, joiners, pictographs, arrows, and decorative symbols are
 * deliberately omitted.
 */
fun sanitizeForSpeech(text: String): String {
    val out = StringBuilder(text.length)
    var pendingSpace = false
    var index = 0

    while (index < text.length) {
        val codePoint = Character.codePointAt(text, index)
        index += Character.charCount(codePoint)

        if (codePoint == '\r'.code) {
            if (index < text.length && text[index] == '\n') index++
            trimTrailingSpace(out)
            out.append('\n')
            pendingSpace = false
            continue
        }
        if (codePoint == '\n'.code) {
            trimTrailingSpace(out)
            out.append('\n')
            pendingSpace = false
            continue
        }

        val type = Character.getType(codePoint)
        if (codePoint == '\t'.code || Character.isWhitespace(codePoint) || isSeparator(type)) {
            pendingSpace = true
            continue
        }

        if (!isAllowedCodePoint(codePoint, type)) continue

        if (pendingSpace && out.isNotEmpty() && out.last() != '\n') out.append(' ')
        out.appendCodePoint(codePoint)
        pendingSpace = false
    }

    trimTrailingSpace(out)
    return out.toString().trim('\n')
}

private fun isAllowedCodePoint(codePoint: Int, type: Int): Boolean {
    if (codePoint < 0x80) return !Character.isISOControl(codePoint)
    if (codePoint == 0x20E3 || isVariationSelector(codePoint) || codePoint in 0xE0020..0xE007F) return false

    return when (type) {
        Character.UPPERCASE_LETTER.toInt(),
        Character.LOWERCASE_LETTER.toInt(),
        Character.TITLECASE_LETTER.toInt(),
        Character.MODIFIER_LETTER.toInt(),
        Character.OTHER_LETTER.toInt(),
        Character.NON_SPACING_MARK.toInt(),
        Character.COMBINING_SPACING_MARK.toInt(),
        Character.ENCLOSING_MARK.toInt(),
        Character.DECIMAL_DIGIT_NUMBER.toInt(),
        Character.LETTER_NUMBER.toInt(),
        Character.OTHER_NUMBER.toInt(),
        Character.CONNECTOR_PUNCTUATION.toInt(),
        Character.DASH_PUNCTUATION.toInt(),
        Character.START_PUNCTUATION.toInt(),
        Character.END_PUNCTUATION.toInt(),
        Character.INITIAL_QUOTE_PUNCTUATION.toInt(),
        Character.FINAL_QUOTE_PUNCTUATION.toInt(),
        Character.OTHER_PUNCTUATION.toInt() -> true
        else -> codePoint in SPEAKABLE_SYMBOLS
    }
}

private fun isSeparator(type: Int): Boolean = when (type) {
    Character.SPACE_SEPARATOR.toInt(),
    Character.LINE_SEPARATOR.toInt(),
    Character.PARAGRAPH_SEPARATOR.toInt() -> true
    else -> false
}

private fun isVariationSelector(codePoint: Int): Boolean =
    codePoint in 0xFE00..0xFE0F || codePoint in 0xE0100..0xE01EF

private fun trimTrailingSpace(text: StringBuilder) {
    if (text.isNotEmpty() && text.last() == ' ') text.setLength(text.length - 1)
}

private val SPEAKABLE_SYMBOLS = setOf(
    '€'.code,
    '£'.code,
    '¥'.code,
    '‰'.code,
    '°'.code,
    '§'.code,
    '©'.code,
    '®'.code,
    '™'.code,
    '×'.code,
    '÷'.code,
    '±'.code,
    '½'.code,
    '¼'.code,
    '¾'.code
)

/**
 * The on-screen counterpart of [sanitizeForSpeech]: collapses whitespace the
 * same way but keeps emoji, arrows and other symbols (with the joiners and
 * variation selectors they need to render), dropping only control,
 * private-use and invisible format characters. [sanitizeForSpeech] applied to
 * this result gives the same string as applying it to the original text, so
 * the spoken text -- and every TTS/translation cache key -- is unchanged.
 */
fun sanitizeForDisplay(text: String): String {
    val out = StringBuilder(text.length)
    var pendingSpace = false
    var index = 0

    while (index < text.length) {
        val codePoint = Character.codePointAt(text, index)
        index += Character.charCount(codePoint)

        if (codePoint == '\n'.code || codePoint == '\r'.code) {
            if (codePoint == '\r'.code && index < text.length && text[index] == '\n') index++
            trimTrailingSpace(out)
            out.append('\n')
            pendingSpace = false
            continue
        }

        val type = Character.getType(codePoint)
        if (codePoint == '\t'.code || Character.isWhitespace(codePoint) || isSeparator(type)) {
            pendingSpace = true
            continue
        }

        val keep = when (type) {
            Character.CONTROL.toInt(),
            Character.PRIVATE_USE.toInt(),
            Character.SURROGATE.toInt(),
            Character.UNASSIGNED.toInt() -> false
            Character.FORMAT.toInt() ->
                codePoint == 0x200D || codePoint in 0xE0020..0xE007F
            else -> true
        }
        if (!keep) continue

        if (pendingSpace && out.isNotEmpty() && out.last() != '\n') out.append(' ')
        out.appendCodePoint(codePoint)
        pendingSpace = false
    }

    trimTrailingSpace(out)
    return out.toString().trim('\n')
}
