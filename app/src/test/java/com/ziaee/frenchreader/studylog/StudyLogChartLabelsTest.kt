package com.ziaee.frenchreader.studylog

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class StudyLogChartLabelsTest {
    private val formatter = DateTimeFormatter.ofPattern("dd/MM", Locale.ENGLISH)

    @Test
    fun `sparse labels use at most four evenly spaced dates including endpoints`() {
        val start = LocalDate.of(2026, 9, 1).toEpochDay()
        val labels = sparseLabels((0L..27L).map { start + it }, formatter)

        assertEquals(4, labels.count { it.isNotEmpty() })
        assertEquals("01/09", labels.first())
        assertEquals("28/09", labels.last())
        assertEquals(listOf(0, 9, 18, 27), labels.indices.filter { labels[it].isNotEmpty() })
    }

    @Test
    fun `sparse labels keep both endpoints for two buckets`() {
        val start = LocalDate.of(2026, 9, 26).toEpochDay()
        assertEquals(
            listOf("26/09", "27/09"),
            sparseLabels(listOf(start, start + 1), formatter)
        )
    }
}
