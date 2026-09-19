package com.ziaee.frenchreader.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextBodyStoreTest {
    @Test
    fun `accepts only direct files inside the text bodies directory`() {
        assertTrue(TextBodyStore.isOwnedPath("text_bodies/42.txt"))
        assertFalse(TextBodyStore.isOwnedPath("text_images/42.txt"))
        assertFalse(TextBodyStore.isOwnedPath("text_bodies/../42.txt"))
        assertFalse(TextBodyStore.isOwnedPath("/text_bodies/42.txt"))
        assertFalse(TextBodyStore.isOwnedPath("text_bodies/nested/42.txt"))
    }
}
