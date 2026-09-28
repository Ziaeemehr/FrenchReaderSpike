package com.ziaee.frenchreader.studylog

import com.ziaee.frenchreader.data.StudyLogConverters
import com.ziaee.frenchreader.data.StudySkill
import org.junit.Assert.assertEquals
import org.junit.Test

class StudySkillTest {
    @Test
    fun `unknown stored skill code safely falls back to lex`() {
        assertEquals(StudySkill.LEX, StudySkill.fromCode("unknown"))
        assertEquals(StudySkill.LEX, StudyLogConverters().codeToSkill("unknown"))
    }
}
