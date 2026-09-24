package com.ziaee.frenchreader.ui

import android.app.Application
import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.ui.semantics.Role
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.ziaee.frenchreader.ui.statistics.computeAccuracyPercent
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Undo
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
import androidx.room.withTransaction
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import com.ziaee.frenchreader.R
import com.ziaee.frenchreader.data.*
import com.ziaee.frenchreader.translate.TranslationRepository
import com.ziaee.frenchreader.tts.TtsChunkRepository
import com.ziaee.frenchreader.ui.components.TappableFrenchText
import com.ziaee.frenchreader.ui.statistics.computeStreak
import com.ziaee.frenchreader.ui.statistics.loadActiveDates
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
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
    private val translationRepo = TranslationRepository(app)
    private val player = ExoPlayer.Builder(app).build()
    private val voiceCache = HashMap<Long, Pair<String, Int>>()
    private val queue = mutableListOf<VocabEntry>()
    private val completedIds = mutableSetOf<Long>()
    private var sessionStartedAtMs = 0L
    private var sessionInProgress = false
    private var scopeId = VOCAB_SCOPE_ALL
    private var stats = ReviewSessionStats()
    private var learnedReviewMode = false
    internal val isLearnedReview get() = learnedReviewMode
    private var undoing = false
    private var loadJob: Job? = null
    private var audioJob: Job? = null
    private var listNames: Map<Long, String> = emptyMap()

    private data class UndoRecord(
        val previousEntry: VocabEntry, val logId: Long, val statsBefore: ReviewSessionStats,
        val wasNewCard: Boolean, val requeued: VocabEntry?, val completedId: Long?, val answeredDate: String,
        val learnedCursorBefore: Long?
    )
    private var undoRecord: UndoRecord? = null
    var canUndo by mutableStateOf(false); private set

    var loading by mutableStateOf(true); private set
    var stage by mutableStateOf(ReviewStage.OVERVIEW); private set
    private var slot by mutableStateOf<CardSlot?>(null)
    internal val currentSlot: CardSlot? get() = slot
    val current: VocabEntry? get() = slot?.entry
    private fun show(entry: VocabEntry?) {
        sentenceTranslation = null
        sentenceTranslationLoading = false
        sentenceTranslationError = false
        wordAudioLoading = false
        wordAudioError = false
        slot = entry?.let { CardSlot(it) }
    }
    var dueCount by mutableIntStateOf(0); private set
    var newCount by mutableIntStateOf(0); private set
    var newCapReached by mutableStateOf(false); private set
    var reviewedToday by mutableIntStateOf(0); private set
    var dailyGoal by mutableIntStateOf(20); private set
    var streak by mutableIntStateOf(0); private set
    var boxes by mutableStateOf<List<ReviewBoxSummary>>(emptyList()); private set
    var learnedCount by mutableIntStateOf(0); private set
    var learnedReviewed by mutableIntStateOf(0); private set
    var totalCards by mutableIntStateOf(0); private set
    var cardsReviewed by mutableIntStateOf(0); private set
    var cardsReviewedToday by mutableIntStateOf(0); private set
    var answerCount by mutableIntStateOf(0); private set
    var correctCount by mutableIntStateOf(0); private set
    var movedForward by mutableIntStateOf(0); private set
    var movedForwardToday by mutableIntStateOf(0); private set
    var returnedToBoxOne by mutableIntStateOf(0); private set
    var returnedToBoxOneToday by mutableIntStateOf(0); private set
    var studyTimeMs by mutableLongStateOf(0L); private set
    var nextScheduledAtMs by mutableStateOf<Long?>(null); private set
    var moveLabel by mutableStateOf<String?>(null); private set
    var sentenceAudioLoading by mutableStateOf(false)
        private set
    var sentenceAudioError by mutableStateOf(false)
        private set
    var wordAudioLoading by mutableStateOf(false)
        private set
    var wordAudioError by mutableStateOf(false)
        private set
    var sentenceTranslation by mutableStateOf<String?>(null); private set
    var sentenceTranslationLoading by mutableStateOf(false); private set
    var sentenceTranslationError by mutableStateOf(false); private set

    val progressPosition get() = if (totalCards == 0) 0 else (completedIds.size + 1).coerceAtMost(totalCards)
    val estimatedMinutes get() = ((dueCount + newCount) * 12 / 60.0).roundToInt().coerceAtLeast(if (dueCount + newCount > 0) 1 else 0)
    val intervals get() = VocabPrefs.getIntervals(context)
    val audioAutoplay get() = VocabPrefs.getAudioAutoplay(context)
    fun listNameFor(entry: VocabEntry): String? = entry.listId?.let { listNames[it] }

    private fun localDateFor(epochMs: Long): String = Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()).toLocalDate().toString()

    private fun remainingNewAt(now: Long): Int =
        (VocabPrefs.getMaxNewCards(context) - VocabPrefs.getNewReviewedToday(context, localDateFor(now))).coerceAtLeast(0)

    fun load(scope: Long) {
        scopeId = scope
        val requestedScope = scope
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            loading = true
            val now = System.currentTimeMillis()
            val dayStart = VocabSrs.startOfDayMs(now)
            val scopedEntries = scoped(db.vocabDao().getAllOnce(), requestedScope)
            val all = scopedEntries.filter { !it.learned }
            val learnedQueue = buildLearnedReviewQueue(scopedEntries, VocabPrefs.getLearnedCursor(context, requestedScope), requestedScope)
            val loadedListNames = db.vocabListDao().getAllOnce().associate { it.id to it.name }
            val availableNew = all.count { it.lastReviewedAtMs == null }
            val remainingNew = remainingNewAt(now)
            val loadedNewCount = availableNew.coerceAtMost(remainingNew)
            val loadedDueCount = reviewableCount(all, now, 0)
            val loadedNewCapReached = availableNew > 0 && remainingNew == 0
            val loadedReviewedToday = db.reviewLogDao().countSince(dayStart)
            val loadedDailyGoal = VocabPrefs.getDailyGoal(context)
            val loadedStreak = computeStreak(loadActiveDates(
                reviewLogDates = { db.reviewLogDao().distinctActiveDates() },
                activityLogDates = { db.activityLogDao().activeDates() }
            ), LocalDate.now())
            val loadedBoxes = (1..4).map { box ->
                val entries = all.filter { it.leitnerBox == box }
                ReviewBoxSummary(box, entries.size, intervals[box - 1], entries.count { it.lastReviewedAtMs != null && it.nextReviewAtMs <= now }, entries.minOfOrNull { it.nextReviewAtMs })
            }
            if (scopeId != requestedScope) return@launch
            listNames = loadedListNames
            dueCount = loadedDueCount
            newCount = loadedNewCount
            newCapReached = loadedNewCapReached
            reviewedToday = loadedReviewedToday
            dailyGoal = loadedDailyGoal
            streak = loadedStreak
            boxes = loadedBoxes
            learnedCount = learnedQueue.total
            learnedReviewed = learnedQueue.reviewed
            loading = false
        }
    }

    fun startReview(box: Int? = null) {
        viewModelScope.launch {
            learnedReviewMode = false
            val now = System.currentTimeMillis()
            val dayStart = VocabSrs.startOfDayMs(now)
            queue.clear(); queue.addAll(buildReviewQueue(scoped(db.vocabDao().getAllOnce()), now, dayStart, remainingNewAt(now), box))
            completedIds.clear(); totalCards = queue.map { it.id }.distinct().size
            cardsReviewed = 0; applyStats(ReviewSessionStats()); undoRecord = null; canUndo = false
            sessionStartedAtMs = now
            show(queue.removeFirstOrNull())
            stage = if (current == null) ReviewStage.SUMMARY else ReviewStage.REVIEW
            sessionInProgress = stage == ReviewStage.REVIEW
        }
    }

    fun startLearnedReview(restart: Boolean = false) {
        viewModelScope.launch {
            if (restart) VocabPrefs.setLearnedCursor(context, scopeId, 0)
            val cursor = VocabPrefs.getLearnedCursor(context, scopeId)
            val result = buildLearnedReviewQueue(db.vocabDao().getAllOnce(), cursor, scopeId)
            if (result.resetCursor) VocabPrefs.setLearnedCursor(context, scopeId, 0)
            learnedReviewMode = true
            queue.clear(); queue.addAll(result.cards)
            completedIds.clear()
            scoped(db.vocabDao().getAllOnce()).filter { it.learned && it.id <= if (result.resetCursor) 0 else cursor }
                .forEach { completedIds.add(it.id) }
            totalCards = result.total
            cardsReviewed = completedIds.size
            applyStats(ReviewSessionStats()); undoRecord = null; canUndo = false
            sessionStartedAtMs = System.currentTimeMillis()
            show(queue.removeFirstOrNull())
            stage = if (current == null) ReviewStage.SUMMARY else ReviewStage.REVIEW
            sessionInProgress = stage == ReviewStage.REVIEW
        }
    }

    private fun applyStats(s: ReviewSessionStats) {
        stats = s
        answerCount = s.answerCount; correctCount = s.correctCount
        movedForward = s.movedForward; returnedToBoxOne = s.returnedToBoxOne
    }

    fun answer(answer: VocabAnswer) {
        if (undoing) return
        val entry = current ?: return
        show(null)
        canUndo = false
        audioJob?.cancel()
        audioJob = null
        sentenceAudioLoading = false
        player.stop()
        sentenceAudioError = false
        wordAudioError = false
        wordAudioLoading = false
        val now = System.currentTimeMillis()
        val boxBefore = if (learnedReviewMode) 5 else entry.leitnerBox
        val updated = if (learnedReviewMode && answer != VocabAnswer.FORGOT) {
            entry.copy(lastReviewedAtMs = now)
        } else VocabSrs.apply(entry, answer, now, intervals)
        val boxAfter = if (learnedReviewMode && answer != VocabAnswer.FORGOT) 5 else updated.leitnerBox
        viewModelScope.launch {
            val logId = db.withTransaction {
                db.vocabDao().updateSchedule(updated.id, updated.leitnerBox, updated.nextReviewAtMs, updated.lastReviewedAtMs, updated.learned)
                db.reviewLogDao().insert(ReviewLogEntry(entryId = entry.id, timestampMs = now, knew = answer != VocabAnswer.FORGOT, boxBefore = boxBefore, boxAfter = boxAfter))
            }
            val statsBefore = stats
            applyStats(stats.after(answer, boxBefore, boxAfter))
            val date = localDateFor(now)
            val wasNew = !learnedReviewMode && entry.lastReviewedAtMs == null
            if (wasNew) VocabPrefs.incrementNewReviewed(context, date)
            moveLabel = when {
                boxAfter > boxBefore -> context.getString(R.string.review_moved_forward, boxAfter)
                boxAfter < boxBefore -> context.getString(R.string.review_returned_box_one)
                else -> context.getString(R.string.review_stayed_box, boxAfter)
            }
            val requeued = if (!learnedReviewMode && answer == VocabAnswer.FORGOT) updated else null
            val completedId = if (requeued == null) entry.id else null
            if (requeued != null) queue.add(3.coerceAtMost(queue.size), requeued)
            else completedIds.add(entry.id)
            val cursorBefore = if (learnedReviewMode) VocabPrefs.getLearnedCursor(context, scopeId) else null
            if (learnedReviewMode) VocabPrefs.setLearnedCursor(context, scopeId, entry.id)
            cardsReviewed = completedIds.size
            undoRecord = UndoRecord(entry, logId, statsBefore, wasNew, requeued, completedId, date, cursorBefore)
            show(queue.removeFirstOrNull())
            if (current == null) finishSession() else canUndo = true
        }
    }

    fun undo() {
        val r = undoRecord ?: return
        if (current == null || undoing) return
        undoing = true
        canUndo = false
        viewModelScope.launch {
            try {
                db.withTransaction {
                    db.vocabDao().updateSchedule(r.previousEntry.id, r.previousEntry.leitnerBox, r.previousEntry.nextReviewAtMs, r.previousEntry.lastReviewedAtMs, r.previousEntry.learned)
                    db.reviewLogDao().deleteById(r.logId)
                }
                if (r.wasNewCard) VocabPrefs.decrementNewReviewed(context, r.answeredDate)
                r.learnedCursorBefore?.let { VocabPrefs.setLearnedCursor(context, scopeId, it) }
                applyStats(r.statsBefore)
                if (r.requeued == null && r.completedId != null) completedIds.remove(r.completedId)
                cardsReviewed = completedIds.size
                val restored = restoreQueueAfterUndo(queue.toList(), current, r.requeued)
                queue.clear(); queue.addAll(restored)
                audioJob?.cancel()
                audioJob = null
                sentenceAudioLoading = false
                player.stop()
                sentenceAudioError = false
                wordAudioError = false
                wordAudioLoading = false
                moveLabel = null
                show(r.previousEntry.copy())
                undoRecord = null
            } finally { undoing = false }
        }
    }

    /** Edits the card on screen in place (text only; schedule untouched) and returns the new instance. */
    fun editCurrent(word: String, meaning: String?, sentence: String): VocabEntry? {
        val slot = slot ?: return null
        val old = slot.entry
        val edited = old.copy(word = word, meaning = meaning, sentence = sentence)
        slot.entry = edited
        val r = undoRecord
        var requeued = r?.requeued
        if (old === requeued) requeued = edited
        for (i in queue.indices) if (queue[i].id == edited.id) {
            val patched = queue[i].copy(word = word, meaning = meaning, sentence = sentence)
            if (queue[i] === r?.requeued) requeued = patched
            queue[i] = patched
        }
        if (r != null) {
            val prev = if (r.previousEntry.id == edited.id) r.previousEntry.copy(word = word, meaning = meaning, sentence = sentence) else r.previousEntry
            undoRecord = r.copy(previousEntry = prev, requeued = requeued)
        }
        viewModelScope.launch { db.vocabDao().updateText(edited.id, word, meaning, sentence) }
        return edited
    }

    fun consumeMoveLabel() { moveLabel = null }
    private suspend fun finishSession() {
        val now = System.currentTimeMillis()
        val sessionDurationMs = now - sessionStartedAtMs
        val date = LocalDate.now().toString()
        VocabPrefs.addStudyTimeMs(context, date, sessionDurationMs)
        sessionInProgress = false
        studyTimeMs = VocabPrefs.getStudyTimeMsToday(context, date)
        nextScheduledAtMs = scoped(db.vocabDao().getAllOnce()).filter { !it.learned && it.nextReviewAtMs > now }.minOfOrNull { it.nextReviewAtMs }
        val dayStart = VocabSrs.startOfDayMs(now)
        cardsReviewedToday = db.reviewLogDao().countSince(dayStart)
        movedForwardToday = db.reviewLogDao().countMovedForwardSince(dayStart)
        returnedToBoxOneToday = db.reviewLogDao().countReturnedToBoxOneSince(dayStart)
        if (learnedReviewMode) {
            VocabPrefs.setLearnedCursor(context, scopeId, 0)
            learnedReviewed = 0
        }
        stage = ReviewStage.SUMMARY
    }

    fun playSentence() {
        val entry = current ?: return
        if (entry.sentence.isBlank()) return
        audioJob?.cancel()
        audioJob = viewModelScope.launch {
            wordAudioLoading = false
            sentenceAudioError = false
            sentenceAudioLoading = true
            player.stop()
            val voiceAndRate = voiceCache[entry.textId] ?: run {
                val text = if (entry.textId == 0L) null else db.textDao().getById(entry.textId)
                if (text == null) {
                    VocabPrefs.getCardVoice(context) to 0
                } else {
                    (text.voice to text.ratePercent).also { voiceCache[entry.textId] = it }
                }
            }
            try {
                ttsRepo.getOrSynthesize(entry.sentence, voiceAndRate.first, voiceAndRate.second).fold(
                    onSuccess = {
                        player.setMediaItem(MediaItem.fromUri(it.audioFile.toURI().toString()))
                        player.prepare()
                        player.play()
                    },
                    onFailure = { sentenceAudioError = true }
                )
            } finally {
                // Also runs when answering/undo cancels this job mid-synthesis; a newer job owns the flag then.
                if (audioJob == coroutineContext[Job]) sentenceAudioLoading = false
            }
        }
    }

    fun playWord() {
        val entry = current ?: return
        if (!isLikelyFrench(entry.word)) return
        audioJob?.cancel()
        audioJob = viewModelScope.launch {
            sentenceAudioLoading = false
            wordAudioError = false
            wordAudioLoading = true
            player.stop()
            try {
                ttsRepo.getOrSynthesize(entry.word, VocabPrefs.getCardVoice(context), 0).fold(
                    onSuccess = {
                        player.setMediaItem(MediaItem.fromUri(it.audioFile.toURI().toString()))
                        player.prepare()
                        player.play()
                    },
                    onFailure = { wordAudioError = true }
                )
            } finally {
                if (audioJob == coroutineContext[Job]) wordAudioLoading = false
            }
        }
    }

    suspend fun translateSentence() {
        val entry = current ?: return
        if (entry.sentence.isBlank()) return
        sentenceTranslation = null
        sentenceTranslationError = false
        sentenceTranslationLoading = true
        val targetLang = meaningTargetLanguage(VocabPrefs.getMeaningLanguage(context))
        val result = translationRepo.getOrTranslate(entry.sentence, targetLang)
        if (current !== entry) return
        result.fold(
            onSuccess = { sentenceTranslation = it },
            onFailure = { sentenceTranslationError = true }
        )
        sentenceTranslationLoading = false
    }

    private fun scoped(all: List<VocabEntry>, scope: Long = scopeId) = when (scope) {
        VOCAB_SCOPE_ALL -> all
        VOCAB_SCOPE_UNFILED -> all.filter { it.listId == null }
        else -> all.filter { it.listId == scope }
    }
    override fun onCleared() {
        if (sessionInProgress && stage == ReviewStage.REVIEW && answerCount > 0) {
            val now = System.currentTimeMillis()
            VocabPrefs.addStudyTimeMs(context, localDateFor(now), now - sessionStartedAtMs)
            sessionInProgress = false
        }
        player.release()
        super.onCleared()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VocabReviewScreen(scope: Long, onBack: () -> Unit, onOpenSettings: () -> Unit) {
    val vm: VocabReviewViewModel = viewModel()
    val snackbar = remember { SnackbarHostState() }
    // Reveal state is keyed by entry instance (identity): each presentation is a distinct object
    // (answer() re-queues a copy), so the outgoing card keeps its state and a returning card starts unrevealed.
    var revealedEntry by remember { newRevealedEntryState() }
    val currentRevealed = vm.current != null && vm.current === revealedEntry
    var showDictionary by remember { mutableStateOf(false) }
    var showEdit by remember { mutableStateOf(false) }
    var tappedWord by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(scope) { vm.load(scope) }
    LaunchedEffect(vm.currentSlot) { showDictionary = false; showEdit = false; tappedWord = null }
    // Held so the card container does not collapse while vm.current is briefly null between answers.
    var lastSlot by remember { mutableStateOf<CardSlot?>(null) }
    LaunchedEffect(vm.currentSlot) { vm.currentSlot?.let { lastSlot = it } }
    LaunchedEffect(vm.stage) { if (vm.stage != ReviewStage.REVIEW) lastSlot = null }
    LaunchedEffect(vm.moveLabel) { vm.moveLabel?.let { snackbar.showSnackbar(it); vm.consumeMoveLabel() } }
    LaunchedEffect(currentRevealed, vm.current) { if (currentRevealed && vm.current?.sentence?.isNotBlank() == true && vm.audioAutoplay) vm.playSentence() }
    LaunchedEffect(currentRevealed, vm.current) { if (currentRevealed && vm.current?.sentence?.isNotBlank() == true) vm.translateSentence() }
    vm.current?.takeIf { showEdit }?.let { e ->
        VocabEditDialog(e, onDismiss = { showEdit = false }) { w, m, s ->
            val wasRevealed = e === revealedEntry
            vm.editCurrent(w, m, s)?.let { if (wasRevealed) revealedEntry = it }
            showEdit = false
        }
    }
    vm.current?.takeIf { showDictionary }?.let { e ->
        DictionarySheet(e.textId, e.word, e.sentence, e.meaning, e.listId, false, onDismiss = { showDictionary = false })
    }
    vm.current?.let { e -> tappedWord?.let { w ->
        DictionarySheet(e.textId, w, e.sentence, null, null, true, onDismiss = { tappedWord = null })
    } }
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = { TopAppBar(title = { Text(stringResource(R.string.vocab_review_title)) }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.accessibility_back)) } }, actions = { if (vm.stage == ReviewStage.REVIEW) IconButton(onClick = vm::undo, enabled = vm.canUndo) { Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = stringResource(R.string.review_undo)) }; IconButton(onClick = onOpenSettings) { Icon(Icons.Default.Settings, stringResource(R.string.review_open_settings)) } }) }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp)) {
            when {
                vm.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                vm.stage == ReviewStage.OVERVIEW -> ReviewOverview(vm)
                vm.stage == ReviewStage.SUMMARY -> ReviewSummary(vm, onBack, Modifier.align(Alignment.Center))
                else -> {
                    val dir = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1 else 1
                    AnimatedContent(
                        targetState = vm.currentSlot ?: lastSlot,
                        transitionSpec = { (slideInHorizontally { dir * it / 4 } + fadeIn(tween(220))) togetherWith (slideOutHorizontally { -dir * it / 4 } + fadeOut(tween(160))) using SizeTransform(clip = false) },
                        modifier = Modifier.align(Alignment.Center),
                        label = "card"
                    ) { slot -> slot?.let { sl -> val e = sl.entry; ReviewCard(vm, e, e === revealedEntry, e === vm.current, { revealedEntry = e }, { showDictionary = true }, { showEdit = true }, { w -> tappedWord = w }, Modifier) } }
                }
            }
        }
    }
}

@Composable
private fun ReviewOverview(vm: VocabReviewViewModel) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(stringResource(R.string.review_today_title), style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            GoalRing(vm.reviewedToday.coerceAtMost(vm.dailyGoal), vm.dailyGoal, Modifier.size(72.dp))
            Spacer(Modifier.width(16.dp))
            Column {
                Text(stringResource(R.string.review_goal_progress, vm.reviewedToday.coerceAtMost(vm.dailyGoal), vm.dailyGoal))
                Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.secondaryContainer) {
                    Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.LocalFireDepartment, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.review_streak, vm.streak), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Metric(vm.dueCount.toString(), stringResource(R.string.review_due_count))
            Metric(vm.newCount.toString(), stringResource(R.string.review_new_count))
            Metric(stringResource(R.string.review_minutes_short, vm.estimatedMinutes), stringResource(R.string.review_estimated_time))
        }
        if (vm.dueCount + vm.newCount == 0) {
            Text(
                stringResource(if (vm.newCapReached) R.string.review_new_cap_reached else R.string.review_no_cards_due),
                style = MaterialTheme.typography.bodyMedium
            )
        }
        Button(onClick = { vm.startReview() }, enabled = vm.dueCount + vm.newCount > 0, modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp).height(52.dp)) {
            Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.review_start))
        }
        Text(stringResource(R.string.review_box_tap_hint), style = MaterialTheme.typography.labelSmall)
        Spacer(Modifier.height(4.dp))
        BoxLadder(vm.boxes, onBoxClick = { vm.startReview(it) })
        LearnedWordsCard(vm)
    }
}

@Composable
private fun LearnedWordsCard(vm: VocabReviewViewModel) {
    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.EmojiEvents, null, tint = leitnerBoxColor(5))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.review_learned_words), style = MaterialTheme.typography.titleSmall)
                    Text(stringResource(R.string.review_learned_progress, vm.learnedReviewed, vm.learnedCount), style = MaterialTheme.typography.bodySmall)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { vm.startLearnedReview(restart = true) }, enabled = vm.learnedCount > 0) {
                    Text(stringResource(R.string.review_learned_restart))
                }
                Button(onClick = { vm.startLearnedReview() }, enabled = vm.learnedCount > 0) {
                    Text(stringResource(if (vm.learnedReviewed > 0) R.string.review_learned_continue else R.string.review_learned_start))
                }
            }
        }
    }
}

@Composable
private fun GoalRing(done: Int, goal: Int, modifier: Modifier = Modifier) {
    val track = MaterialTheme.colorScheme.outlineVariant
    val fill = MaterialTheme.colorScheme.primary
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().padding(4.dp)) {
            val stroke = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
            drawArc(track, 0f, 360f, false, style = stroke)
            drawArc(fill, -90f, (done.toFloat() / goal.coerceAtLeast(1)).coerceIn(0f, 1f) * 360f, false, style = stroke)
        }
        Text("$done/$goal", style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
}

@Composable private fun Metric(value: String, label: String) = Column(horizontalAlignment = Alignment.CenterHorizontally) {
    Text(value, style = MaterialTheme.typography.titleLarge); Text(label, style = MaterialTheme.typography.labelSmall)
}

@Composable
private fun BoxLadder(boxes: List<ReviewBoxSummary>, onBoxClick: (Int) -> Unit) {
    val icons = listOf(Icons.Default.School, Icons.Default.AutoStories, Icons.Default.Psychology, Icons.Default.TrendingUp, Icons.Default.EmojiEvents)
    val fractions = boxBarFractions(boxes.map { it.count })
    boxes.forEachIndexed { i, box ->
        val enabled = box.dueCount > 0
        val color = leitnerBoxColor(box.box)
        Card(onClick = { onBoxClick(box.box) }, enabled = enabled, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Column(Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(icons[(box.box - 1).coerceIn(0, icons.lastIndex)], null, tint = color); Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.review_box_label, box.box), style = MaterialTheme.typography.titleSmall)
                        Text(stringResource(R.string.review_box_details, box.count, box.intervalDays, box.dueCount), style = MaterialTheme.typography.bodySmall)
                    }
                    Text(box.nextReviewAtMs?.let(::formatDate) ?: "—", style = MaterialTheme.typography.labelSmall)
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                        Box(Modifier.fillMaxWidth(fractions[i]).fillMaxHeight().background(color))
                    }
                    if (enabled) {
                        Spacer(Modifier.width(8.dp))
                        Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.primary) {
                            Text(box.dueCount.toString(), Modifier.padding(horizontal = 8.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FlipCard(revealed: Boolean, onFlip: () -> Unit, interactive: Boolean = true, front: @Composable () -> Unit, back: @Composable () -> Unit) {
    val rotation by animateFloatAsState(if (revealed) 180f else 0f, tween(350), label = "flip")
    val density = LocalDensity.current.density
    Card(
        Modifier.fillMaxWidth().graphicsLayer { rotationY = rotation; cameraDistance = 12f * density }
            .clickable(enabled = !revealed && interactive, role = Role.Button, onClick = onFlip)
    ) {
        if (rotation <= 90f) front()
        else Box(Modifier.graphicsLayer { rotationY = 180f }) { back() }
    }
}

@Composable
private fun BoxDots(box: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (i in 1..5) {
            val target = if (i <= box) leitnerBoxColor(i) else MaterialTheme.colorScheme.outlineVariant
            val color by animateColorAsState(target, label = "dot")
            Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        }
    }
}

@Composable
private fun AccuracyRing(percent: Int) {
    val track = MaterialTheme.colorScheme.outlineVariant
    val fill = MaterialTheme.colorScheme.primary
    Box(Modifier.size(96.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().padding(6.dp)) {
            val stroke = Stroke(width = 10.dp.toPx(), cap = StrokeCap.Round)
            drawArc(track, 0f, 360f, false, style = stroke)
            drawArc(fill, -90f, percent.coerceIn(0, 100) / 100f * 360f, false, style = stroke)
        }
        Text("$percent%", style = MaterialTheme.typography.titleMedium, maxLines = 1)
    }
}

@Composable
private fun ReviewCard(
    vm: VocabReviewViewModel,
    entry: VocabEntry,
    revealed: Boolean,
    interactive: Boolean,
    onReveal: () -> Unit,
    onDictionary: () -> Unit,
    onEdit: () -> Unit,
    onWordTap: (String) -> Unit,
    modifier: Modifier
) {
    val wordTap: (String) -> Unit = { w -> if (interactive) onWordTap(w) }
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        val cornerLabel = listOfNotNull(vm.listNameFor(entry), entry.lessonNumber()?.let { stringResource(R.string.review_lesson_short, it) }).joinToString(" · ")
        if (cornerLabel.isNotEmpty()) {
            Text(cornerLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
        }
        BoxDots(entry.leitnerBox)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.review_progress, vm.progressPosition, vm.totalCards), style = MaterialTheme.typography.labelLarge)
        LinearProgressIndicator(progress = { if (vm.totalCards == 0) 0f else vm.progressPosition.toFloat() / vm.totalCards }, modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp))
        FlipCard(
            revealed, onReveal, interactive,
            front = {
                Column(Modifier.fillMaxWidth().padding(26.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(entry.word, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
                    if (isLikelyFrench(entry.word)) {
                        IconButton(onClick = vm::playWord, enabled = interactive) {
                            if (vm.wordAudioLoading) {
                                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(
                                    Icons.Default.VolumeUp,
                                    stringResource(R.string.accessibility_play_word)
                                )
                            }
                        }
                        if (vm.wordAudioError) {
                            Text(
                                stringResource(R.string.error_audio_generation),
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.review_tap_to_reveal), style = MaterialTheme.typography.labelSmall)
                }
            },
            back = {
                Column(Modifier.fillMaxWidth().padding(26.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    TappableFrenchText(entry.word, wordTap, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(14.dp)); HorizontalDivider(); Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = onDictionary, enabled = interactive) { Icon(Icons.Default.Translate, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.vocab_open_dictionary)) }
                        IconButton(onClick = onEdit, enabled = interactive) { Icon(Icons.Default.Edit, stringResource(R.string.action_edit)) }
                    }
                    entry.displayMeaning()?.let { Text(it, style = MaterialTheme.typography.titleMedium); Spacer(Modifier.height(8.dp)) }
                    if (entry.sentence.isNotBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TappableFrenchText(entry.sentence, wordTap, fontStyle = FontStyle.Italic, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                            IconButton(onClick = vm::playSentence, enabled = interactive) { if (vm.sentenceAudioLoading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Icon(Icons.Default.VolumeUp, stringResource(R.string.accessibility_play_sentence)) }
                        }
                        when {
                            vm.sentenceTranslationLoading -> Text(stringResource(R.string.translation_loading), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                            vm.sentenceTranslationError -> Text(stringResource(R.string.error_translation_unavailable), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                            vm.sentenceTranslation != null -> Text(vm.sentenceTranslation!!, color = MaterialTheme.colorScheme.onSurfaceVariant, fontStyle = FontStyle.Italic, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                        }
                        if (vm.sentenceAudioError) Text(stringResource(R.string.error_audio_generation), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        )
        Spacer(Modifier.height(14.dp))
        if (!revealed) Button(onClick = onReveal, enabled = interactive, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.vocab_reveal_meaning)) }
        else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ReviewAnswerButton(stringResource(R.string.vocab_answer_again), vm.intervals[0], Modifier.weight(1f), MaterialTheme.colorScheme.error, interactive) { vm.answer(VocabAnswer.FORGOT) }
            ReviewAnswerButton(stringResource(R.string.vocab_answer_hard), if (vm.isLearnedReview) null else VocabSrs.previewIntervalDays(entry, VocabAnswer.HARD, vm.intervals), Modifier.weight(1f), enabled = interactive) { vm.answer(VocabAnswer.HARD) }
            ReviewAnswerButton(stringResource(R.string.vocab_answer_good), if (vm.isLearnedReview) null else VocabSrs.previewIntervalDays(entry, VocabAnswer.KNEW, vm.intervals), Modifier.weight(1f), MaterialTheme.colorScheme.primary, interactive) { vm.answer(VocabAnswer.KNEW) }
        }
    }
}

@Composable private fun ReviewAnswerButton(label: String, days: Long?, modifier: Modifier, color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface, enabled: Boolean = true, onClick: () -> Unit) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        OutlinedButton(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth(), border = BorderStroke(1.dp, color), colors = ButtonDefaults.outlinedButtonColors(contentColor = color)) { Text(label) }
        if (days != null) Text(stringResource(if (days == 1L) R.string.interval_day else R.string.interval_days, days), style = MaterialTheme.typography.labelSmall)
    }
}

@Composable private fun ReviewSummary(vm: VocabReviewViewModel, onBack: () -> Unit, modifier: Modifier) {
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Default.CheckCircle, null, Modifier.size(58.dp), tint = MaterialTheme.colorScheme.primary)
        Text(stringResource(R.string.review_summary_title), style = MaterialTheme.typography.headlineSmall); Spacer(Modifier.height(12.dp))
        AccuracyRing(computeAccuracyPercent(vm.correctCount, vm.answerCount)); Spacer(Modifier.height(12.dp))
        SummaryLine(R.string.review_summary_cards, vm.cardsReviewedToday.toString())
        SummaryLine(R.string.review_summary_forward, vm.movedForwardToday.toString())
        SummaryLine(R.string.review_summary_returned, vm.returnedToBoxOneToday.toString())
        SummaryLine(R.string.review_summary_time, formatDuration(vm.studyTimeMs))
        SummaryLine(R.string.review_summary_next, vm.nextScheduledAtMs?.let(::formatDate) ?: "—")
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth().padding(top = 20.dp)) { Text(stringResource(R.string.action_close)) }
    }
}
@Composable private fun SummaryLine(label: Int, value: String) = Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) { Text(stringResource(label), Modifier.weight(1f)); Text(value, style = MaterialTheme.typography.titleSmall) }
private fun formatDate(ms: Long) = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate().format(DateTimeFormatter.ofPattern("d MMM"))
private fun formatDuration(ms: Long): String { val seconds = ms / 1000; return if (seconds < 60) "${seconds}s" else "${seconds / 60}m ${seconds % 60}s" }
internal fun meaningTargetLanguage(language: VocabPrefs.MeaningLanguage) =
    if (language == VocabPrefs.MeaningLanguage.PERSIAN) "fa" else "en"
