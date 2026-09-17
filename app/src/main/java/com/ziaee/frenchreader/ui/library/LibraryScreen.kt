package com.ziaee.frenchreader.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ziaee.frenchreader.R
import com.ziaee.frenchreader.data.TextDocument
import com.ziaee.frenchreader.ui.components.EditorialBottomBar
import com.ziaee.frenchreader.ui.components.EditorialDestination
import com.ziaee.frenchreader.ui.components.EditorialEmptyState
import com.ziaee.frenchreader.ui.components.EditorialTopAppBar
import com.ziaee.frenchreader.ui.components.MetadataBadge
import com.ziaee.frenchreader.ui.home.DocumentThumbnail
import com.ziaee.frenchreader.ui.shared.AddTextHost
import com.ziaee.frenchreader.ui.shared.rememberAddTextUiState
import com.ziaee.frenchreader.ui.shared.rememberFilePickerLauncher
import com.ziaee.frenchreader.ui.statistics.isTextCompleted
import com.ziaee.frenchreader.ui.theme.FrenchReaderDesign

/**
 * The full saved-text collection, searchable and sortable, entirely local
 * and offline -- see the design doc's Library section. Reached from Home's
 * "See All" or its own bottom-navigation tab.
 */
@Composable
fun LibraryScreen(onOpenText: (Long) -> Unit, onOpenHome: () -> Unit) {
    val vm: LibraryViewModel = viewModel()
    val state by vm.uiState.collectAsState()

    val addTextState = rememberAddTextUiState()
    val openFilePicker = rememberFilePickerLauncher(addTextState)
    val untitledFallback = stringResource(R.string.text_untitled)

    LibraryContent(
        state = state,
        onQueryChange = { vm.setQuery(it) },
        onSortSelect = { vm.setSort(it) },
        onOpen = { onOpenText(it.id) },
        onDelete = { vm.delete(it) },
        onAddText = { addTextState.openBlank() },
        onFilePickerClick = openFilePicker,
        onOpenHome = onOpenHome
    )

    AddTextHost(addTextState) { title, body ->
        vm.pasteText(title.ifBlank { untitledFallback }, body) { id -> onOpenText(id) }
    }
}

/** Stateless Library layout, hoisted out so a UI test can drive it with a
 * static [LibraryUiState] instead of a real database-backed ViewModel. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryContent(
    state: LibraryUiState,
    onQueryChange: (String) -> Unit,
    onSortSelect: (LibrarySort) -> Unit,
    onOpen: (TextDocument) -> Unit,
    onDelete: (TextDocument) -> Unit,
    onAddText: () -> Unit,
    onFilePickerClick: () -> Unit,
    onOpenHome: () -> Unit
) {
    var sortMenuExpanded by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<TextDocument?>(null) }

    Scaffold(
        topBar = {
            EditorialTopAppBar(
                title = stringResource(R.string.nav_library),
                actions = {
                    IconButton(onClick = onFilePickerClick) {
                        Icon(Icons.Default.FileOpen, contentDescription = stringResource(R.string.accessibility_import_file))
                    }
                    Box {
                        IconButton(onClick = { sortMenuExpanded = true }) {
                            Icon(Icons.Default.Sort, contentDescription = stringResource(R.string.accessibility_sort))
                        }
                        DropdownMenu(expanded = sortMenuExpanded, onDismissRequest = { sortMenuExpanded = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.library_sort_newest)) },
                                onClick = { onSortSelect(LibrarySort.NEWEST); sortMenuExpanded = false }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.library_sort_title)) },
                                onClick = { onSortSelect(LibrarySort.TITLE); sortMenuExpanded = false }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.library_sort_last_read)) },
                                onClick = { onSortSelect(LibrarySort.LAST_READ); sortMenuExpanded = false }
                            )
                        }
                    }
                }
            )
        },
        bottomBar = {
            EditorialBottomBar(
                selectedDestination = EditorialDestination.LIBRARY,
                onHome = onOpenHome,
                onLibrary = {},
                onAddText = onAddText
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            // A real live-as-you-type text field, restyled with editorial
            // tokens -- unlike Home's EditorialSearchEntry (a non-editable
            // trigger that opens a separate search sheet), Library filters
            // its own list directly as the user types.
            OutlinedTextField(
                value = state.query,
                onValueChange = onQueryChange,
                placeholder = { Text(stringResource(R.string.library_search_hint)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = stringResource(R.string.accessibility_search)) },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = FrenchReaderDesign.spacing.small, vertical = FrenchReaderDesign.spacing.xSmall)
            )

            when {
                state.documents.isEmpty() && !state.isSearching -> EditorialEmptyState(
                    icon = Icons.Default.Article,
                    title = stringResource(R.string.library_empty_title),
                    body = stringResource(R.string.library_empty_body),
                    actionLabel = stringResource(R.string.action_add_text),
                    onAction = onAddText,
                    modifier = Modifier.fillMaxSize()
                )
                state.documents.isEmpty() -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(R.string.library_no_results, state.query),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = FrenchReaderDesign.spacing.medium)
                    )
                }
                else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(state.documents, key = { it.id }) { doc ->
                        LibraryRow(doc, onClick = { onOpen(doc) }, onDeleteRequest = { pendingDelete = doc })
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    pendingDelete?.let { doc ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.library_delete_confirm_title)) },
            text = { Text(stringResource(R.string.library_delete_confirm_message)) },
            confirmButton = {
                TextButton(onClick = { onDelete(doc); pendingDelete = null }) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }
}

@Composable
private fun LibraryRow(doc: TextDocument, onClick: () -> Unit, onDeleteRequest: () -> Unit) {
    var menuExpanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DocumentThumbnail(doc, modifier = Modifier.size(FrenchReaderDesign.sizes.thumbnailSmall))
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(doc.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            Row {
                doc.sourceName?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = FrenchReaderDesign.spacing.half)
                    )
                }
                val readLabel = if (isTextCompleted(doc)) {
                    stringResource(R.string.library_status_read)
                } else {
                    stringResource(R.string.library_status_in_progress)
                }
                MetadataBadge(text = readLabel)
            }
        }
        Box {
            IconButton(onClick = { menuExpanded = true }) {
                Icon(
                    Icons.Default.MoreVert,
                    contentDescription = stringResource(R.string.library_row_more_actions, doc.title)
                )
            }
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_delete)) },
                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                    onClick = { menuExpanded = false; onDeleteRequest() }
                )
            }
        }
    }
}
