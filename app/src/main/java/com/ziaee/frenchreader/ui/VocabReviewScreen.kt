package com.ziaee.frenchreader.ui

import android.app.Application
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import com.ziaee.frenchreader.R
import com.ziaee.frenchreader.data.AppDatabase
import com.ziaee.frenchreader.data.ReviewLogEntry
import com.ziaee.frenchreader.data.VocabAnswer
import com.ziaee.frenchreader.data.VocabEntry
import com.ziaee.frenchreader.data.VocabSrs
import com.ziaee.frenchreader.tts.TtsChunkRepository
import kotlinx.coroutines.launch

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

    fun answer(answer: VocabAnswer) {
        val entry = current ?: return
        player.stop()
        sentenceAudioError = false
        val now = System.currentTimeMillis()
        val updated = VocabSrs.apply(entry, answer, now)
        viewModelScope.launch {
            db.vocabDao().update(updated)
            db.reviewLogDao().insert(
                ReviewLogEntry(
                    entryId = entry.id,
                    timestampMs = now,
                    knew = answer != VocabAnswer.FORGOT,
                    boxBefore = entry.leitnerBox,
                    boxAfter = updated.leitnerBox
                )
            )
        }
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
        val pair = (doc?.voice ?: "fr-FR-HenriNeural") to (doc?.ratePercent ?: 0)
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
    var showDictionary by remember { mutableStateOf(false) }

    LaunchedEffect(scope) { vm.start(scope) }
    LaunchedEffect(vm.current) { revealed = false; showDictionary = false }

    vm.current?.takeIf { showDictionary }?.let { e ->
        DictionarySheet(
            textId = e.textId,
            word = e.word,
            sentence = e.sentence,
            initialMeaning = e.meaning,
            initialListId = e.listId,
            isNew = false,
            onDismiss = { showDictionary = false }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.vocab_review_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.accessibility_back))
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
                        if (vm.reviewedCount > 0) stringResource(R.string.vocab_review_done, vm.reviewedCount)
                        else stringResource(R.string.vocab_nothing_due),
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(20.dp))
                    Button(onClick = onBack) { Text(stringResource(R.string.accessibility_back)) }
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
                            Spacer(Modifier.height(14.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                repeat(5) { index ->
                                    Surface(
                                        modifier = Modifier.weight(1f).height(6.dp),
                                        shape = MaterialTheme.shapes.extraSmall,
                                        color = if (index < entry.leitnerBox) {
                                            leitnerBoxColor(index + 1)
                                        } else {
                                            MaterialTheme.colorScheme.surfaceVariant
                                        }
                                    ) {}
                                }
                            }
                            if (revealed) {
                                Spacer(Modifier.height(14.dp))
                                HorizontalDivider()
                                Spacer(Modifier.height(14.dp))
                                TextButton(onClick = { showDictionary = true }) {
                                    Icon(Icons.Default.Translate, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(stringResource(R.string.vocab_open_dictionary))
                                }
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
                                                contentDescription = stringResource(R.string.accessibility_play_sentence),
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }
                                if (vm.sentenceAudioError) {
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        stringResource(R.string.error_audio_generation),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }

                    if (!revealed) {
                        Button(onClick = { revealed = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.vocab_reveal_meaning))
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            AnswerButton(
                                interval = formatInterval(VocabSrs.previewIntervalMs(entry, VocabAnswer.FORGOT)),
                                modifier = Modifier.weight(1f)
                            ) {
                                OutlinedButton(
                                    onClick = { vm.answer(VocabAnswer.FORGOT) },
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text(stringResource(R.string.vocab_answer_forgot)) }
                            }
                            AnswerButton(
                                interval = formatInterval(VocabSrs.previewIntervalMs(entry, VocabAnswer.HARD)),
                                modifier = Modifier.weight(1f)
                            ) {
                                FilledTonalButton(
                                    onClick = { vm.answer(VocabAnswer.HARD) },
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text(stringResource(R.string.vocab_answer_hard)) }
                            }
                            AnswerButton(
                                interval = formatInterval(VocabSrs.previewIntervalMs(entry, VocabAnswer.KNEW)),
                                modifier = Modifier.weight(1f)
                            ) {
                                Button(
                                    onClick = { vm.answer(VocabAnswer.KNEW) },
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text(stringResource(R.string.vocab_answer_knew)) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AnswerButton(
    interval: String,
    modifier: Modifier = Modifier,
    button: @Composable () -> Unit
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        button()
        Spacer(Modifier.height(4.dp))
        Text(
            interval,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun formatInterval(ms: Long): String {
    if (ms < 60 * 60_000L) {
        val minutes = (ms / 60_000L).coerceAtLeast(1)
        return stringResource(
            if (minutes == 1L) R.string.interval_minute else R.string.interval_minutes,
            minutes
        )
    }
    if (ms < VocabSrs.DAY_MS) {
        val hours = ((ms + 30 * 60_000L) / (60 * 60_000L)).coerceAtLeast(1)
        return stringResource(
            if (hours == 1L) R.string.interval_hour else R.string.interval_hours,
            hours
        )
    }
    val days = (ms + VocabSrs.DAY_MS - 1) / VocabSrs.DAY_MS
    return stringResource(if (days == 1L) R.string.interval_day else R.string.interval_days, days)
}
