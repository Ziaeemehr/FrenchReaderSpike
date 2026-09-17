package com.ziaee.frenchreader.llm

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LlmNativeSanityTest {
    @Test
    fun nativeLibraryLoadsAndReturnsExpectedValue() {
        assertEquals(42, LlmNative.nativeSanityCheck())
    }
}
