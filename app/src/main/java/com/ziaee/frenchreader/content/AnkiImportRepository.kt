package com.ziaee.frenchreader.content

import androidx.room.withTransaction
import com.ziaee.frenchreader.data.AppDatabase
import com.ziaee.frenchreader.data.VocabList
import com.ziaee.frenchreader.ui.wordReferenceUrl

data class AnkiImportResult(val added: Int, val skipped: Int, val lists: Int)

class AnkiImportRepository(private val db: AppDatabase) {
    suspend fun import(json: String, keepProgress: Boolean, nowMs: Long = System.currentTimeMillis()): AnkiImportResult {
        val export = parseAnkiExport(json)
        return db.withTransaction {
            val lists = db.vocabListDao().getAllOnce()
            val listIdByName = lists.associate { it.name.lowercase() to it.id }.toMutableMap()
            val nameById = lists.associate { it.id to it.name.lowercase() }
            val existingKeys = db.vocabDao().getAllOnce().mapNotNull { e ->
                e.listId?.let { nameById[it] }?.let { it to e.word.lowercase() }
            }.toSet()

            val plan = planAnkiImport(export, existingKeys)
            var newLists = 0
            for (name in plan.newListNames) {
                if (name.lowercase() !in listIdByName) {
                    listIdByName[name.lowercase()] = db.vocabListDao().insert(VocabList(name = name))
                    newLists++
                }
            }
            for (w in plan.words) {
                val listId = listIdByName.getValue(w.listName.lowercase())
                db.vocabDao().insert(
                    buildVocabEntry(w.card, w.mapped, listId, keepProgress, export.todayDay, nowMs, wordReferenceUrl(w.mapped.word))
                )
            }
            AnkiImportResult(added = plan.words.size, skipped = plan.skipped, lists = newLists)
        }
    }
}
