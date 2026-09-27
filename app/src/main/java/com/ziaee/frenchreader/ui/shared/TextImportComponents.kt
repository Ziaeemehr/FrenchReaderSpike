package com.ziaee.frenchreader.ui.shared

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.activity.result.contract.ActivityResultContract
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import com.ziaee.frenchreader.R
import com.ziaee.frenchreader.content.ContentResult
import com.ziaee.frenchreader.content.ImageTextExtractor
import com.ziaee.frenchreader.ui.components.EditorialTopAppBar
import com.ziaee.frenchreader.ui.theme.FrenchReaderDesign
import com.ziaee.frenchreader.util.SharedTextHolder
import com.ziaee.frenchreader.util.MAX_TEXT_IMPORT_BYTES
import com.ziaee.frenchreader.util.readBytesLimited

/** State of the "find content" search sheet -- see ROADMAP.md section 6.
 * [Importing] tracks which result (by [ContentResult.ref]) is being
 * downloaded so its row can show a spinner without blocking the rest of
 * the list. */
sealed class ContentSearchUiState {
    data object Idle : ContentSearchUiState()
    data object Searching : ContentSearchUiState()
    data class Results(val items: List<ContentResult>) : ContentSearchUiState()
    data object NoResults : ContentSearchUiState()
    data class Error(val message: String) : ContentSearchUiState()
}

/**
 * Holds the "add/paste text" dialog's transient UI state (whether it's
 * open, and what prefilled title/body it should show) so a file pick or an
 * incoming share can open it the same way a bare tap on Add Text does.
 * Shared by Home and Library -- see the implementation plan's Task 6,
 * "reuse and relocate current search/add flows".
 */
@Stable
class AddTextUiState {
    var showDialog by mutableStateOf(false)
        private set
    var prefillTitle by mutableStateOf("")
        private set
    var prefillBody by mutableStateOf("")
        private set

    fun openBlank() {
        prefillTitle = ""
        prefillBody = ""
        showDialog = true
    }

    fun openWithPrefill(title: String, body: String) {
        prefillTitle = title
        prefillBody = body
        showDialog = true
    }

    fun dismiss() {
        showDialog = false
    }
}

@Composable
fun rememberAddTextUiState(): AddTextUiState = remember { AddTextUiState() }

private const val MAX_EDITABLE_PREFILL_CHARS = 30_000

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSourceSheet(
    visible: Boolean,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onPasteText: () -> Unit,
    onImportFile: () -> Unit,
    onImportFolder: () -> Unit,
    onImportPdf: () -> Unit,
    onImportImages: () -> Unit
) {
    if (!visible) return
    // Fully expanded, scrollable and padded past the navigation bar: in the
    // default half-expanded state the last option sat under the nav bar.
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
      Column(Modifier.navigationBarsPadding().verticalScroll(rememberScrollState())) {
        Text(
            text = stringResource(R.string.add_source_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
        )
        AddSourceRow(Icons.Default.EditNote, R.string.add_source_paste_title, R.string.add_source_paste_subtitle) {
            onDismiss(); onPasteText()
        }
        AddSourceRow(Icons.Default.FileOpen, R.string.add_source_file_title, R.string.add_source_file_subtitle) {
            onDismiss(); onImportFile()
        }
        AddSourceRow(Icons.Default.FolderOpen, R.string.add_source_folder_title, R.string.add_source_folder_subtitle) {
            onDismiss(); onImportFolder()
        }
        AddSourceRow(Icons.Default.PictureAsPdf, R.string.add_source_pdf_title, R.string.add_source_pdf_subtitle) {
            onDismiss(); onImportPdf()
        }
        AddSourceRow(Icons.Default.Image, R.string.add_source_image_title, R.string.add_source_image_subtitle) {
            onDismiss(); onImportImages()
        }
        if (isLoading) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center
            ) {
                CircularProgressIndicator(Modifier.size(28.dp))
                Spacer(Modifier.size(12.dp))
                Text(stringResource(R.string.text_import_extracting), modifier = Modifier.align(Alignment.CenterVertically))
            }
        } else {
            Spacer(Modifier.height(16.dp))
        }
      }
    }
}

@Composable
private fun AddSourceRow(icon: ImageVector, title: Int, subtitle: Int, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(stringResource(title)) },
        supportingContent = { Text(stringResource(subtitle), maxLines = 1) },
        leadingContent = { Icon(icon, contentDescription = null) },
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    )
}

@Composable
fun TextExtractionProgress(visible: Boolean) {
    if (!visible) return
    Dialog(onDismissRequest = {}) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.large)
                .padding(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(Modifier.size(28.dp))
            Spacer(Modifier.size(16.dp))
            Text(stringResource(R.string.text_import_extracting))
        }
    }
}

/**
 * Renders the add/paste dialog for [state] and wires up the incoming
 * Share/Open-With bridge ([SharedTextHolder]) to prefill and open it.
 * [onSave] persists the text and is called with the dialog already dismissed.
 */
@Composable
fun AddTextHost(
    state: AddTextUiState,
    onEpub: (Uri) -> Unit = {},
    onPdf: (Uri) -> Unit = {},
    onImages: (List<Uri>) -> Unit = {},
    onSave: (title: String, body: String) -> Unit
) {
    val incomingShare by SharedTextHolder.pending.collectAsState()
    LaunchedEffect(incomingShare) {
        incomingShare?.let {
            when {
                it.epubUri != null -> onEpub(it.epubUri)
                it.pdfUri != null -> onPdf(it.pdfUri)
                it.imageUris.isNotEmpty() -> onImages(it.imageUris)
                else -> state.openWithPrefill(it.suggestedTitle, it.body)
            }
            SharedTextHolder.consume()
        }
    }

    if (state.showDialog) {
        AddTextEditor(
            initialTitle = state.prefillTitle,
            initialBody = state.prefillBody,
            // A whole PDF/book in an editable TextField freezes the UI, so
            // long bodies are saved as-is and only the title is reviewable.
            bodyEditable = state.prefillBody.length <= MAX_EDITABLE_PREFILL_CHARS,
            onDismiss = { state.dismiss() },
            onSave = { title, body ->
                state.dismiss()
                onSave(title, body)
            }
        )
    }
}

data class TextExtractionImporter(
    val launchPdfPicker: () -> Unit,
    val launchImagePicker: () -> Unit,
    val importPdf: (Uri) -> Unit,
    val importImages: (List<Uri>) -> Unit
)

private const val KEY_LAST_PDF_URI = "last_pdf_uri"

private fun lastPdfPrefs(context: Context) =
    context.getSharedPreferences("import_prefs", Context.MODE_PRIVATE)

/**
 * [ActivityResultContracts.OpenDocument] that also passes
 * [DocumentsContract.EXTRA_INITIAL_URI], so the system picker opens in the
 * folder of the last imported file. The picker ignores a stale or
 * inaccessible URI and falls back to its default location. Apps can't set
 * the picker's sort order, only where it starts.
 */
private class OpenDocumentAt : ActivityResultContract<OpenDocumentAt.Input, Uri?>() {
    class Input(val mimeTypes: Array<String>, val initialUri: Uri?)

    private val base = ActivityResultContracts.OpenDocument()

    override fun createIntent(context: Context, input: Input): Intent =
        base.createIntent(context, input.mimeTypes).apply {
            input.initialUri?.let { putExtra(DocumentsContract.EXTRA_INITIAL_URI, it) }
        }

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? = base.parseResult(resultCode, intent)
}

@Composable
fun rememberTextExtractionImporter(
    state: AddTextUiState,
    extractPdf: (Uri, (Result<String>) -> Unit) -> Unit,
    extractImages: (List<Uri>, (Result<String>) -> Unit) -> Unit
): TextExtractionImporter {
    val context = LocalContext.current
    val untitledFallback = stringResource(R.string.text_untitled)
    val pdfNoText = stringResource(R.string.pdf_import_no_text)
    val imageNoText = stringResource(R.string.image_import_no_text)
    val pdfFailed = stringResource(R.string.pdf_import_failed)
    val imageFailed = stringResource(R.string.image_import_failed)
    val imageTextTitle = stringResource(R.string.image_text_title)

    val importPdf: (Uri) -> Unit = { uri ->
        extractPdf(uri) { result ->
            result.onSuccess { text ->
                if (text.isBlank()) Toast.makeText(context, pdfNoText, Toast.LENGTH_LONG).show()
                else state.openWithPrefill(queryDisplayName(context, uri) ?: untitledFallback, text)
            }.onFailure { Toast.makeText(context, pdfFailed, Toast.LENGTH_LONG).show() }
        }
    }
    val importImages: (List<Uri>) -> Unit = { uris ->
        val accepted = uris.take(ImageTextExtractor.MAX_IMAGES)
        if (accepted.isNotEmpty()) extractImages(accepted) { result ->
            result.onSuccess { text ->
                if (text.isBlank()) Toast.makeText(context, imageNoText, Toast.LENGTH_LONG).show()
                else state.openWithPrefill(imageTextTitle, text)
            }.onFailure { Toast.makeText(context, imageFailed, Toast.LENGTH_LONG).show() }
        }
    }
    val pdfPicker = rememberLauncherForActivityResult(OpenDocumentAt()) { uri ->
        uri?.let {
            // Only picker results are remembered: a URI shared in from
            // another app's provider is useless as a picker start location.
            lastPdfPrefs(context).edit().putString(KEY_LAST_PDF_URI, it.toString()).apply()
            importPdf(it)
        }
    }
    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(ImageTextExtractor.MAX_IMAGES)
    ) { uris -> importImages(uris) }

    return TextExtractionImporter(
        launchPdfPicker = {
            val last = lastPdfPrefs(context).getString(KEY_LAST_PDF_URI, null)?.let(Uri::parse)
            pdfPicker.launch(OpenDocumentAt.Input(arrayOf("application/pdf"), last))
        },
        launchImagePicker = {
            imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        },
        importPdf = importPdf,
        importImages = importImages
    )
}

/** Returns a launcher callback that opens the system file picker for a
 * TXT/MD document and, on success, opens [state]'s dialog prefilled with
 * the file's contents and display name. */
@Composable
fun rememberFilePickerLauncher(
    state: AddTextUiState,
    onEpub: (Uri) -> Unit = {},
    onMultiple: (List<Uri>) -> Unit = {}
): () -> Unit {
    val context = LocalContext.current
    val defaultTitle = stringResource(R.string.text_untitled)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        if (uris.size > 1) {
            onMultiple(uris)
            return@rememberLauncherForActivityResult
        }
        val uri = uris.single()
        val displayName = queryDisplayName(context, uri, stripExtension = false)
        val isEpub = context.contentResolver.getType(uri) == "application/epub+zip" ||
            displayName?.endsWith(".epub", ignoreCase = true) == true
        if (isEpub) {
            onEpub(uri)
            return@rememberLauncherForActivityResult
        }
        val body = try {
            context.contentResolver.openInputStream(uri)?.use {
                it.readBytesLimited(MAX_TEXT_IMPORT_BYTES).toString(Charsets.UTF_8)
            }
        } catch (e: Exception) {
            null
        }
        if (!body.isNullOrBlank()) {
            val title = queryDisplayName(context, uri) ?: defaultTitle
            state.openWithPrefill(title, body)
        }
    }
    return { launcher.launch(arrayOf("text/plain", "text/markdown", "text/*", "application/epub+zip")) }
}

fun queryDisplayName(context: Context, uri: Uri, stripExtension: Boolean = true): String? = try {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
        if (c.moveToFirst()) {
            val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0) c.getString(idx)?.let { name ->
                if (stripExtension) name.substringBeforeLast(".") else name
            } else null
        } else null
    }
} catch (e: Exception) {
    null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTextDialog(
    initialTitle: String = "",
    initialBody: String = "",
    bodyEditable: Boolean = true,
    dialogTitle: String? = null,
    confirmLabel: String? = null,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var title by remember(initialTitle) { mutableStateOf(initialTitle) }
    var body by remember(initialBody) { mutableStateOf(initialBody) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                dialogTitle
                    ?: stringResource(if (initialBody.isBlank()) R.string.add_text_title_new else R.string.add_text_title_review)
            )
        },
        text = {
            Column {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.add_text_field_title)) },
                    modifier = Modifier.fillMaxWidth()
                )
                if (bodyEditable) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = body,
                        onValueChange = { body = it },
                        label = { Text(stringResource(R.string.add_text_field_body)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (!bodyEditable || body.isNotBlank()) onSave(title, body) }) {
                Text(confirmLabel ?: stringResource(R.string.add_text_save_open))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTextEditor(
    initialTitle: String = "",
    initialBody: String = "",
    bodyEditable: Boolean = true,
    dialogTitle: String? = null,
    confirmLabel: String? = null,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var title by remember(initialTitle) { mutableStateOf(initialTitle) }
    var body by remember(initialBody) { mutableStateOf(TextFieldValue(initialBody)) }
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val wordCount = remember(body.text) {
        body.text.trim().takeIf { it.isNotEmpty() }?.split(Regex("\\s+"))?.size ?: 0
    }
    val history = remember(initialBody) { mutableStateListOf(initialBody) }
    var historyIndex by remember(initialBody) { mutableStateOf(0) }
    var findVisible by remember { mutableStateOf(false) }
    var findQuery by remember { mutableStateOf("") }
    var replacement by remember { mutableStateOf("") }
    var matchCase by remember { mutableStateOf(false) }
    var wholeWord by remember { mutableStateOf(false) }
    var currentMatch by remember { mutableStateOf(0) }
    var toolsExpanded by remember { mutableStateOf(false) }
    var replacementNotice by remember { mutableStateOf<String?>(null) }
    var confirmDiscard by remember { mutableStateOf(false) }
    val findFocusRequester = remember { FocusRequester() }
    val bodyFocusRequester = remember { FocusRequester() }
    val matches = remember(body.text, findQuery, matchCase, wholeWord) {
        findMatches(body.text, findQuery, matchCase, wholeWord)
    }
    val hasChanges = title != initialTitle || body.text != initialBody

    fun pushSnapshot(text: String) {
        if (history.getOrNull(historyIndex) == text) return
        while (history.lastIndex > historyIndex) history.removeAt(history.lastIndex)
        history += text
        if (history.size > 100) history.removeAt(0) else historyIndex++
        historyIndex = history.lastIndex
    }

    fun applyBodyText(text: String, selection: TextRange = TextRange(text.length)) {
        pushSnapshot(body.text)
        body = TextFieldValue(
            text,
            TextRange(
                selection.start.coerceIn(0, text.length),
                selection.end.coerceIn(0, text.length)
            )
        )
        pushSnapshot(text)
    }

    fun requestDismiss() {
        if (hasChanges) confirmDiscard = true else onDismiss()
    }

    fun selectMatch(index: Int) {
        if (matches.isEmpty()) return
        currentMatch = (index % matches.size + matches.size) % matches.size
        val range = matches[currentMatch]
        body = body.copy(selection = TextRange(range.first, range.last + 1))
        bodyFocusRequester.requestFocus()
    }

    LaunchedEffect(body.text) {
        delay(800)
        pushSnapshot(body.text)
    }
    LaunchedEffect(findVisible) {
        if (findVisible) findFocusRequester.requestFocus()
    }
    LaunchedEffect(matches) {
        currentMatch = currentMatch.coerceIn(0, (matches.size - 1).coerceAtLeast(0))
    }
    LaunchedEffect(replacementNotice) {
        if (replacementNotice != null) {
            delay(2_000)
            replacementNotice = null
        }
    }

    Dialog(
        onDismissRequest = ::requestDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize().imePadding(),
            topBar = {
                EditorialTopAppBar(
                    title = dialogTitle
                        ?: stringResource(if (initialBody.isBlank()) R.string.add_text_title_new else R.string.add_text_title_review),
                    navigationIcon = {
                        IconButton(onClick = ::requestDismiss) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.action_cancel))
                        }
                    },
                    actions = {
                        if (bodyEditable) {
                            IconButton(
                                onClick = {
                                    pushSnapshot(body.text)
                                    if (historyIndex > 0) {
                                        historyIndex--
                                        body = historyValue(body.text, history[historyIndex])
                                    }
                                },
                                enabled = historyIndex > 0 || body.text != history[historyIndex]
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Undo, stringResource(R.string.editor_undo))
                            }
                            IconButton(
                                onClick = {
                                    if (historyIndex < history.lastIndex) {
                                        historyIndex++
                                        body = historyValue(body.text, history[historyIndex])
                                    }
                                },
                                enabled = historyIndex < history.lastIndex
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Redo, stringResource(R.string.editor_redo))
                            }
                            IconButton(onClick = { findVisible = !findVisible }) {
                                Icon(Icons.Default.Search, stringResource(R.string.editor_find))
                            }
                            Box {
                                IconButton(onClick = { toolsExpanded = true }) {
                                    Icon(Icons.Default.MoreVert, stringResource(R.string.editor_tools))
                                }
                                DropdownMenu(expanded = toolsExpanded, onDismissRequest = { toolsExpanded = false }) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.editor_join_lines)) },
                                        onClick = {
                                            toolsExpanded = false
                                            applyBodyText(joinWrappedLines(body.text))
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.editor_tidy_spaces)) },
                                        onClick = {
                                            toolsExpanded = false
                                            applyBodyText(tidyWhitespace(body.text))
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.editor_copy_all)) },
                                        leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                                        onClick = {
                                            toolsExpanded = false
                                            clipboard.setText(AnnotatedString(body.text))
                                        }
                                    )
                                }
                            }
                        }
                        Button(
                            onClick = { onSave(title, body.text) },
                            enabled = !bodyEditable || body.text.isNotBlank(),
                            modifier = Modifier.padding(end = FrenchReaderDesign.spacing.xSmall)
                        ) { Text(confirmLabel ?: stringResource(R.string.add_text_save_open)) }
                    }
                )
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier.fillMaxSize().padding(innerPadding)
                    .padding(horizontal = FrenchReaderDesign.spacing.medium)
            ) {
                TextField(
                    value = title,
                    onValueChange = { title = it },
                    placeholder = { Text(stringResource(R.string.add_text_field_title)) },
                    textStyle = MaterialTheme.typography.headlineMedium,
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.background,
                        unfocusedContainerColor = MaterialTheme.colorScheme.background,
                        focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                        unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                if (bodyEditable && findVisible) {
                    Column(Modifier.fillMaxWidth()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = findQuery,
                                onValueChange = { findQuery = it; currentMatch = 0 },
                                placeholder = { Text(stringResource(R.string.editor_find_hint)) },
                                singleLine = true,
                                modifier = Modifier.weight(1f).focusRequester(findFocusRequester)
                            )
                            Text(
                                stringResource(
                                    R.string.editor_match_count,
                                    if (matches.isEmpty()) 0 else currentMatch + 1,
                                    matches.size
                                ),
                                style = MaterialTheme.typography.labelMedium
                            )
                            IconButton(onClick = { selectMatch(currentMatch - 1) }, enabled = matches.isNotEmpty()) {
                                Icon(Icons.Default.KeyboardArrowUp, stringResource(R.string.editor_previous_match))
                            }
                            IconButton(onClick = { selectMatch(currentMatch + 1) }, enabled = matches.isNotEmpty()) {
                                Icon(Icons.Default.KeyboardArrowDown, stringResource(R.string.editor_next_match))
                            }
                            IconButton(onClick = { findVisible = false }) {
                                Icon(Icons.Default.Close, stringResource(R.string.editor_close_find))
                            }
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = replacement,
                                onValueChange = { replacement = it },
                                placeholder = { Text(stringResource(R.string.editor_replace_hint)) },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            FilterChip(
                                selected = matchCase,
                                onClick = { matchCase = !matchCase; currentMatch = 0 },
                                label = { Text(stringResource(R.string.editor_match_case_short)) }
                            )
                            Spacer(Modifier.size(4.dp))
                            FilterChip(
                                selected = wholeWord,
                                onClick = { wholeWord = !wholeWord; currentMatch = 0 },
                                label = { Text(stringResource(R.string.editor_whole_word)) }
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            TextButton(
                                enabled = matches.isNotEmpty(),
                                onClick = {
                                    val match = matches[currentMatch]
                                    // Only replace a match the user can see: the first tap selects it.
                                    if (body.selection != TextRange(match.first, match.last + 1)) {
                                        selectMatch(currentMatch)
                                        return@TextButton
                                    }
                                    val replaced = body.text.replaceRange(match, replacement)
                                    val remaining = findMatches(replaced, findQuery, matchCase, wholeWord)
                                    val nextIndex = remaining.indexOfFirst {
                                        it.first >= match.first + replacement.length
                                    }.takeIf { it >= 0 } ?: 0
                                    val nextSelection = remaining.getOrNull(nextIndex)?.let {
                                        TextRange(it.first, it.last + 1)
                                    } ?: TextRange(match.first + replacement.length)
                                    applyBodyText(
                                        replaced,
                                        nextSelection
                                    )
                                    currentMatch = nextIndex
                                    replacementNotice = null
                                }
                            ) { Text(stringResource(R.string.editor_replace)) }
                            TextButton(
                                enabled = matches.isNotEmpty(),
                                onClick = {
                                    val (replaced, count) = replaceAll(
                                        body.text, findQuery, replacement, matchCase, wholeWord
                                    )
                                    applyBodyText(replaced)
                                    replacementNotice = context.getString(R.string.editor_replaced_count, count)
                                }
                            ) { Text(stringResource(R.string.editor_replace_all)) }
                            replacementNotice?.let {
                                Text(it, style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row {
                        if (bodyEditable) {
                            TextButton(onClick = {
                                clipboard.getText()?.text?.takeIf { it.isNotEmpty() }?.let { pasted ->
                                    applyBodyText(if (body.text.isBlank()) pasted else "${body.text}\n$pasted")
                                }
                            }) { Text(stringResource(R.string.add_text_paste)) }
                            TextButton(onClick = { applyBodyText("") }, enabled = body.text.isNotEmpty()) {
                                Text(stringResource(R.string.add_text_clear))
                            }
                        }
                    }
                    Text(
                        stringResource(R.string.add_text_counts, wordCount, body.text.length),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (bodyEditable) {
                    TextField(
                        value = body,
                        onValueChange = {
                            if (it.text != body.text && historyIndex < history.lastIndex) {
                                while (history.lastIndex > historyIndex) history.removeAt(history.lastIndex)
                            }
                            body = it
                        },
                        placeholder = { Text(stringResource(R.string.add_text_body_hint)) },
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            fontFamily = FrenchReaderDesign.editorialTypography.articleHeadline.fontFamily
                        ),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                            unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent
                        ),
                        modifier = Modifier.fillMaxWidth().weight(1f)
                            .focusRequester(bodyFocusRequester)
                            .padding(bottom = FrenchReaderDesign.spacing.small)
                    )
                } else {
                    Text(
                        stringResource(R.string.add_text_full_text_note, wordCount),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(vertical = FrenchReaderDesign.spacing.xSmall)
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth().weight(1f)
                            .padding(bottom = FrenchReaderDesign.spacing.small)
                    ) {
                        Text(
                            text = body.text.take(2_000),
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontFamily = FrenchReaderDesign.editorialTypography.articleHeadline.fontFamily
                            ),
                            modifier = Modifier.padding(FrenchReaderDesign.spacing.small)
                                .verticalScroll(rememberScrollState())
                        )
                    }
                }
            }
        }
    }
    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text(stringResource(R.string.editor_discard_title)) },
            text = { Text(stringResource(R.string.editor_discard_message)) },
            confirmButton = {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.editor_discard)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDiscard = false }) {
                    Text(stringResource(R.string.editor_keep_editing))
                }
            }
        )
    }
}

/** Restores an undo/redo snapshot with the cursor at the first changed character, so long texts don't jump to the top. */
private fun historyValue(current: String, restored: String): TextFieldValue {
    val changedAt = current.commonPrefixWith(restored).length
    return TextFieldValue(restored, TextRange(changedAt))
}

/** Test tag for [FindArticleSheet]'s query field, so UI tests can assert it
 * grabs focus as soon as the sheet opens without a second tap. */
const val FIND_ARTICLE_QUERY_FIELD_TEST_TAG = "find_article_query_field"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FindArticleSheet(
    searchState: ContentSearchUiState,
    importingRef: String?,
    onSearch: (String) -> Unit,
    onSelect: (ContentResult) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val isSearching = searchState is ContentSearchUiState.Searching

    fun submit() {
        if (query.isNotBlank() && !isSearching) onSearch(query)
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        // The sheet's content is its own subcomposition (rendered in a
        // separate window/Popup), so requesting focus has to happen once
        // that subcomposition -- not FindArticleSheet's own -- is up, or
        // the FocusRequester throws "not initialized" (it isn't attached
        // to the field yet). Placing this LaunchedEffect here, alongside
        // the field it targets, is what makes that ordering line up.
        LaunchedEffect(Unit) {
            focusRequester.requestFocus()
            keyboardController?.show()
        }
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Text(stringResource(R.string.content_search_title), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(10.dp))

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text(stringResource(R.string.content_search_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { submit() }),
                trailingIcon = {
                    IconButton(onClick = { submit() }) {
                        Icon(Icons.Default.Search, contentDescription = stringResource(R.string.accessibility_search))
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .testTag(FIND_ARTICLE_QUERY_FIELD_TEST_TAG)
            )

            Spacer(Modifier.height(12.dp))

            // Search failures surface as a Snackbar (handled by the caller),
            // so there's no error branch to render here -- by the time this
            // recomposes, the state's already back to Idle.
            when (searchState) {
                ContentSearchUiState.Idle -> {}
                ContentSearchUiState.Searching -> {
                    Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                ContentSearchUiState.NoResults -> {
                    Text(stringResource(R.string.content_search_no_results, query))
                }
                is ContentSearchUiState.Error -> {}
                is ContentSearchUiState.Results -> {
                    LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                        items(searchState.items, key = { it.ref }) { result ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = importingRef == null) { onSelect(result) }
                                    .padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(result.title, style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        result.snippet,
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 2
                                    )
                                    Text("${result.sourceLabel} · ${result.lengthHint}", style = MaterialTheme.typography.labelSmall)
                                }
                                if (importingRef == result.ref) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                }
                            }
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}
