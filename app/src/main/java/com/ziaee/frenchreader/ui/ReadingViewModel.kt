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
        result.fold(
            onSuccess = { synth ->
                val itemIndex = player.mediaItemCount
                player.addMediaItem(MediaItem.fromUri(synth.audioFile.toURI().toString()))
                if (player.playbackState == Player.STATE_IDLE) player.prepare()
                updateChunk(index) {
                    it.copy(
                        status = ChunkStatus.READY,
                        sentences = synth.sentences,
                        playerItemIndex = itemIndex
                    )
                }
            },
            onFailure = { e ->
                updateChunk(index) {
                    it.copy(status = ChunkStatus.ERROR, error = e.message ?: e.toString())
                }
            }
        )
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
