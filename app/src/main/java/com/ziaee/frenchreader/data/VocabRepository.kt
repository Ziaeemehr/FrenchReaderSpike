package com.ziaee.frenchreader.data

import java.text.Normalizer
import java.util.Locale

/** Manual entries must not share Anki's legacy source sentinel (0L). */
const val MANUAL_VOCAB_TEXT_ID = -1L

internal fun vocabIdentityKey(word: String): String =
    Normalizer.normalize(word.trim(), Normalizer.Form.NFKC).lowercase(Locale.ROOT)

class VocabRepository(private val dao: VocabDao) {
    suspend fun save(
        textId: Long,
        word: String,
        sentence: String,
        dictionaryUrl: String,
        meaning: String?,
        listId: Long?
    ) {
        val existing = if (textId == MANUAL_VOCAB_TEXT_ID) {
            val identity = vocabIdentityKey(word)
            dao.getManualEntries().firstOrNull { vocabIdentityKey(it.word) == identity }
        } else {
            dao.findExisting(textId, word, sentence)
        }
        if (existing != null) {
            dao.update(
                existing.copy(
                    meaning = meaning?.ifBlank { existing.meaning } ?: existing.meaning,
                    listId = listId
                )
            )
        } else {
            dao.insert(
                VocabEntry(
                    word = word,
                    sentence = sentence,
                    textId = textId,
                    dictionaryUrl = dictionaryUrl,
                    meaning = meaning?.ifBlank { null },
                    listId = listId
                )
            )
        }
    }
}
