package com.ziaee.frenchreader.ui

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import com.ziaee.frenchreader.data.AppDatabase
import com.ziaee.frenchreader.data.VocabEntry
import com.ziaee.frenchreader.tts.TtsChunkRepository
import kotlinx.coroutines.launch

/**
 * A simple 5-box Leitner scheduler. A correct answer moves the card up a
 * box (capped at 5, at which point it's treated as learned/graduated and
 * drops out of the review queue); an incorrect answer sends it straight
 * back to box 1 and resurfaces it soon (short enough to reappear later in
 * the same or the next session, not weeks away).
 */
private const val DAY_MS = 86_400_000L
private const val FORGOT_DELAY_MS = 10 * 60_000L
private val BOX_INTERVAL_DAYS = mapOf(1 to 1L, 2 to 3L, 3 to 7L, 4 to 16L, 5 to 30L)

class VocabReviewViewModel(app: Application) : AndroidViewModel(app) {
    private val db = AppDatabase.get(app)
    private val ttsRepo = TtsChunkRepository(app)
    private val player: ExoPlayer = ExoPlayer.Builder(app).build()

    // Cached per textId so replaying the same card's sentence, or the next
    // card from the same text, doesn't re-query the texts table each time.
    private val voiceCache = HashMap<Long, Pair<String, Int>>()

    var loading by mutableStateOf(true)
        private set
    var current by mutableStateOf<VocabEntry?>(null)
        private set
    var reviewedCount by mutableStateOf(0)
        private set
    var sentenceAudioLoading by mutableStateOf(false)
        private set
    var sentenceAudioError by mutableStateOf(false)
        private set

    private val queue = ArrayDeque<VocabEntry>()

    fun start(scope: Long) {
        viewModelScope.launch {
            player.stop()
            loading = true
            reviewedCount = 0
            val now = System.currentTimeMillis()
            val all = db.vocabDao().getAllOnce()
            val scoped = when (scope) {
                VOCAB_SCOPE_ALL -> all
                VOCAB_SCOPE_UNFILED -> all.filter { it.listId == null }
                else -> all.filter { it.listId == scope }
            }
            val due = scoped.filter { !it.learned && it.nextReviewAtMs <= now }.sortedBy { it.nextReviewAtMs }
            queue.clear()
            queue.addAll(due)
            current = queue.removeFirstOrNull()
            loading = false
        }
    }

    fun answer(knew: Boolean) {
        val entry = current ?: return
        player.stop()
        sentenceAudioError = false
        val now = System.currentTimeMillis()
        val updated = if (knew) {
            val newBox = (entry.leitnerBox + 1).coerceAtMost(5)
            entry.copy(
                leitnerBox = newBox,
                nextReviewAtMs = now + (BOX_INTERVAL_DAYS[newBox] ?: 30L) * DAY_MS,
                lastReviewedAtMs = now,
                learned = newBox >= 5
            )
        } else {
            entry.copy(
                leitnerBox = 1,
                nextReviewAtMs = now + FORGOT_DELAY_MS,
                lastReviewedAtMs = now
            )
        }
        viewModelScope.launch { db.vocabDao().update(updated) }
        reviewedCount++
        current = queue.removeFirstOrNull()
    }

    /**
     * Synthesizes (or reuses from the shared TTS disk cache) and plays just
     * this card's example sentence -- opt-in, per a long-press/tap on a play
     * icon, never automatically. Uses the same voice/rate the sentence's
     * source text was set up with, so it sounds the same as during reading.
     */
    fun playSentence() {
        val entry = current ?: return
        viewModelScope.launch {
            sentenceAudioError = false
            sentenceAudioLoading = true
            player.stop()
            val (voice, rate) = voiceFor(entry.textId)
            val result = ttsRepo.getOrSynthesize(entry.sentence, voice, rate)
            sentenceAudioLoading = false
            result.fold(
                onSuccess = { synth ->
                    player.setMediaItem(MediaItem.fromUri(synth.audioFile.toURI().toString()))
                    player.prepare()
                    player.play()
                },
                onFailure = { sentenceAudioError = true }
            )
        }
    }

    private suspend fun voiceFor(textId: Long): Pair<String, Int> {
        voiceCache[textId]?.let { return it }
        val doc = db.textDao().getById(textId)
        val pair = (doc?.voice ?: "fr-FR-DeniseNeural") to (doc?.ratePercent ?: 0)
        voiceCache[textId] = pair
        return pair
    }

    override fun onCleared() {
        player.release()
        super.onCleared()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VocabReviewScreen(scope: Long, onBack: () -> Unit) {
    val vm: VocabReviewViewModel = viewModel()
    var revealed by remember { mutableStateOf(false) }

    LaunchedEffect(scope) { vm.start(scope) }
    LaunchedEffect(vm.current) { revealed = false }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("مرور لغات") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "بازگشت")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            val entry = vm.current
            when {
                vm.loading -> CircularProgressIndicator()

                entry == null -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(56.dp))
                    Spacer(Modifier.height(12.dp))
                    Text(
                        if (vm.reviewedCount > 0) "آفرین! ${vm.reviewedCount} کلمه مرور شد."
                        else "چیزی برای مرور نیست.",
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(20.dp))
                    Button(onClick = onBack) { Text("بازگشت") }
                }

                else -> Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(entry.word, style = MaterialTheme.typography.headlineMedium)
                            if (revealed) {
                                Spacer(Modifier.height(14.dp))
                                HorizontalDivider()
                                Spacer(Modifier.height(14.dp))
                                if (!entry.meaning.isNullOrBlank()) {
                                    Text(entry.meaning, style = MaterialTheme.typography.titleMedium)
                                    Spacer(Modifier.height(8.dp))
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        entry.sentence,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontStyle = FontStyle.Italic,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    // Opt-in playback of just this sentence's
                                    // audio -- nothing plays on its own when
                                    // the card is revealed.
                                    IconButton(
                                        onClick = { vm.playSentence() },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        if (vm.sentenceAudioLoading) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(18.dp),
                                                strokeWidth = 2.dp
                                            )
                                        } else {
                                            Icon(
                                                Icons.Default.VolumeUp,
                                                contentDescription = "پخش صدای جمله",
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }
                                if (vm.sentenceAudioError) {
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        "دریافت صدا ممکن نشد.",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }

                    if (!revealed) {
                        Button(onClick = { revealed = true }, modifier = Modifier.fillMaxWidth()) {
                            Text("نمایش معنی")
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedButton(onClick = { vm.answer(false) }, modifier = Modifier.weight(1f)) {
                                Text("بلد نبودم")
                            }
                            Button(onClick = { vm.answer(true) }, modifier = Modifier.weight(1f)) {
                                Text("بلد بودم")
                            }
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    Text(
                        "جعبه فعلی: ${entry.leitnerBox}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
