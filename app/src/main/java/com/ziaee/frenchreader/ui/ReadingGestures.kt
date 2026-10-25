package com.ziaee.frenchreader.ui

import com.ziaee.frenchreader.tts.SentenceBoundary

internal fun tappedWordBounds(text: String, offset: Int): IntRange? {
    if (offset !in text.indices || !text[offset].isLetter()) return null
    return wordBoundsAt(text, offset)
}

internal data class ReadingSentenceLocation(
    val chunkIndex: Int,
    val range: IntRange,
    val sentence: SentenceBoundary
)

internal fun readingSentenceLocations(chunks: List<ChunkState>): List<ReadingSentenceLocation> =
    chunks.flatMapIndexed { index, chunk ->
        var cursor = 0
        chunk.sentences.map { sentence ->
            val start = chunk.text.indexOf(sentence.text, cursor).takeIf { it >= 0 } ?: cursor
            val end = (start + sentence.text.length).coerceAtMost(chunk.text.length)
            cursor = end
            ReadingSentenceLocation(index, start until end, sentence)
        }
    }

/** Where "Play from this page" starts: the sentence holding the page's first character, in
 * the page's first paragraph (a sentence continued from the previous page restarts at its own
 * start). Null when that paragraph has no timings yet; the caller then starts the paragraph. */
internal fun pagePlaybackStart(
    page: ReadingPage,
    sentences: List<ReadingSentenceLocation>
): ReadingSentenceLocation? {
    val first = page.fragments.firstOrNull() ?: return null
    return sentences.firstOrNull { it.chunkIndex == first.chunkIndex && it.range.last >= first.startOffset }
}

internal fun pageContainsSentence(page: ReadingPage, sentence: ReadingSentenceLocation): Boolean =
    page.fragments.any {
        it.chunkIndex == sentence.chunkIndex && it.startOffset <= sentence.range.last &&
            it.endOffset > sentence.range.first
    }
