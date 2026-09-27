package com.ziaee.frenchreader.ui

import com.ziaee.frenchreader.text.isRtlText

/** A source-text line measured with the exact Compose text style used by the reader. */
data class MeasuredReadingLine(
    val startOffset: Int,
    val endOffset: Int,
    val heightPx: Int
)

/** Measurements for one reader chunk. Atomic chunks (tables/images) are never split. */
data class MeasuredReadingChunk(
    val chunkIndex: Int,
    val textLength: Int,
    val lines: List<MeasuredReadingLine>,
    val spacingAfterPx: Int = 0,
    val trailingHeightPx: Int = 0,
    val showTranslation: Boolean = false,
    val keepWithNextPx: Int = 0,
    val atomicHeightPx: Int? = null
) {
    companion object {
        fun atomic(chunkIndex: Int, textLength: Int, heightPx: Int) = MeasuredReadingChunk(
            chunkIndex = chunkIndex,
            textLength = textLength,
            lines = emptyList(),
            atomicHeightPx = heightPx
        )
    }
}

data class ReadingPageFragment(
    val chunkIndex: Int,
    val startOffset: Int,
    val endOffset: Int,
    val heightPx: Int,
    val atomic: Boolean = false,
    val internallyScrollable: Boolean = false,
    val showTranslation: Boolean = false
)

data class ReadingPage(val fragments: List<ReadingPageFragment>)

data class ReadingAnchor(val chunkIndex: Int, val characterOffset: Int = 0)

/**
 * Packs already-measured lines into pages. This function is deliberately Android-free so its
 * character-continuity and boundary behavior can be exercised by local JVM tests.
 */
fun paginateMeasuredChunks(
    chunks: List<MeasuredReadingChunk>,
    pageHeightPx: Int
): List<ReadingPage> {
    if (chunks.isEmpty() || pageHeightPx <= 0) return emptyList()
    val pages = mutableListOf<ReadingPage>()
    var fragments = mutableListOf<ReadingPageFragment>()
    var used = 0

    fun finishPage() {
        if (fragments.isNotEmpty()) pages += ReadingPage(fragments)
        fragments = mutableListOf()
        used = 0
    }

    chunks.forEach { chunk ->
        val atomicHeight = chunk.atomicHeightPx
        if (atomicHeight != null) {
            if (fragments.isNotEmpty()) finishPage()
            fragments += ReadingPageFragment(
                chunkIndex = chunk.chunkIndex,
                startOffset = 0,
                endOffset = chunk.textLength,
                heightPx = atomicHeight.coerceAtMost(pageHeightPx),
                atomic = true,
                internallyScrollable = atomicHeight > pageHeightPx
            )
            finishPage()
            return@forEach
        }

        if (chunk.lines.isEmpty()) return@forEach
        if (fragments.isNotEmpty() && chunk.keepWithNextPx > 0 &&
            used + chunk.lines.first().heightPx + chunk.keepWithNextPx > pageHeightPx
        ) finishPage()

        var lineIndex = 0
        while (lineIndex < chunk.lines.size) {
            if (used >= pageHeightPx) finishPage()
            val startLine = lineIndex
            var fragmentHeight = 0
            var internallyScrollable = false
            while (lineIndex < chunk.lines.size) {
                val line = chunk.lines[lineIndex]
                val lastLine = lineIndex == chunk.lines.lastIndex
                val extra = if (lastLine) chunk.trailingHeightPx else 0
                val needed = line.heightPx + extra
                if (needed > pageHeightPx) internallyScrollable = true
                if (used + fragmentHeight + needed > pageHeightPx && lineIndex > startLine) break
                if (used + needed > pageHeightPx && lineIndex == startLine && fragments.isNotEmpty()) {
                    finishPage()
                    continue
                }
                fragmentHeight += needed.coerceAtMost(pageHeightPx)
                lineIndex++
                if (used + fragmentHeight >= pageHeightPx) break
            }
            val first = chunk.lines[startLine]
            val last = chunk.lines[lineIndex - 1]
            val isLastFragment = lineIndex == chunk.lines.size
            val spacing = if (isLastFragment) chunk.spacingAfterPx else 0
            fragments += ReadingPageFragment(
                chunkIndex = chunk.chunkIndex,
                startOffset = first.startOffset,
                endOffset = if (isLastFragment) chunk.textLength else last.endOffset,
                heightPx = fragmentHeight,
                internallyScrollable = internallyScrollable,
                showTranslation = isLastFragment && chunk.showTranslation
            )
            used += fragmentHeight + spacing
            if (used > pageHeightPx) used = pageHeightPx
        }
    }
    finishPage()
    return pages
}

fun List<ReadingPage>.pageIndexFor(anchor: ReadingAnchor): Int {
    if (isEmpty()) return 0
    forEachIndexed { pageIndex, page ->
        page.fragments.forEach { fragment ->
            if (fragment.chunkIndex == anchor.chunkIndex &&
                anchor.characterOffset >= fragment.startOffset &&
                (anchor.characterOffset < fragment.endOffset ||
                    anchor.characterOffset == fragment.endOffset && fragment.endOffset == fragment.startOffset)
            ) return pageIndex
        }
    }
    val lastForChunk = indexOfLast { page -> page.fragments.any { it.chunkIndex == anchor.chunkIndex } }
    if (lastForChunk >= 0) return lastForChunk
    return indexOfFirst { page -> page.fragments.any { it.chunkIndex > anchor.chunkIndex } }
        .takeIf { it >= 0 } ?: lastIndex
}

fun ReadingPage.chunkIndices(): List<Int> = fragments.map { it.chunkIndex }.distinct()

fun dominantReadingDirectionIsRtl(chunks: List<ChunkState>): Boolean {
    var rtl = 0L
    var ltr = 0L
    chunks.forEach { chunk ->
        val length = chunk.readingDisplayText().length.toLong()
        if (isRtlText(chunk.readingDisplayText())) rtl += length else ltr += length
    }
    return rtl > ltr
}

fun activeSentenceCharacterOffset(chunk: ChunkState?, positionMs: Long): Int {
    chunk ?: return 0
    val active = chunk.sentences.indexOfFirst {
        positionMs >= it.offsetMs && positionMs < it.offsetMs + it.durationMs
    }
    return if (active <= 0) 0 else chunk.sentences.take(active).sumOf { it.text.length + 1 }
}
