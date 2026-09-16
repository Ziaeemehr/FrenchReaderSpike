package com.ziaee.frenchreader.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A text the user has imported (pasted, for phase 1 -- file import comes in
 * phase 2). `lastPositionMs` / `lastChunkIndex` let us resume exactly where
 * the user left off (acceptance criterion #1).
 */
@Entity(tableName = "texts")
data class TextDocument(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val rawText: String,
    val createdAtMs: Long = System.currentTimeMillis(),
    val lastChunkIndex: Int = 0,
    val lastPositionMs: Long = 0,
    val voice: String = "fr-FR-DeniseNeural",
    val ratePercent: Int = 0, // edge-tts rate, e.g. -15..+40, applied as "+N%"/"-N%"
    val translationLang: String = "fa", // ISO code for the paragraph-translation target language
    // Attribution for texts imported from an external source (e.g. Vikidia,
    // see ROADMAP.md section 6). All null for pasted/file-imported texts and
    // for the existing RFI/France Info RSS import, which doesn't set these yet.
    val sourceUrl: String? = null,
    val sourceName: String? = null,
    val author: String? = null,
    val license: String? = null,
    val publishedAt: Long? = null // epoch ms of the source's original publish/revision date
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
