package com.ziaee.frenchreader.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.gestures.animateScrollBy
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.Popup
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ziaee.frenchreader.R
import com.ziaee.frenchreader.text.BlockType
import com.ziaee.frenchreader.tts.AVAILABLE_VOICES
import com.ziaee.frenchreader.tts.SentenceBoundary
import com.ziaee.frenchreader.ui.theme.AppearanceState
import com.ziaee.frenchreader.ui.theme.ReadingPalette
import com.ziaee.frenchreader.ui.theme.readingPaletteFor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val SPEED_OPTIONS = listOf(0.75f, 1.0f, 1.25f, 1.5f)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadingScreen(textId: Long, onBack: () -> Unit, onOpenVocab: () -> Unit) {
    val vm: ReadingViewModel = viewModel()
    val state by vm.state.collectAsState()
    val palette = readingPaletteFor(AppearanceState.readingBackground)
    val fontScale = AppearanceState.fontScale.multiplier

    // word + the sentence it came from, while the dictionary sheet is open.
    var dictionaryTarget by remember { mutableStateOf<Pair<String, String>?>(null) }
    // Word/sentence a selection just resolved to -- shows a "Define" affordance next to
    // the still-active selection handles; only opens the dictionary once the user taps it,
    // so a long-press's initial single-word selection doesn't collapse itself before the
    // user has a chance to drag a handle and extend it to a phrase.
    var selectedWord by remember { mutableStateOf<Pair<String, String>?>(null) }
    var selectedPhrase by remember { mutableStateOf<String?>(null) }
    var clearSelectionTick by remember { mutableIntStateOf(0) }
    val selectionToolbarController = remember { SelectionToolbarController() }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current

    val listState = rememberLazyListState()
    var autoScrollEnabled by remember { mutableStateOf(true) }
    var voiceMenuExpanded by remember { mutableStateOf(false) }
    var showSourceInfoSheet by remember { mutableStateOf(false) }

    LaunchedEffect(textId) { vm.load(textId) }
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
                            Text(
                                state.textDoc?.title ?: stringResource(R.string.reading_loading_title),
                                maxLines = 1
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = onBack) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.accessibility_back))
                            }
                        },
                        actions = {
                            if (state.chunks.isNotEmpty()) {
                                Text(
                                    "${state.currentChunkIndex + 1}/${state.chunks.size}",
                                    color = palette.inkFaded,
                                    modifier = Modifier.padding(end = 4.dp)
                                )
                            }
                            IconButton(onClick = onOpenVocab) {
                                Icon(Icons.Default.MenuBook, contentDescription = stringResource(R.string.accessibility_vocabulary))
                            }
                            if (state.textDoc?.sourceUrl != null) {
                                IconButton(onClick = { showSourceInfoSheet = true }) {
                                    Icon(Icons.Default.Info, contentDescription = stringResource(R.string.accessibility_source_info))
                                }
                            }
                            Box {
                                IconButton(onClick = { voiceMenuExpanded = true }) {
                                    Icon(Icons.Default.RecordVoiceOver, contentDescription = stringResource(R.string.accessibility_select_voice))
                                }
                                DropdownMenu(
                                    expanded = voiceMenuExpanded,
                                    onDismissRequest = { voiceMenuExpanded = false }
                                ) {
                                    AVAILABLE_VOICES.forEach { voice ->
                                        DropdownMenuItem(
                                            text = { Text(stringResource(voice.labelRes)) },
                                            leadingIcon = {
                                                if (state.textDoc?.voice == voice.id) {
                                                    Icon(Icons.Default.Check, contentDescription = null)
                                                }
                                            },
                                            onClick = {
                                                voiceMenuExpanded = false
                                                vm.changeVoice(voice.id)
                                            }
                                        )
                                    }
                                }
                            }
                            IconButton(onClick = { autoScrollEnabled = !autoScrollEnabled }) {
                                Icon(
                                    Icons.Default.SwapVert,
                                    contentDescription = stringResource(R.string.accessibility_autoplay),
                                    tint = if (autoScrollEnabled) palette.accent
                                    else palette.inkFaded
                                )
                            }
                            IconButton(onClick = { vm.toggleShowTranslations() }) {
                                Icon(
                                    Icons.Default.Translate,
                                    contentDescription = stringResource(R.string.accessibility_toggle_translation),
                                    tint = if (state.showTranslations) palette.accent
                                    else palette.inkFaded
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = palette.background,
                            titleContentColor = palette.ink,
                            navigationIconContentColor = palette.ink,
                            actionIconContentColor = palette.ink
                        )
                    )
                    if (state.chunks.isNotEmpty()) {
                        LinearProgressIndicator(
                            progress = { (state.currentChunkIndex + 1).toFloat() / state.chunks.size },
                            modifier = Modifier.fillMaxWidth().height(2.dp),
                            color = palette.accent,
                            trackColor = palette.divider
                        )
                    }
                }
            },
            bottomBar = { PlaybackControls(vm, state, palette) }
        ) { padding ->
            if (!state.ready) {
                Box(
                    Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = palette.accent)
                }
                return@Scaffold
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 24.dp),
                contentPadding = PaddingValues(vertical = 20.dp)
            ) {
                itemsIndexed(state.chunks) { chunkIndex, chunk ->
                    ChunkParagraph(
                        chunk = chunk,
                        isCurrentChunk = chunkIndex == state.currentChunkIndex,
                        currentPositionMs = state.currentPositionMs,
                        showTranslation = state.showTranslations,
                        palette = palette,
                        fontScale = fontScale,
                        onSentenceClick = { sentence -> vm.seekToSentence(chunkIndex, sentence) },
                        onRetry = { vm.retryChunk(chunkIndex) },
                        onWordLookup = { word, sentenceText ->
                            selectedWord = word to sentenceText
                            selectedPhrase = null
                        },
                        onPhraseSelected = { phrase ->
                            selectedPhrase = phrase
                            selectedWord = null
                        },
                        clearSelectionSignal = clearSelectionTick
                    )
                    val spacing = if (chunk.block.type == BlockType.LIST_ITEM &&
                        state.chunks.getOrNull(chunkIndex + 1)?.block?.type == BlockType.LIST_ITEM
                    ) 6.dp else 28.dp
                    Spacer(Modifier.height(spacing))
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
        Popup(
            popupPositionProvider = SelectionRectPositionProvider(
                rect = toolbarRect,
                marginPx = with(density) { 8.dp.toPx() }
            ),
            onDismissRequest = {
                vm.stopSelectionPlayback()
                selectedPhrase = null
                clearSelectionTick++
            }
        ) {
            SelectionToolbarContent(
                onCopy = {
                    // Written directly instead of via the framework's own
                    // onCopyRequested callback -- that callback copies whatever the
                    // *raw* (un-snapped) selection currently is, which can differ
                    // from `phrase` (already snapped to whole word boundaries by
                    // classifySelection) and would silently overwrite it.
                    clipboard.setText(androidx.compose.ui.text.AnnotatedString(phrase))
                    selectedPhrase = null
                    clearSelectionTick++
                },
                onListen = {
                    vm.player.pause()
                    vm.playSelection(phrase)
                    selectedPhrase = null
                    clearSelectionTick++
                }
            )
        }
    }

    if (selectedWord != null && toolbarRect != null &&
        selectionToolbarController.status == TextToolbarStatus.Shown
    ) {
        val (word, sentence) = selectedWord!!
        Popup(
            popupPositionProvider = SelectionRectPositionProvider(
                rect = toolbarRect,
                marginPx = with(density) { 8.dp.toPx() }
            ),
            onDismissRequest = {
                selectedWord = null
                clearSelectionTick++
            }
        ) {
            DefineToolbarContent(
                onDefine = {
                    vm.player.pause()
                    dictionaryTarget = word to sentence
                    selectedWord = null
                    clearSelectionTick++
                }
            )
        }
    }

    if (showSourceInfoSheet) {
        state.textDoc?.let { doc ->
            SourceInfoSheet(doc = doc, onDismiss = { showSourceInfoSheet = false })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SourceInfoSheet(doc: com.ziaee.frenchreader.data.TextDocument, onDismiss: () -> Unit) {
    val context = LocalContext.current

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(doc.sourceName ?: stringResource(R.string.source_default_label), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                doc.sourceUrl?.let { url ->
                    IconButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }) {
                        Icon(Icons.Default.OpenInBrowser, contentDescription = stringResource(R.string.accessibility_open_in_browser))
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            doc.author?.let { Text(stringResource(R.string.source_author_label, it)) }
            doc.license?.let { Text(stringResource(R.string.source_license_label, it)) }
            doc.publishedAt?.let {
                val dateLabel = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(it))
                Text(stringResource(R.string.source_published_label, dateLabel))
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
    palette: ReadingPalette,
    fontScale: Float,
    onSentenceClick: (SentenceBoundary) -> Unit,
    onRetry: () -> Unit,
    onWordLookup: (word: String, sentence: String) -> Unit,
    onPhraseSelected: (String) -> Unit,
    clearSelectionSignal: Int
) {
    when (chunk.status) {
        ChunkStatus.READY -> {
            // The French text must always read left-to-right regardless of
            // the surrounding Persian UI's layout direction.
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Column {
                    when (chunk.block.type) {
                        BlockType.HEADER -> SentenceFlowText(
                            chunk = chunk,
                            isCurrentChunk = isCurrentChunk,
                            currentPositionMs = currentPositionMs,
                            fontSize = headerFontSize(chunk.block.headerLevel, fontScale),
                            lineHeight = headerLineHeight(chunk.block.headerLevel, fontScale),
                            fontWeight = FontWeight.Bold,
                            color = palette.ink,
                            palette = palette,
                            onSentenceClick = onSentenceClick,
                            onWordLookup = onWordLookup,
                            onPhraseSelected = onPhraseSelected,
                            clearSelectionSignal = clearSelectionSignal
                        )
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
                                    onSentenceClick = onSentenceClick,
                                    onWordLookup = onWordLookup,
                                    onPhraseSelected = onPhraseSelected,
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
                            onSentenceClick = onSentenceClick,
                            onWordLookup = onWordLookup,
                            onPhraseSelected = onPhraseSelected,
                            clearSelectionSignal = clearSelectionSignal
                        )
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
        ChunkStatus.LOADING -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = palette.accent
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.audio_preparing),
                    fontSize = (13f * fontScale).sp,
                    color = palette.inkFaded
                )
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
        ChunkStatus.PENDING -> {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Text(
                    chunk.text,
                    fontSize = (19f * fontScale).sp,
                    lineHeight = (31f * fontScale).sp,
                    color = palette.inkFaded
                )
            }
        }
    }
}

private fun headerFontSize(level: Int, fontScale: Float): TextUnit = (when (level) {
    1 -> 26f
    2 -> 24f
    3 -> 22f
    4 -> 20f
    5 -> 19f
    else -> 18f
} * fontScale).sp

private fun headerLineHeight(level: Int, fontScale: Float): TextUnit = (when (level) {
    1 -> 34f
    2 -> 31f
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
    onSentenceClick: (SentenceBoundary) -> Unit,
    onWordLookup: (word: String, sentence: String) -> Unit,
    onPhraseSelected: (String) -> Unit,
    clearSelectionSignal: Int
) {
    val ranges = remember(chunk.sentences) { mutableListOf<IntRange>() }
    val annotated = remember(chunk.sentences, chunk.block, isCurrentChunk, currentPositionMs, palette) {
        buildAnnotatedString {
            ranges.clear()
            chunk.sentences.forEachIndexed { i, s ->
                val start = length
                append(s.text)
                val end = length
                ranges.add(start until end)
                val isActive = isCurrentChunk &&
                    currentPositionMs >= s.offsetMs &&
                    currentPositionMs < s.offsetMs + s.durationMs
                if (isActive) {
                    addStyle(
                        SpanStyle(background = palette.highlightBg, color = palette.highlightInk),
                        start, end
                    )
                }
                if (i != chunk.sentences.lastIndex) append(" ")
            }

            val full = toString()
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
                    val idx = ranges.indexOfFirst { newValue.selection.start in it }
                    if (idx >= 0) onSentenceClick(chunk.sentences[idx])
                    selection = TextRange.Zero
                } else {
                    selection = newValue.selection
                    when (val kind = classifySelection(annotated.text, newValue.selection)) {
                        is SelectionKind.Word ->
                            onWordLookup(kind.word, sentenceTextFor(newValue.selection.start))
                        is SelectionKind.Phrase -> onPhraseSelected(kind.text)
                        null -> selection = TextRange.Zero
                    }
                }
            },
            readOnly = true,
            textStyle = TextStyle(
                fontSize = fontSize,
                lineHeight = lineHeight,
                letterSpacing = 0.1.sp,
                color = color,
                fontWeight = fontWeight,
                textAlign = TextAlign.Start
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
    data class Word(val word: String) : SelectionKind
    data class Phrase(val text: String) : SelectionKind
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
        SelectionKind.Word(word)
    } else {
        SelectionKind.Phrase(snapped.trim())
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaybackControls(vm: ReadingViewModel, state: ReadingUiState, palette: ReadingPalette) {
    var speedMenuExpanded by remember { mutableStateOf(false) }

    Surface(color = palette.background, tonalElevation = 4.dp, shadowElevation = 8.dp) {
        Column(modifier = Modifier.padding(top = 6.dp, bottom = 14.dp)) {
            HorizontalDivider(color = palette.divider)
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { vm.skipMs(-10_000) }) {
                    Icon(Icons.Default.Replay10, contentDescription = stringResource(R.string.accessibility_skip_back), tint = palette.ink)
                }
                IconButton(onClick = { vm.previousSentence() }) {
                    Icon(Icons.Default.SkipPrevious, contentDescription = stringResource(R.string.accessibility_previous_sentence), tint = palette.ink)
                }
                FilledIconButton(
                    onClick = { vm.togglePlayPause() },
                    modifier = Modifier.size(58.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = palette.accent)
                ) {
                    Icon(
                        if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = stringResource(R.string.accessibility_play_pause),
                        modifier = Modifier.size(30.dp),
                        tint = Color.White
                    )
                }
                IconButton(onClick = { vm.nextSentence() }) {
                    Icon(Icons.Default.SkipNext, contentDescription = stringResource(R.string.accessibility_next_sentence), tint = palette.ink)
                }
                IconButton(onClick = { vm.skipMs(10_000) }) {
                    Icon(Icons.Default.Forward10, contentDescription = stringResource(R.string.accessibility_skip_forward), tint = palette.ink)
                }
                Box {
                    TextButton(onClick = { speedMenuExpanded = true }) {
                        Text("${state.speed}x", color = palette.accent, fontWeight = FontWeight.Medium)
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
