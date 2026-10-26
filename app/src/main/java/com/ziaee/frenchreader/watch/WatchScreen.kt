package com.ziaee.frenchreader.watch

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.LibraryAdd
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextOverflow
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Translate
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Forward5
import androidx.compose.material.icons.filled.Replay5
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.onSizeChanged
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.collect
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.luminance
import com.ziaee.frenchreader.data.VocabStatus
import com.ziaee.frenchreader.ui.containerColor
import com.ziaee.frenchreader.content.SavedVocab
import com.ziaee.frenchreader.content.VocabHighlighter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.buildAnnotatedString
import kotlinx.coroutines.CancellationException
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.ziaee.frenchreader.shadowing.*
import com.ziaee.frenchreader.R
import com.ziaee.frenchreader.ui.DictionaryPane

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun WatchScreen(videoId: String, onBack: () -> Unit, onOpenText: (Long) -> Unit) {
    val vm: WatchViewModel = viewModel()
    var editingTranslation by remember(videoId) { mutableStateOf<String?>(null) }
    val state by vm.uiState.collectAsState()
    val savedVocabStatuses by vm.savedVocabStatuses.collectAsState()
    val vocabHighlighter = remember(savedVocabStatuses) {
        VocabHighlighter(savedVocabStatuses.map { SavedVocab(it.key, it.value) })
    }
    // A new controller after rotation: resume where the old player was, not at 0:00.
    val controller = remember(videoId) {
        YoutubePlayerController().also { if (state.currentTimeMs > 0) it.seekTo(state.currentTimeMs) }
    }
    editingTranslation?.let { text ->
        val shown = state.translations[text].orEmpty()
        val focusRequester = remember(text) { FocusRequester() }
        var draft by remember(text) { mutableStateOf(TextFieldValue(shown, TextRange(shown.length))) }
        val rtl = state.translationLanguage == "fa"
        val editSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        LaunchedEffect(text) { controller.pause() }
        ModalBottomSheet(
            onDismissRequest = { editingTranslation = null },
            sheetState = editSheetState,
            dragHandle = {
                Surface(shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.padding(top = 8.dp, bottom = 2.dp).size(32.dp, 4.dp)) {}
            }
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 16.dp).imePadding()) {
                Text(text, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(12.dp))
                CompositionLocalProvider(LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                        BasicTextField(value = draft, onValueChange = { draft = it },
                            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface,
                                textDirection = if (rtl) TextDirection.Rtl else TextDirection.Ltr),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary), minLines = 2, maxLines = 6,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp).focusRequester(focusRequester))
                    }
                }
                LaunchedEffect(text) { focusRequester.requestFocus() }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    if (text in state.userTranslations) {
                        TextButton(onClick = { vm.setUserTranslation(text, null); editingTranslation = null }) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.translation_automatic))
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    FilledTonalButton(onClick = { vm.setUserTranslation(text, draft.text); editingTranslation = null },
                        enabled = draft.text.isNotBlank() && draft.text.trim() != shown) {
                        Text(stringResource(R.string.translation_save))
                    }
                }
            }
        }
    }
    val context = LocalContext.current
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    var exportOpen by remember(videoId) { mutableStateOf(false) }
    val exportTitle = state.title.ifBlank { "YouTube $videoId" }
    val srtPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/x-subrip")) { uri ->
        vm.completeSrtExport(uri)
    }
    if (state.exportError) {
        AlertDialog(onDismissRequest = { vm.dismissExportError() },
            text = { Text(stringResource(R.string.watch_export_error)) },
            confirmButton = {
                TextButton(onClick = { vm.dismissExportError() }) { Text(stringResource(R.string.watch_export_dismiss)) }
            })
    }
    if (exportOpen) {
        ModalBottomSheet(
            onDismissRequest = { exportOpen = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            dragHandle = {
                Surface(shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.padding(top = 8.dp, bottom = 2.dp).size(32.dp, 4.dp)) {}
            }
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 16.dp)) {
                Text(exportTitle, style = MaterialTheme.typography.titleMedium,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(8.dp))
                TranscriptExportRow(Icons.Default.LibraryAdd,
                    stringResource(if (state.libraryTextId != null) R.string.watch_export_open_library else R.string.watch_export_library),
                    stringResource(R.string.watch_export_library_detail), enabled = !state.exportBusy) {
                    val wasInLibrary = state.libraryTextId != null
                    vm.addTranscript { id ->
                        exportOpen = false
                        if (!wasInLibrary) android.widget.Toast.makeText(context, R.string.watch_export_added, android.widget.Toast.LENGTH_SHORT).show()
                        onOpenText(id)
                    }
                }
                TranscriptExportRow(Icons.Default.IosShare, stringResource(R.string.watch_export_share),
                    stringResource(R.string.watch_export_share_detail)) {
                    val share = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, transcriptParagraphs(state.cues))
                        putExtra(Intent.EXTRA_SUBJECT, exportTitle)
                    }
                    try {
                        context.startActivity(Intent.createChooser(share, null))
                        exportOpen = false
                    } catch (_: ActivityNotFoundException) { vm.reportExportError(); exportOpen = false }
                }
                TranscriptExportRow(Icons.Default.SaveAlt, stringResource(R.string.watch_export_srt),
                    stringResource(R.string.watch_export_srt_detail)) {
                    vm.prepareSrtExport()
                    try { srtPicker.launch(transcriptFileName(exportTitle))
                    } catch (_: ActivityNotFoundException) { vm.completeSrtExport(null); vm.reportExportError() }
                    exportOpen = false
                }
            }
        }
    }
    val subtitleSizes = remember { listOf(85, 100, 115, 130, 150, 175) }
    val subtitlePercent = state.subtitleSizePercent
    var sizeMenuOpen by remember { mutableStateOf(false) }
    val subtitleScale = subtitlePercent / 100f
    val subtitleStyle = MaterialTheme.typography.bodyLarge.let {
        it.copy(fontSize = it.fontSize * subtitleScale, lineHeight = it.lineHeight * subtitleScale)
    }
    val translationStyle = MaterialTheme.typography.bodySmall.let {
        it.copy(fontSize = it.fontSize * subtitleScale, lineHeight = it.lineHeight * subtitleScale)
    }
    val compactPlayer = state.compactPlayer
    var freeze by remember(videoId) { mutableStateOf(PlaybackFreeze()) }
    val frozenCueTime = freeze.cueTimeMs
    var cueGestureActive by remember(videoId) { mutableStateOf(false) }
    // Watching (not shadowing) when a touch reached the transcript; a lookup from that touch resumes on close.
    var playingBeforeTouch by remember(videoId) { mutableStateOf(false) }
    var resumeAfterDictionary by remember(videoId) { mutableStateOf(false) }
    val latestTime by rememberUpdatedState(state.currentTimeMs)
    // Moving the player between compact/full containers preserves the WebView and playback.
    val movePlayer = remember(videoId) {
        movableContentOf<@Composable () -> Unit> { content -> content() }
    }
    val density = LocalDensity.current
    val fallbackTitle = stringResource(R.string.watch_video_title)
    var shadowing by remember { mutableStateOf(false) }
    val shadowScope = rememberCoroutineScope()
    var recordRequest by remember(videoId) { mutableStateOf<Int?>(null) }
    val videoShadowing = remember(videoId, controller) {
        VideoShadowingController(shadowScope, controller, engineFactory = {
            when (ShadowingPrefs.getEngine(context)) {
                SpeechEngineKind.VOSK -> VoskEngine(VoskModelManager(context).modelDir)
                SpeechEngineKind.ANDROID -> AndroidSpeechEngine(context)
            }
        }, requestRecording = { recordRequest = it }, beforePlayback = {
            recordRequest = null
            freeze = freeze.resume()
            cueGestureActive = false
        }, onUnitComplete = { unit ->
            // Hold the shadowed line on screen while the late pause and seek-back settle.
            freeze = PlaybackFreeze(maxOf(unit.startMs, unit.endMs - 1), SystemClock.uptimeMillis())
        })
    }
    val practice by videoShadowing.state.collectAsState()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(videoShadowing, lifecycle) {
        vm.setForeground(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) vm.setForeground(true)
            if (event == Lifecycle.Event.ON_STOP) vm.setForeground(false)
            if (event == Lifecycle.Event.ON_PAUSE) {
                videoShadowing.cancel()
            }
            if (event == Lifecycle.Event.ON_STOP) { vm.recordRecent(); videoShadowing.release(); recordRequest = null; shadowing = false }
        }
        lifecycle.addObserver(observer)
        onDispose { vm.setForeground(false); vm.recordRecent(); lifecycle.removeObserver(observer); videoShadowing.release() }
    }
    LaunchedEffect(state.cues) {
        videoShadowing.setCues(state.cues)
        if (shadowing) videoShadowing.selectAt(latestTime)
    }
    fun leaveShadowing() {
        videoShadowing.release()
        recordRequest = null
        shadowing = false
    }

    var externalError by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importSubtitles(uri)
    }
    LaunchedEffect(videoId) { vm.load(videoId) }
    LaunchedEffect(state.preferencesLoaded, controller) {
        if (state.preferencesLoaded) controller.seekTo(state.currentTimeMs)
    }
    // In the gap between two cues keep showing the one that just ended, so the line doesn't blink away.
    // While a practice line plays, polling overshoot past its end must not light the next line.
    val cueTime = frozenCueTime ?: practice.unit?.takeIf { shadowing && practice.playingUnit }
        ?.let { state.currentTimeMs.coerceIn(it.startMs, maxOf(it.startMs, it.endMs - 1)) } ?: state.currentTimeMs
    val index = remember(state.cues, cueTime) {
        findCueIndex(state.cues, cueTime).takeIf { it >= 0 }
            ?: state.cues.indexOfLast { it.endMs <= cueTime }
    }
    val merged = remember(state.cues) { mergeSentenceUnits(state.cues) }
    val sentences = merged.units
    val sentenceIndex = merged.unitIndex(index)
    LaunchedEffect(sentences, sentenceIndex, state.translationEnabled, state.translationLanguage) {
        vm.prefetchTranslations(sentences, sentenceIndex)
    }
    val activeRange = merged.cueTextRanges.getOrNull(index)
    val transcriptState = rememberLazyListState()
    val transcriptViewport by remember { derivedStateOf { transcriptState.layoutInfo.viewportSize.height } }
    val measuredPadding by remember { derivedStateOf { transcriptState.layoutInfo.beforeContentPadding } }
    val transcriptPadding = with(density) { transcriptEdgePadding(transcriptViewport, 8.dp.roundToPx()).toDp() }
    var dictionaryOpen by remember(videoId) { mutableStateOf(state.lookup != null) }
    var dictionarySentence by remember(videoId) { mutableStateOf(state.lookup?.sentence.orEmpty()) }
    var recenterRequest by remember(videoId) { mutableIntStateOf(0) }
    val sentenceHeights = remember(sentences) { mutableStateMapOf<Int, Int>() }
    var following by remember(videoId) { mutableStateOf(true) }
    var lastTouch by remember(videoId) { mutableLongStateOf(0L) }
    LaunchedEffect(transcriptState) {
        transcriptState.interactionSource.interactions.collect { interaction ->
            if (interaction is DragInteraction.Start) following = false
        }
    }
    LaunchedEffect(lastTouch, cueGestureActive) {
        if (lastTouch != 0L && !cueGestureActive) {
            delay(4_000)
            following = true
        }
    }
    LaunchedEffect(state.playerState, frozenCueTime) {
        if (state.playerState == 1 && frozenCueTime == null) following = true
    }
    LaunchedEffect(subtitlePercent, dictionaryOpen, recenterRequest, sentenceIndex, following, cueGestureActive, sentences, sentenceHeights[sentenceIndex], transcriptViewport, measuredPadding, state.translationEnabled,
        state.translations[sentences.getOrNull(sentenceIndex)?.text]) {
        if (!dictionaryOpen && following && !cueGestureActive && sentenceIndex in sentences.indices) {
            // Font changes must finish measurement before reading the new item height.
            withFrameNanos { }
            // Bring distant items into the measured viewport, then centre using their actual height.
            if (transcriptState.layoutInfo.visibleItemsInfo.none { it.index == sentenceIndex }) {
                transcriptState.animateScrollToItem(sentenceIndex)
            }
            val layout = snapshotFlow { transcriptState.layoutInfo }
                .first { info -> info.visibleItemsInfo.any { it.index == sentenceIndex } }
            val item = layout.visibleItemsInfo.first { it.index == sentenceIndex }
            transcriptState.animateScrollToItem(sentenceIndex,
                transcriptCenterOffset(layout.viewportSize.height, item.size, layout.beforeContentPadding))
        }
    }
    fun changeSubtitleSize(step: Int) {
        vm.setSubtitleSizePercent(subtitleSizes[(subtitleSizes.indexOf(subtitlePercent) + step).coerceIn(subtitleSizes.indices)])
        following = true
        lastTouch = 0L
        recenterRequest++
    }
    fun seek(index: Int) {
        freeze = freeze.resume()
        sentences.getOrNull(index)?.let {
            if (shadowing) {
                videoShadowing.cancel()
                // Shadowing follows the subtitle the user jumped to.
                videoShadowing.selectAt(it.startMs)
            }
            controller.seekTo(it.startMs)
            vm.setTime(it.startMs)
        }
    }
    /**
     * Line navigation never leaves the video stopped: ordinary watching plays on from the new line;
     * in shadowing (stopped or playing a practice line) only the new line plays, then pauses for repeating.
     */
    fun playFromLine(index: Int) {
        if (sentences.getOrNull(index) == null) return
        // A just-finished practice line still reports "playing" until its late pause lands; its freeze marks it.
        val watching = state.playerState == 1 && !videoShadowing.state.value.playingUnit && frozenCueTime == null
        following = true
        lastTouch = 0L
        cueGestureActive = false
        recordRequest = null
        seek(index)
        if (shadowing && !watching) videoShadowing.playUnit() else controller.play()
    }
    fun skipBy(deltaMs: Long) {
        freeze = freeze.resume()
        cueGestureActive = false
        recordRequest = null
        following = true
        lastTouch = 0L
        if (shadowing) videoShadowing.cancel(pauseVideo = false)
        val target = (latestTime + deltaMs).coerceAtLeast(0L)
        controller.seekTo(target)
        vm.setTime(target)
    }
    fun closeDictionary() {
        dictionaryOpen = false
        if (resumeAfterDictionary && !shadowing) {
            recordRequest = null
            freeze = freeze.resume()
            cueGestureActive = false
            controller.play()
        }
        resumeAfterDictionary = false
        vm.dismissLookup()
        following = true
        lastTouch = 0L
        recenterRequest++
    }
    BackHandler(enabled = dictionaryOpen) { closeDictionary() }
    // Ordinary playback can move to a different sentence without using the practice controls.
    LaunchedEffect(shadowing, sentenceIndex) {
        val sentence = sentences.getOrNull(sentenceIndex)
        val practice = videoShadowing.state.value
        if (shadowing && sentence != null && !practice.playingUnit &&
            practice.phase != ShadowPhase.Recording && practice.phase != ShadowPhase.Recognizing &&
            recordRequest == null && practice.unit?.startMs != sentence.startMs) {
            recordRequest = null
            videoShadowing.selectAt(sentence.startMs, pauseVideo = false)
        }
    }
    Scaffold { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
            // Respect the physical pixel minimum even on low density devices.
            val minimumHeight = maxOf(96.dp, with(density) { 200.toDp() })
            val playerHeight = if (compactPlayer) minimumHeight else
                maxOf(minimumHeight, minOf(maxWidth * 9f / 16f, maxHeight * 0.35f))
            // On low density/narrow displays a less wide viewport leaves usable control space.
            val playerWidth = if (compactPlayer)
                maxOf(with(density) { 200.toDp() }, minOf(playerHeight * 16f / 9f, maxWidth * 0.55f))
                else playerHeight * 16f / 9f
            Column(Modifier.fillMaxSize()) {
                val controls: @Composable () -> Unit = {
                    IconButton(onClick = { if (dictionaryOpen) closeDictionary() else onBack() }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.watch_back), modifier = Modifier.size(20.dp))
                    }
                    IconButton(onClick = {
                        vm.setCompactPlayer(!compactPlayer)
                    }, enabled = state.preferencesLoaded, modifier = Modifier.size(36.dp)) {
                        Icon(if (compactPlayer) Icons.Default.Fullscreen else Icons.Default.FullscreenExit,
                            stringResource(if (compactPlayer) R.string.videos_expand_player else R.string.videos_compact_player), modifier = Modifier.size(20.dp))
                    }
                    IconToggleButton(checked = state.translationEnabled,
                        onCheckedChange = vm::setTranslationEnabled,
                        enabled = state.preferencesLoaded, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Translate, stringResource(R.string.watch_translation), modifier = Modifier.size(20.dp))
                    }
                    Box {
                        IconButton(onClick = { sizeMenuOpen = true }, enabled = state.preferencesLoaded, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Default.FormatSize, stringResource(R.string.watch_subtitle_size), modifier = Modifier.size(20.dp))
                        }
                        DropdownMenu(expanded = sizeMenuOpen, onDismissRequest = { sizeMenuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.watch_subtitle_size_percent, subtitlePercent)) },
                                onClick = {}, enabled = false
                            )
                            DropdownMenuItem(text = { Text(stringResource(R.string.watch_subtitle_size_decrease)) },
                                enabled = subtitlePercent > subtitleSizes.first(), onClick = { changeSubtitleSize(-1) })
                            DropdownMenuItem(text = { Text(stringResource(R.string.watch_subtitle_size_increase)) },
                                enabled = subtitlePercent < subtitleSizes.last(), onClick = { changeSubtitleSize(1) })
                        }
                    }
                    IconToggleButton(checked = shadowing, onCheckedChange = { enabled ->
                        if (enabled) {
                            recordRequest = null
                            videoShadowing.selectAt(latestTime)
                            shadowing = true
                        } else leaveShadowing()
                    }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.RecordVoiceOver, stringResource(R.string.watch_shadowing),
                            modifier = Modifier.size(20.dp),
                            tint = if (shadowing) MaterialTheme.colorScheme.primary else LocalContentColor.current)
                    }
                    IconButton(onClick = { vm.refreshExport(); exportOpen = true },
                        enabled = state.cues.isNotEmpty(), modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.IosShare, stringResource(R.string.watch_export), modifier = Modifier.size(20.dp))
                    }
                    IconButton(onClick = { picker.launch(arrayOf("*/*")) }, enabled = !state.loading, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Subtitles, stringResource(R.string.watch_pick_subtitles), modifier = Modifier.size(20.dp))
                    }
                }
                val player: @Composable () -> Unit = {
                    if (state.preferencesLoaded) {
                        YoutubePlayerView(videoId, controller, { time, generation ->
                            freeze = freeze.onTime(vm.uiState.value.currentTimeMs, time,
                                vm.uiState.value.playerState, videoShadowing.state.value.playingUnit, SystemClock.uptimeMillis())
                            vm.setTime(time)
                            videoShadowing.onTime(time, generation)
                        }, { value, generation ->
                            freeze = freeze.onPlayerState(value, videoShadowing.state.value.playingUnit)
                            if (value == 1) {
                                // The player keeps reporting "playing" for a moment after a touch paused it; only a
                                // later play (YouTube's own button) releases the frozen line, or the tap would be lost.
                                if (freeze.guardsPlaying(cueGestureActive, SystemClock.uptimeMillis())) controller.pause()
                                else {
                                    freeze = freeze.resume()
                                    if (!videoShadowing.state.value.playingUnit) recordRequest = null
                                }
                            }
                            vm.setPlayerState(value)
                            videoShadowing.onPlayerState(value, generation)
                        }, { code -> videoShadowing.cancel(); vm.setPlayerError(code) },
                            Modifier.size(width = playerWidth, height = playerHeight))
                    } else Spacer(Modifier.size(width = playerWidth, height = playerHeight))
                }
                if (compactPlayer) {
                    // Physical right, including in a Persian UI. Controls wrap without scrolling.
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            movePlayer(player)
                            FlowRow(Modifier.weight(1f).padding(horizontal = 4.dp)) { controls() }
                        }
                    }
                } else {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { movePlayer(player) }
                    FlowRow(Modifier.fillMaxWidth().heightIn(min = 40.dp).padding(horizontal = 4.dp)) { controls() }
                }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    val lineHeight = with(density) { kotlin.math.ceil(subtitleStyle.lineHeight.toPx()).toInt() }
                    val itemHeight = with(density) {
                        transcriptItemHeight(transcriptViewport, lineHeight, 32.dp.roundToPx(), 8.dp.roundToPx()).toDp()
                    }
                    // Keep the transcript and practice lifecycle alive beneath the opaque dictionary.
                    Column(Modifier.fillMaxSize().alpha(if (dictionaryOpen) 0f else 1f)
                        .then(if (dictionaryOpen) Modifier.clearAndSetSemantics {} else Modifier)) {
                        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            state.playerError?.let { code ->
                                Text(stringResource(if (code in listOf(101, 150, 152)) R.string.watch_embed_error else R.string.watch_player_error),
                                    color = MaterialTheme.colorScheme.error)
                                TextButton(onClick = {
                                    try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(YoutubeUrl.watchUrl(videoId)))) }
                                    catch (_: ActivityNotFoundException) { externalError = true }
                                }) { Text(stringResource(R.string.watch_open_youtube)) }
                            }
                            if (externalError) Text(stringResource(R.string.watch_open_error), color = MaterialTheme.colorScheme.error)
                            if (state.loading) CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
                            if (state.captionError) Text(stringResource(R.string.watch_caption_error))
                            if (state.importError) Text(stringResource(R.string.watch_import_error), color = MaterialTheme.colorScheme.error)
                            if (state.captionError) TextButton(onClick = { picker.launch(arrayOf("*/*")) }, enabled = !state.loading) {
                                Text(stringResource(R.string.watch_pick_subtitles))
                            }
                        }
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            LazyColumn(
                                state = transcriptState,
                                contentPadding = PaddingValues(vertical = transcriptPadding),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp)
                                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                                    .drawWithContent {
                                        drawContent()
                                        val edge = minOf(32.dp.toPx(), size.height / 2f)
                                        if (size.height > 0f) drawRect(
                                            brush = Brush.verticalGradient(
                                                0f to Color.Transparent,
                                                (edge / size.height) to Color.Black,
                                                (1f - edge / size.height) to Color.Black,
                                                1f to Color.Transparent
                                            ),
                                            blendMode = BlendMode.DstIn
                                        )
                                    }.pointerInput(videoId) {
                                    awaitEachGesture {
                                        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                                        playingBeforeTouch = vm.uiState.value.playerState == 1 && freeze.cueTimeMs == null &&
                                            !videoShadowing.state.value.playingUnit
                                        cueGestureActive = true
                                        lastTouch = SystemClock.uptimeMillis()
                                        freeze = PlaybackFreeze(freeze.cueTimeMs ?: latestTime, lastTouch)
                                        videoShadowing.cancel()
                                        controller.pause()
                                        try {
                                            do {
                                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                                lastTouch = SystemClock.uptimeMillis()
                                            } while (event.changes.any { it.pressed })
                                        } finally {
                                            lastTouch = SystemClock.uptimeMillis()
                                            cueGestureActive = false
                                        }
                                    }
                                }
                            ) {
                                itemsIndexed(sentences) { itemIndex, sentence ->
                                    val isCurrent = itemIndex == sentenceIndex
                                    val innerScroll = rememberScrollState()
                                    LaunchedEffect(innerScroll) {
                                        innerScroll.interactionSource.interactions.collect { interaction ->
                                            if (interaction is DragInteraction.Start) following = false
                                        }
                                    }
                                    var activeLayout by remember(sentence.text) { mutableStateOf<TextLayoutResult?>(null) }
                                    var innerHeight by remember { mutableIntStateOf(0) }
                                    LaunchedEffect(dictionaryOpen, recenterRequest, isCurrent, activeRange, activeLayout, innerHeight, innerScroll.maxValue, following, cueGestureActive) {
                                        if (!dictionaryOpen && isCurrent && following && !cueGestureActive) {
                                            val layout = activeLayout
                                            val range = activeRange
                                            if (layout != null && range != null && layout.layoutInput.text.isNotEmpty()) {
                                                val first = layout.getLineForOffset(range.first.coerceIn(0, sentence.text.lastIndex))
                                                val last = layout.getLineForOffset(range.last.coerceIn(0, sentence.text.lastIndex))
                                                innerScroll.animateScrollTo(transcriptActiveOffset(
                                                    layout.getLineTop(first).toInt(), layout.getLineBottom(last).toInt(),
                                                    innerHeight, innerHeight + innerScroll.maxValue
                                                ))
                                            }
                                        }
                                    }
                                    Box {
                                    Column(Modifier
                                        .heightIn(max = itemHeight)
                                        .onSizeChanged { sentenceHeights[itemIndex] = it.height; innerHeight = it.height }
                                        .verticalScroll(innerScroll)) {
                                        val lookup: (String, String?) -> Unit = { query, gloss ->
                                            resumeAfterDictionary = playingBeforeTouch
                                            playingBeforeTouch = false
                                            videoShadowing.cancel()
                                            recordRequest = null
                                            controller.pause()
                                            dictionarySentence = sentence.text
                                            dictionaryOpen = true
                                            vm.lookup(query, sentence.text, fallbackTitle, gloss)
                                        }
                                        // The copy button sits beside the French line so it is not read as copying the translation.
                                        Row(verticalAlignment = Alignment.Top) {
                                            Box(Modifier.weight(1f)) { TranscriptSentence(
                                                text = sentence.text,
                                                highlighter = vocabHighlighter,
                                                textStyle = subtitleStyle,
                                                current = isCurrent,
                                                activeRange = if (isCurrent) activeRange else null,
                                                matchIdioms = vm::matchIdioms,
                                                onLayout = { activeLayout = it },
                                                onPhrase = { lookup(it, null) },
                                                onWord = lookup
                                            ) }
                                            // Room for the copy button, which stays outside the scrolling sentence.
                                            if (isCurrent) Spacer(Modifier.width(32.dp))
                                        }
                                        if (isCurrent && state.translationEnabled) {
                                            val failed = sentence.text in state.failedTranslations
                                            val translated = state.translations[sentence.text]
                                            val line = translated ?: if (failed) stringResource(R.string.watch_translation_failed)
                                                else if (!state.translations.containsKey(sentence.text))
                                                stringResource(R.string.watch_translation_loading) else ""
                                            if (line.isNotBlank()) {
                                                val rtl = state.translationLanguage == "fa"
                                                CompositionLocalProvider(LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                                                    val corrected = sentence.text in state.userTranslations
                                                    val translationText = buildAnnotatedString {
                                                        if (corrected) { appendInlineContent("correction", "\uFFFC"); append(" ") }
                                                        append(line)
                                                    }
                                                    Text(translationText, inlineContent = mapOf("correction" to InlineTextContent(
                                                        Placeholder(12.sp, 12.sp, PlaceholderVerticalAlign.TextCenter)
                                                    ) {
                                                        Icon(Icons.Default.Edit, contentDescription = null,
                                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                                            modifier = Modifier.size(12.dp))
                                                    }), modifier = Modifier.fillMaxWidth().pointerInput(sentence.text, translated, failed) {
                                                        detectTapGestures(onLongPress = {
                                                            if (translated != null) {
                                                                videoShadowing.cancel()
                                                                recordRequest = null
                                                                controller.pause()
                                                                editingTranslation = sentence.text
                                                            }
                                                        }, onTap = {
                                                            if (failed) vm.prefetchTranslations(sentences, itemIndex) else seek(itemIndex)
                                                        })
                                                    },
                                                        style = translationStyle.copy(
                                                            textDirection = if (rtl) TextDirection.Rtl else TextDirection.Ltr),
                                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f))
                                                }
                                            }
                                        }
                                    }
                                    if (isCurrent) {
                                        IconButton(onClick = {
                                            clipboard.setText(androidx.compose.ui.text.AnnotatedString(sentence.text))
                                            // Touching the transcript paused playback; copying alone should not stop the video.
                                            if (playingBeforeTouch && !shadowing) {
                                                freeze = freeze.resume()
                                                cueGestureActive = false
                                                controller.play()
                                            }
                                            playingBeforeTouch = false
                                            if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) {
                                                android.widget.Toast.makeText(context, R.string.sentence_copied, android.widget.Toast.LENGTH_SHORT).show()
                                            }
                                        }, modifier = Modifier.align(Alignment.TopEnd).size(32.dp)) {
                                            Icon(Icons.Default.ContentCopy, contentDescription = stringResource(R.string.copy_sentence),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                                modifier = Modifier.size(16.dp))
                                        }
                                    }
                                    }
                                }
                                if (sentences.isEmpty()) item { Text(stringResource(R.string.watch_no_current_cue)) }
                            }
                        }
                        if (shadowing) VideoShadowingPane(videoShadowing, recordRequest,
                            onStep = { step -> playFromLine(videoShadowing.state.value.index + step) })
                    }
                    // The non-scoped overload: inside the Column the ColumnScope extension would be picked.
                    androidx.compose.animation.AnimatedVisibility(
                        visible = dictionaryOpen,
                        enter = slideInVertically(tween(160)) { it / 6 } + fadeIn(tween(160)),
                        exit = slideOutVertically(tween(120)) { it / 6 } + fadeOut(tween(120)),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Surface(modifier = Modifier.fillMaxSize().pointerInput(Unit) {
                            // Consume blank-area taps as well as the dictionary's own interactive content.
                            detectTapGestures { }
                        }, color = MaterialTheme.colorScheme.surface) {
                            Column(Modifier.fillMaxSize()) {
                                Row(Modifier.fillMaxWidth().padding(start = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically) {
                                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                        Text(dictionarySentence, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                            style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.Ltr),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.weight(1f))
                                    }
                                    // A close action is available even while preparing the lookup or after an error.
                                    if (state.lookup == null) IconButton(onClick = { closeDictionary() }) {
                                        Icon(Icons.Default.Close, stringResource(R.string.accessibility_close))
                                    }
                                }
                                val lookup = state.lookup
                                if (lookup != null) {
                                    lookup.idiomGloss?.let { gloss ->
                                        Text(stringResource(R.string.watch_expression, lookup.word, gloss),
                                            style = MaterialTheme.typography.bodyMedium,
                                            modifier = Modifier.padding(horizontal = 12.dp))
                                    }
                                    DictionaryPane(
                                        textId = lookup.textId,
                                        word = lookup.word,
                                        sentence = lookup.sentence,
                                        onDismiss = { closeDictionary() },
                                        modifier = Modifier.weight(1f).fillMaxWidth()
                                    )
                                } else Box(Modifier.weight(1f).fillMaxWidth()) {
                                    if (state.lookupLoading) CircularProgressIndicator(Modifier.align(Alignment.Center))
                                    if (state.lookupError) Text(stringResource(R.string.watch_lookup_error),
                                        color = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.align(Alignment.Center).padding(12.dp))
                                }
                            }
                        }
                    }
                }
                // Shadowing navigates from the practice line, like its own pane; otherwise from the shown line.
                val lineIndex = if (shadowing && practice.unit != null) practice.index else sentenceIndex
                // The dictionary needs the whole height; the bar returns once its exit animation is done.
                AnimatedVisibility(visible = !dictionaryOpen,
                    enter = expandVertically(tween(160, delayMillis = 120)) + fadeIn(tween(160, delayMillis = 120)),
                    exit = shrinkVertically(tween(160)) + fadeOut(tween(120))) {
                WatchPlaybackBar(
                    // Still visible while it animates out; the dictionary has already paused playback.
                    enabled = !dictionaryOpen,
                    playing = state.playerState == 1,
                    hasPrevious = lineIndex > 0,
                    hasNext = lineIndex in 0 until sentences.lastIndex || (lineIndex < 0 && sentences.isNotEmpty()),
                    onPrevious = { playFromLine((lineIndex - 1).coerceAtLeast(0)) },
                    onBack = { skipBy(-5_000) },
                    onPlayPause = {
                        if (state.playerState == 1) controller.pause()
                        else { recordRequest = null; freeze = freeze.resume(); cueGestureActive = false; videoShadowing.cancel(); controller.play() }
                    },
                    onForward = { skipBy(5_000) },
                    onNext = { playFromLine(lineIndex + 1) }
                )
                }
            }
        }
    }
}

/** Thumb-reach transport: the most used actions sit at the bottom with play/pause in the middle. */
@Composable
private fun WatchPlaybackBar(
    enabled: Boolean,
    playing: Boolean,
    hasPrevious: Boolean,
    hasNext: Boolean,
    onPrevious: () -> Unit,
    onBack: () -> Unit,
    onPlayPause: () -> Unit,
    onForward: () -> Unit,
    onNext: () -> Unit
) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 2.dp) {
        // Physical order, including in a Persian UI.
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onPrevious, enabled = enabled && hasPrevious, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Default.SkipPrevious, stringResource(R.string.watch_shadow_previous), modifier = Modifier.size(28.dp))
                }
                IconButton(onClick = onBack, enabled = enabled, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Default.Replay5, stringResource(R.string.watch_skip_back), modifier = Modifier.size(28.dp))
                }
                FilledIconButton(onClick = onPlayPause, enabled = enabled, modifier = Modifier.size(64.dp)) {
                    Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                        stringResource(if (playing) R.string.videos_pause else R.string.videos_play), modifier = Modifier.size(36.dp))
                }
                IconButton(onClick = onForward, enabled = enabled, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Default.Forward5, stringResource(R.string.watch_skip_forward), modifier = Modifier.size(28.dp))
                }
                IconButton(onClick = onNext, enabled = enabled && hasNext, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Default.SkipNext, stringResource(R.string.shadowing_next), modifier = Modifier.size(28.dp))
                }
            }
        }
    }
}

@Composable
fun WatchLinkDialog(onDismiss: () -> Unit, onOpen: (String) -> Unit) {
    var link by remember { mutableStateOf("") }
    var invalid by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.watch_action)) }, text = {
        Column {
            OutlinedTextField(value = link, onValueChange = { link = it; invalid = false },
                label = { Text(stringResource(R.string.watch_link_hint)) }, isError = invalid, singleLine = true)
            if (invalid) Text(stringResource(R.string.watch_invalid_link), color = MaterialTheme.colorScheme.error)
        }
    }, confirmButton = {
        TextButton(onClick = {
            val id = YoutubeUrl.videoId(link)
            if (id == null) invalid = true else onOpen(id)
        }) { Text(stringResource(R.string.watch_open)) }
    }, dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.watch_cancel)) } })
}

/** Reader vocabulary styling; sentence emphasis supplies the foreground independently. */
@Composable
private fun savedSubtitleText(text: String, highlighter: VocabHighlighter): androidx.compose.ui.text.AnnotatedString {
    val matches = remember(text, highlighter) { highlighter.findMatches(text) }
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    return remember(text, matches, dark) {
        buildAnnotatedString {
            append(text)
            matches.forEach { match ->
                addStyle(SpanStyle(
                    background = match.status.containerColor(dark).copy(
                        alpha = if (match.status == VocabStatus.LEARNED) 0.12f else 0.7f),
                    textDecoration = if (match.status == VocabStatus.LEARNED) null else TextDecoration.Underline
                ), match.range.first, match.range.last + 1)
            }
        }
    }
}

/** Each lazy item owns its cached text and a gesture detector that survives playback ticks. */
@Composable
private fun TranscriptSentence(
    text: String,
    highlighter: VocabHighlighter,
    textStyle: androidx.compose.ui.text.TextStyle,
    current: Boolean,
    activeRange: IntRange?,
    matchIdioms: suspend (String) -> List<IdiomMatch>,
    maxLines: Int = Int.MAX_VALUE,
    onLayout: (TextLayoutResult) -> Unit = {},
    onWord: (String, String?) -> Unit,
    onPhrase: (String) -> Unit = { onWord(it, null) }
) {
    val idioms by produceState(emptyList<IdiomMatch>(), text) {
        try { value = matchIdioms(text) }
        catch (error: CancellationException) { throw error }
        catch (_: Exception) { /* Word lookup remains available without the idiom asset. */ }
    }
    val vocabText = savedSubtitleText(text, highlighter)
    val ink = MaterialTheme.colorScheme.onSurface
    val idiomColor = MaterialTheme.colorScheme.tertiary
    val annotated = remember(vocabText, activeRange, idioms, ink, idiomColor, current) {
        buildAnnotatedString {
            tokenizeSubtitle(text).forEach { token ->
                if (token.isWord) pushStringAnnotation("word", token.text)
                append(token.text)
                if (token.isWord) pop()
            }
            vocabText.spanStyles.forEach { addStyle(it.item, it.start, it.end) }
            if (current) {
                addStyle(SpanStyle(color = ink.copy(alpha = 0.7f)), 0, length)
                activeRange?.let { addStyle(SpanStyle(color = ink), it.first, it.last + 1) }
            }
            idioms.forEach {
                addStyle(SpanStyle(color = idiomColor, fontWeight = FontWeight.Medium), it.startChar, it.endChar)
            }
        }
    }
    val latestClick by rememberUpdatedState<(Int) -> Unit>({ offset ->
        val idiom = idioms.firstOrNull { offset >= it.startChar && offset < it.endChar }
        if (idiom != null) onWord(idiom.expression, idiom.gloss)
        else annotated.getStringAnnotations("word", offset, offset).firstOrNull()?.let { onWord(it.item, null) }
    })
    val latestPhrase by rememberUpdatedState(onPhrase)
    val latestText by rememberUpdatedState(text)
    val words = remember(text) {
        annotated.getStringAnnotations("word", 0, text.length).map { it.start until it.end }
    }
    val latestWords by rememberUpdatedState(words)
    var selection by remember { mutableStateOf<IntRange?>(null) }
    val selectionColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
    val displayed = remember(annotated, selection, selectionColor) {
        buildAnnotatedString {
            append(annotated)
            selection?.let { addStyle(SpanStyle(background = selectionColor), it.first, it.last + 1) }
        }
    }
    var textLayout by remember { mutableStateOf<androidx.compose.ui.text.TextLayoutResult?>(null) }
    BasicText(
        text = displayed,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        style = textStyle.copy(color = ink, textDirection = TextDirection.Ltr),
        modifier = Modifier.fillMaxWidth().alpha(if (current || selection != null) 1f else 0.45f)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    fun offsetAt(position: androidx.compose.ui.geometry.Offset, inside: Boolean): Int? {
                        val layout = textLayout ?: return null
                        val line = layout.getLineForVerticalPosition(position.y)
                        if (inside && (position.y < 0 || position.y >= layout.size.height ||
                                position.x < layout.getLineLeft(line) || position.x >= layout.getLineRight(line))) return null
                        return layout.getOffsetForPosition(position)
                    }
                    var multiTouch = false
                    val longPress = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id }
                            if (event.changes.size > 1) { multiTouch = true; break }
                            if (change == null || change.isConsumed ||
                                (change.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                                break
                            }
                            if (!change.pressed) {
                                offsetAt(change.position, true)?.let { latestClick(it) }
                                break
                            }
                            val final = awaitPointerEvent(PointerEventPass.Final)
                            if (final.changes.any { it.isConsumed }) {
                                break
                            }
                        }
                        false
                    } == null
                    if (longPress && !multiTouch) {
                        val gestureText = latestText
                        val gestureWords = latestWords
                        val anchorOffset = offsetAt(down.position, true)
                        val anchor = gestureWords.indexOfFirst { anchorOffset != null && anchorOffset in it }
                        var target = anchor
                        // Once the drag reaches another word, release looks up the selection, even back on the anchor.
                        var dragged = false
                        if (anchor >= 0) selection = gestureWords[anchor]
                        try {
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                // A second finger cancels: no lookup or seek from an unfinished multi-touch.
                                if (change.isConsumed || latestText != gestureText || event.changes.count { it.pressed } > 1) break
                                if (anchor >= 0) {
                                    val offset = offsetAt(change.position, false)
                                    val word = gestureWords.indexOfFirst { offset != null && offset in it }
                                    if (word >= 0) target = word
                                    if (target != anchor) dragged = true
                                    selection = transcriptSelectionRange(gestureWords, anchor, target)
                                }
                                event.changes.forEach { it.consume() }
                                if (!change.pressed) {
                                    if (anchor >= 0 && dragged) {
                                        selection?.let { latestPhrase(gestureText.substring(it).trim()) }
                                    } else anchorOffset?.let { latestClick(it) }
                                    break
                                }
                            }
                        } finally {
                            selection = null
                        }
                    }
                }
            },
        onTextLayout = { textLayout = it; onLayout(it) }
    )
}


@Composable
private fun TranscriptExportRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    headline: String,
    supporting: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Row(Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick)
        .padding(vertical = 14.dp).alpha(if (enabled) 1f else 0.5f),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(headline, style = MaterialTheme.typography.bodyLarge)
            Text(supporting, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
