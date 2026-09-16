package com.ziaee.frenchreader.images

import org.junit.Assert.assertEquals
import org.junit.Test

class ArticleImageStoreTest {
    @Test
    fun `scales down to the max width preserving aspect ratio`() {
        assertEquals(1080 to 810, scaledDimensions(1600, 1200, maxWidth = 1080))
    }

    @Test
    fun `leaves images at or under the max width unchanged`() {
        assertEquals(800 to 600, scaledDimensions(800, 600, maxWidth = 1080))
        assertEquals(1080 to 900, scaledDimensions(1080, 900, maxWidth = 1080))
    }
}
