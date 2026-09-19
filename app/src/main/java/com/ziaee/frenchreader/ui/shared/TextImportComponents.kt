package com.ziaee.frenchreader.ui.shared

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.ziaee.frenchreader.R
import com.ziaee.frenchreader.content.ContentResult
import com.ziaee.frenchreader.util.SharedTextHolder

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

/**
 * Renders the add/paste dialog for [state] and wires up the incoming
 * Share/Open-With bridge ([SharedTextHolder]) to prefill and open it.
 * [onSave] persists the text and is called with the dialog already dismissed.
 */
@Composable
fun AddTextHost(
    state: AddTextUiState,
    onEpub: (Uri) -> Unit = {},
    onSave: (title: String, body: String) -> Unit
) {
    val incomingShare by SharedTextHolder.pending.collectAsState()
    LaunchedEffect(incomingShare) {
        incomingShare?.let {
            if (it.epubUri != null) onEpub(it.epubUri)
            else state.openWithPrefill(it.suggestedTitle, it.body)
            SharedTextHolder.consume()
        }
    }

    if (state.showDialog) {
        AddTextDialog(
            initialTitle = state.prefillTitle,
            initialBody = state.prefillBody,
            onDismiss = { state.dismiss() },
            onSave = { title, body ->
                state.dismiss()
                onSave(title, body)
            }
        )
    }
}

/** Returns a launcher callback that opens the system file picker for a
 * TXT/MD document and, on success, opens [state]'s dialog prefilled with
 * the file's contents and display name. */
@Composable
fun rememberFilePickerLauncher(
    state: AddTextUiState,
    onEpub: (Uri) -> Unit = {}
): () -> Unit {
    val context = LocalContext.current
    val defaultTitle = stringResource(R.string.text_untitled)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        val displayName = queryDisplayName(context, uri, stripExtension = false)
        val isEpub = context.contentResolver.getType(uri) == "application/epub+zip" ||
            displayName?.endsWith(".epub", ignoreCase = true) == true
        if (isEpub) {
            onEpub(uri)
            return@rememberLauncherForActivityResult
        }
        val body = try {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
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
