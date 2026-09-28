package com.ziaee.frenchreader.studylog

import com.ziaee.frenchreader.data.StudyLogConverters
import com.ziaee.frenchreader.data.StudySkill
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StudySkillTest {
    @Test
    fun `unknown stored skill code safely falls back to lex`() {
        assertEquals(StudySkill.LEX, StudySkill.fromCode("unknown"))
        assertEquals(StudySkill.LEX, StudyLogConverters().codeToSkill("unknown"))
    }

    @Test
    fun `vocabulary and grammar are new skills while lex remains last and legacy`() {
        assertEquals(
            listOf("CO", "CE", "EE", "EO", "VOC", "GRAM", "LEX"),
            StudySkill.entries.map { it.code }
        )
        assertFalse(StudySkill.VOC.legacy)
        assertFalse(StudySkill.GRAM.legacy)
        assertTrue(StudySkill.LEX.legacy)
    }
}
