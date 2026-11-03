package com.ziaee.frenchreader.ui.pdf

import android.content.ClipData
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.layout.onSizeChanged
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.delay
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import com.ziaee.frenchreader.ui.components.TappableFrenchText
import com.ziaee.frenchreader.translate.isRtlTarget
import com.ziaee.frenchreader.translate.llm.LlmTranslator
import com.ziaee.frenchreader.translate.llm.LlmPrefs
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ziaee.frenchreader.R
import com.ziaee.frenchreader.content.pdf.*
import com.ziaee.frenchreader.data.TextDocument
import com.ziaee.frenchreader.ui.DictionarySheet
import com.ziaee.frenchreader.ui.SelectionRectPositionProvider
import com.ziaee.frenchreader.ui.SelectionToolbarContent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.ziaee.frenchreader.translate.llm.LlmError
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration

private data class PdfSelection(val words: List<PdfWord>, val first: Int, val last: Int, val anchor: Rect) {
    val selected: List<PdfWord> get() = words.subList(minOf(first, last), maxOf(first, last) + 1)
    val text: String get() = selected.joinToString(" ") { it.text }
    val sentence: String get() = sentenceAround(words, minOf(first, last))
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PdfReaderScreen(document: TextDocument, onBack: () -> Unit, onTextView: () -> Unit, onInteraction: () -> Unit = {}, onBodyUpdated: () -> Unit = {}) {
    val vm: PdfReaderViewModel = viewModel(key = "pdf_${document.id}")
    val renderer by vm.renderer.collectAsState()
    val failed by vm.failed.collectAsState()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val interact by rememberUpdatedState(onInteraction)
    val density = LocalDensity.current
    val listState = rememberLazyListState()
    var restoredPage by rememberSaveable(document.id) { mutableStateOf(false) }
    var zoom by rememberSaveable(document.id) { mutableFloatStateOf(1f) }
    val horizontalState = rememberScrollState()
    var viewportWidth by remember { mutableIntStateOf(0) }
    val currentPage by remember { derivedStateOf {
        val info = listState.layoutInfo
        pageAtCentre(info.visibleItemsInfo.map { PdfVisiblePage(it.index, it.offset, it.size) },
            info.viewportStartOffset, info.viewportEndOffset) ?: listState.firstVisibleItemIndex
    } }
    // A pending anchor is applied after the new page width has been measured.
    var zoomAnchor by remember { mutableStateOf<PdfZoomAnchor?>(null) }
    // Reading position as [page, fraction of page height, fraction of horizontal pan, viewport width]:
    // pixel offsets are meaningless once rotation changes the page width.
    var widthAnchor by rememberSaveable(document.id) { mutableStateOf<List<Float>?>(null) }
    LaunchedEffect(listState, viewportWidth) {
        snapshotFlow {
            val first = listState.layoutInfo.visibleItemsInfo.firstOrNull()
            if (first == null || viewportWidth == 0 || zoomAnchor != null) null
            else listOf(first.index.toFloat(), -first.offset.toFloat() / first.size.coerceAtLeast(1),
                if (horizontalState.maxValue > 0) horizontalState.value.toFloat() / horizontalState.maxValue else 0f,
                viewportWidth.toFloat())
        }.collect { anchor ->
            // Until a width change has been restored, the old anchor is the reading position.
            val stored = widthAnchor
            if (anchor != null && (stored == null || stored[3] == anchor[3])) widthAnchor = anchor
        }
    }
    LaunchedEffect(renderer, viewportWidth) {
        val current = renderer ?: return@LaunchedEffect
        val anchor = widthAnchor ?: return@LaunchedEffect
        if (viewportWidth == 0 || anchor[3] == viewportWidth.toFloat()) return@LaunchedEffect
        val page = anchor[0].toInt().coerceIn(0, current.pageCount - 1)
        val pageHeight = viewportWidth * zoom * current.pageAspects[page]
        withFrameNanos { }
        withFrameNanos { }
        listState.scrollToItem(page, (anchor[1] * pageHeight).toInt().coerceAtLeast(0))
        horizontalState.scrollTo((anchor[2] * horizontalState.maxValue).toInt())
        widthAnchor = anchor.toMutableList().also { it[3] = viewportWidth.toFloat() }
    }
    var selection by remember { mutableStateOf<PdfSelection?>(null) }
    var dictionary by remember { mutableStateOf<Pair<String, String>?>(null) }
    var lastSentence by remember { mutableStateOf<String?>(null) }
    var overflow by remember { mutableStateOf(false) }
    var translationSource by remember { mutableStateOf<String?>(null) }
    var translation by remember { mutableStateOf<String?>(null) }
    var translating by remember { mutableStateOf(false) }
    var translationFailed by remember { mutableStateOf(false) }
    var translationRequest by remember { mutableIntStateOf(0) }
    var lassoMode by remember { mutableStateOf(false) }
    // Build the OCR spelling index in the background as soon as a PDF is open: it takes a few
    // seconds once per process, and doing it after the lasso is drawn made the first OCR slow.
    LaunchedEffect(Unit) {
        runCatching { com.ziaee.frenchreader.comprehension.LemmaLexicon.get(context).getOcrCorrector() }
    }
    var recognizing by remember { mutableStateOf(false) }
    var recognizedText by remember { mutableStateOf<String?>(null) }
    var ocrCorrection by remember { mutableStateOf<OcrCorrection?>(null) }
    var showOriginalOcr by remember { mutableStateOf(false) }
    var correctionMenu by remember { mutableStateOf<OcrChange?>(null) }
    var fixingOcr by remember { mutableStateOf(false) }
    var fixOcrError by remember { mutableStateOf<String?>(null) }
    var ocrRequest by remember { mutableIntStateOf(0) }
    // Construction reads configuration only. Network calls happen inside the button action.
    val ocrAi = LlmTranslator.forContext(context)
    var ocrFailed by remember { mutableStateOf(false) }
    var addingText by remember { mutableStateOf(false) }
    var addFailed by remember { mutableStateOf(false) }
    var translationBefore by remember { mutableStateOf<String?>(null) }
    var translationAfter by remember { mutableStateOf<String?>(null) }
    var translationEngine by remember { mutableStateOf<String?>(null) }
    val ocr = remember(context) { PdfRegionOcr(context) }
    val shareError = stringResource(R.string.pdf_share_failed)
    val pageError = stringResource(R.string.pdf_page_failed)

    fun translate(text: String, before: String? = null, after: String? = null) {
        val request = ++translationRequest
        translationBefore = before
        translationAfter = after
        translationEngine = if (LlmTranslator.forContext(context) != null) LlmPrefs(context).currentModel else null
        translationSource = text
        translating = true
        translation = null
        translationFailed = false
        selection = null
        scope.launch {
            try {
                val result = vm.translate(text, before, after, document.translationLang)
                if (request == translationRequest) {
                    translation = result ?: text
                    translating = false
                }
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) {
                if (request == translationRequest) { translating = false; translationFailed = true }
            }
        }
    }
    LaunchedEffect(document.pdfPath) { document.pdfPath?.let(vm::load) }
    LaunchedEffect(renderer, document.id) {
        val current = renderer ?: return@LaunchedEffect
        if (!restoredPage) {
            if (current.pageCount > 0) listState.scrollToItem(vm.lastPage(document.id).coerceAtMost(current.pageCount - 1))
            restoredPage = true
        }
    }
    LaunchedEffect(renderer, restoredPage, document.id) {
        val current = renderer ?: return@LaunchedEffect
        if (!restoredPage) return@LaunchedEffect
        snapshotFlow { currentPage }.distinctUntilChanged().collectLatest {
            delay(250)
            vm.savePage(document.id, it, current.pageCount)
        }
    }
    val latestPage by rememberUpdatedState(currentPage)
    val latestRenderer by rememberUpdatedState(renderer)
    DisposableEffect(document.id) {
        onDispose { latestRenderer?.let { vm.savePage(document.id, latestPage, it.pageCount) } }
    }
    fun changeZoom(requested: Float, focal: Offset) {
        val next = requested.coerceIn(1f, 4f)
        if (next == zoom) return
        val info = listState.layoutInfo
        val focalPage = info.visibleItemsInfo.firstOrNull { focal.y >= it.offset && focal.y < it.offset + it.size }
            ?: info.visibleItemsInfo.minByOrNull { kotlin.math.abs(it.offset - focal.y) }
            ?: return
        zoomAnchor = PdfZoomAnchor(focalPage.index,
            focalPageZoomOffset(focalPage.offset.toFloat(), focal.y, zoom, next),
            focalScrollOffset(horizontalState.value.toFloat(), focal.x, zoom, next))
        zoom = next
        selection = null
    }
    val zoomAtPoint by rememberUpdatedState<(Float, Offset) -> Unit> { value, point -> changeZoom(value, point) }
    val latestZoom by rememberUpdatedState(zoom)
    LaunchedEffect(zoomAnchor) {
        val anchor = zoomAnchor ?: return@LaunchedEffect
        // Wait for layout to use the new width before clamping the pan range.
        withFrameNanos { }
        withFrameNanos { }
        horizontalState.scrollTo(anchor.horizontal.toInt().coerceAtLeast(0))
        listState.scrollToItem(anchor.page, anchor.pageScrollOffset.toInt())
        zoomAnchor = null
    }
    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) selection = null
    }
    Scaffold(modifier = Modifier.pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.changes.any { it.pressed || it.previousPressed }) interact()
            }
        }
    }, topBar = {
        TopAppBar(
            title = { Text(document.title, maxLines = 1) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.accessibility_back)) } },
            actions = {
                IconButton(enabled = renderer != null && !recognizing, onClick = {
                    lassoMode = !lassoMode
                    selection = null
                }) {
                    Icon(Icons.Default.Gesture, stringResource(R.string.pdf_select_area),
                        tint = if (lassoMode) MaterialTheme.colorScheme.primary else LocalContentColor.current)
                }
                TextButton(onClick = onTextView) { Text(stringResource(R.string.pdf_text_view)) }
                Box {
                    IconButton(onClick = { overflow = true }) { Icon(Icons.Default.MoreVert, stringResource(R.string.selection_action_more)) }
                    DropdownMenu(expanded = overflow, onDismissRequest = { overflow = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.pdf_share)) }, onClick = {
                            overflow = false
                            try {
                                val file = document.pdfPath?.let { PdfFileStore.resolve(context, it) } ?: error("Missing PDF")
                                val uri = FileProvider.getUriForFile(context, "${context.packageName}.share", file)
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "application/pdf"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    clipData = ClipData.newRawUri("", uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(intent, null))
                            } catch (_: Exception) { Toast.makeText(context, shareError, Toast.LENGTH_LONG).show() }
                        })
                        DropdownMenuItem(enabled = lastSentence != null,
                            text = { Text(stringResource(R.string.pdf_translate_sentence)) },
                            onClick = { overflow = false; lastSentence?.let { translate(it) } })
                    }
                }
            }
        )
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            val current = renderer
            when {
                failed -> Text(stringResource(R.string.pdf_open_failed), Modifier.padding(16.dp))
                current == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                else -> {
                    Text(stringResource(R.string.pdf_page_indicator,
                        (currentPage + 1).coerceAtMost(current.pageCount), current.pageCount), Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                    Box(Modifier.weight(1f).fillMaxWidth().onSizeChanged { viewportWidth = it.width }
                        .pointerInput(lassoMode) {
                            if (lassoMode) return@pointerInput
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false)
                                do {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                    if (event.changes.count { it.pressed } >= 2) {
                                        val factor = event.calculateZoom()
                                        if (factor.isFinite()) zoomAtPoint(latestZoom * factor, event.calculateCentroid(useCurrent = false))
                                        event.changes.forEach { it.consume() }
                                    }
                                } while (event.changes.any { it.pressed })
                            }
                        }) {
                    Box(Modifier.fillMaxSize().horizontalScroll(horizontalState, enabled = zoom > 1f && !lassoMode)) {
                    LazyColumn(state = listState, userScrollEnabled = !lassoMode,
                        modifier = Modifier.width(with(density) { (viewportWidth * zoom).toDp() }).fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items((0 until current.pageCount).toList(), key = { it }) { page ->
                            PdfPage(current, vm.layout, page,
                                lassoMode = lassoMode,
                                onRegion = { path, width ->
                                    lassoMode = false
                                    recognizing = true
                                    ocrFailed = false
                                    addFailed = false
                                    recognizedText = null
                                    ocrCorrection = null
                                    showOriginalOcr = false
                                    correctionMenu = null
                                    fixOcrError = null
                                    fixingOcr = false
                                    val request = ++ocrRequest
                                    scope.launch {
                                        try {
                                            val result = ocr.extractRegion(current, page, width, path)
                                            if (request == ocrRequest) {
                                                recognizedText = result.original
                                                ocrCorrection = result.corrected
                                            }
                                        } catch (cancelled: CancellationException) { throw cancelled
                                        } catch (_: Exception) { ocrFailed = true
                                        } finally { recognizing = false }
                                    }
                                },
                                onDoubleTap = { point ->
                                    val item = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == page }
                                    if (item != null) zoomAtPoint(if (latestZoom > 1f) 1f else 2.5f,
                                        Offset(point.x - horizontalState.value, point.y + item.offset))
                                },
                                selection = selection,
                                onSelection = { selection = it },
                                onWord = { words, index ->
                                    val sentence = sentenceAround(words, index)
                                    lastSentence = sentence
                                    dictionary = words[index].text to sentence
                                    selection = null
                                },
                                onError = { Toast.makeText(context, pageError, Toast.LENGTH_LONG).show() })
                        }
                    }
                    }
                    }
                }
            }
        }
    }
    selection?.let { selected ->
        Popup(popupPositionProvider = SelectionRectPositionProvider(selected.anchor, with(density) { 8.dp.toPx() }),
            onDismissRequest = { selection = null }) {
            SelectionToolbarContent(
                onCopy = { clipboard.setText(AnnotatedString(selected.text)); selection = null },
                onSave = { dictionary = selected.text to selected.sentence; selection = null },
                onListen = { vm.speak(selected.text, document.voice, document.ratePercent); selection = null }, onHighlight = {}, onChooseHighlightColor = {}, onDeleteHighlight = null, onMore = {},
                onTranslate = {
                    val start = minOf(selected.first, selected.last)
                    val end = maxOf(selected.first, selected.last)
                    val before = selected.words.take(start).takeLast(40).joinToString(" ") { it.text }.ifBlank { null }
                    val after = selected.words.drop(end + 1).take(40).joinToString(" ") { it.text }.ifBlank { null }
                    translate(selected.text, before, after)
                }, showReadingActions = false, showListen = true)
        }
    }
    translationSource?.let { source ->
        ModalBottomSheet(sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), onDismissRequest = { translationRequest++; translationSource = null }) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp).navigationBarsPadding()) {
                Text(source, style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(16.dp))
                when {
                    translating -> {
                        CircularProgressIndicator()
                        Text(stringResource(R.string.translation_loading))
                    }
                    translationFailed -> {
                        Text(stringResource(R.string.error_translation_unavailable), color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = { translate(source, translationBefore, translationAfter) }) {
                            Text(stringResource(R.string.action_retry))
                        }
                    }
                    else -> {
                        val rtl = isRtlTarget(document.translationLang)
                        SelectionContainer {
                            Text(translation.orEmpty(), Modifier.fillMaxWidth(),
                                textAlign = if (rtl) TextAlign.Right else TextAlign.Left,
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    textDirection = if (rtl) TextDirection.Rtl else TextDirection.Ltr))
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(translationEngine?.let { stringResource(R.string.pdf_ai_model, it) }
                            ?: stringResource(R.string.translation_engine_google),
                            style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
    if (recognizing || ocrFailed || recognizedText != null) {
        ModalBottomSheet(sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), onDismissRequest = { if (!recognizing && !addingText) { ocrRequest++; recognizedText = null; ocrFailed = false; fixingOcr = false; correctionMenu = null } }) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp).navigationBarsPadding()) {
                Text(stringResource(R.string.pdf_recognized_text), style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(12.dp))
                when {
                    recognizing -> CircularProgressIndicator()
                    ocrFailed -> Text(stringResource(R.string.pdf_ocr_failed), color = MaterialTheme.colorScheme.error)
                    else -> {
                        val text = if (showOriginalOcr) recognizedText.orEmpty() else ocrCorrection?.text ?: recognizedText.orEmpty()
                        val changes = if (showOriginalOcr) emptyList() else ocrCorrection?.changes.orEmpty()
                        if (text.isBlank()) Text(stringResource(R.string.pdf_ocr_empty))
                        else {
                            Box {
                                val annotated = buildAnnotatedString {
                                    append(text)
                                    for (change in changes) addStyle(SpanStyle(textDecoration = TextDecoration.Underline), change.start, change.end)
                                }
                                TappableFrenchText(text, onWordTap = { word -> dictionary = word to text },
                                    selectable = true, annotatedText = annotated, onOffsetTap = { offset ->
                                        val change = if (fixingOcr) null else changes.firstOrNull { offset >= it.start && offset < it.end }
                                        correctionMenu = change
                                        change != null
                                    })
                                DropdownMenu(expanded = correctionMenu != null, onDismissRequest = { correctionMenu = null }) {
                                    correctionMenu?.let { change ->
                                        DropdownMenuItem(text = { Text(stringResource(R.string.pdf_ocr_original_word, change.original)) },
                                            onClick = {}, enabled = false)
                                        DropdownMenuItem(enabled = !fixingOcr, text = { Text(stringResource(R.string.pdf_ocr_use_original)) }, onClick = {
                                            ocrCorrection = ocrCorrection?.revert(change)
                                            correctionMenu = null
                                        })
                                    }
                                }
                            }
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(enabled = !fixingOcr, onClick = { showOriginalOcr = !showOriginalOcr; correctionMenu = null }) {
                                    Text(stringResource(if (showOriginalOcr) R.string.pdf_ocr_show_corrected else R.string.pdf_ocr_show_original))
                                }
                                if (ocrAi != null) TextButton(enabled = !fixingOcr && !addingText, onClick = {
                                    fixingOcr = true
                                    fixOcrError = null
                                    correctionMenu = null
                                    val request = ocrRequest
                                    scope.launch {
                                        try {
                                            val fixed = withContext(Dispatchers.IO) { ocrAi.fixOcr(text) }
                                            val correction = withContext(Dispatchers.Default) { ocrWordDiff(text, fixed) }
                                            if (request == ocrRequest && recognizedText != null) {
                                                ocrCorrection = correction
                                                showOriginalOcr = false
                                            }
                                        } catch (cancelled: CancellationException) { throw cancelled
                                        } catch (error: Exception) {
                                            if (request == ocrRequest && recognizedText != null) {
                                                val reason = context.getString(when ((error as? LlmError)?.kind) {
                                                    LlmError.Kind.MISSING_KEY -> R.string.translation_error_missing_key
                                                    LlmError.Kind.UNAUTHORIZED -> R.string.translation_error_key
                                                    LlmError.Kind.PAYMENT -> R.string.translation_error_payment
                                                    LlmError.Kind.FORBIDDEN -> R.string.translation_error_forbidden
                                                    LlmError.Kind.NOT_FOUND -> R.string.translation_error_model
                                                    LlmError.Kind.RATE_LIMIT -> R.string.translation_error_rate
                                                    LlmError.Kind.UNREACHABLE -> R.string.translation_error_unreachable
                                                    else -> R.string.translation_error_response
                                                })
                                                fixOcrError = (error as? LlmError)?.detail?.let { "$reason\n$it" } ?: reason
                                            }
                                        } finally { if (request == ocrRequest) fixingOcr = false }
                                    }
                                }) { Text(stringResource(R.string.pdf_ocr_fix_ai)) }
                                TextButton(onClick = { clipboard.setText(AnnotatedString(text)) }) {
                                    Text(stringResource(R.string.selection_action_copy))
                                }
                                TextButton(onClick = { recognizedText = null; translate(text) }) {
                                    Text(stringResource(R.string.pdf_translate))
                                }
                                if (text.trim().split(Regex("\\s+")).size <= 8) {
                                    TextButton(onClick = { dictionary = text to text }) {
                                        Text(stringResource(R.string.pdf_save_phrase))
                                    }
                                }
                                TextButton(enabled = !addingText && !fixingOcr, onClick = {
                                    addingText = true
                                    addFailed = false
                                    scope.launch {
                                        try {
                                            vm.addRecognizedText(document.id, text)
                                            onBodyUpdated()
                                            recognizedText = null
                                        } catch (cancelled: CancellationException) { throw cancelled
                                        } catch (_: Exception) { addFailed = true
                                        } finally { addingText = false }
                                    }
                                }) { Text(stringResource(R.string.pdf_add_to_text)) }
                            }
                            if (fixingOcr || addingText) CircularProgressIndicator()
                            fixOcrError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                            if (addFailed) Text(stringResource(R.string.pdf_add_failed), color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
    dictionary?.let { (word, sentence) ->
        DictionarySheet(textId = document.id, word = word, sentence = sentence, onDismiss = { dictionary = null })
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun PdfPage(
    renderer: PdfPageRenderer, layout: PdfWordLayout?, page: Int,
    lassoMode: Boolean, onRegion: (List<PdfPoint>, Int) -> Unit,
    onDoubleTap: (Offset) -> Unit, selection: PdfSelection?, onSelection: (PdfSelection?) -> Unit,
    onWord: (List<PdfWord>, Int) -> Unit, onError: () -> Unit
) {
    val doubleTap by rememberUpdatedState(onDoubleTap)
    var lassoPoints by remember { mutableStateOf<List<Offset>>(emptyList()) }
    LaunchedEffect(lassoMode) { lassoPoints = emptyList() }
    var words by remember(layout, page) { mutableStateOf<List<PdfWord>?>(null) }
    var renderFailed by remember(renderer, page) { mutableStateOf(false) }
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    LaunchedEffect(layout, page) {
        try { words = layout?.words(page) ?: emptyList() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { onError() }
    }
    BoxWithConstraints(Modifier.fillMaxWidth().background(Color.White)) {
        val widthPx = with(LocalDensity.current) { maxWidth.roundToPx() }
        val rendered by produceState<android.graphics.Bitmap?>(null, renderer, page, widthPx) {
            try { value = renderer.render(page, widthPx.coerceAtMost(4096)); renderFailed = false }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { renderFailed = true; onError() }
        }
        val bitmap = rendered
        if (renderFailed) Text(stringResource(R.string.pdf_page_failed), Modifier.padding(16.dp))
        else if (bitmap == null) Box(Modifier.fillMaxWidth().aspectRatio(1f / renderer.pageAspects[page].coerceAtLeast(0.01f)),
            contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        else Column {
            Box(Modifier.fillMaxWidth().aspectRatio(bitmap.width.toFloat() / bitmap.height)
                .onGloballyPositioned { coordinates = it }
                .pointerInput(lassoMode) {
                    if (!lassoMode) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        lassoPoints = listOf(down.position)
                        var cancelled = false
                        do {
                            val event = awaitPointerEvent()
                            if (event.changes.count { it.pressed } > 1) cancelled = true
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            change.consume()
                            if (!cancelled) lassoPoints = lassoPoints + change.position
                            if (!change.pressed) {
                                if (!cancelled) {
                                    val path = normalizeRegionPath(lassoPoints.map { PdfPoint(it.x, it.y) }, size.width.toFloat(), size.height.toFloat())
                                    if (regionBounds(path) != null) onRegion(path, widthPx)
                                }
                                break
                            }
                        } while (true)
                        lassoPoints = emptyList()
                    }
                }
                .pointerInput(words, lassoMode) {
                    if (lassoMode) return@pointerInput
                    detectTapGestures(onDoubleTap = { doubleTap(it) }, onTap = { point ->
                        val list = words.orEmpty()
                        val hit = hitTestWord(list, point.x / size.width, point.y / size.height, .006f)
                        if (hit == null) onSelection(null) else onWord(list, list.indexOf(hit))
                    })
                }
                .pointerInput(words, lassoMode) {
                    if (lassoMode) return@pointerInput
                    var start = -1
                    var position = Offset.Zero
                    fun select(at: Offset) {
                        val list = words.orEmpty()
                        val hit = hitTestWord(list, at.x / size.width, at.y / size.height, .015f) ?: return
                        val end = list.indexOf(hit)
                        if (start < 0) start = end
                        val anchor = coordinates?.takeIf { it.isAttached }?.localToWindow(at) ?: return
                        onSelection(PdfSelection(list, start, end, Rect(anchor, Size(1f, 1f))))
                    }
                    detectDragGesturesAfterLongPress(
                        onDragStart = { start = -1; position = it; select(it) },
                        onDrag = { change, delta -> change.consume(); position += delta; select(position) },
                        onDragEnd = { start = -1 }, onDragCancel = { start = -1 })
                }) {
                Image(bitmap.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
                Canvas(Modifier.matchParentSize()) {
                    if (lassoPoints.size > 1) {
                        val path = Path().apply {
                            moveTo(lassoPoints[0].x, lassoPoints[0].y)
                            lassoPoints.drop(1).forEach { lineTo(it.x, it.y) }
                            close()
                        }
                        drawPath(path, Color(0x333399FF))
                        drawPath(path, Color(0xFF3366CC), style = Stroke(width = 2.dp.toPx()))
                    }
                    selection?.takeIf { it.words === words }?.selected?.forEach { word ->
                        val b = word.bounds
                        drawRect(Color(0x663399FF), Offset(b.left * size.width, b.top * size.height), Size((b.right - b.left) * size.width, (b.bottom - b.top) * size.height))
                    }
                }
            }
            if (words?.isEmpty() == true) {
                Text(stringResource(R.string.pdf_ocr_coming), color = Color.DarkGray, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(12.dp))
            }
        }
    }
}
