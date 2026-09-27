package com.ziaee.frenchreader.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadingPaginationTest {
    @Test
    fun `long chunks split only at measured line boundaries without losing characters`() {
        val pages = paginateMeasuredChunks(
            chunks = listOf(
                MeasuredReadingChunk(
                    chunkIndex = 0,
                    textLength = 12,
                    lines = listOf(
                        MeasuredReadingLine(0, 4, 10),
                        MeasuredReadingLine(4, 8, 10),
                        MeasuredReadingLine(8, 12, 10)
                    ),
                    spacingAfterPx = 2
                )
            ),
            pageHeightPx = 21
        )

        assertEquals(listOf(0 to 8, 8 to 12), pages.flatMap { page -> page.fragments.map { it.startOffset to it.endOffset } })
        assertEquals("abcdefghijkl", pages.joinToString("") { page ->
            page.fragments.joinToString("") { "abcdefghijkl".substring(it.startOffset, it.endOffset) }
        })
    }

    @Test
    fun `several chunks share remaining page height`() {
        val pages = paginateMeasuredChunks(
            listOf(
                measuredChunk(0, 5, 10, spacing = 2),
                measuredChunk(1, 5, 10, spacing = 2),
                measuredChunk(2, 5, 10, spacing = 0)
            ),
            pageHeightPx = 25
        )

        assertEquals(2, pages.size)
        assertEquals(listOf(0, 1), pages[0].fragments.map { it.chunkIndex })
        assertEquals(listOf(2), pages[1].fragments.map { it.chunkIndex })
    }

    @Test
    fun `heading moves to next page when no following line fits`() {
        val pages = paginateMeasuredChunks(
            listOf(
                measuredChunk(0, 4, 18, spacing = 0),
                measuredChunk(1, 5, 6, spacing = 0, keepWithNextPx = 8),
                measuredChunk(2, 5, 8, spacing = 0)
            ),
            pageHeightPx = 25
        )

        assertEquals(listOf(0), pages[0].fragments.map { it.chunkIndex })
        assertEquals(listOf(1, 2), pages[1].fragments.map { it.chunkIndex })
    }

    @Test
    fun `oversized atomic block receives its own page`() {
        val pages = paginateMeasuredChunks(
            listOf(
                measuredChunk(0, 3, 8, spacing = 0),
                MeasuredReadingChunk.atomic(chunkIndex = 1, textLength = 0, heightPx = 80),
                measuredChunk(2, 3, 8, spacing = 0)
            ),
            pageHeightPx = 30
        )

        assertEquals(3, pages.size)
        assertTrue(pages[1].fragments.single().atomic)
        assertEquals(30, pages[1].fragments.single().heightPx)
    }

    @Test
    fun `page lookup uses chunk and character anchor`() {
        val pages = paginateMeasuredChunks(
            listOf(
                MeasuredReadingChunk(
                    chunkIndex = 4,
                    textLength = 12,
                    lines = listOf(
                        MeasuredReadingLine(0, 4, 10),
                        MeasuredReadingLine(4, 8, 10),
                        MeasuredReadingLine(8, 12, 10)
                    )
                )
            ),
            pageHeightPx = 10
        )

        assertEquals(0, pages.pageIndexFor(ReadingAnchor(4, 0)))
        assertEquals(1, pages.pageIndexFor(ReadingAnchor(4, 6)))
        assertEquals(2, pages.pageIndexFor(ReadingAnchor(4, 12)))
    }

    private fun measuredChunk(
        index: Int,
        length: Int,
        height: Int,
        spacing: Int,
        keepWithNextPx: Int = 0
    ) = MeasuredReadingChunk(
        chunkIndex = index,
        textLength = length,
        lines = listOf(MeasuredReadingLine(0, length, height)),
        spacingAfterPx = spacing,
        keepWithNextPx = keepWithNextPx
    )
}
