package com.ziaee.frenchreader.ui

import com.ziaee.frenchreader.translate.MeaningOption
import org.junit.Assert.*
import org.junit.Test

class ReviewMeaningTest {
    @Test fun `replacement preserves other language and returns only replaced line`() {
        assertEquals(MeaningReplacement("to be free\nرها بودن", "آزاد بودن"),
            replaceMeaningLine("to be free\nآزاد بودن", "رها بودن"))
        assertEquals(MeaningReplacement("available\nآزاد بودن", "to be free"),
            replaceMeaningLine("to be free\nآزاد بودن", "available"))
        assertEquals(MeaningReplacement("available\nآزاد بودن\nیادداشت", "to be free"),
            replaceMeaningLine("to be free\nآزاد بودن\nیادداشت", "available"))
    }

    @Test fun `deck lesson tag stays at the end and is not part of the replaced line`() {
        assertEquals(MeaningReplacement("to be late\nمعوق ماندن — Leçon 01", "دیر کردن"),
            replaceMeaningLine("to be late\nدیر کردن — Leçon 01", "معوق ماندن"))
        assertEquals(MeaningReplacement("late\nدیر کردن — Leçon 01", "to be late"),
            replaceMeaningLine("to be late\nدیر کردن — Leçon 01", "late"))
    }

    @Test fun `missing script appends without dropping original`() {
        assertEquals(MeaningReplacement("to be free\nآزاد بودن", null), replaceMeaningLine("to be free", "آزاد بودن"))
        assertEquals(MeaningReplacement("free", null), replaceMeaningLine(null, "free"))
    }

    @Test fun `review hides normalized current meaning and each line`() {
        val options = listOf("to be free", "آزاد بودن", "to be free\nآزاد بودن", "available")
            .map { MeaningOption(it, null, null) }
        assertEquals(listOf(options.last()), reviewMeaningOptions(options, " TO  BE FREE \nآزاد بودن", null, null))
    }

    @Test fun `reverting highlights a previous line absent from dictionary options`() {
        val free = MeaningOption("free", null, null)
        assertEquals(listOf(MeaningOption("to be free", null, null), free),
            reviewMeaningOptions(listOf(free), "to be free\nآزاد بودن", "to be free", "free"))
    }

    @Test fun `applied option stays visible alongside one quiet revert line`() {
        val free = MeaningOption("free", "libre", null)
        val available = MeaningOption("available", null, null)
        assertEquals(listOf(free, available, MeaningOption("to be free", null, null)),
            reviewMeaningOptions(listOf(free, available), "free\nآزاد بودن", "free", "to be free"))
    }
}
