package com.ziaee.frenchreader.comprehension

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LemmaLexicon private constructor(private val context: Context) {
    @Volatile
    private var loaded: FrenchLemmaLexicon? = null

    suspend fun get(): FrenchLemmaLexicon = loaded ?: withContext(Dispatchers.IO) {
        loaded ?: synchronized(this@LemmaLexicon) {
            loaded ?: load().also { loaded = it }
        }
    }

    private fun load(): FrenchLemmaLexicon {
        val forms = HashMap<String, Set<String>>(90_000)
        context.assets.open(LEMMA_ASSET).bufferedReader().useLines { lines ->
            lines.forEach { line ->
                val separator = line.indexOf('\t')
                if (separator > 0 && separator < line.lastIndex) {
                    val form = normalizeFrench(line.substring(0, separator).trim())
                    forms[form] = line.substring(separator + 1)
                        .split(',')
                        .map { normalizeFrench(it.trim()) }
                        .filterTo(linkedSetOf(), String::isNotBlank)
                }
            }
        }
        val common = context.assets.open(COMMON_ASSET).bufferedReader().useLines { lines ->
            lines.map { normalizeFrench(it.trim()) }.filter(String::isNotBlank).toCollection(linkedSetOf())
        }
        return InMemoryLemmaLexicon(forms, common)
    }

    companion object {
        private const val LEMMA_ASSET = "lexicon/lemmas.tsv"
        private const val COMMON_ASSET = "lexicon/common_lemmas.txt"

        @Volatile
        private var instance: LemmaLexicon? = null

        fun get(context: Context): LemmaLexicon = instance ?: synchronized(this) {
            instance ?: LemmaLexicon(context.applicationContext).also { instance = it }
        }
    }
}
