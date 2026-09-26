package com.ziaee.frenchreader.text

/**
 * Maps the sentences TTS returned for [spoken] back onto [display], the
 * on-screen text [spoken] was derived from by [sanitizeForSpeech] (so it is
 * [display] minus emoji/symbols, with whitespace collapsed).
 *
 * Returns one display segment per sentence, so that joining them with a
 * single space reproduces [display] -- symbols included -- the way
 * ReadingScreen renders a chunk. A symbol sitting between two sentences goes
 * with the sentence after it, since it usually labels what follows ("✅ Correction").
 *
 * Returns null when a sentence can't be located in [spoken] (e.g. the TTS
 * engine normalized its text), so the caller can keep the sentences as TTS
 * returned them.
 */
fun alignSentencesToDisplay(display: String, spoken: String, sentences: List<String>): List<String>? {
    if (sentences.isEmpty()) return null
    if (display == spoken) return sentences

    // spokenToDisplay[j] = index in [display] of spoken[j]. spoken is a
    // subsequence of display, so a greedy earliest match is always valid.
    val spokenToDisplay = IntArray(spoken.length)
    var j = 0
    for (i in display.indices) {
        if (j < spoken.length && display[i] == spoken[j]) {
            spokenToDisplay[j] = i
            j++
        }
    }
    if (j < spoken.length) return null

    val starts = IntArray(sentences.size)
    var cursor = 0
    for ((k, sentence) in sentences.withIndex()) {
        val text = sentence.trim()
        if (text.isEmpty()) return null
        val at = spoken.indexOf(text, cursor)
        if (at < 0) return null
        starts[k] = if (k == 0 || at == 0) {
            0
        } else {
            // Start right after the display char preceding this sentence,
            // skipping whitespace, so leading symbols stay with it.
            var s = spokenToDisplay[at - 1] + 1
            while (s < display.length && display[s].isWhitespace()) s++
            s
        }
        cursor = at + text.length
    }

    return sentences.indices.map { k ->
        val end = if (k == sentences.lastIndex) display.length else starts[k + 1]
        if (starts[k] > end) return null
        display.substring(starts[k], end).trimEnd()
    }
}
