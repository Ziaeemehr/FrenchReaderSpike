package com.ziaee.frenchreader.data

import org.junit.Assert.assertEquals
import org.junit.Test

class VocabRepositoryTest {
    @Test
    fun vocabIdentityKeyIsUnicodeNormalizedAndCaseInsensitive() {
        assertEquals(vocabIdentityKey("École"), vocabIdentityKey("école"))
        assertEquals(vocabIdentityKey("école"), vocabIdentityKey("e\u0301cole"))
    }
}
