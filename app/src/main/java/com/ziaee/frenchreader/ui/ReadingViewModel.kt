package com.ziaee.frenchreader.ui

import android.app.Application
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.PlayerMessage
import com.ziaee.frenchreader.data.AppDatabase
import com.ziaee.frenchreader.data.HighlightEntry
import com.ziaee.frenchreader.data.HighlightRepository
import com.ziaee.frenchreader.data.TextBodyStore
import com.ziaee.frenchreader.data.TextDocument
import com.ziaee.frenchreader.data.VocabStatus
import com.ziaee.frenchreader.data.VocabPrefs
import com.ziaee.frenchreader.data.status
import com.ziaee.frenchreader.content.SavedVocab
import com.ziaee.frenchreader.content.buildVocabStatusMap
import com.ziaee.frenchreader.text.BlockType
import com.ziaee.frenchreader.text.MarkdownParser
import com.ziaee.frenchreader.text.ParsedBlock
import com.ziaee.frenchreader.text.TextChunker
import com.ziaee.frenchreader.text.alignSentencesToDisplay
import com.ziaee.frenchreader.text.sanitizeForSpeech
import com.ziaee.frenchreader.shadowing.*
import com.ziaee.frenchreader.translate.TranslationRepository
import com.ziaee.frenchreader.tts.SentenceBoundary
import com.ziaee.frenchreader.tts.TtsChunkRepository
import com.ziaee.frenchreader.tts.XTTS_VOICE_PREFIX
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId

enum class ChunkStatus { PENDING, LOADING, READY, ERROR }

data class ChunkState(
    val block: ParsedBlock,
    val status: ChunkStatus = ChunkStatus.PENDING,
    val sentences: List<SentenceBoundary> = emptyList(),
    val error: String? = null,
    val playerItemIndex: Int? = null,
    val translation: String? = null,
    val translationStatus: ChunkStatus = ChunkStatus.PENDING
) {
    // The Markdown-stripped display text (symbols and emoji included) -- kept
    // as a property (instead of a stored field) so every call site that read
    // `chunk.text` before the Markdown-parsing pass keeps working unchanged.
    val text: String get() = block.plainText

    // The symbol-free text actually sent to TTS/translation (and their caches).
    val spokenText: String get() = block.spokenText
}

/** Re-cuts TTS [sentences] (spoken text) into display segments that keep the block's symbols. */
internal fun displaySentences(block: ParsedBlock, sentences: List<SentenceBoundary>): List<SentenceBoundary> {
    val segments = alignSentencesToDisplay(block.plainText, block.spokenText, sentences.map { it.text })
        ?: return sentences
    return sentences.zip(segments) { sentence, segment -> sentence.copy(text = segment) }
}

data class ReadingUiState(
    val textDoc: TextDocument? = null,
    val chunks: SnapshotStateList<ChunkState> = mutableStateListOf(),
    val currentChunkIndex: Int = 0,
    val currentPositionMs: Long = 0,
    val isPlaying: Boolean = false,
    val speed: Float = 1.0f,
    val ready: Boolean = false,
    val showTranslations: Boolean = false,
    val fullSynthesisTotal: Int = 0,
    val fullSynthesisDone: Int = 0,
    val fullSynthesisError: String? = null
)

internal fun fullSynthesisTargets(chunks: List<ChunkState>): List<String> =
    chunks.filter { it.block.type != BlockType.IMAGE }.map { it.spokenText }

internal fun nextSpokenChunkIndex(chunks: List<ChunkState>, fromIndex: Int): Int? =
    (fromIndex.coerceAtLeast(0) until chunks.size).firstOrNull {
        chunks[it].block.type != BlockType.IMAGE
    }

internal fun previousSpokenChunkIndex(chunks: List<ChunkState>, fromIndex: Int): Int? =
    (fromIndex.coerceAtMost(chunks.lastIndex) downTo 0).firstOrNull {
        chunks[it].block.type != BlockType.IMAGE
    }

internal fun nextSentenceRef(chunks: List<ChunkState>, ref: SentenceRef): SentenceRef? {
    val chunk = chunks.getOrNull(ref.chunkIndex) ?: return null
    if (ref.sentenceIndex < chunk.sentences.size - 1) return ref.copy(sentenceIndex = ref.sentenceIndex + 1)
    val next = nextSpokenChunkIndex(chunks, ref.chunkIndex + 1) ?: return null
    return SentenceRef(next, 0)
}

internal fun shadowStopPositionMs(sentence: SentenceBoundary, itemDurationMs: Long): Long {
    val start = sentence.offsetMs.toLong()
    val end = (sentence.offsetMs + sentence.durationMs).toLong()
    val stop = if (itemDurationMs > 0) minOf(end, itemDurationMs - 50) else end
    return maxOf(start, stop)
}

internal fun previousSentenceRef(chunks: List<ChunkState>, ref: SentenceRef): SentenceRef? {
    if (ref.sentenceIndex > 0) return ref.copy(sentenceIndex = ref.sentenceIndex - 1)
    val prev = previousSpokenChunkIndex(chunks, ref.chunkIndex - 1) ?: return null
    return SentenceRef(prev, (chunks[prev].sentences.size - 1).coerceAtLeast(0))
}

internal const val AUDIO_LOOKAHEAD = 3
internal const val TRANSLATION_LOOKAHEAD = 5

internal fun audioWindowIndices(
    chunks: List<ChunkState>,
    currentIndex: Int,
    lookahead: Int = AUDIO_LOOKAHEAD
): List<Int> {
    if (chunks.isEmpty()) return emptyList()
    val result = ArrayList<Int>(lookahead.coerceAtLeast(0) + 1)
    var index = currentIndex.coerceIn(0, chunks.lastIndex)
    while (result.size <= lookahead) {
        val spoken = nextSpokenChunkIndex(chunks, index) ?: break
        result += spoken
        index = spoken + 1
    }
    return result
}

internal fun translationWindowIndices(
    chunkCount: Int,
    currentIndex: Int,
    lookahead: Int = TRANSLATION_LOOKAHEAD
): List<Int> {
    if (chunkCount <= 0) return emptyList()
    val start = currentIndex.coerceIn(0, chunkCount - 1)
    val end = (start + lookahead.coerceAtLeast(0)).coerceAtMost(chunkCount - 1)
    return (start..end).toList()
}

internal fun playerItemIndexMap(chunkIndices: List<Int>): Map<Int, Int> =
    chunkIndices.withIndex().associate { (itemIndex, chunkIndex) -> chunkIndex to itemIndex }

internal fun resetAudioForJump(chunks: List<ChunkState>): List<ChunkState> = chunks.map { chunk ->
    if (chunk.block.type == BlockType.IMAGE) {
        chunk.copy(status = ChunkStatus.READY, sentences = emptyList(), error = null, playerItemIndex = null)
    } else {
        chunk.copy(status = ChunkStatus.PENDING, sentences = emptyList(), error = null, playerItemIndex = null)
    }
}

class ReadingViewModel(app: Application) : AndroidViewModel(app) {
    data class ShadowSummaryUi(val summary: ShadowTextSummary, val weakTexts: Map<SentenceRef, String>)

    private val db = AppDatabase.get(app)
    private val bodyStore = TextBodyStore(app)
    private val ttsRepo = TtsChunkRepository(app)
    private val translationRepo = TranslationRepository(app)
    private val highlightRepository = HighlightRepository(db.highlightDao())
    val player: ExoPlayer = ExoPlayer.Builder(app).build()
    private val selectionPlayer: ExoPlayer = ExoPlayer.Builder(app).build()
    private var selectionPlaybackJob: Job? = null

    private val shadowingController = ShadowingController(
        scope = viewModelScope,
        engineFactory = {
            when (ShadowingPrefs.getEngine(app)) {
                SpeechEngineKind.VOSK -> VoskEngine(VoskModelManager(app).modelDir)
                SpeechEngineKind.ANDROID -> AndroidSpeechEngine(app)
            }
        },
        saveAttempt = { db.shadowAttemptDao().insert(it) },
        addStudyTimeMs = { delta ->
            VocabPrefs.addStudyTimeMs(app, java.time.LocalDate.now().toString(), delta)
        },
        now = System::currentTimeMillis,
        isPlaying = { player.isPlaying || player.playWhenReady }
    )
    val shadowing: StateFlow<ShadowingState> = shadowingController.state
    private val _shadowSummary = MutableStateFlow<ShadowSummaryUi?>(null)
    val shadowSummary: StateFlow<ShadowSummaryUi?> = _shadowSummary.asStateFlow()
    private var shadowStopMessage: PlayerMessage? = null
    private var shadowSessionStartedAtMs = 0L
    // Set when the target chunk had no sentences/player item yet; the ticker retries.
    private var shadowPendingPlay = false

    private val _state = MutableStateFlow(ReadingUiState())
    val state: StateFlow<ReadingUiState> = _state.asStateFlow()
    private val _highlights = MutableStateFlow<List<HighlightEntry>>(emptyList())
    val highlights: StateFlow<List<HighlightEntry>> = _highlights.asStateFlow()
    val savedVocabStatuses: StateFlow<Map<String, VocabStatus>> = db.vocabDao()
        .observeAll()
        .map { entries ->
            buildVocabStatusMap(entries.map { SavedVocab(it.word, it.status()) })
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    private var audioWindowJob: Job? = null
    private var fullSynthesisJob: Job? = null
    private var translationJob: Job? = null
    private var highlightsJob: Job? = null
    private var loadJob: Job? = null
    private var requestedTextId: Long? = null
    private var positionTickerJob: Job? = null
    private var savePositionJob: Job? = null
    private val pendingListeningMsByDate = linkedMapOf<String, Long>()
    private var lastListeningAccountedAtMs: Long? = null
    private var playlistGeneration = 0L
    private var translationGeneration = 0L
    private val playerItemToChunk = mutableListOf<Int>()
    private var requestedAudioEndIndex = -1
    private var nextSynthesisIndex = 0

    init {
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                accountListeningUntil(System.currentTimeMillis())
                lastListeningAccountedAtMs = if (isPlaying) System.currentTimeMillis() else null
                _state.value = _state.value.copy(isPlaying = isPlaying)
                if (isPlaying) requestAudioWindow(_state.value.currentChunkIndex)
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (
                    shadowing.value.enabled &&
                    reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO &&
                    shadowStopMessage != null
                ) {
                    player.pause()
                    cancelShadowStop()
                }
                if (mediaItem == null) return
                val chunkIdx = playerItemToChunk.getOrNull(player.currentMediaItemIndex)
                if (chunkIdx != null) {
                    _state.value = _state.value.copy(currentChunkIndex = chunkIdx)
                    restartTranslationWindow(chunkIdx)
                    if (player.playWhenReady) requestAudioWindow(chunkIdx)
                }
            }
        })
        startPositionTicker()
    }

    fun load(textId: Long) {
        requestedTextId = textId
        highlightsJob?.cancel()
        _highlights.value = emptyList()
        highlightsJob = viewModelScope.launch {
            highlightRepository.observeHighlights(textId).collect { _highlights.value = it }
        }
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val doc = db.textDao().getById(textId) ?: return@launch
            // Once per successful load, not on every playback tick -- feeds
            // Home's Continue Reading and Library's "last read" sort.
            db.textDao().markAccessed(textId, System.currentTimeMillis())
            val chunks = withContext(Dispatchers.Default) {
                TextChunker.chunk(bodyStore.read(doc)).map { text ->
                    val block = MarkdownParser.parse(text)
                    if (block.type == BlockType.IMAGE) {
                        ChunkState(
                            block = block,
                            status = ChunkStatus.READY,
                            translationStatus = ChunkStatus.READY
                        )
                    } else {
                        ChunkState(block = block)
                    }
                }
            }.toMutableStateList()
            if (requestedTextId != textId) return@launch
            _state.value = ReadingUiState(
                textDoc = doc,
                chunks = chunks,
                currentChunkIndex = doc.lastChunkIndex.coerceIn(0, (chunks.size - 1).coerceAtLeast(0)),
                currentPositionMs = doc.lastPositionMs,
                speed = 1.0f,
                ready = false
            )
            resumeFromSavedPosition(doc)
            restartTranslationWindow(_state.value.currentChunkIndex)
        }
    }

    fun addHighlight(startOffset: Int, endOffset: Int, colorKey: String) {
        val textId = _state.value.textDoc?.id ?: return
        viewModelScope.launch {
            highlightRepository.addHighlight(textId, startOffset, endOffset, colorKey)
        }
    }

    fun updateHighlightColor(id: Long, colorKey: String) {
        viewModelScope.launch { highlightRepository.updateColor(id, colorKey) }
    }

    fun deleteHighlight(id: Long) {
        viewModelScope.launch { highlightRepository.delete(id) }
    }

    fun deleteHighlights(ids: Collection<Long>) {
        if (ids.isEmpty()) return
        viewModelScope.launch { highlightRepository.delete(ids) }
    }

    /** Translates what's on screen (from [firstVisible], [count] paragraphs plus a few ahead), so
     * translations appear wherever the reader scrolls, not only around the playback position. */
    fun translateVisible(firstVisible: Int, count: Int) {
        restartTranslationWindow(firstVisible, count + TRANSLATION_LOOKAHEAD)
    }

    private fun restartTranslationWindow(currentIndex: Int, lookahead: Int = TRANSLATION_LOOKAHEAD) {
        val targetLang = _state.value.textDoc?.translationLang ?: return
        val generation = ++translationGeneration
        translationJob?.cancel()
        translationJob = viewModelScope.launch {
            for (i in translationWindowIndices(_state.value.chunks.size, currentIndex, lookahead)) {
                val chunk = _state.value.chunks.getOrNull(i) ?: continue
                if (chunk.block.type == BlockType.IMAGE) {
                    // Images have no text, so no translation request.
                    updateChunk(i) { it.copy(translationStatus = ChunkStatus.READY, translation = null) }
                    continue
                }
                if (chunk.translationStatus == ChunkStatus.READY) continue
                updateChunk(i) { it.copy(translationStatus = ChunkStatus.LOADING) }
                val result = translationRepo.getOrTranslate(chunk.spokenText, targetLang)
                if (generation != translationGeneration) return@launch
                result.fold(
                    onSuccess = { translated ->
                        updateChunk(i) { it.copy(translation = translated, translationStatus = ChunkStatus.READY) }
                    },
                    onFailure = {
                        updateChunk(i) { it.copy(translationStatus = ChunkStatus.ERROR) }
                    }
                )
            }
        }
    }

    fun toggleShowTranslations() {
        _state.value = _state.value.copy(showTranslations = !_state.value.showTranslations)
    }

    fun togglePinned() {
        val doc = _state.value.textDoc ?: return
        val pinned = !doc.pinned
        _state.value = _state.value.copy(textDoc = doc.copy(pinned = pinned))
        val chunkTexts = _state.value.chunks.map { it.spokenText }
        viewModelScope.launch(Dispatchers.IO) {
            db.textDao().setPinned(doc.id, pinned)
            ttsRepo.setDocumentPinned(chunkTexts, doc.voice, doc.ratePercent, pinned)
        }
    }

    fun synthesizeFullDocument() {
        val doc = _state.value.textDoc ?: return
        if (fullSynthesisJob?.isActive == true) return
        val voice = doc.voice
        val ratePercent = doc.ratePercent
        if (!voice.startsWith(XTTS_VOICE_PREFIX)) return
        val targets = fullSynthesisTargets(_state.value.chunks)
        _state.value = _state.value.copy(
            fullSynthesisTotal = targets.size,
            fullSynthesisDone = 0,
            fullSynthesisError = null
        )
        if (!doc.pinned) {
            _state.value = _state.value.copy(textDoc = doc.copy(pinned = true))
        }
        fullSynthesisJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                if (!doc.pinned) {
                    db.textDao().setPinned(doc.id, true)
                    ttsRepo.setDocumentPinned(targets, voice, ratePercent, true)
                }
                for (text in targets) {
                    if (!isActive) {
                        _state.value = _state.value.copy(fullSynthesisTotal = 0, fullSynthesisDone = 0)
                        return@launch
                    }
                    val result = ttsRepo.getOrSynthesize(text, voice, ratePercent)
                    if (!isActive) {
                        _state.value = _state.value.copy(fullSynthesisTotal = 0, fullSynthesisDone = 0)
                        return@launch
                    }
                    if (result.isFailure) {
                        _state.value = _state.value.copy(
                            fullSynthesisTotal = 0,
                            fullSynthesisDone = 0,
                            fullSynthesisError = result.exceptionOrNull()?.message
                                ?: result.exceptionOrNull()?.toString()
                                ?: "Audio synthesis failed"
                        )
                        return@launch
                    }
                    ttsRepo.pinText(text, voice, ratePercent)
                    _state.value = _state.value.copy(
                        fullSynthesisDone = _state.value.fullSynthesisDone + 1
                    )
                }
                _state.value = _state.value.copy(fullSynthesisTotal = 0, fullSynthesisDone = 0)
            } catch (_: CancellationException) {
                _state.value = _state.value.copy(fullSynthesisTotal = 0, fullSynthesisDone = 0)
            } catch (error: Exception) {
                _state.value = _state.value.copy(
                    fullSynthesisTotal = 0,
                    fullSynthesisDone = 0,
                    fullSynthesisError = error.message ?: error.toString()
                )
            }
        }
    }

    fun cancelFullSynthesis() {
        fullSynthesisJob?.cancel()
    }

    fun clearFullSynthesisError() {
        _state.value = _state.value.copy(fullSynthesisError = null)
    }

    private suspend fun resumeFromSavedPosition(doc: TextDocument) {
        val chunks = _state.value.chunks
        val savedIndex = doc.lastChunkIndex.coerceIn(0, (chunks.size - 1).coerceAtLeast(0))
        val targetIndex = nextSpokenChunkIndex(chunks, savedIndex)
            ?: previousSpokenChunkIndex(chunks, savedIndex)
            ?: savedIndex
        val resumePositionMs = if (targetIndex == savedIndex) doc.lastPositionMs else 0L
        _state.value = _state.value.copy(
            currentChunkIndex = targetIndex,
            currentPositionMs = resumePositionMs
        )
        rebuildPlaylist(targetIndex, resetChunks = false)
        synthesizeChunk(targetIndex, playlistGeneration)
        val targetItemIndex = _state.value.chunks.getOrNull(targetIndex)?.playerItemIndex
        if (targetItemIndex != null) {
            player.seekTo(targetItemIndex, resumePositionMs)
        }
        _state.value = _state.value.copy(ready = true)
        requestAudioWindow(targetIndex)
    }

    private fun requestAudioWindow(currentIndex: Int) {
        val desired = audioWindowIndices(_state.value.chunks, currentIndex)
        val desiredEnd = desired.lastOrNull() ?: return
        if (desiredEnd > requestedAudioEndIndex) requestedAudioEndIndex = desiredEnd
        if (audioWindowJob?.isActive == true) return
        val generation = playlistGeneration
        audioWindowJob = viewModelScope.launch {
            while (generation == playlistGeneration) {
                val next = nextSpokenChunkIndex(_state.value.chunks, nextSynthesisIndex) ?: break
                if (next > requestedAudioEndIndex) break
                val chunk = _state.value.chunks[next]
                if (chunk.playerItemIndex != null || chunk.status == ChunkStatus.READY) {
                    nextSynthesisIndex = next + 1
                    continue
                }
                if (chunk.status == ChunkStatus.LOADING) break
                synthesizeChunk(next, generation)
                if (_state.value.chunks.getOrNull(next)?.status == ChunkStatus.ERROR) break
                nextSynthesisIndex = next + 1
            }
        }
    }

    private suspend fun synthesizeChunk(index: Int, generation: Long = playlistGeneration) {
        val doc = _state.value.textDoc ?: return
        val chunk = _state.value.chunks.getOrNull(index) ?: return
        if (chunk.block.type == BlockType.IMAGE) {
            updateChunk(index) {
                it.copy(
                    status = ChunkStatus.READY,
                    sentences = emptyList(),
                    error = null,
                    playerItemIndex = null,
                    translationStatus = ChunkStatus.READY,
                    translation = null
                )
            }
            return
        }
        if (chunk.status == ChunkStatus.READY || chunk.status == ChunkStatus.LOADING) return

        updateChunk(index) { it.copy(status = ChunkStatus.LOADING, error = null) }

        val result = ttsRepo.getOrSynthesize(chunk.spokenText, doc.voice, doc.ratePercent)

        // A voice switch (or a fresh load()) can reset this text's chunks
        // while this synthesis call is still in flight -- coroutine
        // cancellation is cooperative and can't forcibly abort a
        // network/native call that's already under way. If the voice this
        // result was generated for is no longer the text's current voice,
        // it belongs to a player timeline that no longer exists; inserting
        // it now would silently corrupt the chunk<->player-item mapping
        // (and desync the highlight from the audio) rather than fail
        // loudly, so it's discarded instead.
        if (_state.value.textDoc?.voice != doc.voice || generation != playlistGeneration) return

        result.fold(
            onSuccess = { synth ->
                // Record the chunk -> player-item mapping *before* touching
                // the player. Adding the very first item to an emptied
                // playlist (right after a voice switch) can fire
                // onMediaItemTransition synchronously, and that listener
                // looks up the chunk by this mapping -- if it ran before
                // the mapping was set, the lookup would miss and the
                // highlighted chunk would fall out of sync with the audio.
                val itemIndex = player.mediaItemCount
                updateChunk(index) {
                    it.copy(
                        status = ChunkStatus.READY,
                        sentences = displaySentences(it.block, synth.sentences),
                        playerItemIndex = itemIndex
                    )
                }
                val wasWaitingAtEnd = player.playWhenReady && player.playbackState == Player.STATE_ENDED
                playerItemToChunk += index
                player.addMediaItem(MediaItem.fromUri(synth.audioFile.toURI().toString()))
                if (player.playbackState == Player.STATE_IDLE) player.prepare()
                if (wasWaitingAtEnd) {
                    player.seekTo(itemIndex, 0L)
                    player.prepare()
                    player.play()
                }
            },
            onFailure = { e ->
                updateChunk(index) {
                    it.copy(status = ChunkStatus.ERROR, error = e.message ?: e.toString())
                }
            }
        )
    }

    private fun rebuildPlaylist(startIndex: Int, resetChunks: Boolean = true) {
        playlistGeneration++
        audioWindowJob?.cancel()
        player.stop()
        playerItemToChunk.clear()
        player.clearMediaItems()
        requestedAudioEndIndex = startIndex - 1
        nextSynthesisIndex = startIndex
        if (resetChunks) {
            val reset = resetAudioForJump(_state.value.chunks)
            for (i in reset.indices) _state.value.chunks[i] = reset[i]
        }
    }

    /**
     * Switches this text's TTS voice and re-synthesizes starting at the
     * current chunk (audio already generated for the old voice can't be
     * reused -- it's a different recording). Persisted to the text's own
     * `voice` column so it sticks next time this text is opened.
     *
     * Deliberately does NOT re-synthesize the paragraphs already read
     * (0 until resumeIndex) up front -- for a long text that could mean
     * redoing a dozen-plus network calls before playback can resume at all.
     * Only the current paragraph is synthesized synchronously (fast: one
     * call), and playback picks up from the matching *sentence* within it
     * (by index, not by millisecond offset -- a different voice speaks at a
     * different pace, so old timings don't line up, but sentence order
     * does). Paragraphs before it keep their text/formatting on screen
     * exactly as before; only their now-invalid link into the cleared
     * player timeline is dropped, so tapping a sentence there is a no-op
     * until the text is reopened, rather than jumping to the wrong audio.
     */
    fun changeVoice(voice: String) {
        val doc = _state.value.textDoc ?: return
        if (doc.voice == voice) return
        viewModelScope.launch {
            val updatedDoc = doc.copy(voice = voice)
            db.textDao().update(updatedDoc)

            val chunks = _state.value.chunks
            val currentIndex = _state.value.currentChunkIndex
            val resumeIndex = nextSpokenChunkIndex(chunks, currentIndex)
                ?: previousSpokenChunkIndex(chunks, currentIndex)
                ?: currentIndex
            val resumeSentenceIdx = (_state.value.chunks.getOrNull(resumeIndex)?.sentences
                ?.indexOfLast { _state.value.currentPositionMs >= it.offsetMs } ?: -1)
                .coerceAtLeast(0)

            _state.value = _state.value.copy(
                textDoc = updatedDoc,
                currentChunkIndex = resumeIndex,
                ready = false
            )

            rebuildPlaylist(resumeIndex)
            synthesizeChunk(resumeIndex)
            val resumed = _state.value.chunks.getOrNull(resumeIndex)
            val resumedItemIndex = resumed?.playerItemIndex
            if (resumed?.status == ChunkStatus.READY && resumedItemIndex != null) {
                val seekMs = resumed.sentences.getOrNull(resumeSentenceIdx)?.offsetMs?.toLong() ?: 0L
                player.seekTo(resumedItemIndex, seekMs)
                _state.value = _state.value.copy(currentPositionMs = seekMs)
            }
            _state.value = _state.value.copy(ready = true)
            requestAudioWindow(resumeIndex)
        }
    }

    fun retryChunk(index: Int) {
        viewModelScope.launch {
            updateChunk(index) { it.copy(status = ChunkStatus.PENDING, error = null) }
            synthesizeChunk(index)
            if (_state.value.chunks.getOrNull(index)?.status == ChunkStatus.READY) {
                requestAudioWindow(_state.value.currentChunkIndex)
            }
        }
    }

    fun jumpToChunk(index: Int) {
        val chunks = _state.value.chunks
        if (chunks.isEmpty()) return
        val target = nextSpokenChunkIndex(chunks, index.coerceIn(0, chunks.lastIndex))
            ?: previousSpokenChunkIndex(chunks, index.coerceIn(0, chunks.lastIndex))
            ?: return
        val keepPlaying = player.isPlaying || player.playWhenReady
        viewModelScope.launch { jumpToChunkInternal(target, keepPlaying, seekToLastSentence = false) }
    }

    private suspend fun jumpToChunkInternal(
        target: Int,
        keepPlaying: Boolean,
        seekToLastSentence: Boolean
    ) {
        rebuildPlaylist(target)
        _state.value = _state.value.copy(currentChunkIndex = target, currentPositionMs = 0L)
        restartTranslationWindow(target)
        synthesizeChunk(target)
        val chunk = _state.value.chunks.getOrNull(target)
        val itemIndex = chunk?.playerItemIndex
        if (itemIndex != null) {
            val position = if (seekToLastSentence) {
                chunk.sentences.lastOrNull()?.offsetMs?.toLong() ?: 0L
            } else 0L
            player.seekTo(itemIndex, position)
            _state.value = _state.value.copy(currentPositionMs = position)
            if (keepPlaying) player.play()
        }
        requestAudioWindow(target)
    }

    fun togglePlayPause() {
        if (shadowing.value.enabled) { if (player.isPlaying) player.pause() else shadowPlayOriginal(); return }
        if (player.isPlaying) player.pause() else player.play()
    }

    fun setShadowing(enabled: Boolean) {
        shadowingController.setEnabled(enabled)
        cancelShadowStop()
        if (!enabled) return
        shadowSessionStartedAtMs = System.currentTimeMillis()
        player.pause()
        val ref = activeSentenceRef() ?: firstSentenceRefFrom(_state.value.currentChunkIndex) ?: return
        setShadowTarget(ref)
    }

    fun shadowPlayOriginal() { shadowing.value.target?.let { playShadowSentence(it) } }

    fun shadowNext() {
        val ref = shadowing.value.target ?: return
        val next = nextSentenceRef(_state.value.chunks, ref)
        if (next == null) { finishShadowing(); return }
        setShadowTarget(next); playShadowSentence(next)
    }

    /** Ends the session and, if anything was saved in it, shows the per-text summary. */
    fun finishShadowing() {
        val doc = _state.value.textDoc
        val since = shadowSessionStartedAtMs
        setShadowing(false)
        if (doc == null) return
        viewModelScope.launch {
            val summary = shadowTextSummary(db.shadowAttemptDao().getForTextSince(doc.id, since)) ?: return@launch
            val chunks = _state.value.chunks
            val texts = summary.weakest.associate { w ->
                w.ref to chunks.getOrNull(w.ref.chunkIndex)?.sentences?.getOrNull(w.ref.sentenceIndex)?.text.orEmpty()
            }
            _shadowSummary.value = ShadowSummaryUi(summary, texts)
        }
    }

    fun dismissShadowSummary() { _shadowSummary.value = null }

    /** From the summary: turn shadowing back on at a weak sentence (permission and model already set up). */
    fun practiceSentence(ref: SentenceRef) {
        _shadowSummary.value = null
        setShadowing(true)
        setShadowTarget(ref)
        playShadowSentence(ref)
    }

    fun shadowPrevious() {
        val ref = shadowing.value.target ?: return
        previousSentenceRef(_state.value.chunks, ref)?.let { setShadowTarget(it); playShadowSentence(it) }
    }

    fun shadowPressStart(): Boolean = shadowingController.pressStart()
    fun shadowPressEnd() = shadowingController.pressEnd()
    fun shadowCancelRecording() = shadowingController.cancel()
    fun shadowRetry() = shadowingController.retry()
    fun shadowReplayMine() { player.pause(); shadowingController.replayMine() }

    private fun activeSentenceRef(): SentenceRef? =
        activeSentenceGlobalIndex()?.let { SentenceRef(it.chunkIdx, it.sentenceIdx) }

    private fun firstSentenceRefFrom(chunkIndex: Int): SentenceRef? =
        nextSpokenChunkIndex(_state.value.chunks, chunkIndex)?.let { SentenceRef(it, 0) }

    private fun setShadowTarget(ref: SentenceRef) {
        val doc = _state.value.textDoc ?: return
        val sentence = _state.value.chunks.getOrNull(ref.chunkIndex)?.sentences?.getOrNull(ref.sentenceIndex)
        val expectedMs = sentence?.let { (it.durationMs / _state.value.speed).toLong() } ?: 0L
        shadowingController.setTarget(doc.id, ref, sentence?.text.orEmpty(), expectedMs)
    }

    private fun playShadowSentence(ref: SentenceRef) {
        shadowingController.cancel()
        val chunk = _state.value.chunks.getOrNull(ref.chunkIndex) ?: return
        val sentence = chunk.sentences.getOrNull(ref.sentenceIndex)
        val itemIndex = chunk.playerItemIndex
        if (sentence == null || itemIndex == null) {
            // Not synthesized yet: jump there; the ticker plays once sentences arrive.
            shadowPendingPlay = true
            jumpToChunk(ref.chunkIndex)
            return
        }
        shadowPendingPlay = false
        cancelShadowStop()
        val timeline = player.currentTimeline
        val durationOfItem = if (itemIndex in 0 until timeline.windowCount) {
            timeline.getWindow(itemIndex, Timeline.Window()).durationMs.let {
                if (it == C.TIME_UNSET) -1L else it
            }
        } else {
            -1L
        }
        val stopPositionMs = shadowStopPositionMs(sentence, durationOfItem)
        shadowStopMessage = player.createMessage { _, _ ->
            player.pause()
            shadowStopMessage = null
        }
            .setLooper(android.os.Looper.getMainLooper())
            .setPosition(itemIndex, stopPositionMs)
            .setDeleteAfterDelivery(true)
            .send()
        player.seekTo(itemIndex, sentence.offsetMs.toLong())
        player.play()
    }

    private fun cancelShadowStop() { shadowStopMessage?.cancel(); shadowStopMessage = null }

    fun skipMs(deltaMs: Long) {
        player.seekTo((player.currentPosition + deltaMs).coerceAtLeast(0))
    }

    /** Plays [text] (an arbitrary multi-word selection, not necessarily a whole sentence)
     * via a synthesis call independent of the paragraph/sentence playlist -- selections
     * don't line up with the per-chunk audio already generated for playback, and using the
     * main [player] for a one-off clip would corrupt its chunk<->player-item bookkeeping. */
    fun playSelection(text: String) {
        val doc = _state.value.textDoc ?: return
        selectionPlaybackJob?.cancel()
        selectionPlaybackJob = viewModelScope.launch {
            selectionPlayer.stop()
            selectionPlayer.clearMediaItems()
            val spoken = sanitizeForSpeech(text)
            if (spoken.isBlank()) return@launch
            val result = ttsRepo.getOrSynthesize(spoken, doc.voice, doc.ratePercent)
            result.onSuccess { synth ->
                selectionPlayer.setMediaItem(MediaItem.fromUri(synth.audioFile.toURI().toString()))
                selectionPlayer.prepare()
                selectionPlayer.play()
            }
        }
    }

    fun stopSelectionPlayback() {
        selectionPlaybackJob?.cancel()
        selectionPlayer.stop()
    }

    fun setSpeed(speed: Float) {
        player.setPlaybackSpeed(speed)
        _state.value = _state.value.copy(speed = speed)
    }

    fun seekToSentence(chunkIndex: Int, sentence: SentenceBoundary) {
        if (shadowing.value.enabled) {
            val idx = _state.value.chunks.getOrNull(chunkIndex)?.sentences?.indexOf(sentence) ?: -1
            if (idx >= 0) {
                val ref = SentenceRef(chunkIndex, idx)
                setShadowTarget(ref)
                playShadowSentence(ref)
                return
            }
        }
        val itemIndex = _state.value.chunks.getOrNull(chunkIndex)?.playerItemIndex
        if (itemIndex == null) {
            jumpToChunk(chunkIndex)
            return
        }
        player.seekTo(itemIndex, sentence.offsetMs.toLong())
        player.play()
    }

    fun previousSentence() {
        if (shadowing.value.enabled) { shadowPrevious(); return }
        val active = activeSentenceGlobalIndex() ?: return
        if (active.sentenceIdx > 0) {
            val chunk = _state.value.chunks[active.chunkIdx]
            seekToSentence(active.chunkIdx, chunk.sentences[active.sentenceIdx - 1])
        } else if (active.chunkIdx > 0) {
            val prevIndex = previousSpokenChunkIndex(_state.value.chunks, active.chunkIdx - 1) ?: return
            val prevChunk = _state.value.chunks[prevIndex]
            if (prevChunk.playerItemIndex == null) {
                viewModelScope.launch { jumpToChunkInternal(prevIndex, keepPlaying = true, seekToLastSentence = true) }
            } else {
                prevChunk.sentences.lastOrNull()?.let { seekToSentence(prevIndex, it) }
            }
        }
    }

    fun nextSentence() {
        if (shadowing.value.enabled) { shadowNext(); return }
        val active = activeSentenceGlobalIndex() ?: return
        val chunk = _state.value.chunks[active.chunkIdx]
        if (active.sentenceIdx < chunk.sentences.size - 1) {
            seekToSentence(active.chunkIdx, chunk.sentences[active.sentenceIdx + 1])
        } else if (active.chunkIdx < _state.value.chunks.size - 1) {
            val nextIndex = nextSpokenChunkIndex(_state.value.chunks, active.chunkIdx + 1) ?: return
            val nextChunk = _state.value.chunks[nextIndex]
            nextChunk.sentences.firstOrNull()?.let { seekToSentence(nextIndex, it) }
        }
    }

    private data class ActiveSentence(val chunkIdx: Int, val sentenceIdx: Int)

    private fun activeSentenceGlobalIndex(): ActiveSentence? {
        val s = _state.value
        val chunk = s.chunks.getOrNull(s.currentChunkIndex) ?: return null
        val pos = s.currentPositionMs
        val idx = chunk.sentences.indexOfLast { pos >= it.offsetMs }
        return if (idx >= 0) ActiveSentence(s.currentChunkIndex, idx) else null
    }

    private fun updateChunk(index: Int, transform: (ChunkState) -> ChunkState) {
        val chunks = _state.value.chunks
        if (index !in chunks.indices) return
        chunks[index] = transform(chunks[index])
    }

    private fun startPositionTicker() {
        positionTickerJob = viewModelScope.launch {
            while (true) {
                if (player.isPlaying) {
                    _state.value = _state.value.copy(currentPositionMs = player.currentPosition)
                    accountListeningUntil(System.currentTimeMillis())
                    schedulePositionSave()
                }
                if (shadowPendingPlay) shadowing.value.target?.let { ref ->
                    val c = _state.value.chunks.getOrNull(ref.chunkIndex)
                    if (c?.playerItemIndex != null && c.sentences.isNotEmpty()) { setShadowTarget(ref); playShadowSentence(ref) }
                }
                delay(150)
            }
        }
    }

    private fun schedulePositionSave() {
        if (savePositionJob?.isActive == true) return
        savePositionJob = viewModelScope.launch {
            delay(3000)
            flushPositionNow()
        }
    }

    fun persistPositionNow() {
        savePositionJob?.cancel()
        savePositionJob = viewModelScope.launch { flushPositionNow() }
    }

    private suspend fun flushPositionNow() {
        val doc = _state.value.textDoc ?: return
        accountListeningUntil(System.currentTimeMillis())
        val listeningByDate = pendingListeningMsByDate.toMap()
        persistPositionSnapshot(doc.id, _state.value.currentChunkIndex, player.currentPosition, listeningByDate)
        listeningByDate.forEach { (date, savedMs) ->
            val remaining = pendingListeningMsByDate.getOrDefault(date, 0L) - savedMs
            if (remaining > 0) pendingListeningMsByDate[date] = remaining else pendingListeningMsByDate.remove(date)
        }
    }

    private fun accountListeningUntil(nowMs: Long) {
        var startMs = lastListeningAccountedAtMs ?: return
        if (nowMs <= startMs) return
        val zone = ZoneId.systemDefault()
        while (startMs < nowMs) {
            val date = Instant.ofEpochMilli(startMs).atZone(zone).toLocalDate()
            val nextDayMs = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val endMs = minOf(nowMs, nextDayMs)
            pendingListeningMsByDate[date.toString()] =
                pendingListeningMsByDate.getOrDefault(date.toString(), 0L) + (endMs - startMs)
            startMs = endMs
        }
        lastListeningAccountedAtMs = nowMs
    }

    private suspend fun persistPositionSnapshot(
        textId: Long,
        chunkIndex: Int,
        positionMs: Long,
        listeningByDate: Map<String, Long>
    ) {
        db.textDao().savePosition(textId, chunkIndex, positionMs)
        listeningByDate.forEach { (date, durationMs) ->
            if (durationMs > 0) db.activityLogDao().addListening(date, durationMs)
        }
    }

    override fun onCleared() {
        savePositionJob?.cancel()
        runBlocking(NonCancellable) { flushPositionNow() }
        shadowingController.release()
        player.release()
        selectionPlayer.release()
        super.onCleared()
    }
}
