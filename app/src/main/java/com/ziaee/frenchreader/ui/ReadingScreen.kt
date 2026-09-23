package com.ziaee.frenchreader.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.Popup
import coil.compose.AsyncImage
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ziaee.frenchreader.R
import com.ziaee.frenchreader.content.SavedVocab
import com.ziaee.frenchreader.content.VocabHighlighter
import com.ziaee.frenchreader.data.VocabStatus
import com.ziaee.frenchreader.data.HighlightEntry
import com.ziaee.frenchreader.data.HighlightPrefs
import com.ziaee.frenchreader.text.BlockType
import com.ziaee.frenchreader.tts.AVAILABLE_VOICES
import com.ziaee.frenchreader.tts.SentenceBoundary
import com.ziaee.frenchreader.tts.XTTS_VOICE_PREFIX
import com.ziaee.frenchreader.data.AppearancePrefs
import com.ziaee.frenchreader.ui.theme.AppearanceState
import com.ziaee.frenchreader.ui.theme.FontScale
import com.ziaee.frenchreader.ui.theme.ReadingPalette
import com.ziaee.frenchreader.ui.theme.readingPaletteFor
import com.ziaee.frenchreader.ui.theme.FrenchReaderDesign
import java.text.SimpleDateFormat
import com.ziaee.frenchreader.util.launchProcessTextApp
import com.ziaee.frenchreader.util.queryProcessTextApps
import java.util.Date
import java.util.Locale
import java.io.File
import kotlinx.coroutines.launch

private val SPEED_OPTIONS = listOf(0.75f, 1.0f, 1.25f, 1.5f)
private val EPUB_IMAGE_REF = Regex("^[0-9a-f]+/[A-Za-z0-9][A-Za-z0-9_-]*\\.jpg$")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadingScreen(textId: Long, onBack: () -> Unit, onOpenVocab: () -> Unit) {
    val vm: ReadingViewModel = viewModel()
    val state by vm.state.collectAsState()
    val allSavedVocabStatuses by vm.savedVocabStatuses.collectAsState()
    val highlights by vm.highlights.collectAsState()
    val savedVocabStatuses = if (AppearanceState.highlightSavedWords) allSavedVocabStatuses else emptyMap()
    val palette = readingPaletteFor(AppearanceState.readingBackground, AppearanceState.highlightColor)
    val settingsContext = LocalContext.current
    val fontScale = AppearanceState.fontScale.multiplier
    val vocabularyDescription = stringResource(R.string.accessibility_vocabulary)
    val voiceDescription = stringResource(R.string.accessibility_select_voice)
    val autoScrollDescription = stringResource(R.string.accessibility_autoplay)
    val sourceInfoDescription = stringResource(R.string.accessibility_source_info)

    // word + the sentence it came from, while the dictionary sheet is open.
    var dictionaryTarget by remember { mutableStateOf<Pair<String, String>?>(null) }
    // Word/sentence a selection just resolved to -- shows a "Define" affordance next to
    // the still-active selection handles; only opens the dictionary once the user taps it,
    // so a long-press's initial single-word selection doesn't collapse itself before the
    // user has a chance to drag a handle and extend it to a phrase.
    var selectedWord by remember { mutableStateOf<Pair<String, String>?>(null) }
    var selectedWordRange by remember { mutableStateOf<TextRange?>(null) }
    var selectedPhrase by remember { mutableStateOf<String?>(null) }
    var selectedPhraseSentence by remember { mutableStateOf("") }
    var selectedPhraseRange by remember { mutableStateOf<TextRange?>(null) }
    var showHighlightPalette by remember { mutableStateOf(false) }
    var highlightPopupTarget by remember { mutableStateOf<HighlightEntry?>(null) }
    var highlightPopupRect by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    var changeHighlightColor by remember { mutableStateOf(false) }
    var lastHighlightColorKey by remember(settingsContext) {
        mutableStateOf(HighlightPrefs.getLastColorKey(settingsContext))
    }
    var clearSelectionTick by remember { mutableIntStateOf(0) }
    var processTextTarget by remember { mutableStateOf<String?>(null) }
    val selectionToolbarController = remember { SelectionToolbarController() }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current

    val listState = rememberLazyListState()
    var autoScrollEnabled by remember { mutableStateOf(true) }
    var voiceMenuExpanded by remember { mutableStateOf(false) }
    var moreMenuExpanded by remember { mutableStateOf(false) }
    var showSourceInfoSheet by remember { mutableStateOf(false) }
    var showContentsSheet by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val headings = remember(state.chunks) {
        state.chunks.mapIndexedNotNull { index, chunk ->
            chunk.takeIf { it.block.type == BlockType.HEADER && it.block.headerLevel in 1..2 }
                ?.let { index to it }
        }
    }
    val spokenChunkIndices = remember(state.chunks) {
        state.chunks.indices.filter { state.chunks[it].block.type != BlockType.IMAGE }
    }
    val chunkDocumentOffsets = remember(state.chunks) { documentOffsets(state.chunks) }
    val spokenProgress = remember(spokenChunkIndices, state.currentChunkIndex) {
        val reached = spokenChunkIndices.count { it <= state.currentChunkIndex }
        reached to spokenChunkIndices.size
    }

    LaunchedEffect(textId) { vm.load(textId) }
    LaunchedEffect(state.fullSynthesisError) {
        val error = state.fullSynthesisError ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(error)
        vm.clearFullSynthesisError()
    }
    DisposableEffect(Unit) {
        onDispose { vm.persistPositionNow() }
    }

    // Follows playback down the page as the current paragraph advances, so
    // reading along doesn't require manually dragging the screen up every
    // few sentences. Toggled off, the person scrolls entirely by hand.
    LaunchedEffect(state.currentChunkIndex, state.ready, autoScrollEnabled) {
        if (autoScrollEnabled && state.ready && state.chunks.isNotEmpty()) {
            listState.animateScrollToItem(state.currentChunkIndex)
        }
    }

    // A paragraph taller than one screen doesn't get followed by the effect
    // above (it only fires on paragraph change) -- this continuously nudges
    // the scroll forward as the read-aloud position within a long paragraph
    // approaches the bottom of the visible area, so its tail is never being
    // read while sitting off-screen. Only activates for paragraphs that
    // don't already fit on screen; short paragraphs are untouched.
    LaunchedEffect(autoScrollEnabled) {
        if (!autoScrollEnabled) return@LaunchedEffect
        snapshotFlow { state.currentPositionMs to state.currentChunkIndex }
            .collect { (positionMs, chunkIndex) ->
                if (!state.ready || state.chunks.isEmpty()) return@collect
                val chunk = state.chunks.getOrNull(chunkIndex) ?: return@collect
                val visibleInfo = listState.layoutInfo.visibleItemsInfo.find { it.index == chunkIndex } ?: return@collect
                val viewportHeight =
                    (listState.layoutInfo.viewportEndOffset - listState.layoutInfo.viewportStartOffset).toFloat()
                if (viewportHeight <= 0f || visibleInfo.size <= viewportHeight) return@collect

                val totalChars = chunk.sentences.sumOf { it.text.length + 1 }.coerceAtLeast(1)
                val activeIdx = chunk.sentences.indexOfFirst {
                    positionMs >= it.offsetMs && positionMs < it.offsetMs + it.durationMs
                }
                if (activeIdx < 0) return@collect
                val charsBefore = chunk.sentences.take(activeIdx).sumOf { it.text.length + 1 }
                val fraction = (charsBefore.toFloat() / totalChars).coerceIn(0f, 1f)
                val activeTopPx = visibleInfo.offset + fraction * visibleInfo.size

                val lowerThreshold = viewportHeight * 0.75f
                if (activeTopPx > lowerThreshold) {
                    val targetPx = viewportHeight * 0.35f
                    listState.animateScrollBy(activeTopPx - targetPx)
                }
            }
    }

    androidx.compose.runtime.CompositionLocalProvider(LocalTextToolbar provides selectionToolbarController) {
        Scaffold(
            containerColor = palette.background,
            topBar = {
                Column {
                    TopAppBar(
                        title = {
                            Column(Modifier.fillMaxWidth()) {
                                Text(
                                    state.textDoc?.title ?: stringResource(R.string.reading_loading_title),
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                if (spokenProgress.second > 0) {
                                    Text(
                                        stringResource(
                                            R.string.reading_position,
                                            spokenProgress.first.coerceAtLeast(1),
                                            spokenProgress.second
                                        ),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = palette.inkFaded
                                    )
                                }
                            }
                        },
                        navigationIcon = {
                            IconButton(onClick = onBack) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.accessibility_back))
                            }
                        },
                        actions = {
                            if (headings.size >= 2) {
                                IconButton(onClick = { showContentsSheet = true }) {
                                    Icon(
                                        Icons.Default.Toc,
                                        contentDescription = stringResource(R.string.reading_contents)
                                    )
                                }
                            }
                            IconButton(onClick = { vm.toggleShowTranslations() }) {
                                Icon(
                                    Icons.Default.Translate,
                                    contentDescription = stringResource(R.string.accessibility_toggle_translation),
                                    tint = if (state.showTranslations) palette.accent else palette.inkFaded
                                )
                            }
                            Box {
                                IconButton(onClick = { moreMenuExpanded = true }) {
                                    Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.reading_more_actions))
                                }
                                DropdownMenu(
                                    expanded = moreMenuExpanded,
                                    onDismissRequest = { moreMenuExpanded = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.accessibility_vocabulary)) },
                                        leadingIcon = { Icon(Icons.Default.MenuBook, contentDescription = null) },
                                        modifier = Modifier.semantics { contentDescription = vocabularyDescription },
                                        onClick = { moreMenuExpanded = false; onOpenVocab() }
                                    )
                                    Row(
                                        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(stringResource(R.string.reading_font_scale_title), Modifier.weight(1f))
                                        val scales = FontScale.entries
                                        val index = scales.indexOf(AppearanceState.fontScale)
                                        fun setScale(scale: FontScale) {
                                            AppearanceState.fontScale = scale
                                            AppearancePrefs.setFontScale(settingsContext, scale)
                                        }
                                        IconButton(
                                            onClick = { setScale(scales[index - 1]) },
                                            enabled = index > 0
                                        ) { Text("A−", style = MaterialTheme.typography.bodyMedium) }
                                        IconButton(
                                            onClick = { setScale(scales[index + 1]) },
                                            enabled = index < scales.lastIndex
                                        ) { Text("A+", style = MaterialTheme.typography.titleMedium) }
                                    }
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.accessibility_select_voice)) },
                                        leadingIcon = { Icon(Icons.Default.RecordVoiceOver, contentDescription = null) },
                                        modifier = Modifier.semantics { contentDescription = voiceDescription },
                                        onClick = { moreMenuExpanded = false; voiceMenuExpanded = true }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.accessibility_autoplay)) },
                                        leadingIcon = {
                                            Icon(
                                                if (autoScrollEnabled) Icons.Default.Check else Icons.Default.SwapVert,
                                                contentDescription = null,
                                                tint = if (autoScrollEnabled) palette.accent else LocalContentColor.current
                                            )
                                        },
                                        modifier = Modifier.semantics { contentDescription = autoScrollDescription },
                                        onClick = { moreMenuExpanded = false; autoScrollEnabled = !autoScrollEnabled }
                                    )
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                stringResource(
                                                    if (state.textDoc?.pinned == true) {
                                                        R.string.settings_unpin_audio
                                                    } else {
                                                        R.string.settings_pin_audio
                                                    }
                                                )
                                            )
                                        },
                                        leadingIcon = {
                                            Box(Modifier.size(24.dp)) {
                                                Icon(Icons.Default.PushPin, contentDescription = null)
                                                if (state.textDoc?.pinned == true) {
                                                    Icon(
                                                        Icons.Default.Check,
                                                        contentDescription = null,
                                                        tint = palette.accent,
                                                        modifier = Modifier.size(12.dp).align(Alignment.BottomEnd)
                                                    )
                                                }
                                            }
                                        },
                                        onClick = { moreMenuExpanded = false; vm.togglePinned() }
                                    )
                                    if (state.textDoc?.voice?.startsWith(XTTS_VOICE_PREFIX) == true) {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.settings_synthesize_full_document)) },
                                            leadingIcon = { Icon(Icons.Default.CloudDownload, contentDescription = null) },
                                            enabled = state.fullSynthesisTotal == 0,
                                            onClick = {
                                                moreMenuExpanded = false
                                                vm.synthesizeFullDocument()
                                            }
                                        )
                                    }
                                    if (state.textDoc?.sourceUrl != null) {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.accessibility_source_info)) },
                                            leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) },
                                            modifier = Modifier.semantics { contentDescription = sourceInfoDescription },
                                            onClick = { moreMenuExpanded = false; showSourceInfoSheet = true }
                                        )
                                    }
                                }
                                DropdownMenu(
                                    expanded = voiceMenuExpanded,
                                    onDismissRequest = { voiceMenuExpanded = false }
                                ) {
                                    AVAILABLE_VOICES.forEach { voice ->
                                        DropdownMenuItem(
                                            text = { Text(stringResource(voice.labelRes)) },
                                            leadingIcon = { if (state.textDoc?.voice == voice.id) Icon(Icons.Default.Check, null) },
                                            onClick = { voiceMenuExpanded = false; vm.changeVoice(voice.id) }
                                        )
                                    }
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = palette.background,
                            titleContentColor = palette.ink,
                            navigationIconContentColor = palette.ink,
                            actionIconContentColor = palette.ink
                        )
                    )
                    if (spokenProgress.second > 0) {
                        Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)) {
                            LinearProgressIndicator(
                                progress = { spokenProgress.first.toFloat() / spokenProgress.second },
                                modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                                color = palette.accent,
                                trackColor = palette.divider
                            )
                        }
                    }
                    if (state.fullSynthesisTotal > 0) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            LinearProgressIndicator(
                                progress = {
                                    state.fullSynthesisDone.toFloat() /
                                        state.fullSynthesisTotal.coerceAtLeast(1).toFloat()
                                },
                                modifier = Modifier.weight(1f),
                                color = palette.accent,
                                trackColor = palette.divider
                            )
                            Text(
                                "${state.fullSynthesisDone} / ${state.fullSynthesisTotal}",
                                modifier = Modifier.padding(start = 12.dp),
                                style = MaterialTheme.typography.labelMedium
                            )
                            IconButton(onClick = vm::cancelFullSynthesis) {
                                Icon(Icons.Default.Close, contentDescription = null)
                            }
                        }
                    }
                }
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = { PlaybackControls(vm, state, palette) }
        ) { padding ->
            if (state.chunks.isEmpty()) {
                Box(
                    Modifier.fillMaxSize().padding(padding).padding(bottom = 48.dp),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    CircularProgressIndicator(color = palette.accent)
                }
                return@Scaffold
            }

            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxHeight()
                        .widthIn(max = FrenchReaderDesign.sizes.readerMeasure)
                        .fillMaxWidth()
                        .padding(horizontal = FrenchReaderDesign.spacing.medium),
                    contentPadding = PaddingValues(vertical = 20.dp)
                ) {
                itemsIndexed(state.chunks, key = { index, _ -> index }) { chunkIndex, chunk ->
                    ChunkParagraph(
                        chunk = chunk,
                        isCurrentChunk = chunkIndex == state.currentChunkIndex,
                        currentPositionMs = if (chunkIndex == state.currentChunkIndex) state.currentPositionMs else 0L,
                        showTranslation = state.showTranslations,
                        savedVocabStatuses = savedVocabStatuses,
                        highlights = highlights,
                        documentStartOffset = chunkDocumentOffsets.getOrElse(chunkIndex) { 0 },
                        palette = palette,
                        fontScale = fontScale,
                        onSentenceClick = { sentence -> vm.seekToSentence(chunkIndex, sentence) },
                        onPendingClick = { vm.jumpToChunk(chunkIndex) },
                        onRetry = { vm.retryChunk(chunkIndex) },
                        onWordLookup = { word, sentenceText, range ->
                            selectedWord = word to sentenceText
                            selectedWordRange = range
                            selectedPhrase = null
                            selectedPhraseRange = null
                            showHighlightPalette = false
                        },
                        onPhraseSelected = { phrase, sentenceText, range ->
                            selectedPhrase = phrase
                            selectedPhraseSentence = sentenceText
                            selectedPhraseRange = range
                            selectedWord = null
                            selectedWordRange = null
                            highlightPopupTarget = null
                            highlightPopupRect = null
                        },
                        onHighlightClick = { highlight, rect ->
                            selectedPhrase = null
                            selectedPhraseRange = null
                            selectedWord = null
                            selectedWordRange = null
                            highlightPopupTarget = highlight
                            highlightPopupRect = rect
                            changeHighlightColor = false
                        },
                        clearSelectionSignal = clearSelectionTick
                    )
                    val spacing = when {
                        chunk.block.type == BlockType.IMAGE -> FrenchReaderDesign.spacing.small
                        chunk.block.type == BlockType.LIST_ITEM &&
                            state.chunks.getOrNull(chunkIndex + 1)?.block?.type == BlockType.LIST_ITEM -> 6.dp
                        chunk.block.type == BlockType.HEADER -> FrenchReaderDesign.spacing.large
                        else -> FrenchReaderDesign.spacing.medium
                    }
                    Spacer(Modifier.height(spacing))
                }
            }
            }
        }
    }

    dictionaryTarget?.let { (word, sentence) ->
        DictionarySheet(
            textId = textId,
            word = word,
            sentence = sentence,
            onDismiss = { dictionaryTarget = null }
        )
    }

    val toolbarRect = selectionToolbarController.rect
    if (selectedPhrase != null && toolbarRect != null &&
        selectionToolbarController.status == TextToolbarStatus.Shown
    ) {
        val phrase = selectedPhrase!!
        val selectedHighlightIds = selectedPhraseRange
            ?.let { overlappingHighlightIds(it, highlights) }
            .orEmpty()
        Popup(
            popupPositionProvider = SelectionRectPositionProvider(
                rect = toolbarRect,
                marginPx = with(density) { 8.dp.toPx() }
            ),
            // Dragging a selection handle is an "outside" touch for the popup; dismissing on it
            // would clear the selection mid-adjustment. Tapping elsewhere collapses the
            // selection, which hides the toolbar on its own.
            properties = androidx.compose.ui.window.PopupProperties(dismissOnClickOutside = false),
            onDismissRequest = {
                vm.stopSelectionPlayback()
                selectedPhrase = null
                selectedPhraseRange = null
                showHighlightPalette = false
                processTextTarget = null
                clearSelectionTick++
            }
        ) {
            // Swaps in place between the main actions and the ACTION_PROCESS_TEXT
            // app list -- like Android's own selection toolbar, tapping the
            // overflow (More) replaces this popup's content rather than opening a
            // separate one, and Back restores the actions without losing the
            // still-highlighted selection (clearSelectionTick isn't touched here).
            if (showHighlightPalette) {
                HighlightPalettePopupContent(
                    onColorSelected = { colorKey ->
                        selectedPhraseRange?.let { range ->
                            vm.addHighlight(range.min, range.max, colorKey)
                        }
                        HighlightPrefs.setLastColorKey(settingsContext, colorKey)
                        lastHighlightColorKey = colorKey
                        showHighlightPalette = false
                        selectedPhrase = null
                        selectedPhraseRange = null
                        clearSelectionTick++
                    },
                    onBack = { showHighlightPalette = false }
                )
            } else if (processTextTarget != null) {
                val text = processTextTarget!!
                val context = LocalContext.current
                val apps = remember(text) { queryProcessTextApps(context) }
                ProcessTextAppsPopupContent(
                    apps = apps,
                    onAppSelected = { app ->
                        launchProcessTextApp(context, app, text)
                        selectedPhrase = null
                        selectedPhraseRange = null
                        processTextTarget = null
                        clearSelectionTick++
                    },
                    onBack = { processTextTarget = null }
                )
            } else {
                SelectionToolbarContent(
                    onCopy = {
                        // Written directly instead of via the framework's own
                        // onCopyRequested callback -- that callback copies whatever the
                        // *raw* (un-snapped) selection currently is, which can differ
                        // from `phrase` (already snapped to whole word boundaries by
                        // classifySelection) and would silently overwrite it.
                        clipboard.setText(androidx.compose.ui.text.AnnotatedString(phrase))
                        selectedPhrase = null
                        selectedPhraseRange = null
                        clearSelectionTick++
                    },
                    onSave = {
                        vm.player.pause()
                        dictionaryTarget = phrase to selectedPhraseSentence
                        selectedPhrase = null
                        selectedPhraseRange = null
                        clearSelectionTick++
                    },
                    onListen = {
                        vm.player.pause()
                        vm.playSelection(phrase)
                        selectedPhrase = null
                        selectedPhraseRange = null
                        clearSelectionTick++
                    },
                    onHighlight = {
                        selectedPhraseRange?.let { range ->
                            vm.addHighlight(range.min, range.max, lastHighlightColorKey)
                        }
                        selectedPhrase = null
                        selectedPhraseRange = null
                        clearSelectionTick++
                    },
                    onChooseHighlightColor = { showHighlightPalette = true },
                    onDeleteHighlight = selectedHighlightIds.takeIf { it.isNotEmpty() }?.let { ids ->
                        {
                            vm.deleteHighlights(ids)
                            selectedPhrase = null
                            selectedPhraseRange = null
                            clearSelectionTick++
                        }
                    },
                    onMore = { processTextTarget = phrase }
                )
            }
        }
    }

    val existingHighlight = highlightPopupTarget
    val existingHighlightRect = highlightPopupRect
    if (existingHighlight != null && existingHighlightRect != null) {
        Popup(
            popupPositionProvider = SelectionRectPositionProvider(
                rect = existingHighlightRect,
                marginPx = with(density) { 8.dp.toPx() }
            ),
            onDismissRequest = {
                highlightPopupTarget = null
                highlightPopupRect = null
                changeHighlightColor = false
            }
        ) {
            if (changeHighlightColor) {
                HighlightPalettePopupContent(
                    onColorSelected = { colorKey ->
                        vm.updateHighlightColor(existingHighlight.id, colorKey)
                        HighlightPrefs.setLastColorKey(settingsContext, colorKey)
                        lastHighlightColorKey = colorKey
                        highlightPopupTarget = null
                        highlightPopupRect = null
                        changeHighlightColor = false
                    },
                    onBack = { changeHighlightColor = false }
                )
            } else {
                HighlightActionsPopupContent(
                    onChangeColor = { changeHighlightColor = true },
                    onDelete = {
                        vm.deleteHighlight(existingHighlight.id)
                        highlightPopupTarget = null
                        highlightPopupRect = null
                    }
                )
            }
        }
    }

    if (selectedWord != null && toolbarRect != null &&
        selectionToolbarController.status == TextToolbarStatus.Shown
    ) {
        val (word, sentence) = selectedWord!!
        val selectedHighlightIds = selectedWordRange
            ?.let { overlappingHighlightIds(it, highlights) }
            .orEmpty()
        Popup(
            popupPositionProvider = SelectionRectPositionProvider(
                rect = toolbarRect,
                marginPx = with(density) { 8.dp.toPx() }
            ),
            // Dragging a selection handle is an "outside" touch for the popup; dismissing on it
            // would clear the selection mid-adjustment. Tapping elsewhere collapses the
            // selection, which hides the toolbar on its own.
            properties = androidx.compose.ui.window.PopupProperties(dismissOnClickOutside = false),
            onDismissRequest = {
                selectedWord = null
                selectedWordRange = null
                processTextTarget = null
                clearSelectionTick++
            }
        ) {
            if (showHighlightPalette) {
                HighlightPalettePopupContent(
                    onColorSelected = { colorKey ->
                        selectedWordRange?.let { range ->
                            vm.addHighlight(range.min, range.max, colorKey)
                        }
                        HighlightPrefs.setLastColorKey(settingsContext, colorKey)
                        lastHighlightColorKey = colorKey
                        showHighlightPalette = false
                        selectedWord = null
                        selectedWordRange = null
                        clearSelectionTick++
                    },
                    onBack = { showHighlightPalette = false }
                )
            } else if (processTextTarget != null) {
                val text = processTextTarget!!
                val context = LocalContext.current
                val apps = remember(text) { queryProcessTextApps(context) }
                ProcessTextAppsPopupContent(
                    apps = apps,
                    onAppSelected = { app ->
                        launchProcessTextApp(context, app, text)
                        selectedWord = null
                        selectedWordRange = null
                        processTextTarget = null
                        clearSelectionTick++
                    },
                    onBack = { processTextTarget = null }
                )
            } else {
                DefineToolbarContent(
                    onDefine = {
                        vm.player.pause()
                        dictionaryTarget = word to sentence
                        selectedWord = null
                        selectedWordRange = null
                        clearSelectionTick++
                    },
                    onHighlight = {
                        selectedWordRange?.let { range ->
                            vm.addHighlight(range.min, range.max, lastHighlightColorKey)
                        }
                        selectedWord = null
                        selectedWordRange = null
                        clearSelectionTick++
                    },
                    onChooseHighlightColor = { showHighlightPalette = true },
                    onDeleteHighlight = selectedHighlightIds.takeIf { it.isNotEmpty() }?.let { ids ->
                        {
                            vm.deleteHighlights(ids)
                            selectedWord = null
                            selectedWordRange = null
                            clearSelectionTick++
                        }
                    },
                    onMore = { processTextTarget = word }
                )
            }
        }
    }

    if (showSourceInfoSheet) {
        state.textDoc?.let { doc ->
            SourceInfoSheet(doc = doc, palette = palette, onDismiss = { showSourceInfoSheet = false })
        }
    }

    if (showContentsSheet) {
        ContentsSheet(
            headings = headings,
            currentChunkIndex = state.currentChunkIndex,
            totalChunks = state.chunks.size,
            palette = palette,
            onSelect = { index ->
                vm.jumpToChunk(index)
                showContentsSheet = false
                scope.launch { listState.scrollToItem(index) }
            },
            onDismiss = { showContentsSheet = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ContentsSheet(
    headings: List<Pair<Int, ChunkState>>,
    currentChunkIndex: Int,
    totalChunks: Int,
    palette: ReadingPalette,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val currentHeadingIndex = headings.indexOfLast { it.first <= currentChunkIndex }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = palette.background, contentColor = palette.ink) {
        Text(
            stringResource(R.string.reading_contents),
            style = FrenchReaderDesign.editorialTypography.sectionTitle,
            modifier = Modifier.padding(horizontal = FrenchReaderDesign.spacing.medium, vertical = FrenchReaderDesign.spacing.xSmall)
        )
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            itemsIndexed(headings, key = { _, entry -> entry.first }) { headingIndex, (index, chunk) ->
                val isCurrent = headingIndex == currentHeadingIndex
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (isCurrent) palette.accent.copy(alpha = 0.14f) else Color.Transparent)
                        .clickable { onSelect(index) }
                        .padding(
                            start = if (chunk.block.headerLevel == 2) FrenchReaderDesign.spacing.large else FrenchReaderDesign.spacing.medium,
                            end = FrenchReaderDesign.spacing.medium,
                            top = 12.dp,
                            bottom = 12.dp
                        ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(chunk.text, fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Medium)
                        Text(
                            stringResource(R.string.reading_position, index + 1, totalChunks),
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.inkFaded
                        )
                    }
                }
                if (headingIndex != headings.lastIndex) HorizontalDivider(color = palette.divider)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SourceInfoSheet(
    doc: com.ziaee.frenchreader.data.TextDocument,
    palette: ReadingPalette,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = palette.background, contentColor = palette.ink) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = FrenchReaderDesign.spacing.medium).padding(bottom = FrenchReaderDesign.spacing.large)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    doc.sourceName ?: stringResource(R.string.source_default_label),
                    style = FrenchReaderDesign.editorialTypography.sectionTitle,
                    modifier = Modifier.weight(1f)
                )
                doc.sourceUrl?.let { url ->
                    IconButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }) {
                        Icon(Icons.Default.OpenInBrowser, contentDescription = stringResource(R.string.accessibility_open_in_browser))
                    }
                }
            }
            HorizontalDivider(Modifier.padding(vertical = FrenchReaderDesign.spacing.small), color = palette.divider)
            doc.author?.let { Text(stringResource(R.string.source_author_label, it), style = MaterialTheme.typography.bodyLarge) }
            doc.license?.let { Text(stringResource(R.string.source_license_label, it), style = MaterialTheme.typography.bodyLarge) }
            doc.publishedAt?.let {
                val dateLabel = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(it))
                Text(stringResource(R.string.source_published_label, dateLabel), style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
private fun ChunkParagraph(
    chunk: ChunkState,
    isCurrentChunk: Boolean,
    currentPositionMs: Long,
    showTranslation: Boolean,
    savedVocabStatuses: Map<String, VocabStatus>,
    highlights: List<HighlightEntry>,
    documentStartOffset: Int,
    palette: ReadingPalette,
    fontScale: Float,
    onSentenceClick: (SentenceBoundary) -> Unit,
    onPendingClick: () -> Unit,
    onRetry: () -> Unit,
    onWordLookup: (word: String, sentence: String, range: TextRange) -> Unit,
    onPhraseSelected: (phrase: String, sentence: String, range: TextRange) -> Unit,
    onHighlightClick: (HighlightEntry, androidx.compose.ui.geometry.Rect) -> Unit,
    clearSelectionSignal: Int
) {
    // Not-yet-synthesized chunks have no sentence timings, but must still render with
    // their Markdown structure (headings, lists, emphasis) -- so show the whole block as
    // one untimed sentence through the same path as READY chunks.
    val unsynthesized = chunk.status == ChunkStatus.PENDING || chunk.status == ChunkStatus.LOADING
    val chunk = if (unsynthesized && chunk.sentences.isEmpty()) {
        chunk.copy(sentences = listOf(SentenceBoundary(text = chunk.text, offsetMs = 0.0, durationMs = 0.0)))
    } else chunk
    val onSentenceClick: (SentenceBoundary) -> Unit =
        if (unsynthesized) { _ -> onPendingClick() } else onSentenceClick

    if (chunk.block.type == BlockType.IMAGE) {
        EpubImage(block = chunk.block, palette = palette, fontScale = fontScale)
        return
    }

    when (chunk.status) {
        ChunkStatus.READY, ChunkStatus.PENDING, ChunkStatus.LOADING -> {
            // The French text must always read left-to-right regardless of
            // the surrounding Persian UI's layout direction.
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Column {
                    when (chunk.block.type) {
                        BlockType.HEADER -> Column {
                            Spacer(Modifier.height(if (chunk.block.headerLevel == 1) 12.dp else 4.dp))
                            SentenceFlowText(
                                chunk = chunk,
                                isCurrentChunk = isCurrentChunk,
                                currentPositionMs = currentPositionMs,
                                fontSize = headerFontSize(chunk.block.headerLevel, fontScale),
                                lineHeight = headerLineHeight(chunk.block.headerLevel, fontScale),
                                fontWeight = FontWeight.SemiBold,
                                color = palette.ink,
                                palette = palette,
                                savedVocabStatuses = savedVocabStatuses,
                                highlights = highlights,
                                documentStartOffset = documentStartOffset,
                                onSentenceClick = onSentenceClick,
                                onWordLookup = onWordLookup,
                                onPhraseSelected = onPhraseSelected,
                                onHighlightClick = onHighlightClick,
                                clearSelectionSignal = clearSelectionSignal
                            )
                            if (chunk.block.headerLevel == 1) {
                                Box(
                                    Modifier.padding(top = 12.dp).width(48.dp).height(2.dp)
                                        .clip(RoundedCornerShape(1.dp)).background(palette.accent)
                                )
                            }
                        }
                        BlockType.LIST_ITEM -> Row {
                            Text(
                                "•  ",
                                fontSize = (19f * fontScale).sp,
                                lineHeight = (29f * fontScale).sp,
                                color = palette.accent
                            )
                            Box(modifier = Modifier.weight(1f)) {
                                SentenceFlowText(
                                    chunk = chunk,
                                    isCurrentChunk = isCurrentChunk,
                                    currentPositionMs = currentPositionMs,
                                    fontSize = (19f * fontScale).sp,
                                    lineHeight = (29f * fontScale).sp,
                                    fontWeight = null,
                                    color = palette.ink,
                                    palette = palette,
                                    savedVocabStatuses = savedVocabStatuses,
                                    highlights = highlights,
                                    documentStartOffset = documentStartOffset,
                                    onSentenceClick = onSentenceClick,
                                    onWordLookup = onWordLookup,
                                    onPhraseSelected = onPhraseSelected,
                                    onHighlightClick = onHighlightClick,
                                    clearSelectionSignal = clearSelectionSignal
                                )
                            }
                        }
                        BlockType.PARAGRAPH -> SentenceFlowText(
                            chunk = chunk,
                            isCurrentChunk = isCurrentChunk,
                            currentPositionMs = currentPositionMs,
                            fontSize = (19f * fontScale).sp,
                            lineHeight = (31f * fontScale).sp,
                            fontWeight = null,
                            color = palette.ink,
                            palette = palette,
                            savedVocabStatuses = savedVocabStatuses,
                            highlights = highlights,
                            documentStartOffset = documentStartOffset,
                            onSentenceClick = onSentenceClick,
                            onWordLookup = onWordLookup,
                            onPhraseSelected = onPhraseSelected,
                            onHighlightClick = onHighlightClick,
                            clearSelectionSignal = clearSelectionSignal
                        )
                        BlockType.IMAGE -> Unit
                    }

                    if (chunk.status == ChunkStatus.LOADING) {
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = palette.accent)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.audio_preparing), fontSize = (12f * fontScale).sp, color = palette.inkFaded)
                        }
                    }

                    if (showTranslation && chunk.block.type != BlockType.HEADER) {
                        Spacer(Modifier.height(6.dp))
                        when (chunk.translationStatus) {
                            ChunkStatus.READY -> chunk.translation?.let {
                                Text(
                                    it,
                                    fontSize = (14f * fontScale).sp,
                                    lineHeight = (21f * fontScale).sp,
                                    fontStyle = FontStyle.Italic,
                                    color = palette.inkFaded
                                )
                            }
                            ChunkStatus.LOADING -> Text(
                                stringResource(R.string.translation_loading),
                                fontSize = (13f * fontScale).sp,
                                fontStyle = FontStyle.Italic,
                                color = palette.inkFaded
                            )
                            ChunkStatus.ERROR -> Text(
                                stringResource(R.string.error_translation_unavailable),
                                fontSize = (13f * fontScale).sp,
                                fontStyle = FontStyle.Italic,
                                color = palette.inkFaded
                            )
                            ChunkStatus.PENDING -> {}
                        }
                    }
                }
            }
        }
        ChunkStatus.ERROR -> {
            Column {
                Text(
                    stringResource(R.string.error_audio_generation_detail, chunk.error.orEmpty()),
                    color = MaterialTheme.colorScheme.error,
                    fontSize = (13f * fontScale).sp
                )
                TextButton(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
            }
        }
    }
}

@Composable
private fun EpubImage(
    block: com.ziaee.frenchreader.text.ParsedBlock,
    palette: ReadingPalette,
    fontScale: Float
) {
    val context = LocalContext.current
    val ref = block.imageRef?.takeIf(EPUB_IMAGE_REF::matches) ?: return
    val file = remember(context.filesDir, ref) {
        File(context.filesDir, "text_images/epub_$ref").takeIf { it.isFile }
    } ?: return

    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = FrenchReaderDesign.spacing.xSmall),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AsyncImage(
            model = file,
            contentDescription = block.imageAlt.takeIf { it.isNotBlank() },
            contentScale = ContentScale.FillWidth,
            modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium)
                .border(1.dp, palette.divider, MaterialTheme.shapes.medium)
        )
        block.imageAlt.takeIf { it.isNotBlank() }?.let { alt ->
            Text(
                alt,
                modifier = Modifier.padding(top = FrenchReaderDesign.spacing.xSmall),
                fontSize = (13f * fontScale).sp,
                lineHeight = (19f * fontScale).sp,
                color = palette.inkFaded,
                textAlign = TextAlign.Center
            )
        }
    }
}

private fun headerFontSize(level: Int, fontScale: Float): TextUnit = (when (level) {
    1 -> 30f
    2 -> 25f
    3 -> 22f
    4 -> 20f
    5 -> 19f
    else -> 18f
} * fontScale).sp

private fun headerLineHeight(level: Int, fontScale: Float): TextUnit = (when (level) {
    1 -> 39f
    2 -> 33f
    3 -> 29f
    4 -> 27f
    5 -> 26f
    else -> 25f
} * fontScale).sp

/**
 * The shared "flowing, sentence-highlighted, tappable, long-pressable" text
 * renderer used for every block type (paragraph, heading, list item). The
 * displayed string is built by concatenating [ChunkState.sentences] (edge-
 * tts's own SentenceBoundary text, in order) -- exactly as the original
 * Phase 1 renderer did -- so playback-time highlighting keeps working
 * unchanged. Bold/italic spans from Markdown are then re-applied on top by
 * searching for their exact phrase text rather than trusting character
 * offsets, since edge-tts's sentence text can differ very slightly
 * (whitespace/quote normalization) from the Markdown-stripped source: a
 * missed search match is silently skipped, never mis-applied to the wrong
 * text.
 *
 * A single tap seeks/continues playback from the tapped sentence (Phase 1
 * behaviour, unchanged). A long-press on a word instead opens the
 * dictionary for that word -- the two never fire for the same gesture, so
 * they cannot conflict per the design doc's acceptance criterion.
 */
@Composable
private fun SentenceFlowText(
    chunk: ChunkState,
    isCurrentChunk: Boolean,
    currentPositionMs: Long,
    fontSize: TextUnit,
    lineHeight: TextUnit,
    fontWeight: FontWeight?,
    color: Color,
    palette: ReadingPalette,
    savedVocabStatuses: Map<String, VocabStatus>,
    highlights: List<HighlightEntry>,
    documentStartOffset: Int,
    onSentenceClick: (SentenceBoundary) -> Unit,
    onWordLookup: (word: String, sentence: String, range: TextRange) -> Unit,
    onPhraseSelected: (phrase: String, sentence: String, range: TextRange) -> Unit,
    onHighlightClick: (HighlightEntry, androidx.compose.ui.geometry.Rect) -> Unit,
    clearSelectionSignal: Int
) {
    val vocabHighlighter = remember(savedVocabStatuses) {
        VocabHighlighter(savedVocabStatuses.map { SavedVocab(it.key, it.value) })
    }
    // Pick colors for the reading page's own background, not the system
    // theme: a light sepia page can sit under a dark system theme.
    val darkPage = palette.ink.luminance() > 0.5f
    val vocabStyles = VocabStatus.entries.associateWith { status ->
        SpanStyle(
            // Keep the page's high-contrast ink over the status tint. The
            // status hue remains visible in the background and underline.
            color = palette.ink,
            background = status.containerColor(darkPage).copy(
                alpha = if (status == VocabStatus.LEARNED) 0.12f else 0.7f
            ),
            textDecoration = if (status == VocabStatus.LEARNED) null else TextDecoration.Underline
        )
    }
    val ranges = remember(chunk.sentences) { mutableListOf<IntRange>() }
    val activeRange = remember(chunk.sentences, isCurrentChunk, currentPositionMs) {
        if (!isCurrentChunk) null else {
            val activeIndex = chunk.sentences.indexOfFirst { sentence ->
                currentPositionMs >= sentence.offsetMs &&
                    currentPositionMs < sentence.offsetMs + sentence.durationMs
            }
            if (activeIndex < 0) null else {
                val start = chunk.sentences.take(activeIndex).sumOf { it.text.length + 1 }
                start until (start + chunk.sentences[activeIndex].text.length)
            }
        }
    }
    val annotated = remember(
        chunk.sentences,
        chunk.block,
        isCurrentChunk,
        currentPositionMs,
        palette,
        savedVocabStatuses,
        vocabStyles,
        highlights,
        documentStartOffset
    ) {
        buildAnnotatedString {
            ranges.clear()
            chunk.sentences.forEachIndexed { i, s ->
                val start = length
                append(s.text)
                val end = length
                ranges.add(start until end)
                if (i != chunk.sentences.lastIndex) append(" ")
            }

            // Builder.toString() doesn't return the text built so far, so
            // derive it from the same sentences that were appended above.
            val full = chunk.sentences.joinToString(" ") { it.text }
            if (savedVocabStatuses.isNotEmpty()) {
                for (match in vocabHighlighter.findMatches(full)) {
                    val style = vocabStyles.getValue(match.status)
                    addStyle(style, match.range.first, match.range.last + 1)
                }
            }
            // Manual highlights are a separate, offset-based layer. Apply
            // their background after vocabulary styles so a saved-word tint
            // cannot hide the color the reader explicitly chose; the vocab
            // underline and foreground color remain intact.
            val localHighlights = highlights.mapNotNull { highlight ->
                val start = maxOf(highlight.startOffset, documentStartOffset) - documentStartOffset
                val end = minOf(highlight.endOffset, documentStartOffset + full.length) - documentStartOffset
                if (start < end) highlight.copy(startOffset = start, endOffset = end) else null
            }
            applyHighlights(full, localHighlights).spanStyles.forEach { span ->
                addStyle(span.item, span.start, span.end)
            }
            chunk.sentences.forEachIndexed { index, sentence ->
                val isActive = isCurrentChunk &&
                    currentPositionMs >= sentence.offsetMs &&
                    currentPositionMs < sentence.offsetMs + sentence.durationMs
                if (isActive) {
                    val range = ranges[index]
                    addStyle(
                        SpanStyle(color = palette.highlightInk),
                        range.first,
                        range.last + 1
                    )
                }
            }
            for (span in chunk.block.emphasisSpans) {
                if (span.start < 0 || span.end > chunk.block.plainText.length || span.start >= span.end) continue
                val phrase = chunk.block.plainText.substring(span.start, span.end)
                if (phrase.isBlank()) continue
                val idx = full.indexOf(phrase)
                if (idx >= 0) {
                    addStyle(
                        SpanStyle(
                            fontWeight = if (span.bold) FontWeight.Bold else null,
                            fontStyle = if (span.italic) FontStyle.Italic else null
                        ),
                        idx, idx + phrase.length
                    )
                }
            }
        }
    }

    var selection by remember(chunk.sentences, clearSelectionSignal) { mutableStateOf(TextRange.Zero) }
    var textLayout by remember { mutableStateOf<TextLayoutResult?>(null) }
    var layoutCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val headingFontFamily = FrenchReaderDesign.editorialTypography.articleHeadline.fontFamily

    fun sentenceTextFor(offset: Int): String {
        val idx = ranges.indexOfFirst { offset in it }
        return if (idx >= 0) chunk.sentences[idx].text else annotated.text
    }

    androidx.compose.runtime.CompositionLocalProvider(
        androidx.compose.foundation.text.selection.LocalTextSelectionColors provides
            androidx.compose.foundation.text.selection.TextSelectionColors(
                handleColor = palette.selectionHandle,
                backgroundColor = palette.selectionBg
            )
    ) {
        BasicTextField(
            value = TextFieldValue(annotatedString = annotated, selection = selection),
            onValueChange = { newValue ->
                if (newValue.selection.collapsed) {
                    val localOffset = newValue.selection.start.coerceIn(0, annotated.length)
                    val documentOffset = documentStartOffset + localOffset
                    val tappedHighlight = highlights.lastOrNull {
                        documentOffset >= it.startOffset && documentOffset < it.endOffset
                    }
                    if (tappedHighlight != null) {
                        val layout = textLayout
                        val coordinates = layoutCoordinates
                        if (layout != null && coordinates != null && annotated.isNotEmpty()) {
                            val box = layout.getBoundingBox(localOffset.coerceAtMost(annotated.lastIndex))
                            val topLeft = coordinates.localToWindow(box.topLeft)
                            val bottomRight = coordinates.localToWindow(box.bottomRight)
                            onHighlightClick(
                                tappedHighlight,
                                androidx.compose.ui.geometry.Rect(topLeft, bottomRight)
                            )
                        }
                        selection = TextRange.Zero
                    } else {
                        val idx = ranges.indexOfFirst { newValue.selection.start in it }
                        if (idx >= 0) onSentenceClick(chunk.sentences[idx])
                        selection = TextRange.Zero
                    }
                } else {
                    selection = newValue.selection
                    when (val kind = classifySelection(annotated.text, newValue.selection)) {
                        is SelectionKind.Word ->
                            onWordLookup(
                                kind.word,
                                sentenceTextFor(newValue.selection.start),
                                TextRange(
                                    documentStartOffset + kind.range.min,
                                    documentStartOffset + kind.range.max
                                )
                            )
                        is SelectionKind.Phrase ->
                            onPhraseSelected(
                                kind.text,
                                sentenceTextFor(newValue.selection.start),
                                TextRange(
                                    documentStartOffset + kind.range.min,
                                    documentStartOffset + kind.range.max
                                )
                            )
                        null -> selection = TextRange.Zero
                    }
                }
            },
            readOnly = true,
            modifier = Modifier.onGloballyPositioned { layoutCoordinates = it }.drawBehind {
                val layout = textLayout ?: return@drawBehind
                val range = activeRange ?: return@drawBehind
                if (range.isEmpty()) return@drawBehind
                val firstLine = layout.getLineForOffset(range.first)
                val lastLine = layout.getLineForOffset(range.last)
                for (line in firstLine..lastLine) {
                    val lineStart = maxOf(range.first, layout.getLineStart(line))
                    val visibleLineEnd = layout.getLineEnd(line, visibleEnd = true)
                    val lineEnd = minOf(range.last + 1, visibleLineEnd)
                    if (lineStart >= lineEnd) continue
                    // Justified/hyphenated lines: a line-end offset resolves to the next line's
                    // start, so use per-character boxes and the line's own edges instead.
                    val left = if (lineStart == layout.getLineStart(line)) layout.getLineLeft(line)
                        else layout.getBoundingBox(lineStart).left
                    val right = if (lineEnd == visibleLineEnd) layout.getLineRight(line)
                        else layout.getBoundingBox(lineEnd - 1).right
                    drawRoundRect(
                        color = palette.highlightBg,
                        topLeft = Offset(minOf(left, right) - 3.dp.toPx(), layout.getLineTop(line) + 1.dp.toPx()),
                        size = Size(kotlin.math.abs(right - left) + 6.dp.toPx(), layout.getLineBottom(line) - layout.getLineTop(line) - 2.dp.toPx()),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(6.dp.toPx())
                    )
                }
            },
            onTextLayout = { textLayout = it },
            textStyle = TextStyle(
                fontSize = fontSize,
                lineHeight = lineHeight,
                letterSpacing = 0.1.sp,
                color = color,
                fontWeight = fontWeight,
                fontFamily = if (fontWeight != null) headingFontFamily else null,
                // Body paragraphs are justified like a printed page; French hyphenation keeps
                // justified lines from opening wide gaps. Headings stay start-aligned.
                textAlign = if (fontWeight != null) TextAlign.Start else TextAlign.Justify,
                hyphens = if (fontWeight != null) Hyphens.None else Hyphens.Auto,
                lineBreak = LineBreak.Paragraph,
                localeList = LocaleList("fr")
            ),
            cursorBrush = SolidColor(Color.Transparent)
        )
    }
}

/** Word/selection boundary rules shared by the dictionary long-press lookup and the
 * multi-word selection classifier below. Apostrophes are treated as boundaries (not word
 * characters) so "l'appartement" isolates "appartement", not the elided article with it --
 * WordReference wouldn't recognize the elided form. */
private fun isWordChar(c: Char) = c.isLetter() || c == '-'

/** Returns the [start, end) range of the word touching [offset] in [text], or null if
 * [offset] doesn't touch a word character. */
internal fun wordBoundsAt(text: String, offset: Int): IntRange? {
    if (text.isEmpty()) return null
    var pos = offset.coerceIn(0, text.length)
    if ((pos >= text.length || !isWordChar(text[pos])) && pos > 0 && isWordChar(text[pos - 1])) {
        pos -= 1
    }
    if (pos >= text.length || !isWordChar(text[pos])) return null

    var start = pos
    var end = pos + 1
    while (start > 0 && isWordChar(text[start - 1])) start--
    while (end < text.length && isWordChar(text[end])) end++
    return start until end
}

/** Extracts the word touching [offset] in [text] for the dictionary long-press lookup. */
internal fun extractWordAt(text: String, offset: Int): String {
    val bounds = wordBoundsAt(text, offset) ?: return ""
    return text.substring(bounds.first, bounds.last + 1)
}

/** What a (non-collapsed) text-field selection resolves to once snapped to word
 * boundaries: exactly one word (dictionary lookup), or a multi-word phrase (selection
 * toolbar). */
internal sealed interface SelectionKind {
    data class Word(val word: String, val range: TextRange) : SelectionKind
    data class Phrase(val text: String, val range: TextRange) : SelectionKind
}

/** Classifies a raw text-field [selection] against [text]'s word boundaries. Both edges of
 * the raw selection are snapped outward to the word they touch (so a drag that stops
 * mid-word still selects that whole word), using the same rules as [wordBoundsAt] --
 * independent of how the platform's own long-press/double-tap decided the initial
 * selection. Returns null for a collapsed selection or one that touches no word
 * characters at either edge.
 *
 * A snapped span containing no whitespace resolves to [SelectionKind.Word] using only the
 * word touching the *end* of the selection -- this is what makes a raw selection spanning
 * "l'appartement" (whether from an OS word-break that keeps the elision attached, or a
 * short drag) resolve to "appartement", matching [extractWordAt]'s established behavior,
 * instead of being treated as a two-word phrase. */
internal fun classifySelection(text: String, selection: androidx.compose.ui.text.TextRange): SelectionKind? {
    if (selection.collapsed) return null
    val rawStart = selection.min.coerceIn(0, text.length)
    val rawEnd = selection.max.coerceIn(0, text.length)
    if (rawStart >= rawEnd) return null

    val startBounds = wordBoundsAt(text, rawStart)
    val endBounds = wordBoundsAt(text, rawEnd - 1)
    if (startBounds == null && endBounds == null) return null

    val start = startBounds?.first ?: rawStart
    val end = (endBounds?.last ?: (rawEnd - 1)) + 1
    if (start >= end) return null

    val snapped = text.substring(start, end)
    if (snapped.isBlank()) return null

    return if (snapped.none { it.isWhitespace() }) {
        val word = if (endBounds != null) text.substring(endBounds.first, endBounds.last + 1) else snapped
        val wordRange = endBounds?.let { TextRange(it.first, it.last + 1) } ?: TextRange(start, end)
        SelectionKind.Word(word, wordRange)
    } else {
        val trimmedStart = start + snapped.indexOfFirst { !it.isWhitespace() }.coerceAtLeast(0)
        val trimmedEnd = end - snapped.reversed().indexOfFirst { !it.isWhitespace() }.coerceAtLeast(0)
        SelectionKind.Phrase(snapped.trim(), TextRange(trimmedStart, trimmedEnd))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaybackControls(vm: ReadingViewModel, state: ReadingUiState, palette: ReadingPalette) {
    var speedMenuExpanded by remember { mutableStateOf(false) }
    val isSynthesizing = state.chunks.getOrNull(state.currentChunkIndex)?.status == ChunkStatus.LOADING

    Surface(
        color = palette.background,
        tonalElevation = FrenchReaderDesign.elevations.raised,
        shadowElevation = FrenchReaderDesign.elevations.raised,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Column(modifier = Modifier.padding(top = 4.dp, bottom = FrenchReaderDesign.spacing.xSmall)) {
            Box(
                Modifier.align(Alignment.CenterHorizontally).width(32.dp).height(3.dp)
                    .clip(RoundedCornerShape(2.dp)).background(palette.divider)
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp, start = 4.dp, end = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { vm.previousSentence() }, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Default.SkipPrevious, contentDescription = stringResource(R.string.accessibility_previous_sentence), modifier = Modifier.size(22.dp), tint = palette.ink.copy(alpha = 0.75f))
                }
                IconButton(onClick = { vm.skipMs(-10_000) }, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Default.Replay10, contentDescription = stringResource(R.string.accessibility_skip_back), modifier = Modifier.size(22.dp), tint = palette.ink.copy(alpha = 0.75f))
                }
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(FrenchReaderDesign.sizes.playerPrimaryControl + 4.dp)) {
                    FilledIconButton(
                        onClick = { vm.togglePlayPause() },
                        modifier = Modifier.size(FrenchReaderDesign.sizes.playerPrimaryControl),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = palette.accent,
                            disabledContainerColor = palette.accent.copy(alpha = 0.45f)
                        )
                    ) {
                        androidx.compose.animation.Crossfade(targetState = state.isPlaying, label = "playPause") { playing ->
                            Icon(
                                if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = stringResource(R.string.accessibility_play_pause),
                                modifier = Modifier.size(26.dp),
                                tint = Color.White
                            )
                        }
                    }
                    if (isSynthesizing) {
                        CircularProgressIndicator(
                            modifier = Modifier.fillMaxSize(),
                            color = palette.highlightInk,
                            trackColor = palette.accent.copy(alpha = 0.28f),
                            strokeWidth = 2.dp
                        )
                    }
                }
                IconButton(onClick = { vm.skipMs(10_000) }, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Default.Forward10, contentDescription = stringResource(R.string.accessibility_skip_forward), modifier = Modifier.size(22.dp), tint = palette.ink.copy(alpha = 0.75f))
                }
                IconButton(onClick = { vm.nextSentence() }, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Default.SkipNext, contentDescription = stringResource(R.string.accessibility_next_sentence), modifier = Modifier.size(22.dp), tint = palette.ink.copy(alpha = 0.75f))
                }
                Box {
                    Surface(
                        modifier = Modifier.clickable { speedMenuExpanded = true },
                        shape = RoundedCornerShape(50),
                        color = Color.Transparent,
                        border = BorderStroke(1.dp, palette.divider)
                    ) {
                        Text(
                            "${state.speed}x",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = palette.accent
                        )
                    }
                    DropdownMenu(expanded = speedMenuExpanded, onDismissRequest = { speedMenuExpanded = false }) {
                        SPEED_OPTIONS.forEach { s ->
                            DropdownMenuItem(text = { Text("${s}x") }, onClick = {
                                vm.setSpeed(s)
                                speedMenuExpanded = false
                            })
                        }
                    }
                }
            }
        }
    }
}
