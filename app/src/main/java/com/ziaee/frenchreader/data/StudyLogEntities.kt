package com.ziaee.frenchreader.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter

enum class StudySkill(val code: String, val legacy: Boolean = false) {
    CO("CO"),
    CE("CE"),
    EE("EE"),
    EO("EO"),
    VOC("VOC"),
    GRAM("GRAM"),
    // Kept so previously stored combined vocabulary/grammar sessions remain readable.
    LEX("LEX", legacy = true);

    companion object {
        fun fromCode(code: String) = entries.firstOrNull { it.code == code } ?: LEX
    }
}

enum class SourceKind(val code: String) {
    BOOK("book"),
    NOTES("notes"),
    AUDIO("audio"),
    VIDEO("video"),
    WEBSITE("website"),
    APP("app"),
    MOCK_TEST("mock-test"),
    OTHER("other");

    companion object {
        fun fromCode(code: String) = entries.firstOrNull { it.code == code } ?: OTHER
    }
}

class StudyLogConverters {
    @TypeConverter
    fun skillToCode(value: StudySkill?): String? = value?.code

    @TypeConverter
    fun codeToSkill(value: String?): StudySkill? = value?.let(StudySkill::fromCode)

    @TypeConverter
    fun kindToCode(value: SourceKind): String = value.code

    @TypeConverter
    fun codeToKind(value: String): SourceKind = SourceKind.fromCode(value)
}

@Entity(tableName = "study_source", indices = [Index(value = ["name"], unique = true)])
data class StudySource(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(collate = ColumnInfo.NOCASE) val name: String,
    val kind: SourceKind = SourceKind.OTHER,
    @ColumnInfo(name = "default_skill") val defaultSkill: StudySkill? = null,
    val note: String? = null,
    val archived: Boolean = false,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "study_session",
    foreignKeys = [
        ForeignKey(
            entity = StudySource::class,
            parentColumns = ["id"],
            childColumns = ["source_id"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [Index("date"), Index("source_id")]
)
data class StudySession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: Long,
    @ColumnInfo(name = "duration_min") val durationMin: Int,
    val skill: StudySkill,
    @ColumnInfo(name = "source_id") val sourceId: Long? = null,
    val note: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis()
)
