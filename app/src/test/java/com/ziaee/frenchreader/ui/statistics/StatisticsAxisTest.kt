package com.ziaee.frenchreader.ui.statistics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StatisticsAxisTest {
    @Test fun niceMaximumRoundsUpAcrossDecades() {
        listOf(-1f to 1f, 0f to 1f, .12f to .2f, 1f to 1f,
            2.1f to 2.5f, 3f to 5f, 5.1f to 10f, 24f to 25f,
            51f to 100f, 250f to 250f).forEach { (input, expected) ->
            assertEquals(expected, niceAxisMax(input), .00001f)
        }
        (1..1000).forEach { assertTrue(niceAxisMax(it / 13f) >= it / 13f) }
    }

    @Test fun integerTicksHaveIntegralMidpoints() {
        assertEquals(4f, chartAxisMax(3f, null, true), 0f)
        (0..100).forEach {
            val max = chartAxisMax(it.toFloat(), null, true)
            assertTrue(max >= it)
            assertEquals(0f, max % 2f, 0f)
        }
    }

    @Test fun explicitMaximumWins() {
        assertEquals(100f, chartAxisMax(37f, 100f, false), 0f)
        assertEquals(3f, chartAxisMax(2f, 3f, true), 0f)
    }
}
