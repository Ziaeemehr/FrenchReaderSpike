package com.ziaee.frenchreader.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.FtsOptions
import androidx.room.Index
import androidx.room.PrimaryKey
import com.ziaee.frenchreader.resources.ResourceCategory

/**
 * A text the user has imported (pasted, for phase 1 -- file import comes in
 * phase 2). `lastPositionMs` / `lastChunkIndex` let us resume exactly where
 * the user left off (acceptance criterion #1).
 */
@Entity(
    tableName = "texts",
    indices = [Index(value = ["externalKey"], unique = true)]
)
data class TextDocument(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val rawText: String,
    val createdAtMs: Long = System.currentTimeMillis(),
    val lastChunkIndex: Int = 0,
    val lastPositionMs: Long = 0,
    val voice: String = "fr-FR-HenriNeural",
    val ratePercent: Int = 0, // edge-tts rate, e.g. -15..+40, applied as "+N%"/"-N%"
    val translationLang: String = "fa", // ISO code for the paragraph-translation target language
    // Attribution for texts imported from an external source (e.g. Vikidia,
    // see ROADMAP.md section 6). All null for pasted/file-imported texts and
    // for the existing RFI/France Info RSS import, which doesn't set these yet.
    val sourceUrl: String? = null,
    val sourceName: String? = null,
    val author: String? = null,
    val license: String? = null,
    val publishedAt: Long? = null, // epoch ms of the source's original publish/revision date
    val imagePath: String? = null,
    val externalKey: String? = null,
    val lastAccessedAtMs: Long = 0,
    val folderId: Long? = null,
    val bodyPath: String? = null,
    val pinned: Boolean = false
)

/**
 * Full-text index over each text's title and body (rowid = [TextDocument.id]). SQLite triggers
 * (see [createTextSearchTriggers]) keep it in sync with `texts` for inline bodies; bodies stored
 * as files are indexed from Kotlin (see [indexTextBodyFile]). unicode61 folds case and accents,
 * so "ecole" finds "école".
 */
@Fts4(tokenizer = FtsOptions.TOKENIZER_UNICODE61)
@Entity(tableName = "texts_fts")
data class TextSearchEntry(
    @PrimaryKey @ColumnInfo(name = "rowid") val rowId: Long,
    val title: String,
    val body: String
)

data class TextSearchHit(val id: Long, val snippet: String)

@Entity(tableName = "library_folders")
data class LibraryFolder(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAtMs: Long = System.currentTimeMillis(),
    val parentId: Long? = null
)

@Entity(
    tableName = "library_tags",
    indices = [Index(value = ["name"], unique = true)]
)
data class LibraryTag(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAtMs: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "text_tags",
    primaryKeys = ["textId", "tagId"],
    indices = [Index("tagId")]
)
data class TextTagCrossRef(
    val textId: Long,
    val tagId: Long
)

@Entity(
    tableName = "headlines",
    primaryKeys = ["sourceId", "externalId"],
    indices = [Index("publishedAtMs"), Index("articleUrl")]
)
data class HeadlineEntity(
    val sourceId: String,
    val sourceLabel: String,
    val externalId: String,
    val title: String,
    val snippet: String,
    val articleUrl: String,
    val imageUrl: String?,
    val publishedAtMs: Long?,
    val cachedAtMs: Long
)

/**
 * A saved vocabulary entry: word/phrase + the sentence it was found in +
 * where it came from + a short meaning (auto-filled from translation,
 * user-editable). WordReference is looked up live (via URL), not scraped,
 * per the design doc -- so no "definition" field is auto-filled here.
 *
 * [listId] files the word under a user-created [VocabList] (null = not
 * filed into any particular list, shown under "بدون دسته").
 *
 * The remaining fields drive a simple Leitner-style spaced-repetition
 * review: [leitnerBox] (1..5, higher = better known), [nextReviewAtMs]
 * (when this card is next due), [lastReviewedAtMs]. A card is "due" when
 * `nextReviewAtMs <= now`; `learned` doubles as "graduated out of box 5"
 * but can also still be toggled by hand from the plain list.
 */
@Entity(tableName = "vocab")
data class VocabEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val word: String,
    val sentence: String,
    val textId: Long,
    val dictionaryUrl: String,
    val meaning: String? = null,
    val learned: Boolean = false,
    val createdAtMs: Long = System.currentTimeMillis(),
    val listId: Long? = null,
    val leitnerBox: Int = 1,
    val nextReviewAtMs: Long = System.currentTimeMillis(),
    val lastReviewedAtMs: Long? = null
)

/** A user-created vocabulary list/deck (e.g. "فعل‌ها", "متن سوم"). Words not
 * explicitly filed into one keep [VocabEntry.listId] null. */
@Entity(tableName = "vocab_lists")
data class VocabList(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAtMs: Long = System.currentTimeMillis()
)

/** One row per vocab-review answer, feeding the Statistics screen's
 * accuracy and streak calculations. Written once per [VocabEntry] answer,
 * never updated or deleted. */
@Entity(tableName = "review_log", indices = [Index("timestampMs")])
data class ReviewLogEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val entryId: Long,
    val timestampMs: Long,
    val knew: Boolean,
    val boxBefore: Int,
    val boxAfter: Int
)

/** One row per calendar day with any listening activity, accumulated from
 * [ReadingViewModel][com.ziaee.frenchreader.ui.ReadingViewModel]'s position
 * ticker. [date] is `LocalDate.toString()` (`yyyy-MM-dd`). */
@Entity(tableName = "activity_log")
data class ActivityLogEntry(
    @PrimaryKey val date: String,
    val listeningMs: Long
)

/** A user-managed web bookmark shown on the Resources screen. */
@Entity(
    tableName = "resources",
    indices = [Index(value = ["url"], unique = true)]
)
data class ResourceLink(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val url: String,
    val imageUrl: String? = null,
    val createdAtMs: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "other") val category: String = ResourceCategory.OTHER.key,
    /** User-written; built-in resources leave these null and show their catalog text instead. */
    val description: String? = null,
    val level: String? = null
)

/** One shadowing try on one sentence. Only the score is kept -- never the audio. */
@Entity(tableName = "shadow_attempts", indices = [Index(value = ["timestampMs"])])
data class ShadowAttempt(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val textId: Long,
    val chunkIndex: Int,
    val sentenceIndex: Int,
    val matched: Int,
    val total: Int,
    /** Speech length / expected TTS length; null when the engine gave no audio to measure. */
    val paceRatio: Float? = null,
    val engine: String,
    val timestampMs: Long
)
