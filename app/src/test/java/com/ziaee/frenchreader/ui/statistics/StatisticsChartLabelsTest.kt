package com.ziaee.frenchreader.ui.statistics

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

class StatisticsChartLabelsTest {
    @Test fun `Persian axes use Persian digits for numbers and dates`() {
        val locale = Locale.forLanguageTag("fa")
        assertEquals("۱۲", chartNumber(12, locale))
        assertEquals("۲۴/۱۰", chartDate(LocalDate.of(2026, 10, 24), locale))
    }

    @Test fun `French axes retain Latin digits`() {
        assertEquals("12", chartNumber(12, Locale.FRENCH))
        assertEquals("24/10", chartDate(LocalDate.of(2026, 10, 24), Locale.FRENCH))
    }
}
