package com.ziaee.frenchreader.studylog

import androidx.annotation.StringRes
import com.ziaee.frenchreader.R
import com.ziaee.frenchreader.data.SourceKind
import com.ziaee.frenchreader.data.StudySkill

@StringRes
internal fun StudySkill.labelRes() = when (this) {
    StudySkill.CO -> R.string.study_log_skill_co
    StudySkill.CE -> R.string.study_log_skill_ce
    StudySkill.EE -> R.string.study_log_skill_ee
    StudySkill.EO -> R.string.study_log_skill_eo
    StudySkill.VOC -> R.string.study_log_skill_voc
    StudySkill.GRAM -> R.string.study_log_skill_gram
    StudySkill.LEX -> R.string.study_log_skill_lex
}

@StringRes
internal fun SourceKind.labelRes() = when (this) {
    SourceKind.BOOK -> R.string.study_log_kind_book
    SourceKind.NOTES -> R.string.study_log_kind_notes
    SourceKind.AUDIO -> R.string.study_log_kind_audio
    SourceKind.VIDEO -> R.string.study_log_kind_video
    SourceKind.WEBSITE -> R.string.study_log_kind_website
    SourceKind.APP -> R.string.study_log_kind_app
    SourceKind.MOCK_TEST -> R.string.study_log_kind_mock_test
    SourceKind.OTHER -> R.string.study_log_kind_other
}
