package com.ziaee.frenchreader.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class VocabColorsTest {
    @Test
    fun dueBucket_groupsLearnedDueAndFutureReviews() {
        val now = 1_000_000L

        assertEquals(DueBucket.Learned, dueBucket(now + 1, now, learned = true))
        assertEquals(DueBucket.Today, dueBucket(now, now))
        assertEquals(DueBucket.Today, dueBucket(now - 1, now))
        assertEquals(DueBucket.InDays(1), dueBucket(now + 1, now))
        assertEquals(DueBucket.InDays(3), dueBucket(now + 2 * 86_400_000L + 1, now))
    }
}
