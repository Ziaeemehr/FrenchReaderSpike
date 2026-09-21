package com.ziaee.frenchreader.ui

import com.ziaee.frenchreader.data.VocabAnswer
import org.junit.Assert.assertEquals
import org.junit.Test

class ReviewSessionStatsTest {
    @Test fun `forgot from box 3 counts return to box one`() {
        val s = ReviewSessionStats().after(VocabAnswer.FORGOT, 3, 1)
        assertEquals(ReviewSessionStats(1, 0, 0, 1), s)
    }
    @Test fun `forgot from box 1 does not count return`() {
        assertEquals(ReviewSessionStats(1, 0, 0, 0), ReviewSessionStats().after(VocabAnswer.FORGOT, 1, 1))
    }
    @Test fun `hard counts correct without moving forward`() {
        assertEquals(ReviewSessionStats(1, 1, 0, 0), ReviewSessionStats().after(VocabAnswer.HARD, 2, 2))
    }
    @Test fun `knew moving box counts forward`() {
        assertEquals(ReviewSessionStats(1, 1, 1, 0), ReviewSessionStats().after(VocabAnswer.KNEW, 2, 3))
    }
}
