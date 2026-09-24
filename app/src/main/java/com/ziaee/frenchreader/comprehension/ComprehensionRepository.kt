package com.ziaee.frenchreader.comprehension

import android.content.Context
import com.ziaee.frenchreader.data.AppDatabase
import com.ziaee.frenchreader.data.TextBodyStorage
import com.ziaee.frenchreader.data.TextBodyStore
import com.ziaee.frenchreader.data.TextDocument
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ComprehensionRepository private constructor(
    private val bodyStore: TextBodyStorage,
    private val lexiconLoader: LemmaLexicon,
    database: AppDatabase
) {
    private data class KnownState(val version: Long, val lemmas: Set<String>)
    private data class BodyKey(val id: Long, val bodyPath: String?, val rawTextLength: Int)
    private data class ScoreKey(val body: BodyKey, val knownVersion: Long)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val requestedDocuments = HashMap<Long, MutableStateFlow<TextDocument>>()
    private val tokenCache = HashMap<BodyKey, List<FrenchToken>>()
    private val scoreCache = HashMap<ScoreKey, Int?>()
    private val _scores = MutableStateFlow<Map<Long, Int?>>(emptyMap())
    val scores: StateFlow<Map<Long, Int?>> = _scores

    private val knownState = MutableStateFlow<KnownState?>(null)

    init {
        scope.launch {
            var version = 0L
            database.vocabDao().observeAll()
                .mapLatest { entries -> buildKnownLemmaSet(entries, lexiconLoader.get()) }
                .distinctUntilChanged()
                .collectLatest { knownState.value = KnownState(++version, it) }
        }
    }

    fun request(document: TextDocument) {
        synchronized(requestedDocuments) {
            requestedDocuments[document.id]?.let {
                it.value = document
                return
            }
            val documents = MutableStateFlow(document)
            requestedDocuments[document.id] = documents
            scope.launch {
                documents.collectLatest { current ->
                    knownState.collectLatest { known ->
                        if (known != null) publishScore(current, known)
                    }
                }
            }
        }
    }

    private suspend fun publishScore(document: TextDocument, known: KnownState) {
        val bodyKey = BodyKey(document.id, document.bodyPath, document.rawText.length)
        val scoreKey = ScoreKey(bodyKey, known.version)
        val cached = synchronized(scoreCache) { scoreCache.containsKey(scoreKey) to scoreCache[scoreKey] }
        val score = if (cached.first) {
            cached.second
        } else {
            val tokens = synchronized(tokenCache) { tokenCache[bodyKey] }
                ?: tokenizeFrench(bodyStore.read(document), lexiconLoader.get()).also { parsed ->
                    synchronized(tokenCache) {
                        tokenCache.keys.removeAll { it.id == document.id && it != bodyKey }
                        tokenCache[bodyKey] = parsed
                    }
                }
            scoreComprehension(tokens, known.lemmas).also { computed ->
                synchronized(scoreCache) {
                    scoreCache.keys.removeAll { it.body.id == document.id && it != scoreKey }
                    scoreCache[scoreKey] = computed
                }
            }
        }
        _scores.update { it + (document.id to score) }
    }

    companion object {
        @Volatile
        private var instance: ComprehensionRepository? = null

        fun get(context: Context): ComprehensionRepository = instance ?: synchronized(this) {
            instance ?: run {
                val application = context.applicationContext
                ComprehensionRepository(
                    bodyStore = TextBodyStore(application),
                    lexiconLoader = LemmaLexicon.get(application),
                    database = AppDatabase.get(application)
                ).also { instance = it }
            }
        }
    }
}
