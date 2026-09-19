package com.ziaee.frenchreader.ui

import com.ziaee.frenchreader.text.BlockType
import com.ziaee.frenchreader.text.ParsedBlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadingChunkNavigationTest {
    @Test
    fun `next spoken chunk skips consecutive image chunks`() {
        val chunks = listOf(
            chunk(BlockType.PARAGRAPH),
            chunk(BlockType.IMAGE),
            chunk(BlockType.IMAGE),
            chunk(BlockType.PARAGRAPH)
        )

        assertEquals(3, nextSpokenChunkIndex(chunks, 1))
        assertNull(nextSpokenChunkIndex(chunks, 4))
    }

    @Test
    fun `audio window starts at target and counts only spoken chunks`() {
        val chunks = listOf(
            chunk(BlockType.PARAGRAPH),
            chunk(BlockType.IMAGE),
            chunk(BlockType.PARAGRAPH),
            chunk(BlockType.IMAGE),
            chunk(BlockType.PARAGRAPH),
            chunk(BlockType.PARAGRAPH)
        )

        assertEquals(listOf(2, 4, 5), audioWindowIndices(chunks, currentIndex = 2, lookahead = 2))
    }

    @Test
    fun `audio window stops at end of book`() {
        val chunks = listOf(chunk(BlockType.PARAGRAPH), chunk(BlockType.IMAGE), chunk(BlockType.PARAGRAPH))

        assertEquals(listOf(2), audioWindowIndices(chunks, currentIndex = 2, lookahead = 3))
    }

    @Test
    fun `audio window never synthesizes before a mid book target`() {
        val chunks = List(8) { chunk(BlockType.PARAGRAPH) }

        val window = audioWindowIndices(chunks, currentIndex = 5, lookahead = 2)

        assertEquals(listOf(5, 6, 7), window)
        assertTrue(window.none { it < 5 })
    }

    @Test
    fun `mid book playlist maps only enqueued chunks`() {
        assertEquals(mapOf(12 to 0, 14 to 1, 15 to 2), playerItemIndexMap(listOf(12, 14, 15)))
    }

    @Test
    fun `translation window contains current chunk and bounded lookahead`() {
        assertEquals(listOf(3, 4, 5), translationWindowIndices(chunkCount = 6, currentIndex = 3, lookahead = 5))
    }

    @Test
    fun `jump reset removes old playlist mappings and pending audio outside new window`() {
        val chunks = listOf(
            chunk(BlockType.PARAGRAPH).copy(status = ChunkStatus.READY, playerItemIndex = 0),
            chunk(BlockType.IMAGE),
            chunk(BlockType.PARAGRAPH).copy(status = ChunkStatus.LOADING, playerItemIndex = 1),
            chunk(BlockType.PARAGRAPH).copy(status = ChunkStatus.READY, playerItemIndex = 2)
        )

        val reset = resetAudioForJump(chunks)

        assertEquals(ChunkStatus.PENDING, reset[0].status)
        assertEquals(ChunkStatus.READY, reset[1].status)
        assertEquals(ChunkStatus.PENDING, reset[2].status)
        assertEquals(ChunkStatus.PENDING, reset[3].status)
        assertTrue(reset.all { it.playerItemIndex == null })
    }

    private fun chunk(type: BlockType) = ChunkState(
        block = ParsedBlock(type = type, plainText = if (type == BlockType.IMAGE) "" else "Texte")
    )
}
