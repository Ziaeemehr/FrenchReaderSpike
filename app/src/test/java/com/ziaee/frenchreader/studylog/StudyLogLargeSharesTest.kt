package com.ziaee.frenchreader.studylog

import com.ziaee.frenchreader.data.StudySkill
import org.junit.Assert.assertEquals
import org.junit.Test

class StudyLogLargeSharesTest {
    @Test
    fun `shares remain exact when combined skill totals exceed integer range`() {
        val shares = largestRemainderShares(
            mapOf(StudySkill.CO to 1_500_000_000, StudySkill.CE to 1_500_000_000)
        )
        assertEquals(50, shares[StudySkill.CO])
        assertEquals(50, shares[StudySkill.CE])
        assertEquals(100, shares.values.sum())
    }

    @Test
    fun `shares do not inflate when total wraps to a positive integer`() {
        val shares = largestRemainderShares(StudySkill.entries.associateWith { Int.MAX_VALUE })
        assertEquals(listOf(15, 15, 14, 14, 14, 14, 14), StudySkill.entries.map { shares[it] })
    }
}
