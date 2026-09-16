package com.ziaee.frenchreader.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ziaee.frenchreader.text.BlockType
import com.ziaee.frenchreader.tts.AVAILABLE_VOICES
import com.ziaee.frenchreader.tts.SentenceBoundary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val SPEED_OPTIONS = listOf(0.75f, 1.0f, 1.25f, 1.5f)

/** A warm, paper-like reading palette -- deliberately not stock Material,
 * because a reading screen should feel like a book, not a form. */
private object ReadingPalette {
    val Background = Color(0xFFFBF6EC)
    val Ink = Color(0xFF2E2A22)
    val InkFaded = Color(0xFF6B6252)
    val HighlightBg = Color(0xFFF6D97A)
    val HighlightInk = Color(0xFF2E2A22)
    val Divider = Color(0xFFE6DDC8)
    val Accent = Color(0xFF8A6D3B)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadingScreen(textId: Long, onBack: () -> Unit, onOpenVocab: () -> Unit) {
    val vm: ReadingViewModel = viewModel()
    val state by vm.state.collectAsState()

    // word + the sentence it came from, while the dictionary sheet is open.
    var dictionaryTarget by remember { mutableStateOf<Pair<String, String>?>(null) }

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

    Scaffold(
        containerColor = ReadingPalette.Background,
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Text(
                            state.textDoc?.title ?: "در حال بارگذاری...",
                            maxLines = 1
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "بازگشت")
                        }
                    },
                    actions = {
                        if (state.chunks.isNotEmpty()) {
                            Text(
                                "${state.currentChunkIndex + 1}/${state.chunks.size}",
                                color = ReadingPalette.InkFaded,
                                modifier = Modifier.padding(end = 4.dp)
                            )
                        }
                        IconButton(onClick = onOpenVocab) {
                            Icon(Icons.Default.MenuBook, contentDescription = "لغات ذخیره‌شده")
                        }
                        if (state.textDoc?.sourceUrl != null) {
                            IconButton(onClick = { showSourceInfoSheet = true }) {
                                Icon(Icons.Default.Info, contentDescription = "دربارهٔ این متن")
                            }
                        }
                        Box {
                            IconButton(onClick = { voiceMenuExpanded = true }) {
                                Icon(Icons.Default.RecordVoiceOver, contentDescription = "انتخاب صدا")
                            }
                            DropdownMenu(
                                expanded = voiceMenuExpanded,
                                onDismissRequest = { voiceMenuExpanded = false }
                            ) {
                                AVAILABLE_VOICES.forEach { voice ->
                                    DropdownMenuItem(
                                        text = { Text(voice.label) },
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
                                contentDescription = "پیمایش خودکار صفحه همراه با صدا",
                                tint = if (autoScrollEnabled) ReadingPalette.Accent
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { vm.toggleShowTranslations() }) {
                            Icon(
                                Icons.Default.Translate,
                                contentDescription = "نمایش/عدم‌نمایش ترجمه",
                                tint = if (state.showTranslations) ReadingPalette.Accent
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = ReadingPalette.Background
                    )
                )
                if (state.chunks.isNotEmpty()) {
                    LinearProgressIndicator(
                        progress = { (state.currentChunkIndex + 1).toFloat() / state.chunks.size },
                        modifier = Modifier.fillMaxWidth().height(2.dp),
                        color = ReadingPalette.Accent,
                        trackColor = ReadingPalette.Divider
                    )
                }
            }
        },
        bottomBar = { PlaybackControls(vm, state) }
    ) { padding ->
        if (!state.ready) {
            Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = ReadingPalette.Accent)
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
                    onSentenceClick = { sentence -> vm.seekToSentence(chunkIndex, sentence) },
                    onRetry = { vm.retryChunk(chunkIndex) },
                    onWordLookup = { word, sentenceText ->
                        vm.player.pause()
                        dictionaryTarget = word to sentenceText
                    }
                )
                val spacing = if (chunk.block.type == BlockType.LIST_ITEM &&
                    state.chunks.getOrNull(chunkIndex + 1)?.block?.type == BlockType.LIST_ITEM
                ) 6.dp else 28.dp
                Spacer(Modifier.height(spacing))
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
                Text(doc.sourceName ?: "منبع", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                doc.sourceUrl?.let { url ->
                    IconButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }) {
                        Icon(Icons.Default.OpenInBrowser, contentDescription = "باز کردن منبع در مرورگر")
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            doc.author?.let { Text("نویسنده: $it") }
            doc.license?.let { Text("مجوز: $it") }
            doc.publishedAt?.let {
                val dateLabel = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(it))
                Text("تاریخ انتشار: $dateLabel")
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
    onSentenceClick: (SentenceBoundary) -> Unit,
    onRetry: () -> Unit,
    onWordLookup: (word: String, sentence: String) -> Unit
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
                            fontSize = headerFontSize(chunk.block.headerLevel),
                            lineHeight = headerLineHeight(chunk.block.headerLevel),
                            fontWeight = FontWeight.Bold,
                            color = ReadingPalette.Ink,
                            onSentenceClick = onSentenceClick,
                            onWordLookup = onWordLookup
                        )
                        BlockType.LIST_ITEM -> Row {
                            Text(
                                "•  ",
                                fontSize = 19.sp,
                                lineHeight = 29.sp,
                                color = ReadingPalette.Accent
                            )
                            Box(modifier = Modifier.weight(1f)) {
                                SentenceFlowText(
                                    chunk = chunk,
                                    isCurrentChunk = isCurrentChunk,
                                    currentPositionMs = currentPositionMs,
                                    fontSize = 19.sp,
                                    lineHeight = 29.sp,
                                    fontWeight = null,
                                    color = ReadingPalette.Ink,
                                    onSentenceClick = onSentenceClick,
                                    onWordLookup = onWordLookup
                                )
                            }
                        }
                        BlockType.PARAGRAPH -> SentenceFlowText(
                            chunk = chunk,
                            isCurrentChunk = isCurrentChunk,
                            currentPositionMs = currentPositionMs,
                            fontSize = 19.sp,
                            lineHeight = 31.sp,
                            fontWeight = null,
                            color = ReadingPalette.Ink,
                            onSentenceClick = onSentenceClick,
                            onWordLookup = onWordLookup
                        )
                    }

                    if (showTranslation && chunk.block.type != BlockType.HEADER) {
                        Spacer(Modifier.height(6.dp))
                        when (chunk.translationStatus) {
                            ChunkStatus.READY -> chunk.translation?.let {
                                Text(
                                    it,
                                    fontSize = 14.sp,
                                    lineHeight = 21.sp,
                                    fontStyle = FontStyle.Italic,
                                    color = ReadingPalette.InkFaded
                                )
                            }
                            ChunkStatus.LOADING -> Text(
                                "در حال ترجمه...",
                                fontSize = 13.sp,
                                fontStyle = FontStyle.Italic,
                                color = ReadingPalette.InkFaded
                            )
                            ChunkStatus.ERROR -> Text(
                                "ترجمه در دسترس نیست",
                                fontSize = 13.sp,
                                fontStyle = FontStyle.Italic,
                                color = ReadingPalette.InkFaded
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
                    color = ReadingPalette.Accent
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "در حال آماده‌سازی صدا برای این بخش...",
                    fontSize = 13.sp,
                    color = ReadingPalette.InkFaded
                )
            }
        }
        ChunkStatus.ERROR -> {
            Column {
                Text(
                    "خطا در تولید صدا برای این بخش: ${chunk.error}",
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 13.sp
                )
                TextButton(onClick = onRetry) { Text("تلاش مجدد") }
            }
        }
        ChunkStatus.PENDING -> {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Text(
                    chunk.text,
                    fontSize = 19.sp,
                    lineHeight = 31.sp,
                    color = ReadingPalette.InkFaded
                )
            }
        }
    }
}

private fun headerFontSize(level: Int): TextUnit = when (level) {
    1 -> 26.sp
    2 -> 24.sp
    3 -> 22.sp
    4 -> 20.sp
    5 -> 19.sp
    else -> 18.sp
}

private fun headerLineHeight(level: Int): TextUnit = when (level) {
    1 -> 34.sp
    2 -> 31.sp
    3 -> 29.sp
    4 -> 27.sp
    5 -> 26.sp
    else -> 25.sp
}

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
    onSentenceClick: (SentenceBoundary) -> Unit,
    onWordLookup: (word: String, sentence: String) -> Unit
) {
    val ranges = remember(chunk.sentences) { mutableListOf<IntRange>() }
    val annotated = remember(chunk.sentences, chunk.block, isCurrentChunk, currentPositionMs) {
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
                        SpanStyle(background = ReadingPalette.HighlightBg, color = ReadingPalette.HighlightInk),
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

    var textLayout by remember { mutableStateOf<TextLayoutResult?>(null) }

    Text(
        text = annotated,
        style = TextStyle(
            fontSize = fontSize,
            lineHeight = lineHeight,
            letterSpacing = 0.1.sp,
            color = color,
            fontWeight = fontWeight,
            textAlign = TextAlign.Start
        ),
        onTextLayout = { textLayout = it },
        modifier = Modifier.pointerInput(chunk.sentences) {
            detectTapGestures(
                onTap = { pos ->
                    val layout = textLayout ?: return@detectTapGestures
                    val offset = layout.getOffsetForPosition(pos)
                    val idx = ranges.indexOfFirst { offset in it }
                    if (idx >= 0) onSentenceClick(chunk.sentences[idx])
                },
                onLongPress = { pos ->
                    val layout = textLayout ?: return@detectTapGestures
                    val offset = layout.getOffsetForPosition(pos)
                    val full = annotated.text
                    val word = extractWordAt(full, offset)
                    if (word.isNotBlank()) {
                        val sentIdx = ranges.indexOfFirst { offset in it }
                        val sentenceText = if (sentIdx >= 0) chunk.sentences[sentIdx].text else full
                        onWordLookup(word, sentenceText)
                    }
                }
            )
        }
    )
}

/** Extracts the word touching [offset] in [text] for the dictionary
 * long-press. Apostrophes are treated as boundaries (not word characters)
 * so tapping "l'appartement" isolates "appartement", not the elided
 * article with it -- WordReference wouldn't recognize the elided form. */
private fun extractWordAt(text: String, offset: Int): String {
    if (text.isEmpty()) return ""
    fun isWordChar(c: Char) = c.isLetter() || c == '-'

    var pos = offset.coerceIn(0, text.length)
    if ((pos >= text.length || !isWordChar(text[pos])) && pos > 0 && isWordChar(text[pos - 1])) {
        pos -= 1
    }
    if (pos >= text.length || !isWordChar(text[pos])) return ""

    var start = pos
    var end = pos + 1
    while (start > 0 && isWordChar(text[start - 1])) start--
    while (end < text.length && isWordChar(text[end])) end++
    return text.substring(start, end)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaybackControls(vm: ReadingViewModel, state: ReadingUiState) {
    var speedMenuExpanded by remember { mutableStateOf(false) }

    Surface(color = ReadingPalette.Background, tonalElevation = 4.dp, shadowElevation = 8.dp) {
        Column(modifier = Modifier.padding(top = 6.dp, bottom = 14.dp)) {
            HorizontalDivider(color = ReadingPalette.Divider)
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { vm.skipMs(-10_000) }) {
                    Icon(Icons.Default.Replay10, contentDescription = "۱۰ ثانیه عقب", tint = ReadingPalette.Ink)
                }
                IconButton(onClick = { vm.previousSentence() }) {
                    Icon(Icons.Default.SkipPrevious, contentDescription = "جملهٔ قبل", tint = ReadingPalette.Ink)
                }
                FilledIconButton(
                    onClick = { vm.togglePlayPause() },
                    modifier = Modifier.size(58.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = ReadingPalette.Accent)
                ) {
                    Icon(
                        if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "پخش/توقف",
                        modifier = Modifier.size(30.dp),
                        tint = Color.White
                    )
                }
                IconButton(onClick = { vm.nextSentence() }) {
                    Icon(Icons.Default.SkipNext, contentDescription = "جملهٔ بعد", tint = ReadingPalette.Ink)
                }
                IconButton(onClick = { vm.skipMs(10_000) }) {
                    Icon(Icons.Default.Forward10, contentDescription = "۱۰ ثانیه جلو", tint = ReadingPalette.Ink)
                }
                Box {
                    TextButton(onClick = { speedMenuExpanded = true }) {
                        Text("${state.speed}x", color = ReadingPalette.Accent, fontWeight = FontWeight.Medium)
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
