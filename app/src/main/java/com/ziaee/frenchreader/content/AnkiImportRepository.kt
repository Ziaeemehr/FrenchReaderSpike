package com.ziaee.frenchreader.content

import androidx.room.withTransaction
import com.ziaee.frenchreader.data.AppDatabase
import com.ziaee.frenchreader.data.VocabEntry
import com.ziaee.frenchreader.data.VocabList
import com.ziaee.frenchreader.ui.wordReferenceUrl

data class AnkiImportResult(val added: Int, val skipped: Int, val lists: Int)

/** Imports word files (Anki JSON exports, user CSV/TSV) into vocab lists. */
class AnkiImportRepository(private val db: AppDatabase) {
    suspend fun import(json: String, keepProgress: Boolean, nowMs: Long = System.currentTimeMillis()): AnkiImportResult {
        val export = parseAnkiExport(json)
        return db.withTransaction {
            val decks = DeckIndex.load(db)
            val plan = planAnkiImport(export, decks.existingWordKeys)
            val newLists = decks.ensure(plan.newListNames)
            for (w in plan.words) {
                val listId = decks.idOf(w.listName)
                db.vocabDao().insert(
                    buildVocabEntry(w.card, w.mapped, listId, keepProgress, export.todayDay, nowMs, wordReferenceUrl(w.mapped.word))
                )
            }
            AnkiImportResult(added = plan.words.size, skipped = plan.skipped, lists = newLists)
        }
    }

    /** Adds spreadsheet words as new cards (box 1, due now), skipping duplicates per deck. */
    suspend fun importCsv(
        file: CsvWordFile,
        target: CsvImportTarget,
        nowMs: Long = System.currentTimeMillis()
    ): AnkiImportResult = db.withTransaction {
        val decks = DeckIndex.load(db)
        val plan = planCsvImport(file, target, decks.existingWordKeys)
        val newLists = decks.ensure(plan.deckNames)
        plan.words.forEachIndexed { index, (deck, word) ->
            db.vocabDao().insert(
                VocabEntry(
                    word = word.word,
                    sentence = word.sentence,
                    textId = 0L,
                    dictionaryUrl = wordReferenceUrl(word.word),
                    meaning = word.meaning,
                    listId = decks.idOf(deck),
                    createdAtMs = nowMs + index, // keeps the file's order
                    nextReviewAtMs = nowMs,
                    leitnerBox = 1,
                    lastReviewedAtMs = null
                )
            )
        }
        AnkiImportResult(added = plan.words.size, skipped = plan.skipped + file.malformedRows, lists = newLists)
    }

    /** Vocab lists by lowercase name, plus (list name, word) keys of the cards already in them. */
    private class DeckIndex(
        private val db: AppDatabase,
        private val idByName: MutableMap<String, Long>,
        val existingWordKeys: Set<Pair<String, String>>
    ) {
        fun idOf(name: String): Long = idByName.getValue(name.lowercase())

        /** Creates the lists that don't exist yet; returns how many were created. */
        suspend fun ensure(names: List<String>): Int = names.count { name ->
            if (name.lowercase() in idByName) false
            else { idByName[name.lowercase()] = db.vocabListDao().insert(VocabList(name = name)); true }
        }

        companion object {
            suspend fun load(db: AppDatabase): DeckIndex {
                val lists = db.vocabListDao().getAllOnce()
                val nameById = lists.associate { it.id to it.name.lowercase() }
                val keys = db.vocabDao().getAllOnce().mapNotNull { e ->
                    e.listId?.let { nameById[it] }?.let { it to e.word.lowercase() }
                }.toSet()
                return DeckIndex(db, lists.associate { it.name.lowercase() to it.id }.toMutableMap(), keys)
            }
        }
    }
}
