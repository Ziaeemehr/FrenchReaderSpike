package com.ziaee.frenchreader.text

/**
 * True when the first strongly-directional character of [text] is right-to-left
 * (Persian, Arabic, Hebrew). Digits, punctuation, emoji and Markdown symbols are
 * neutral and skipped; text with no strong character counts as left-to-right.
 */
fun isRtlText(text: String): Boolean {
    var i = 0
    while (i < text.length) {
        val cp = text.codePointAt(i)
        when (Character.getDirectionality(cp)) {
            Character.DIRECTIONALITY_RIGHT_TO_LEFT,
            Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC -> return true
            Character.DIRECTIONALITY_LEFT_TO_RIGHT -> return false
        }
        i += Character.charCount(cp)
    }
    return false
}
