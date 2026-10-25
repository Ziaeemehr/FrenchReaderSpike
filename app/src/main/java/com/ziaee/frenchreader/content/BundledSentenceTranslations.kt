package com.ziaee.frenchreader.content

import android.content.Context
import java.text.Normalizer
import java.util.Locale

internal fun normalizedFrenchSentence(text: String): String =
    Normalizer.normalize(text, Normalizer.Form.NFC).replace('’', '\'')
        .replace(Regex("[\\s\\p{Z}]+"), " ").trim().lowercase(Locale.ROOT)

private fun sentenceKey(text: String, language: String) =
    normalizedFrenchSentence(text) to language.substringBefore('-').substringBefore('_').lowercase(Locale.ROOT)

/** Sentence text is the identity, including for cards installed before this feature existed. */
class BundledSentenceTranslations internal constructor(load: () -> List<DatasetDeckRow>) {
    private val index by lazy {
        buildMap<Pair<String, String>, String> {
            load().filter { it.sentence.isNotBlank() }.forEach { row ->
                row.sentenceTranslations.forEach { (language, value) ->
                    putIfAbsent(sentenceKey(row.sentence, language), value)
                }
            }
        }
    }
    fun get(sentence: String, language: String): String? = index[sentenceKey(sentence, language)]

    companion object {
        @Volatile private var instance: BundledSentenceTranslations? = null
        fun forContext(context: Context): BundledSentenceTranslations = instance ?: synchronized(this) {
            instance ?: run {
                val assets = context.applicationContext.assets
                fun asset(path: String) = assets.open("dataset/$path").bufferedReader().use { it.readText() }
                BundledSentenceTranslations {
                    parseDatasetManifest(asset("manifest.json")).decks.flatMap { parseDatasetDeck(asset(it.file)) }
                }.also { instance = it }
            }
        }
    }
}
