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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
    var body by remember(initialBody) { mutableStateOf(initialBody) }
    val clipboard = LocalClipboardManager.current
    val wordCount = remember(body) { body.trim().takeIf { it.isNotEmpty() }?.split(Regex("\\s+"))?.size ?: 0 }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize().imePadding(),
            topBar = {
                EditorialTopAppBar(
                    title = dialogTitle
                        ?: stringResource(if (initialBody.isBlank()) R.string.add_text_title_new else R.string.add_text_title_review),
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.action_cancel))
                        }
                    },
                    actions = {
                        Button(
                            onClick = { onSave(title, body) },
                            enabled = !bodyEditable || body.isNotBlank(),
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row {
                        if (bodyEditable) {
                            TextButton(onClick = {
                                clipboard.getText()?.text?.takeIf { it.isNotEmpty() }?.let { pasted ->
                                    body = if (body.isBlank()) pasted else "$body\n$pasted"
                                }
                            }) { Text(stringResource(R.string.add_text_paste)) }
                            TextButton(onClick = { body = "" }, enabled = body.isNotEmpty()) {
                                Text(stringResource(R.string.add_text_clear))
                            }
                        }
                    }
                    Text(
                        stringResource(R.string.add_text_counts, wordCount, body.length),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (bodyEditable) {
                    TextField(
                        value = body,
                        onValueChange = { body = it },
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
                            text = body.take(2_000),
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
