package com.ziaee.frenchreader.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.ziaee.frenchreader.data.AppDatabase
import com.ziaee.frenchreader.data.TextDocument
import com.ziaee.frenchreader.text.BlockType
import com.ziaee.frenchreader.text.MarkdownParser
import com.ziaee.frenchreader.text.ParsedBlock
import com.ziaee.frenchreader.text.TextChunker
import com.ziaee.frenchreader.translate.TranslationRepository
import com.ziaee.frenchreader.tts.SentenceBoundary
import com.ziaee.frenchreader.tts.TtsChunkRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

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
    // The exact Markdown-stripped text sent to TTS/translation -- kept as a
    // property (instead of a stored field) so every other call site that
    // read `chunk.text` before the Markdown-parsing pass keeps working
    // unchanged.
    val text: String get() = block.plainText
}

data class ReadingUiState(
    val textDoc: TextDocument? = null,
    val chunks: List<ChunkState> = emptyList(),
    val currentChunkIndex: Int = 0,
    val currentPositionMs: Long = 0,
    val isPlaying: Boolean = false,
    val speed: Float = 1.0f,
    val ready: Boolean = false,
    val showTranslations: Boolean = false
)

class ReadingViewModel(app: Application) : AndroidViewModel(app) {
    private val db = AppDatabase.get(app)
    private val ttsRepo = TtsChunkRepository(app)
    private val translationRepo = TranslationRepository(app)
    val player: ExoPlayer = ExoPlayer.Builder(app).build()

    private val _state = MutableStateFlow(ReadingUiState())
    val state: StateFlow<ReadingUiState> = _state.asStateFlow()

    private var backgroundSynthesisJob: Job? = null
    private var translationJob: Job? = null
    private var positionTickerJob: Job? = null
    private var savePositionJob: Job? = null

    init {
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _state.value = _state.value.copy(isPlaying = isPlaying)
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val idx = player.currentMediaItemIndex
                val chunkIdx = _state.value.chunks.indexOfFirst { it.playerItemIndex == idx }
                if (chunkIdx >= 0) {
                    _state.value = _state.value.copy(currentChunkIndex = chunkIdx)
                }
            }
        })
        startPositionTicker()
    }

    fun load(textId: Long) {
        viewModelScope.launch {
            val doc = db.textDao().getById(textId) ?: return@launch
            val chunkTexts = TextChunker.chunk(doc.rawText)
            _state.value = ReadingUiState(
                textDoc = doc,
                chunks = chunkTexts.map { ChunkState(block = MarkdownParser.parse(it)) },
                currentChunkIndex = doc.lastChunkIndex.coerceIn(0, (chunkTexts.size - 1).coerceAtLeast(0)),
                currentPositionMs = doc.lastPositionMs,
                speed = 1.0f,
                ready = false
            )
            resumeFromSavedPosition(doc)
            startTranslationLoop(doc.translationLang)
        }
    }

    private fun startTranslationLoop(targetLang: String) {
        translationJob?.cancel()
        translationJob = viewModelScope.launch {
            for (i in _state.value.chunks.indices) {
                val chunk = _state.value.chunks.getOrNull(i) ?: continue
                if (chunk.block.type == BlockType.HEADER) {
                    // Headings read fine on their own; translating a title
                    // in isolation is rarely useful and just wastes a call.
                    updateChunk(i) { it.copy(translationStatus = ChunkStatus.READY, translation = null) }
                    continue
                }
                updateChunk(i) { it.copy(translationStatus = ChunkStatus.LOADING) }
                val result = translationRepo.getOrTranslate(chunk.text, targetLang)
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

    private suspend fun resumeFromSavedPosition(doc: TextDocument) {
        val targetIndex = doc.lastChunkIndex.coerceIn(0, (_state.value.chunks.size - 1).coerceAtLeast(0))
        // Synthesize sequentially up to (and including) the resume target so
        // the player timeline has the right items in the right order.
        for (i in 0..targetIndex) {
            synthesizeChunk(i)
            if (_state.value.chunks.getOrNull(i)?.status == ChunkStatus.ERROR) break
        }
        val targetItemIndex = _state.value.chunks.getOrNull(targetIndex)?.playerItemIndex
        if (targetItemIndex != null) {
            player.seekTo(targetItemIndex, doc.lastPositionMs)
        }
        _state.value = _state.value.copy(ready = true)
        continueBackgroundSynthesis(targetIndex + 1)
    }

    private fun continueBackgroundSynthesis(fromIndex: Int) {
        backgroundSynthesisJob?.cancel()
        backgroundSynthesisJob = viewModelScope.launch {
            for (i in fromIndex until _state.value.chunks.size) {
                synthesizeChunk(i)
                if (_state.value.chunks.getOrNull(i)?.status == ChunkStatus.ERROR) break
            }
        }
    }

    private suspend fun synthesizeChunk(index: Int) {
        val doc = _state.value.textDoc ?: return
        val chunk = _state.value.chunks.getOrNull(index) ?: return
        if (chunk.status == ChunkStatus.READY || chunk.status == ChunkStatus.LOADING) return

        updateChunk(index) { it.copy(status = ChunkStatus.LOADING, error = null) }

        val result = ttsRepo.getOrSynthesize(chunk.text, doc.voice, doc.ratePercent)

        // A voice switch (or a fresh load()) can reset this text's chunks
        // while this synthesis call is still in flight -- coroutine
        // cancellation is cooperative and can't forcibly abort a
        // network/native call that's already under way. If the voice this
        // result was generated for is no longer the text's current voice,
        // it belongs to a player timeline that no longer exists; inserting
        // it now would silently corrupt the chunk<->player-item mapping
        // (and desync the highlight from the audio) rather than fail
        // loudly, so it's discarded instead.
        if (_state.value.textDoc?.voice != doc.voice) return

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
                        sentences = synth.sentences,
                        playerItemIndex = itemIndex
                    )
                }
                player.addMediaItem(MediaItem.fromUri(synth.audioFile.toURI().toString()))
                if (player.playbackState == Player.STATE_IDLE) player.prepare()
            },
            onFailure = { e ->
                updateChunk(index) {
                    it.copy(status = ChunkStatus.ERROR, error = e.message ?: e.toString())
                }
            }
        )
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

            val resumeIndex = _state.value.currentChunkIndex
            val resumeSentenceIdx = (_state.value.chunks.getOrNull(resumeIndex)?.sentences
                ?.indexOfLast { _state.value.currentPositionMs >= it.offsetMs } ?: -1)
                .coerceAtLeast(0)

            backgroundSynthesisJob?.cancel()
            player.stop()
            player.clearMediaItems()

            _state.value = _state.value.copy(
                textDoc = updatedDoc,
                chunks = _state.value.chunks.mapIndexed { i, c ->
                    if (i < resumeIndex) c.copy(playerItemIndex = null)
                    else c.copy(status = ChunkStatus.PENDING, sentences = emptyList(), playerItemIndex = null, error = null)
                },
                currentChunkIndex = resumeIndex,
                ready = false
            )

            synthesizeChunk(resumeIndex)
            val resumed = _state.value.chunks.getOrNull(resumeIndex)
            if (resumed?.status == ChunkStatus.READY) {
                val seekMs = resumed.sentences.getOrNull(resumeSentenceIdx)?.offsetMs?.toLong() ?: 0L
                player.seekTo(resumed.playerItemIndex ?: 0, seekMs)
                _state.value = _state.value.copy(currentPositionMs = seekMs)
            }
            _state.value = _state.value.copy(ready = true)
            continueBackgroundSynthesis(resumeIndex + 1)
        }
    }

    fun retryChunk(index: Int) {
        viewModelScope.launch {
            synthesizeChunk(index)
            if (_state.value.chunks.getOrNull(index)?.status == ChunkStatus.READY) {
                continueBackgroundSynthesis(index + 1)
            }
        }
    }

    fun togglePlayPause() {
        if (player.isPlaying) player.pause() else player.play()
    }

    fun skipMs(deltaMs: Long) {
        player.seekTo((player.currentPosition + deltaMs).coerceAtLeast(0))
    }

    fun setSpeed(speed: Float) {
        player.setPlaybackSpeed(speed)
        _state.value = _state.value.copy(speed = speed)
    }

    fun seekToSentence(chunkIndex: Int, sentence: SentenceBoundary) {
        val itemIndex = _state.value.chunks.getOrNull(chunkIndex)?.playerItemIndex ?: return
        player.seekTo(itemIndex, sentence.offsetMs.toLong())
        player.play()
    }

    fun previousSentence() {
        val active = activeSentenceGlobalIndex() ?: return
        if (active.sentenceIdx > 0) {
            val chunk = _state.value.chunks[active.chunkIdx]
            seekToSentence(active.chunkIdx, chunk.sentences[active.sentenceIdx - 1])
        } else if (active.chunkIdx > 0) {
            val prevChunk = _state.value.chunks[active.chunkIdx - 1]
            prevChunk.sentences.lastOrNull()?.let { seekToSentence(active.chunkIdx - 1, it) }
        }
    }

    fun nextSentence() {
        val active = activeSentenceGlobalIndex() ?: return
        val chunk = _state.value.chunks[active.chunkIdx]
        if (active.sentenceIdx < chunk.sentences.size - 1) {
            seekToSentence(active.chunkIdx, chunk.sentences[active.sentenceIdx + 1])
        } else if (active.chunkIdx < _state.value.chunks.size - 1) {
            val nextChunk = _state.value.chunks[active.chunkIdx + 1]
            nextChunk.sentences.firstOrNull()?.let { seekToSentence(active.chunkIdx + 1, it) }
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
        val list = _state.value.chunks.toMutableList()
        if (index !in list.indices) return
        list[index] = transform(list[index])
        _state.value = _state.value.copy(chunks = list)
    }

    private fun startPositionTicker() {
        positionTickerJob = viewModelScope.launch {
            while (true) {
                if (player.isPlaying) {
                    _state.value = _state.value.copy(currentPositionMs = player.currentPosition)
                    schedulePositionSave()
                }
                delay(150)
            }
        }
    }

    private fun schedulePositionSave() {
        if (savePositionJob?.isActive == true) return
        savePositionJob = viewModelScope.launch {
            delay(3000)
            persistPositionNow()
        }
    }

    fun persistPositionNow() {
        val doc = _state.value.textDoc ?: return
        val chunkIndex = _state.value.currentChunkIndex
        val positionMs = player.currentPosition
        viewModelScope.launch {
            db.textDao().savePosition(doc.id, chunkIndex, positionMs)
        }
    }

    override fun onCleared() {
        persistPositionNow()
        player.release()
        super.onCleared()
    }
}
