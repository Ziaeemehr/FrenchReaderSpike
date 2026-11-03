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

    @Volatile private var ocrCorrector: com.ziaee.frenchreader.content.pdf.OcrTextCorrector? = null

    suspend fun getOcrCorrector(): com.ziaee.frenchreader.content.pdf.OcrTextCorrector =
        ocrCorrector ?: withContext(Dispatchers.Default) {
            val lexicon = get()
            synchronized(this@LemmaLexicon) {
                ocrCorrector ?: com.ziaee.frenchreader.content.pdf.OcrTextCorrector(
                    lexicon.wordForms, lexicon.commonLemmas).also { ocrCorrector = it }
            }
        }

    private fun load(): FrenchLemmaLexicon {
        val rawForms = HashSet<String>(90_000)
        val forms = HashMap<String, Set<String>>(90_000)
        context.assets.open(LEMMA_ASSET).bufferedReader().useLines { lines ->
            lines.forEach { line ->
                val separator = line.indexOf('\t')
                if (separator > 0 && separator < line.lastIndex) {
                    rawForms += line.substring(0, separator).trim().lowercase(java.util.Locale.FRENCH)
                    // Base forms (infinitives such as "visiter", many nouns) appear only as lemmas;
                    // without them the OCR corrector "fixes" correct words.
                    line.substring(separator + 1).split(',').forEach { lemma ->
                        lemma.trim().takeIf { it.isNotEmpty() }?.let { rawForms += it.lowercase(java.util.Locale.FRENCH) }
                    }
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
        return InMemoryLemmaLexicon(forms, common, rawForms)
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
