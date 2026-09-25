package com.ziaee.frenchreader.ui.library

import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.ziaee.frenchreader.R
import com.ziaee.frenchreader.data.TextDocument
import com.ziaee.frenchreader.ui.ComprehensionBadge
import com.ziaee.frenchreader.ui.components.EditorialBottomBar
import com.ziaee.frenchreader.ui.components.EditorialDestination
import com.ziaee.frenchreader.ui.components.EditorialEmptyState
import com.ziaee.frenchreader.ui.components.EditorialTopAppBar
import com.ziaee.frenchreader.ui.components.MetadataBadge
import com.ziaee.frenchreader.ui.home.DocumentThumbnail
import com.ziaee.frenchreader.ui.shared.AddTextDialog
import com.ziaee.frenchreader.ui.shared.AddTextHost
import com.ziaee.frenchreader.ui.shared.AddSourceSheet
import com.ziaee.frenchreader.ui.shared.TextExtractionProgress
import com.ziaee.frenchreader.ui.shared.rememberAddTextUiState
import com.ziaee.frenchreader.ui.shared.rememberFilePickerLauncher
import com.ziaee.frenchreader.ui.shared.rememberTextExtractionImporter
import com.ziaee.frenchreader.ui.theme.FrenchReaderDesign

/**
 * The full saved-text collection, searchable and sortable, entirely local
 * and offline -- see the design doc's Library section. Reached from Home's
 * "See All" or its own bottom-navigation tab.
 */
@Composable
fun LibraryScreen(
    onOpenText: (Long) -> Unit,
    onOpenHome: () -> Unit,
    onOpenResources: () -> Unit = {},
    onWords: () -> Unit = {}
) {
    val vm: LibraryViewModel = viewModel()
    val state by vm.uiState.collectAsState()
    val comprehensionScores by vm.comprehensionScores.collectAsState()

    val addTextState = rememberAddTextUiState()
    var showAddSourceSheet by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val untitledFallback = stringResource(R.string.text_untitled)
    val epubImportStarted = stringResource(R.string.epub_import_started)
    val epubImportDone = stringResource(R.string.epub_import_done)
    val epubImportAlready = stringResource(R.string.epub_import_already)
    val epubImportFailed = stringResource(R.string.epub_import_failed)
    val batchImportSummary = stringResource(R.string.batch_import_summary)
    val importEpub: (Uri) -> Unit = { uri ->
        Toast.makeText(context, epubImportStarted, Toast.LENGTH_SHORT).show()
        vm.importEpub(
            uri,
            onDone = { result ->
                val message = if (result.importedCount == 0) {
                    epubImportAlready.format(result.bookTitle)
                } else {
                    epubImportDone.format(result.importedCount, result.bookTitle)
                }
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            },
            onError = { Toast.makeText(context, epubImportFailed, Toast.LENGTH_LONG).show() }
        )
    }
    val showBatchResult: (com.ziaee.frenchreader.content.BatchImportResult) -> Unit = { result ->
        Toast.makeText(context, batchImportSummary.format(result.imported, result.skipped, result.failed), Toast.LENGTH_LONG).show()
    }
    val openFilePicker = rememberFilePickerLauncher(
        addTextState,
        onEpub = importEpub,
        onMultiple = { vm.importFiles(it, showBatchResult) }
    )
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let { vm.importTree(it, showBatchResult) }
    }
    val extractionImporter = rememberTextExtractionImporter(
        state = addTextState,
        extractPdf = vm::extractPdf,
        extractImages = vm::extractImages
    )

    LibraryContent(
        state = state,
        comprehensionScores = comprehensionScores,
        onRequestComprehension = vm::requestComprehension,
        onQueryChange = { vm.setQuery(it) },
        onSortSelect = { vm.setSort(it) },
        onOpen = { onOpenText(it.id) },
        onDelete = { vm.delete(it) },
        onEdit = { doc, title, body -> vm.updateText(doc, title, body) },
        onLoadBody = { doc, loaded -> vm.loadBody(doc, loaded) },
        onFolderFilterChange = vm::setFolderFilter,
        onTagFilterToggle = vm::toggleTagFilter,
        onCreateFolder = { vm.createFolder(it) },
        onCreateFolderIn = vm::createFolder,
        onRenameFolder = vm::renameFolder,
        onMoveFolder = vm::moveFolder,
        onDeleteFolder = vm::deleteFolder,
        onCreateTag = vm::createTag,
        onRenameTag = vm::renameTag,
        onDeleteTag = vm::deleteTag,
        onMoveToFolder = vm::moveToFolder,
        onSetTags = vm::setTags,
        onCreateFolderAndMove = { doc, name -> vm.createFolderAndMove(doc, name) },
        onCreateTagAndAssign = vm::createTagAndAssign,
        onAddText = { showAddSourceSheet = true },
        onFilePickerClick = openFilePicker,
        onFolderPickerClick = { folderPicker.launch(null) },
        onOpenHome = onOpenHome,
        onOpenResources = onOpenResources,
        onWords = onWords,
        onToggleSelection = vm::toggleSelection,
        onStartSelection = vm::startSelection,
        onSelectAll = vm::selectAll,
        onClearSelection = vm::clearSelection,
        onDeleteSelected = vm::deleteSelected,
        onMoveSelectedToFolder = vm::moveSelectedToFolder,
        onAddTagsToSelected = vm::addTagsToSelected,
        onCreateFolderAndMoveSelected = vm::createFolderAndMoveSelected,
        onCreateTagAndAddToSelected = vm::createTagAndAddToSelected
    )

    AddTextHost(
        addTextState,
        onEpub = importEpub,
        onPdf = extractionImporter.importPdf,
        onImages = extractionImporter.importImages
    ) { title, body ->
        vm.pasteText(title.ifBlank { untitledFallback }, body) { id -> onOpenText(id) }
    }
    AddSourceSheet(
        visible = showAddSourceSheet,
        isLoading = vm.isExtractingText,
        onDismiss = { showAddSourceSheet = false },
        onPasteText = addTextState::openBlank,
        onImportFile = openFilePicker,
        onImportFolder = { folderPicker.launch(null) },
        onImportPdf = extractionImporter.launchPdfPicker,
        onImportImages = extractionImporter.launchImagePicker
    )
    TextExtractionProgress(vm.isExtractingText)
}

/** Stateless Library layout, hoisted out so a UI test can drive it with a
 * static [LibraryUiState] instead of a real database-backed ViewModel. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryContent(
    state: LibraryUiState,
    comprehensionScores: Map<Long, Int?> = emptyMap(),
    onRequestComprehension: (TextDocument) -> Unit = {},
    onQueryChange: (String) -> Unit,
    onSortSelect: (LibrarySort) -> Unit,
    onOpen: (TextDocument) -> Unit,
    onDelete: (TextDocument) -> Unit,
    onAddText: () -> Unit,
    onFilePickerClick: () -> Unit,
    onFolderPickerClick: () -> Unit = {},
    onOpenHome: () -> Unit,
    onOpenResources: () -> Unit = {},
    onWords: () -> Unit = {},
    onEdit: (TextDocument, String, String) -> Unit = { _, _, _ -> },
    onLoadBody: (TextDocument, (String) -> Unit) -> Unit = { _, loaded -> loaded("") },
    onFolderFilterChange: (FolderFilter) -> Unit = {},
    onTagFilterToggle: (Long) -> Unit = {},
    onCreateFolder: (String) -> Unit = {},
    onRenameFolder: (Long, String) -> Unit = { _, _ -> },
    onDeleteFolder: (Long, Boolean) -> Unit = { _, _ -> },
    onCreateTag: (String) -> Unit = {},
    onRenameTag: (Long, String) -> Unit = { _, _ -> },
    onDeleteTag: (Long) -> Unit = {},
    onMoveToFolder: (TextDocument, Long?) -> Unit = { _, _ -> },
    onSetTags: (TextDocument, Set<Long>) -> Unit = { _, _ -> },
    onCreateFolderAndMove: (TextDocument, String) -> Unit = { _, _ -> },
    onCreateTagAndAssign: (TextDocument, String, Set<Long>) -> Unit = { _, _, _ -> },
    onCreateFolderIn: (String, Long?) -> Unit = { name, _ -> onCreateFolder(name) },
    onMoveFolder: (Long, Long?) -> Unit = { _, _ -> },
    onToggleSelection: (Long) -> Unit = {},
    onStartSelection: (Long) -> Unit = {},
    onSelectAll: (Set<Long>) -> Unit = {},
    onClearSelection: () -> Unit = {},
    onDeleteSelected: () -> Unit = {},
    onMoveSelectedToFolder: (Long?) -> Unit = {},
    onAddTagsToSelected: (Set<Long>) -> Unit = {},
    onCreateFolderAndMoveSelected: (String) -> Unit = {},
    onCreateTagAndAddToSelected: (String, Set<Long>) -> Unit = { _, _ -> }
) {
    var sortMenuExpanded by remember { mutableStateOf(false) }
    var folderMenuExpanded by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<TextDocument?>(null) }
    var pendingEdit by remember { mutableStateOf<TextDocument?>(null) }
    var pendingEditBody by remember { mutableStateOf<String?>(null) }
    var showOrganizer by remember { mutableStateOf(false) }
    var pendingMove by remember { mutableStateOf<TextDocument?>(null) }
    var pendingTags by remember { mutableStateOf<TextDocument?>(null) }
    var showBulkDelete by remember { mutableStateOf(false) }
    var showBulkMove by remember { mutableStateOf(false) }
    var showBulkTags by remember { mutableStateOf(false) }
    val selectedFolderId = (state.folderFilter as? FolderFilter.Folder)?.id
    val breadcrumb = selectedFolderId?.let { pathTo(state.folders, it) }.orEmpty()
    val flattenedFolders = flattenTree(state.folders)
    val visibleIds = state.documents.mapTo(mutableSetOf()) { it.id }
    val allVisibleSelected = visibleIds.isNotEmpty() && visibleIds.all(state.selectedIds::contains)

    BackHandler(enabled = state.isSelecting, onBack = onClearSelection)

    Scaffold(
        topBar = {
            EditorialTopAppBar(
                title = if (state.isSelecting) pluralStringResource(
                    R.plurals.library_selection_count, state.selectedIds.size, state.selectedIds.size
                ) else stringResource(R.string.nav_library),
                modifier = if (state.isSelecting) Modifier.testTag("selectionBar") else Modifier,
                titleModifier = if (state.isSelecting) Modifier.testTag("selectionCount") else Modifier,
                navigationIcon = {
                    if (state.isSelecting) IconButton(onClick = onClearSelection) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.library_selection_close))
                    }
                },
                actions = {
                    if (state.isSelecting) {
                        IconButton(onClick = { onSelectAll(visibleIds) }, modifier = Modifier.testTag("selectAll")) {
                            Icon(Icons.Default.SelectAll, contentDescription = stringResource(
                                if (allVisibleSelected) R.string.library_deselect_all else R.string.library_select_all
                            ))
                        }
                        IconButton(onClick = { showBulkDelete = true }, modifier = Modifier.testTag("bulkDelete")) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.library_bulk_delete))
                        }
                        IconButton(onClick = { showBulkMove = true }, modifier = Modifier.testTag("bulkMove")) {
                            Icon(Icons.Default.Folder, contentDescription = stringResource(R.string.library_bulk_move))
                        }
                        IconButton(onClick = { showBulkTags = true }, modifier = Modifier.testTag("bulkTags")) {
                            Icon(Icons.Default.Label, contentDescription = stringResource(R.string.library_bulk_tags))
                        }
                    } else {
                        IconButton(
                            onClick = { showOrganizer = true },
                            modifier = Modifier.testTag("manageLibraryOrganizer")
                        ) {
                            Icon(Icons.Default.Folder, contentDescription = stringResource(R.string.library_manage_organizer))
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
                }
            )
        },
        bottomBar = {
            EditorialBottomBar(
                selectedDestination = EditorialDestination.LIBRARY,
                onHome = onOpenHome,
                onLibrary = {},
                onAddText = onAddText,
                onWords = onWords,
                onResources = onOpenResources
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

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = FrenchReaderDesign.spacing.small)
            ) {
                val folderSelectionLabel = when (state.folderFilter) {
                    FolderFilter.All -> stringResource(R.string.library_folder_selection_all)
                    FolderFilter.Unfiled -> stringResource(R.string.library_filter_unfiled)
                    is FolderFilter.Folder -> breadcrumb.joinToString(" › ") { it.name }
                        .ifBlank { stringResource(R.string.library_folder_selection_all) }
                }
                OutlinedButton(
                    onClick = { folderMenuExpanded = true },
                    modifier = Modifier.fillMaxWidth().testTag("folderFilters")
                ) {
                    Text(folderSelectionLabel, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = null)
                }
                DropdownMenu(
                    expanded = folderMenuExpanded,
                    onDismissRequest = { folderMenuExpanded = false },
                    modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp)
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.library_filter_all)) },
                        onClick = {
                            onFolderFilterChange(FolderFilter.All)
                            folderMenuExpanded = false
                        },
                        modifier = Modifier.testTag("folderFilterAll")
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.library_filter_unfiled)) },
                        onClick = {
                            onFolderFilterChange(FolderFilter.Unfiled)
                            folderMenuExpanded = false
                        },
                        modifier = Modifier.testTag("folderFilterUnfiled")
                    )
                    flattenedFolders.forEach { (folder, depth) ->
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Spacer(Modifier.width((depth * 16).dp))
                                    Icon(Icons.Default.Folder, contentDescription = null)
                                    Spacer(Modifier.width(FrenchReaderDesign.spacing.xSmall))
                                    Text(folder.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            },
                            onClick = {
                                onFolderFilterChange(FolderFilter.Folder(folder.id))
                                folderMenuExpanded = false
                            },
                            modifier = Modifier.testTag("folderFilter_${folder.id}")
                        )
                    }
                }
            }

            if (state.tags.isNotEmpty()) {
                LazyRow(modifier = Modifier.fillMaxWidth().testTag("tagFilters")) {
                    items(state.tags, key = { it.id }) { tag ->
                        FilterChip(
                            selected = tag.id in state.selectedTagIds,
                            onClick = { onTagFilterToggle(tag.id) },
                            label = { Text(tag.name) },
                            leadingIcon = { Icon(Icons.Default.Label, contentDescription = null) },
                            modifier = Modifier.padding(horizontal = 4.dp).testTag("tagFilter_${tag.id}")
                        )
                    }
                }
            }

            when {
                state.documents.isEmpty() && state.totalDocumentCount == 0 && !state.isSearching -> EditorialEmptyState(
                    icon = Icons.Default.Article,
                    title = stringResource(R.string.library_empty_title),
                    body = stringResource(R.string.library_empty_body),
                    actionLabel = stringResource(R.string.action_add_text),
                    onAction = onAddText,
                    modifier = Modifier.fillMaxSize()
                )
                state.documents.isEmpty() -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        if (state.isFiltering) stringResource(R.string.library_no_matches_filters)
                        else stringResource(R.string.library_no_results, state.query),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = FrenchReaderDesign.spacing.medium)
                    )
                }
                else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(state.documents, key = { it.id }) { doc ->
                        LaunchedEffect(doc.id, doc.bodyPath, doc.rawText.length) {
                            onRequestComprehension(doc)
                        }
                        LibraryRow(
                            doc,
                            onClick = { if (state.isSelecting) onToggleSelection(doc.id) else onOpen(doc) },
                            onLongClick = { onStartSelection(doc.id) },
                            selected = doc.id in state.selectedIds,
                            selectionMode = state.isSelecting,
                            onEditRequest = {
                                pendingEdit = doc
                                if (doc.bodyPath != null) pendingEditBody = ""
                                else onLoadBody(doc) { body -> pendingEditBody = body }
                            },
                            onMoveRequest = { pendingMove = doc },
                            onTagsRequest = { pendingTags = doc },
                            folderName = doc.folderId?.let { folderId ->
                                pathTo(state.folders, folderId).takeIf { it.isNotEmpty() }
                                    ?.joinToString(" / ") { it.name }
                                    ?: state.folders.firstOrNull { it.id == folderId }?.name
                            },
                            tagNames = state.tags.filter { it.id in state.tagIdsByText[doc.id].orEmpty() }.map { it.name },
                            completed = state.completionByTextId[doc.id] == true,
                            comprehensionPercent = comprehensionScores[doc.id],
                            onDeleteRequest = { pendingDelete = doc }
                        )
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

    if (showBulkDelete) {
        AlertDialog(
            onDismissRequest = { showBulkDelete = false },
            title = { Text(pluralStringResource(R.plurals.library_bulk_delete_title, state.selectedIds.size, state.selectedIds.size)) },
            text = { Text(stringResource(R.string.library_bulk_delete_message)) },
            confirmButton = { TextButton(onClick = { showBulkDelete = false; onDeleteSelected() }) { Text(stringResource(R.string.action_delete)) } },
            dismissButton = { TextButton(onClick = { showBulkDelete = false }) { Text(stringResource(R.string.action_cancel)) } }
        )
    }

    if (showBulkMove) {
        MoveToFolderDialog(
            folders = state.folders,
            onDismiss = { showBulkMove = false },
            onMove = { showBulkMove = false; onMoveSelectedToFolder(it) },
            onCreateAndMove = { showBulkMove = false; onCreateFolderAndMoveSelected(it) }
        )
    }

    if (showBulkTags) {
        EditTagsDialog(
            tags = state.tags,
            initialTagIds = emptySet(),
            addMode = true,
            onDismiss = { showBulkTags = false },
            onSave = { showBulkTags = false; onAddTagsToSelected(it) },
            onCreateAndAssign = { name, tags -> showBulkTags = false; onCreateTagAndAddToSelected(name, tags) }
        )
    }

    pendingEdit?.let { doc -> pendingEditBody?.let { body ->
        AddTextDialog(
            initialTitle = doc.title,
            initialBody = body,
            bodyEditable = doc.bodyPath == null,
            dialogTitle = stringResource(R.string.edit_text_title),
            confirmLabel = stringResource(R.string.action_save),
            onDismiss = { pendingEdit = null; pendingEditBody = null },
            onSave = { title, body ->
                onEdit(doc, title, body)
                pendingEdit = null
                pendingEditBody = null
            }
        )
    } }


    if (showOrganizer) {
        LibraryOrganizerDialog(
            folders = state.folders,
            documents = state.allDocuments,
            tags = state.tags,
            onDismiss = { showOrganizer = false },
            onCreateFolder = onCreateFolder,
            onRenameFolder = onRenameFolder,
            onDeleteFolder = onDeleteFolder,
            onCreateTag = onCreateTag,
            onRenameTag = onRenameTag,
            onDeleteTag = onDeleteTag,
            onCreateFolderIn = onCreateFolderIn,
            onMoveFolder = onMoveFolder
        )
    }

    pendingMove?.let { doc ->
        MoveToFolderDialog(
            doc = doc,
            folders = state.folders,
            onDismiss = { pendingMove = null },
            onMove = { onMoveToFolder(doc, it); pendingMove = null },
            onCreateAndMove = { onCreateFolderAndMove(doc, it); pendingMove = null }
        )
    }

    pendingTags?.let { doc ->
        EditTagsDialog(
            doc = doc,
            tags = state.tags,
            initialTagIds = state.tagIdsByText[doc.id].orEmpty(),
            onDismiss = { pendingTags = null },
            onSave = { onSetTags(doc, it); pendingTags = null },
            onCreateAndAssign = { name, selected ->
                onCreateTagAndAssign(doc, name, selected); pendingTags = null
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LibraryRow(
    doc: TextDocument,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    selected: Boolean,
    selectionMode: Boolean,
    onEditRequest: () -> Unit,
    onMoveRequest: () -> Unit,
    onTagsRequest: () -> Unit,
    folderName: String?,
    tagNames: List<String>,
    completed: Boolean,
    comprehensionPercent: Int?,
    onDeleteRequest: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("libraryRow_${doc.id}")
            .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.background)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (selectionMode) {
            Checkbox(checked = selected, onCheckedChange = { onClick() }, modifier = Modifier.size(FrenchReaderDesign.sizes.thumbnailSmall))
        } else {
            DocumentThumbnail(doc, modifier = Modifier.size(FrenchReaderDesign.sizes.thumbnailSmall))
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(doc.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            Row {
                val organizerMetadata = buildList {
                    if (folderName != null) add(folderName)
                    addAll(tagNames)
                    if (doc.sourceName != null) add(doc.sourceName)
                }.joinToString(" · ")
                if (organizerMetadata.isNotEmpty()) {
                    Text(
                        organizerMetadata,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(end = FrenchReaderDesign.spacing.half)
                    )
                }
                val readLabel = when (readingStatus(doc, completed)) {
                    ReadingStatus.NOT_STARTED -> stringResource(R.string.library_status_new)
                    ReadingStatus.IN_PROGRESS -> stringResource(R.string.library_status_in_progress)
                    ReadingStatus.READ -> stringResource(R.string.library_status_read)
                }
                MetadataBadge(text = readLabel)
                comprehensionPercent?.let {
                    Spacer(Modifier.width(FrenchReaderDesign.spacing.half))
                    ComprehensionBadge(it)
                }
            }
        }
        if (!selectionMode) Box {
            IconButton(onClick = { menuExpanded = true }) {
                Icon(
                    Icons.Default.MoreVert,
                    contentDescription = stringResource(R.string.library_row_more_actions, doc.title)
                )
            }
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_edit)) },
                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                    onClick = { menuExpanded = false; onEditRequest() }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.library_move_to_folder)) },
                    leadingIcon = { Icon(Icons.Default.Folder, contentDescription = null) },
                    modifier = Modifier.testTag("moveToFolder_${doc.id}"),
                    onClick = { menuExpanded = false; onMoveRequest() }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.library_edit_tags)) },
                    leadingIcon = { Icon(Icons.Default.Label, contentDescription = null) },
                    modifier = Modifier.testTag("editTags_${doc.id}"),
                    onClick = { menuExpanded = false; onTagsRequest() }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_delete)) },
                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                    onClick = { menuExpanded = false; onDeleteRequest() }
                )
            }
        }
    }
}
