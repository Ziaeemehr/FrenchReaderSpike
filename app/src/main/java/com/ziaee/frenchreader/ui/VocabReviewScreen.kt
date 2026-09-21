package com.ziaee.frenchreader.ui

import android.app.Application
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.ziaee.frenchreader.data.*
import com.ziaee.frenchreader.tts.TtsChunkRepository
import com.ziaee.frenchreader.ui.components.TappableFrenchText
import com.ziaee.frenchreader.ui.statistics.computeStreak
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

enum class ReviewStage { OVERVIEW, REVIEW, SUMMARY }
data class ReviewBoxSummary(val box: Int, val count: Int, val intervalDays: Long, val dueCount: Int, val nextReviewAtMs: Long?)

class VocabReviewViewModel(app: Application) : AndroidViewModel(app) {
    private val db = AppDatabase.get(app)
    private val context = app.applicationContext
    private val ttsRepo = TtsChunkRepository(app)
    private val player = ExoPlayer.Builder(app).build()
    private val voiceCache = HashMap<Long, Pair<String, Int>>()
    private val queue = mutableListOf<VocabEntry>()
    private val completedIds = mutableSetOf<Long>()
    private var sessionStartedAtMs = 0L
    private var scopeId = VOCAB_SCOPE_ALL

    var loading by mutableStateOf(true); private set
    var stage by mutableStateOf(ReviewStage.OVERVIEW); private set
    var current by mutableStateOf<VocabEntry?>(null); private set
    var dueCount by mutableIntStateOf(0); private set
    var newCount by mutableIntStateOf(0); private set
    var reviewedToday by mutableIntStateOf(0); private set
    var dailyGoal by mutableIntStateOf(20); private set
    var streak by mutableIntStateOf(0); private set
    var boxes by mutableStateOf<List<ReviewBoxSummary>>(emptyList()); private set
    var totalCards by mutableIntStateOf(0); private set
    var cardsReviewed by mutableIntStateOf(0); private set
    var answerCount by mutableIntStateOf(0); private set
    var correctCount by mutableIntStateOf(0); private set
    var movedForward by mutableIntStateOf(0); private set
    var returnedToBoxOne by mutableIntStateOf(0); private set
    var studyTimeMs by mutableLongStateOf(0L); private set
    var nextScheduledAtMs by mutableStateOf<Long?>(null); private set
    var moveLabel by mutableStateOf<String?>(null); private set
    var sentenceAudioLoading by mutableStateOf(false); private set
    var sentenceAudioError by mutableStateOf(false); private set

    val progressPosition get() = if (totalCards == 0) 0 else (completedIds.size + 1).coerceAtMost(totalCards)
    val estimatedMinutes get() = ((dueCount + newCount) * 12 / 60.0).roundToInt().coerceAtLeast(if (dueCount + newCount > 0) 1 else 0)
    val intervals get() = VocabPrefs.getIntervals(context)
    val audioAutoplay get() = VocabPrefs.getAudioAutoplay(context)

    fun load(scope: Long) {
        scopeId = scope
        viewModelScope.launch {
            loading = true
            val now = System.currentTimeMillis()
            val dayStart = VocabSrs.startOfDayMs(now)
            val all = scoped(db.vocabDao().getAllOnce()).filter { !it.learned }
            dueCount = all.count { it.lastReviewedAtMs != null && it.nextReviewAtMs <= now }
            val date = LocalDate.now().toString()
            val remainingNew = (VocabPrefs.getMaxNewCards(context) - VocabPrefs.getNewReviewedToday(context, date)).coerceAtLeast(0)
            newCount = all.count { it.lastReviewedAtMs == null }.coerceAtMost(remainingNew)
            reviewedToday = db.reviewLogDao().countSince(dayStart)
            dailyGoal = VocabPrefs.getDailyGoal(context)
            streak = computeStreak(db.reviewLogDao().distinctActiveDates().map(LocalDate::parse).toSet(), LocalDate.now())
            boxes = (1..5).map { box ->
                val entries = all.filter { it.leitnerBox == box }
                ReviewBoxSummary(box, entries.size, intervals[box - 1], entries.count { it.lastReviewedAtMs != null && it.nextReviewAtMs <= now }, entries.minOfOrNull { it.nextReviewAtMs })
            }
            loading = false
        }
    }

    fun startReview() {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val dayStart = VocabSrs.startOfDayMs(now)
            val all = scoped(db.vocabDao().getAllOnce()).filter { !it.learned }
            val overdue = all.filter { it.lastReviewedAtMs != null && it.nextReviewAtMs < dayStart }.sortedBy { it.nextReviewAtMs }
            val today = all.filter { it.lastReviewedAtMs != null && it.nextReviewAtMs in dayStart..now }.sortedBy { it.nextReviewAtMs }
            val fresh = all.filter { it.lastReviewedAtMs == null }.sortedBy { it.createdAtMs }.take(newCount)
            queue.clear(); queue.addAll(overdue + today + fresh)
            completedIds.clear(); totalCards = queue.map { it.id }.distinct().size
            cardsReviewed = 0; answerCount = 0; correctCount = 0; movedForward = 0; returnedToBoxOne = 0
            sessionStartedAtMs = now
            current = queue.removeFirstOrNull()
            stage = if (current == null) ReviewStage.SUMMARY else ReviewStage.REVIEW
        }
    }

    fun answer(answer: VocabAnswer) {
        val entry = current ?: return
        current = null
        player.stop(); sentenceAudioError = false
        val now = System.currentTimeMillis()
        val updated = VocabSrs.apply(entry, answer, now, intervals)
        viewModelScope.launch {
            db.vocabDao().update(updated)
            db.reviewLogDao().insert(ReviewLogEntry(entryId = entry.id, timestampMs = now, knew = answer != VocabAnswer.FORGOT, boxBefore = entry.leitnerBox, boxAfter = updated.leitnerBox))
            answerCount++
            if (answer != VocabAnswer.FORGOT) correctCount++
            if (updated.leitnerBox > entry.leitnerBox) movedForward++
            if (answer == VocabAnswer.FORGOT && entry.leitnerBox > 1) returnedToBoxOne++
            if (entry.lastReviewedAtMs == null) VocabPrefs.incrementNewReviewed(context, LocalDate.now().toString())
            moveLabel = when {
                updated.leitnerBox > entry.leitnerBox -> context.getString(R.string.review_moved_forward, updated.leitnerBox)
                updated.leitnerBox < entry.leitnerBox -> context.getString(R.string.review_returned_box_one)
                else -> context.getString(R.string.review_stayed_box, updated.leitnerBox)
            }
            if (answer == VocabAnswer.FORGOT) queue.add(3.coerceAtMost(queue.size), updated)
            else completedIds.add(entry.id)
            cardsReviewed = completedIds.size
            current = queue.removeFirstOrNull()
            if (current == null) finishSession()
        }
    }

    fun consumeMoveLabel() { moveLabel = null }
    private suspend fun finishSession() {
        studyTimeMs = System.currentTimeMillis() - sessionStartedAtMs
        nextScheduledAtMs = scoped(db.vocabDao().getAllOnce()).filter { !it.learned }.minOfOrNull { it.nextReviewAtMs }
        stage = ReviewStage.SUMMARY
    }

    fun playSentence() {
        val entry = current ?: return
        viewModelScope.launch {
            sentenceAudioError = false; sentenceAudioLoading = true; player.stop()
            val voiceAndRate = voiceCache[entry.textId] ?: db.textDao().getById(entry.textId).let {
                ((it?.voice ?: "fr-FR-HenriNeural") to (it?.ratePercent ?: 0)).also { pair -> voiceCache[entry.textId] = pair }
            }
            ttsRepo.getOrSynthesize(entry.sentence, voiceAndRate.first, voiceAndRate.second).fold(
                onSuccess = { player.setMediaItem(MediaItem.fromUri(it.audioFile.toURI().toString())); player.prepare(); player.play() },
                onFailure = { sentenceAudioError = true }
            )
            sentenceAudioLoading = false
        }
    }

    private fun scoped(all: List<VocabEntry>) = when (scopeId) {
        VOCAB_SCOPE_ALL -> all
        VOCAB_SCOPE_UNFILED -> all.filter { it.listId == null }
        else -> all.filter { it.listId == scopeId }
    }
    override fun onCleared() { player.release(); super.onCleared() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VocabReviewScreen(scope: Long, onBack: () -> Unit) {
    val vm: VocabReviewViewModel = viewModel()
    val snackbar = remember { SnackbarHostState() }
    var revealed by remember { mutableStateOf(false) }
    var showDictionary by remember { mutableStateOf(false) }
    var tappedWord by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(scope) { vm.load(scope) }
    LaunchedEffect(vm.current) { revealed = false; showDictionary = false; tappedWord = null }
    LaunchedEffect(vm.moveLabel) { vm.moveLabel?.let { snackbar.showSnackbar(it); vm.consumeMoveLabel() } }
    LaunchedEffect(revealed, vm.current) { if (revealed && vm.audioAutoplay) vm.playSentence() }
    vm.current?.takeIf { showDictionary }?.let { e ->
        DictionarySheet(e.textId, e.word, e.sentence, e.meaning, e.listId, false, onDismiss = { showDictionary = false })
    }
    vm.current?.let { e -> tappedWord?.let { w ->
        DictionarySheet(e.textId, w, e.sentence, null, null, true, onDismiss = { tappedWord = null })
    } }
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = { TopAppBar(title = { Text(stringResource(R.string.vocab_review_title)) }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.accessibility_back)) } }) }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp)) {
            when {
                vm.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                vm.stage == ReviewStage.OVERVIEW -> ReviewOverview(vm)
                vm.stage == ReviewStage.SUMMARY -> ReviewSummary(vm, onBack, Modifier.align(Alignment.Center))
                else -> vm.current?.let { ReviewCard(vm, it, revealed, { revealed = true }, { showDictionary = true }, { tappedWord = it }, Modifier.align(Alignment.Center)) }
            }
        }
    }
}

@Composable
private fun ReviewOverview(vm: VocabReviewViewModel) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(stringResource(R.string.review_today_title), style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Metric(vm.dueCount.toString(), stringResource(R.string.review_due_count))
            Metric(vm.newCount.toString(), stringResource(R.string.review_new_count))
            Metric(stringResource(R.string.review_minutes_short, vm.estimatedMinutes), stringResource(R.string.review_estimated_time))
        }
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.review_goal_progress, vm.reviewedToday.coerceAtMost(vm.dailyGoal), vm.dailyGoal))
        LinearProgressIndicator(progress = { (vm.reviewedToday.toFloat() / vm.dailyGoal).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp))
        Text(stringResource(R.string.review_streak, vm.streak))
        Button(onClick = vm::startReview, enabled = vm.dueCount + vm.newCount > 0, modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp).height(52.dp)) {
            Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.review_start))
        }
        vm.boxes.forEach { BoxDashboardRow(it) }
    }
}

@Composable private fun Metric(value: String, label: String) = Column(horizontalAlignment = Alignment.CenterHorizontally) {
    Text(value, style = MaterialTheme.typography.titleLarge); Text(label, style = MaterialTheme.typography.labelSmall)
}

@Composable private fun BoxDashboardRow(box: ReviewBoxSummary) {
    val icons = listOf(Icons.Default.School, Icons.Default.AutoStories, Icons.Default.Psychology, Icons.Default.TrendingUp, Icons.Default.EmojiEvents)
    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icons[box.box - 1], null, tint = leitnerBoxColor(box.box)); Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.review_box_label, box.box), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(R.string.review_box_details, box.count, box.intervalDays, box.dueCount), style = MaterialTheme.typography.bodySmall)
            }
            Text(box.nextReviewAtMs?.let(::formatDate) ?: "—", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun ReviewCard(vm: VocabReviewViewModel, entry: VocabEntry, revealed: Boolean, onReveal: () -> Unit, onDictionary: () -> Unit, onWordTap: (String) -> Unit, modifier: Modifier) {
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(stringResource(R.string.review_progress, vm.progressPosition, vm.totalCards), style = MaterialTheme.typography.labelLarge)
        LinearProgressIndicator(progress = { if (vm.totalCards == 0) 0f else vm.progressPosition.toFloat() / vm.totalCards }, modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp))
        Card(Modifier.fillMaxWidth()) { Column(Modifier.fillMaxWidth().padding(26.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            if (revealed) TappableFrenchText(entry.word, onWordTap, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
            else Text(entry.word, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
            if (revealed) {
                Spacer(Modifier.height(14.dp)); HorizontalDivider(); Spacer(Modifier.height(12.dp))
                TextButton(onClick = onDictionary) { Icon(Icons.Default.Translate, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.vocab_open_dictionary)) }
                if (!entry.meaning.isNullOrBlank()) { Text(entry.meaning, style = MaterialTheme.typography.titleMedium); Spacer(Modifier.height(8.dp)) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TappableFrenchText(entry.sentence, onWordTap, fontStyle = FontStyle.Italic, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                    IconButton(onClick = vm::playSentence) { if (vm.sentenceAudioLoading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Icon(Icons.Default.VolumeUp, stringResource(R.string.accessibility_play_sentence)) }
                }
                if (vm.sentenceAudioError) Text(stringResource(R.string.error_audio_generation), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
            }
        } }
        Spacer(Modifier.height(14.dp))
        if (!revealed) Button(onClick = onReveal, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.vocab_reveal_meaning)) }
        else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ReviewAnswerButton(stringResource(R.string.vocab_answer_again), vm.intervals[0], Modifier.weight(1f), MaterialTheme.colorScheme.error) { vm.answer(VocabAnswer.FORGOT) }
            ReviewAnswerButton(stringResource(R.string.vocab_answer_hard), VocabSrs.previewIntervalDays(entry, VocabAnswer.HARD, vm.intervals), Modifier.weight(1f)) { vm.answer(VocabAnswer.HARD) }
            ReviewAnswerButton(stringResource(R.string.vocab_answer_good), VocabSrs.previewIntervalDays(entry, VocabAnswer.KNEW, vm.intervals), Modifier.weight(1f), MaterialTheme.colorScheme.primary) { vm.answer(VocabAnswer.KNEW) }
        }
    }
}

@Composable private fun ReviewAnswerButton(label: String, days: Long, modifier: Modifier, color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface, onClick: () -> Unit) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth(), border = BorderStroke(1.dp, color), colors = ButtonDefaults.outlinedButtonColors(contentColor = color)) { Text(label) }
        Text(stringResource(if (days == 1L) R.string.interval_day else R.string.interval_days, days), style = MaterialTheme.typography.labelSmall)
    }
}

@Composable private fun ReviewSummary(vm: VocabReviewViewModel, onBack: () -> Unit, modifier: Modifier) {
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Default.CheckCircle, null, Modifier.size(58.dp), tint = MaterialTheme.colorScheme.primary)
        Text(stringResource(R.string.review_summary_title), style = MaterialTheme.typography.headlineSmall); Spacer(Modifier.height(16.dp))
        SummaryLine(R.string.review_summary_cards, vm.cardsReviewed.toString())
        SummaryLine(R.string.review_summary_correct, if (vm.answerCount == 0) "0%" else "${vm.correctCount * 100 / vm.answerCount}%")
        SummaryLine(R.string.review_summary_forward, vm.movedForward.toString())
        SummaryLine(R.string.review_summary_returned, vm.returnedToBoxOne.toString())
        SummaryLine(R.string.review_summary_time, formatDuration(vm.studyTimeMs))
        SummaryLine(R.string.review_summary_next, vm.nextScheduledAtMs?.let(::formatDate) ?: "—")
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth().padding(top = 20.dp)) { Text(stringResource(R.string.action_close)) }
    }
}
@Composable private fun SummaryLine(label: Int, value: String) = Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) { Text(stringResource(label), Modifier.weight(1f)); Text(value, style = MaterialTheme.typography.titleSmall) }
private fun formatDate(ms: Long) = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate().format(DateTimeFormatter.ofPattern("d MMM"))
private fun formatDuration(ms: Long): String { val seconds = ms / 1000; return if (seconds < 60) "${seconds}s" else "${seconds / 60}m ${seconds % 60}s" }
