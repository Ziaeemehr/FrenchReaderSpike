package com.ziaee.frenchreader.watch

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.ziaee.frenchreader.comprehension.LemmaLexicon
import com.ziaee.frenchreader.content.SavedVocab
import com.ziaee.frenchreader.content.buildVocabStatusMap
import com.ziaee.frenchreader.data.status
import com.ziaee.frenchreader.data.VocabStatus
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import com.ziaee.frenchreader.data.VocabPrefs
import com.ziaee.frenchreader.translate.TranslationRepository
import com.ziaee.frenchreader.data.AppDatabase
import com.ziaee.frenchreader.data.TextBodyStore
import com.ziaee.frenchreader.data.TextDocument
import com.ziaee.frenchreader.data.insertTextDocument
import com.ziaee.frenchreader.data.updateTextDocumentBody
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class WatchUiState(
    val libraryTextId: Long? = null,
    val exportBusy: Boolean = false,
    val exportError: Boolean = false,
    val preferencesLoaded: Boolean = false,
    val compactPlayer: Boolean = false,
    val subtitleSizePercent: Int = 115,
    val translationEnabled: Boolean = false,
    val translationLanguage: String = "fa",
    val translations: Map<String, String?> = emptyMap(),
    val userTranslations: Set<String> = emptySet(),
    val failedTranslations: Set<String> = emptySet(),
    val title: String = "",
    val cues: List<SubtitleCue> = emptyList(),
    val loading: Boolean = true,
    val currentTimeMs: Long = 0,
    val playerState: Int = -1,
    val playerError: Int? = null,
    val captionError: Boolean = false,
    val importError: Boolean = false,
    val lookupError: Boolean = false,
    val lookupLoading: Boolean = false,
    val lookup: WatchLookup? = null
)

data class WatchLookup(val textId: Long, val word: String, val sentence: String, val idiomGloss: String? = null)

class WatchViewModel(application: Application, private val saved: SavedStateHandle) : AndroidViewModel(application) {
    private val history = WatchHistory(application)
    private var recent: RecentVideo? = null
    private val mutableState = MutableStateFlow(WatchUiState(
        title = saved.get<String>("title").orEmpty(),
        currentTimeMs = saved.get<Long>(KEY_POSITION) ?: 0L
    ))
    val uiState = mutableState.asStateFlow()
    val savedVocabStatuses: StateFlow<Map<String, VocabStatus>> = AppDatabase.get(application).vocabDao()
        .observeAll()
        .map { entries -> buildVocabStatusMap(entries.map { SavedVocab(it.word, it.status()) }) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())
    private val cache = CaptionCache(application.filesDir)
    private val source: CaptionSource = YoutubeCaptionFetcher()
    private val lookupMutex = Mutex()
    private var videoId: String? = null
    private var captionJob: Job? = null
    private var lookupJob: Job? = null
    private var checkpointJob: Job? = null
    private var foreground = false
    private var lastRecordedPositionMs = 0L
    private val translationRequests = Channel<TranslationRequest>(Channel.CONFLATED)
    private val translationCache = mutableMapOf<Pair<String, String>, MutableMap<String, String?>>()
    private val userTranslationCache = mutableMapOf<Pair<String, String>, MutableSet<String>>()
    private val failedTranslationCache = mutableMapOf<Pair<String, String>, Set<String>>()
    private val correctionVersions = mutableMapOf<Pair<String, String>, Int>()
    private var translationRequest: TranslationRequest? = null

    private data class TranslationRequest(val videoId: String, val language: String, val texts: List<String>, val before: String?)

    init {
        // One worker, never cancelled on cue changes or toggle-off: blocking HTTP can finish
        // after cancellation, so replacing jobs could otherwise overlap physical requests.
        viewModelScope.launch {
            val repository = withContext(Dispatchers.IO) { TranslationRepository(application) }
            for (request in translationRequests) {
                if (!mutableState.value.translationEnabled || translationRequest != request) continue
                val entries = translationCache.getOrPut(request.videoId to request.language) { mutableMapOf() }
                val missing = request.texts.filterNot { entries.containsKey(it) }
                if (missing.isEmpty()) continue
                translationMutex.withLock {
                    if (!mutableState.value.translationEnabled || translationRequest != request) return@withLock
                    val versions = request.texts.associateWith { correctionVersions[it to request.language] ?: 0 }
                    val translated = try {
                        repository.getOrTranslateInContext(request.texts, request.before, request.language)
                    } catch (error: CancellationException) { throw error
                    } catch (error: Exception) { emptyMap() }
                    val corrected = userTranslationCache.getOrPut(request.videoId to request.language) { mutableSetOf() }
                    val overrides = translated.keys.associateWith { repository.hasOverride(it, request.language) }
                    // Recheck after the suspending reads: Save/Reset can run while HTTP is in flight.
                    val current = translated.filterKeys { (correctionVersions[it to request.language] ?: 0) == versions[it] }
                    for (text in current.keys) {
                        if (overrides[text] == true) corrected.add(text) else corrected.remove(text)
                    }
                    entries.putAll(current)
                    val key = request.videoId to request.language
                    failedTranslationCache[key] = (failedTranslationCache[key].orEmpty() + missing.filterNot { entries.containsKey(it) }) - entries.keys
                    if (videoId == request.videoId && mutableState.value.translationLanguage == request.language) {
                        mutableState.update { it.copy(translations = entries.toMap(), userTranslations = corrected.toSet(),
                            failedTranslations = failedTranslationCache[request.videoId to request.language].orEmpty()) }
                    }
                }
            }
        }
    }

    fun refreshExport() {
        val id = videoId ?: return
        viewModelScope.launch {
            try {
                val existing = withContext(Dispatchers.IO) {
                    AppDatabase.get(getApplication()).textDao().findByExternalKey(YoutubeUrl.watchUrl(id))
                }
                if (videoId == id) mutableState.update { it.copy(libraryTextId = existing?.id) }
            } catch (error: CancellationException) { throw error
            } catch (_: Exception) { mutableState.update { it.copy(exportError = true) } }
        }
    }

    fun dismissExportError() { mutableState.update { it.copy(exportError = false) } }

    fun addTranscript(onOpen: (Long) -> Unit) {
        val id = videoId ?: return
        val snapshot = mutableState.value
        if (snapshot.exportBusy || snapshot.cues.isEmpty()) return
        val channel = saved.get<String>("channel").orEmpty().ifBlank { recent?.channel.orEmpty() }.ifBlank { "YouTube" }
        mutableState.update { it.copy(exportBusy = true, exportError = false) }
        viewModelScope.launch {
            try {
                val textId = lookupMutex.withLock {
                    withContext(Dispatchers.IO) {
                        val context = getApplication<Application>()
                        val dao = AppDatabase.get(context).textDao()
                        val existing = dao.findByExternalKey(YoutubeUrl.watchUrl(id))
                        if (existing != null) {
                            replaceTranscript(existing, transcriptOf(snapshot.cues))
                            existing.id
                        } else insertTextDocument(
                            dao, TextBodyStore(context),
                            TextDocument(title = snapshot.title.ifBlank { "YouTube $id" }, rawText = "",
                                sourceUrl = YoutubeUrl.watchUrl(id), sourceName = channel, externalKey = YoutubeUrl.watchUrl(id)),
                            transcriptParagraphs(snapshot.cues))
                    }
                }
                if (videoId == id) {
                    mutableState.update { it.copy(libraryTextId = textId) }
                    onOpen(textId)
                }
            } catch (error: CancellationException) { throw error
            } catch (_: Exception) { mutableState.update { it.copy(exportError = true) }
            } finally { mutableState.update { it.copy(exportBusy = false) } }
        }
    }

    fun prepareSrtExport() { saved[KEY_SRT_VIDEO] = videoId }

    fun completeSrtExport(uri: Uri?) {
        val pendingVideo = saved.remove<String>(KEY_SRT_VIDEO)
        if (uri == null) return
        val cues = mutableState.value.cues
        // After process recreation the picker can return before captions load.
        // Report failure rather than creating an empty or wrong-video export.
        if (pendingVideo == null || pendingVideo != videoId || cues.isEmpty()) {
            reportExportError()
            return
        }
        saveTranscript(uri, transcriptSrt(cues))
    }

    fun reportExportError() { mutableState.update { it.copy(exportError = true) } }

    private fun saveTranscript(uri: Uri, srt: String) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    check(srt.isNotBlank())
                    val resolver = getApplication<Application>().contentResolver
                    checkNotNull(resolver.openOutputStream(uri, "wt")).bufferedWriter(Charsets.UTF_8).use { it.write(srt) }
                }
            } catch (error: CancellationException) { throw error
            } catch (_: Exception) { mutableState.update { it.copy(exportError = true) } }
        }
    }

    fun setSubtitleSizePercent(value: Int) {
        mutableState.update { it.copy(subtitleSizePercent = value) }
        history.setSubtitleSizeAsync(value)
    }

    fun setCompactPlayer(value: Boolean) {
        mutableState.update { it.copy(compactPlayer = value) }
        history.setCompactAsync(value)
    }

    fun setTranslationEnabled(value: Boolean) {
        mutableState.update { it.copy(translationEnabled = value) }
        if (!value) translationRequest = null
        history.setTranslationAsync(value)
    }

    fun prefetchTranslations(units: List<SubtitleCue>, index: Int) {
        val id = videoId ?: return
        if (!mutableState.value.translationEnabled || index !in units.indices) {
            translationRequest = null
            return
        }
        val request = TranslationRequest(id, mutableState.value.translationLanguage,
            units.drop(index).take(3).map { it.text }, units.getOrNull(index - 1)?.text)
        translationRequest = request
        mutableState.update { it.copy(translations = translationCache[id to request.language]?.toMap().orEmpty(),
            userTranslations = userTranslationCache[id to request.language]?.toSet().orEmpty(),
            failedTranslations = failedTranslationCache[id to request.language].orEmpty()) }
        translationRequests.trySend(request)
    }

    fun setUserTranslation(text: String, translation: String?) {
        val id = videoId ?: return
        val language = mutableState.value.translationLanguage
        val versionKey = text to language
        correctionVersions[versionKey] = (correctionVersions[versionKey] ?: 0) + 1
        val version = correctionVersions.getValue(versionKey)
        correctionScope.launch {
            val applyCorrection: suspend () -> Unit = {
                correctionMutex.withLock {
                    if (correctionVersions[versionKey] != version) return@withLock
                    val repository = TranslationRepository(getApplication())
                    val entries = translationCache.getOrPut(id to language) { mutableMapOf() }
                    val corrected = userTranslationCache.getOrPut(id to language) { mutableSetOf() }
                    if (translation.isNullOrBlank()) {
                        withContext(NonCancellable) { repository.resetTranslation(text, language) }
                        entries.remove(text)
                        corrected.remove(text)
                    } else {
                        withContext(NonCancellable) { repository.setUserTranslation(text, language, translation) }
                        entries[text] = translation.trim()
                        corrected.add(text)
                    }
                    failedTranslationCache.filterKeys { it.second == language }.keys.toList().forEach { key ->
                        failedTranslationCache[key] = failedTranslationCache[key].orEmpty() - text
                    }
                    // Overrides are keyed by text and language, shared by every cached video.
                    translationCache.filterKeys { it.second == language }.values.forEach { cached ->
                        if (translation.isNullOrBlank()) cached.remove(text)
                        else if (cached.containsKey(text)) cached[text] = translation.trim()
                    }
                    userTranslationCache.filterKeys { it.second == language }.forEach { (key, corrections) ->
                        if (translation.isNullOrBlank()) corrections.remove(text)
                        else if (translationCache[key]?.containsKey(text) == true) corrections.add(text)
                    }
                    if (videoId == id && mutableState.value.translationLanguage == language) {
                        mutableState.update { it.copy(translations = entries.toMap(), userTranslations = corrected.toSet(),
                            failedTranslations = failedTranslationCache[id to language].orEmpty()) }
                        if (translation.isNullOrBlank() && mutableState.value.translationEnabled) {
                            val request = translationRequest?.takeIf { text in it.texts }
                                ?: TranslationRequest(id, language, listOf(text), null)
                            translationRequest = request
                            translationRequests.trySend(request)
                        }
                    }
                }
            }
            if (translation.isNullOrBlank()) {
                // Wait outside correctionMutex so a new Save never queues behind HTTP.
                withContext(NonCancellable) { translationMutex.withLock { applyCorrection() } }
            } else applyCorrection()
        }
    }

    private val idiomMutex = Mutex()
    private val idiomCache = LinkedHashMap<String, List<IdiomMatch>>()

    suspend fun matchIdioms(text: String): List<IdiomMatch> = withContext(Dispatchers.Default) {
        idiomMutex.withLock {
            idiomCache[text] ?: run {
                val context = getApplication<Application>()
                val matcher = IdiomMatcher(IdiomLexicon.get(context).get(), LemmaLexicon.get(context).get())
                matcher.match(text).also {
                    if (idiomCache.size >= 256) idiomCache.remove(idiomCache.keys.first())
                    idiomCache[text] = it
                }
            }
        }
    }

    fun load(id: String) {
        if (id == videoId) return
        videoId = id
        captionJob?.cancel()
        lookupJob?.cancel()
        checkpointJob?.cancel()
        mutableState.update { it.copy(libraryTextId = null, loading = true, cues = emptyList(), translations = emptyMap(), userTranslations = emptySet(), failedTranslations = emptySet()) }
        captionJob = viewModelScope.launch {
            try {
                val preferences = withContext(Dispatchers.IO) {
                    Triple(history.readLatest().firstOrNull { it.videoId == id },
                        Triple(history.compactPlayer, history.translationEnabled, history.subtitleSizePercent),
                        if (VocabPrefs.getMeaningLanguage(getApplication<Application>()) == VocabPrefs.MeaningLanguage.PERSIAN) "fa" else "en")
                }
                recent = preferences.first
                mutableState.update { it.copy(preferencesLoaded = true,
                    compactPlayer = preferences.second.first, translationEnabled = preferences.second.second,
                    subtitleSizePercent = preferences.second.third,
                    translationLanguage = preferences.third,
                    title = it.title.ifBlank { recent?.title.orEmpty() },
                    currentTimeMs = saved.get<Long>(KEY_POSITION) ?: recent?.positionMs ?: 0L) }
                recordRecent()
                checkpointJob = viewModelScope.launch {
                    while (true) {
                        delay(5_000)
                        val state = mutableState.value
                        if (foreground && state.playerState == 1 &&
                            kotlin.math.abs(state.currentTimeMs - lastRecordedPositionMs) >= 5_000L) recordRecent()
                    }
                }
                val cached = withContext(Dispatchers.IO) { cache.read(id) }
                val fetched = if (shouldFetchCaptions(cached)) {
                    try { source.fetch(id)
                    } catch (error: CancellationException) { throw error
                    } catch (error: Exception) {
                        if (cached == null) throw error
                        null
                    }
                } else null
                val decision = chooseCaptions(cached, fetched) ?: error("No captions")
                val captions = decision.captions
                if (decision.write) withContext(Dispatchers.IO) {
                    runCatching { cache.write(id, captions, decision.source) }
                }
                if (saved.get<String>("channel").isNullOrBlank() && captions.channel.isNotBlank()) saved["channel"] = captions.channel
                mutableState.update { it.copy(title = captions.title.ifBlank { it.title }, cues = captions.cues, loading = false, captionError = captions.cues.isEmpty()) }
                recordRecent()
                syncExistingTranscript(id, captions.cues)
            } catch (error: CancellationException) { throw error
            } catch (_: Exception) {
                mutableState.update { it.copy(loading = false, captionError = true) }
            }
        }
    }

    fun importSubtitles(uri: Uri) {
        val id = videoId ?: return
        captionJob?.cancel()
        mutableState.update { it.copy(loading = true, importError = false) }
        captionJob = viewModelScope.launch {
            try {
                val captions = withContext(Dispatchers.IO) {
                    val resolver = getApplication<Application>().contentResolver
                    val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                        if (it.moveToFirst()) it.getString(0) else null
                    }
                    val content = resolver.openInputStream(uri)?.use { input ->
                        // A subtitle file is a few hundred KB; refuse e.g. a video picked by mistake.
                        val out = java.io.ByteArrayOutputStream()
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            out.write(buffer, 0, read)
                            check(out.size() <= MAX_SUBTITLE_BYTES) { "Subtitle file too large" }
                        }
                        out.toString("UTF-8")
                    } ?: error("Cannot read subtitles")
                    val cues = parseSubtitleFile(name, content)
                    check(cues.isNotEmpty())
                    VideoCaptions(mutableState.value.title, cues, saved.get<String>("channel").orEmpty()).also { runCatching { cache.write(id, it, "file") } }
                }
                mutableState.update { it.copy(cues = captions.cues, loading = false, captionError = false, importError = false) }
                syncExistingTranscript(id, captions.cues)
            } catch (error: CancellationException) { throw error
            } catch (_: Exception) {
                mutableState.update { it.copy(loading = false, importError = true) }
            }
        }
    }

    fun setForeground(value: Boolean) { foreground = value }

    fun recordRecent() {
        val id = videoId ?: return
        if (!mutableState.value.preferencesLoaded) return
        lastRecordedPositionMs = mutableState.value.currentTimeMs
        history.recordAsync(RecentVideo(id, mutableState.value.title.ifBlank { recent?.title.orEmpty() },
            saved.get<String>("channel").orEmpty().ifBlank { recent?.channel.orEmpty() }, mutableState.value.currentTimeMs))
    }

    fun setTime(ms: Long) {
        mutableState.update { it.copy(currentTimeMs = ms) }
        // Survives process death, so the restored screen resumes the video where it was.
        saved[KEY_POSITION] = ms
    }
    fun setPlayerState(state: Int) { mutableState.update { it.copy(playerState = state) } }
    fun setPlayerError(code: Int) { mutableState.update { it.copy(playerError = code) } }
    fun dismissLookup() {
        lookupJob?.cancel()
        mutableState.update { it.copy(lookup = null, lookupLoading = false) }
    }

    fun lookup(word: String, sentence: String, fallbackTitle: String, idiomGloss: String? = null) {
        val id = videoId ?: return
        lookupJob?.cancel()
        mutableState.update { it.copy(lookupLoading = true, lookupError = false) }
        lookupJob = viewModelScope.launch {
            try {
                val textId = lookupMutex.withLock {
                    withContext(Dispatchers.IO) {
                        val context = getApplication<Application>()
                        val dao = AppDatabase.get(context).textDao()
                        val url = YoutubeUrl.watchUrl(id)
                        val existing = dao.findByExternalKey(url)
                        if (existing != null) {
                            replaceTranscript(existing, transcriptOf(mutableState.value.cues))
                            existing.id
                        } else insertTextDocument(
                            dao, TextBodyStore(context),
                            TextDocument(title = mutableState.value.title.ifBlank { fallbackTitle }, rawText = "", sourceUrl = url,
                                sourceName = saved.get<String>("channel").orEmpty().ifBlank { recent?.channel.orEmpty() }.ifBlank { "YouTube" }, externalKey = url),
                            transcriptOf(mutableState.value.cues)
                        )
                    }
                }
                mutableState.update { it.copy(lookup = WatchLookup(textId, word, sentence, idiomGloss), lookupLoading = false) }
            } catch (error: CancellationException) { throw error
            } catch (_: Exception) {
                mutableState.update { it.copy(lookupLoading = false, lookupError = true) }
            }
        }
    }

    private suspend fun syncExistingTranscript(id: String, cues: List<SubtitleCue>) {
        lookupMutex.withLock {
            withContext(Dispatchers.IO) {
                val context = getApplication<Application>()
                val dao = AppDatabase.get(context).textDao()
                val existing = dao.findByExternalKey(YoutubeUrl.watchUrl(id)) ?: return@withContext
                replaceTranscript(existing, transcriptOf(cues))
            }
        }
    }

    /** Highlights are stored as character offsets, so a highlighted transcript is never rewritten under them. */
    private suspend fun replaceTranscript(existing: TextDocument, body: String) {
        val context = getApplication<Application>()
        val db = AppDatabase.get(context)
        val store = TextBodyStore(context)
        if (body.isBlank() || body == store.read(existing)) return
        if (db.highlightDao().overlapping(existing.id, 0, Int.MAX_VALUE).isNotEmpty()) return
        updateTextDocumentBody(db.textDao(), store, existing, existing.title, body)
    }

    private companion object {
        val translationMutex = Mutex()
        val correctionMutex = Mutex()
        val correctionScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

        const val KEY_SRT_VIDEO = "watch_pending_srt_video"
        const val KEY_POSITION = "watch_position_ms"
        const val MAX_SUBTITLE_BYTES = 5 * 1024 * 1024
    }
}
