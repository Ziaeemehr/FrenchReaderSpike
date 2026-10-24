package com.ziaee.frenchreader.watch

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class IdiomLexicon private constructor(private val context: Context) {
    @Volatile
    private var loaded: InMemoryIdiomLexicon? = null

    suspend fun get(): InMemoryIdiomLexicon = loaded ?: withContext(Dispatchers.IO) {
        loaded ?: synchronized(this@IdiomLexicon) {
            loaded ?: context.assets.open("lexicon/idioms.tsv").bufferedReader().useLines { lines ->
                InMemoryIdiomLexicon(lines.mapNotNull { line ->
                    val parts = line.split('\t', limit = 3)
                    if (parts.size == 3) IdiomEntry(parts[0], parts[1], parts[2]) else null
                }.toList())
            }.also { loaded = it }
        }
    }

    companion object {
        @Volatile
        private var instance: IdiomLexicon? = null

        fun get(context: Context): IdiomLexicon = instance ?: synchronized(this) {
            instance ?: IdiomLexicon(context.applicationContext).also { instance = it }
        }
    }
}
