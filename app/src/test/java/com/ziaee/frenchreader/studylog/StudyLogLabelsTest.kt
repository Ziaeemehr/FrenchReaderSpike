package com.ziaee.frenchreader.studylog

import com.ziaee.frenchreader.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class StudyLogLabelsTest {
    @Test
    fun `missing records do not tell users to correct duration or date`() {
        assertEquals(R.string.error_content_loading, SessionValidation.MissingEntry.messageRes())
        assertEquals(R.string.error_content_loading, SessionValidation.MissingSource.messageRes())
        assertNotEquals(SessionValidation.InvalidDuration.messageRes(), SessionValidation.MissingSource.messageRes())
    }

    @Test
    fun `duration and future dates keep their specific errors`() {
        assertEquals(R.string.study_log_duration_error, SessionValidation.InvalidDuration.messageRes())
        assertEquals(R.string.study_log_future_date_error, SessionValidation.FutureDate.messageRes())
    }
}
