package com.ziaee.frenchreader.ui

import android.app.Application
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ziaee.frenchreader.R
import com.ziaee.frenchreader.content.AnkiImportRepository
import com.ziaee.frenchreader.content.AnkiImportResult
import com.ziaee.frenchreader.data.AppDatabase
import com.ziaee.frenchreader.data.VocabEntry
import com.ziaee.frenchreader.data.VocabList
import com.ziaee.frenchreader.data.VocabStatus
import com.ziaee.frenchreader.data.status
import com.ziaee.frenchreader.data.displayMeaning
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.room.withTransaction

/** Scope sentinels for [VocabListScreen]'s filter chips -- real list ids are
 * always >= 1 (Room autoGenerate), so these never collide with one. */
const val VOCAB_SCOPE_ALL = -1L
const val VOCAB_SCOPE_UNFILED = -2L

class VocabListViewModel(app: Application) : AndroidViewModel(app) {
    private val db = AppDatabase.get(app)
    private val _entries = MutableStateFlow<List<VocabEntry>>(emptyList())
    val entries: StateFlow<List<VocabEntry>> = _entries.asStateFlow()
    private val _lists = MutableStateFlow<List<VocabList>>(emptyList())
    val lists: StateFlow<List<VocabList>> = _lists.asStateFlow()

    init {
        viewModelScope.launch { db.vocabDao().observeAll().collect { _entries.value = it } }
        viewModelScope.launch { db.vocabListDao().observeAll().collect { _lists.value = it } }
    }

    fun setLearned(entry: VocabEntry, learned: Boolean) {
        viewModelScope.launch { db.vocabDao().update(entry.copy(learned = learned)) }
    }

    fun edit(entry: VocabEntry, word: String, meaning: String?, sentence: String) {
        viewModelScope.launch { db.vocabDao().update(entry.copy(word = word, meaning = meaning, sentence = sentence)) }
    }

    fun delete(entry: VocabEntry) {
        viewModelScope.launch { db.vocabDao().delete(entry) }
    }

    fun createList(name: String, onCreated: (Long) -> Unit) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val id = db.vocabListDao().insert(VocabList(name = name.trim()))
            onCreated(id)
        }
    }

    fun importAnki(json: String, keepProgress: Boolean, onDone: (AnkiImportResult?) -> Unit) {
        viewModelScope.launch {
            val result = try {
                AnkiImportRepository(db).import(json, keepProgress)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            onDone(result)
        }
    }

    fun deleteList(list: VocabList) {
        viewModelScope.launch {
            db.withTransaction {
                db.vocabDao().clearListId(list.id)
                db.vocabListDao().delete(list)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VocabListScreen(onBack: () -> Unit, onOpenReview: (Long) -> Unit) {
    val vm: VocabListViewModel = viewModel()
    val entries by vm.entries.collectAsState()
    val lists by vm.lists.collectAsState()
    var query by remember { mutableStateOf("") }
    var meaningsVisible by remember { mutableStateOf(true) }
    var editing by remember { mutableStateOf<VocabEntry?>(null) }
    var editingText by remember { mutableStateOf<VocabEntry?>(null) }
    var selectedScope by remember { mutableStateOf(VOCAB_SCOPE_ALL) }
    var selectedStatus by remember { mutableStateOf<VocabStatus?>(null) }
    var showNewListDialog by remember { mutableStateOf(false) }
    var scopeMenuExpanded by remember { mutableStateOf(false) }
    var pendingDeleteList by remember { mutableStateOf<VocabList?>(null) }
    var showManualDictionary by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var pendingAnkiUri by remember { mutableStateOf<Uri?>(null) }
    var keepAnkiProgress by remember { mutableStateOf(true) }
    val ankiPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) { keepAnkiProgress = true; pendingAnkiUri = uri }
    }

    val scoped = remember(entries, selectedScope) {
        when (selectedScope) {
            VOCAB_SCOPE_ALL -> entries
            VOCAB_SCOPE_UNFILED -> entries.filter { it.listId == null }
            else -> entries.filter { it.listId == selectedScope }
        }
    }
    val filtered = remember(scoped, query, selectedStatus) {
        val statusFiltered = selectedStatus?.let { status -> scoped.filter { it.status() == status } } ?: scoped
        if (query.isBlank()) statusFiltered
        else statusFiltered.filter {
            it.word.contains(query, ignoreCase = true) || it.sentence.contains(query, ignoreCase = true) || it.meaning?.contains(query, ignoreCase = true) == true
        }
    }
    val dueCount = remember(scoped) {
        val now = System.currentTimeMillis()
        scoped.count { !it.learned && it.nextReviewAtMs <= now }
    }

    pendingAnkiUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingAnkiUri = null },
            title = { Text(stringResource(R.string.anki_import_title)) },
            text = {
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { keepAnkiProgress = !keepAnkiProgress },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(checked = keepAnkiProgress, onCheckedChange = { keepAnkiProgress = it })
                    Text(stringResource(R.string.anki_import_keep_progress))
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val keep = keepAnkiProgress
                    pendingAnkiUri = null
                    scope.launch {
                        val json = try {
                            withContext(Dispatchers.IO) {
                                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            null
                        }
                        if (json == null) {
                            snackbarHostState.showSnackbar(context.getString(R.string.anki_import_failed))
                        } else {
                            vm.importAnki(json, keep) { r ->
                                scope.launch {
                                    snackbarHostState.showSnackbar(
                                        if (r == null) context.getString(R.string.anki_import_failed)
                                        else context.getString(R.string.anki_import_done, r.added, r.skipped, r.lists)
                                    )
                                }
                            }
                        }
                    }
                }) { Text(stringResource(R.string.anki_import_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingAnkiUri = null }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.vocab_screen_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.accessibility_back))
                    }
                },
                actions = {
                    IconButton(onClick = { showManualDictionary = true }) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = stringResource(R.string.manual_dictionary_action)
                        )
                    }
                    IconButton(onClick = { ankiPicker.launch(arrayOf("application/json", "*/*")) }) {
                        Icon(Icons.Default.Upload, contentDescription = stringResource(R.string.anki_import_action))
                    }
                    IconButton(onClick = { meaningsVisible = !meaningsVisible }) {
                        Icon(
                            if (meaningsVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = stringResource(R.string.accessibility_hide_meaning)
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
                OutlinedButton(
                    onClick = { scopeMenuExpanded = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        when (selectedScope) {
                            VOCAB_SCOPE_ALL -> stringResource(R.string.vocab_list_all)
                            VOCAB_SCOPE_UNFILED -> stringResource(R.string.vocab_list_uncategorized)
                            else -> lists.firstOrNull { it.id == selectedScope }?.name
                                ?: stringResource(R.string.vocab_list_all)
                        },
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                }
                DropdownMenu(
                    expanded = scopeMenuExpanded,
                    onDismissRequest = { scopeMenuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.vocab_list_all)) },
                        onClick = { selectedScope = VOCAB_SCOPE_ALL; scopeMenuExpanded = false }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.vocab_list_uncategorized)) },
                        onClick = { selectedScope = VOCAB_SCOPE_UNFILED; scopeMenuExpanded = false }
                    )
                    lists.forEach { list ->
                        DropdownMenuItem(
                            text = { Text(list.name) },
                            onClick = { selectedScope = list.id; scopeMenuExpanded = false },
                            trailingIcon = {
                                IconButton(onClick = {
                                    scopeMenuExpanded = false
                                    pendingDeleteList = list
                                }) {
                                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.accessibility_delete_list, list.name))
                                }
                            }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.vocab_list_new)) },
                        onClick = { showNewListDialog = true; scopeMenuExpanded = false },
                        leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) }
                    )
                }
            }

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (dueCount > 0) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Style, contentDescription = null)
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.vocab_due_now, dueCount), style = MaterialTheme.typography.titleSmall)
                                if (dueCount == 0) {
                                    Text(stringResource(R.string.vocab_nothing_due), style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            Button(
                                onClick = { onOpenReview(selectedScope) },
                                enabled = dueCount > 0
                            ) {
                                Text(stringResource(R.string.vocab_review_start))
                            }
                        }
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val statuses = VocabStatus.values()
                        val counts = statuses.associateWith { status -> scoped.count { it.status() == status } }
                        val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            statuses.take(2).forEach { StatusChip(it, counts.getValue(it), selectedStatus) { selectedStatus = if (selectedStatus == it) null else it } }
                        }
                        Box(Modifier.padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
                            if (scoped.isNotEmpty()) {
                                Canvas(Modifier.size(96.dp)) {
                                    var startAngle = -90f
                                    statuses.forEach { status ->
                                        val sweepAngle = 360f * counts.getValue(status) / scoped.size
                                        if (sweepAngle > 0f) drawArc(status.pieColor(dark), startAngle, sweepAngle, useCenter = true)
                                        startAngle += sweepAngle
                                    }
                                }
                            }
                        }
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            statuses.takeLast(2).forEach { StatusChip(it, counts.getValue(it), selectedStatus) { selectedStatus = if (selectedStatus == it) null else it } }
                        }
                    }
                }

                item {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = { Text(stringResource(R.string.vocab_search_hint)) },
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        singleLine = true
                    )
                }

                if (filtered.isEmpty()) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            Text(
                                if (entries.isEmpty())
                                    stringResource(R.string.vocab_empty_hint)
                                else stringResource(R.string.vocab_search_no_results)
                            )
                        }
                    }
                } else {
                    items(filtered, key = { it.id }) { entry ->
                        VocabRow(
                            entry = entry,
                            showMeaning = meaningsVisible,
                            onClick = { editing = entry },
                            onEdit = { editingText = entry },
                            onToggleLearned = { vm.setLearned(entry, !entry.learned) },
                            onDelete = { vm.delete(entry) }
                        )
                    }
                }
            }
        }
    }

    editingText?.let { entry ->
        VocabEditDialog(entry, onDismiss = { editingText = null }) { w, m, s ->
            vm.edit(entry, w, m, s); editingText = null
        }
    }

    editing?.let { entry ->
        DictionarySheet(
            textId = entry.textId,
            word = entry.word,
            sentence = entry.sentence,
            initialMeaning = entry.meaning,
            initialListId = entry.listId,
            isNew = false,
            onDismiss = { editing = null }
        )
    }

    ManualDictionaryHost(
        open = showManualDictionary,
        onDismiss = { showManualDictionary = false }
    )

    if (showNewListDialog) {
        var newListName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showNewListDialog = false },
            title = { Text(stringResource(R.string.vocab_list_new)) },
            text = {
                OutlinedTextField(
                    value = newListName,
                    onValueChange = { newListName = it },
                    label = { Text(stringResource(R.string.vocab_list_name_field)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.createList(newListName) { newId -> selectedScope = newId }
                    showNewListDialog = false
                }) { Text(stringResource(R.string.action_create)) }
            },
            dismissButton = {
                TextButton(onClick = { showNewListDialog = false }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }

    pendingDeleteList?.let { list ->
        AlertDialog(
            onDismissRequest = { pendingDeleteList = null },
            title = { Text(stringResource(R.string.vocab_delete_list_title)) },
            text = { Text(stringResource(R.string.vocab_delete_list_message, list.name)) },
            confirmButton = {
                TextButton(onClick = {
                    if (selectedScope == list.id) selectedScope = VOCAB_SCOPE_ALL
                    vm.deleteList(list)
                    pendingDeleteList = null
                }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteList = null }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }
}

@Composable
private fun StatusChip(status: VocabStatus, count: Int, selectedStatus: VocabStatus?, onClick: () -> Unit) {
    val statusColor = status.color()
    val containerColor = status.containerColor()
    FilterChip(
        modifier = Modifier.fillMaxWidth(),
        selected = selectedStatus == status,
        onClick = onClick,
        leadingIcon = { Box(Modifier.size(8.dp).background(statusColor, RoundedCornerShape(50))) },
        label = { Text("${stringResource(status.labelRes())} $count", style = MaterialTheme.typography.labelSmall, maxLines = 1) },
        colors = FilterChipDefaults.filterChipColors(
            containerColor = containerColor,
            labelColor = MaterialTheme.colorScheme.onSurface,
            iconColor = statusColor,
            selectedContainerColor = containerColor,
            selectedLabelColor = MaterialTheme.colorScheme.onSurface,
            selectedLeadingIconColor = statusColor
        )
    )
}

@Composable
private fun VocabRow(
    entry: VocabEntry,
    showMeaning: Boolean,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onToggleLearned: () -> Unit,
    onDelete: () -> Unit
) {
    val status = entry.status()
    val statusColor = status.color()
    val surface = MaterialTheme.colorScheme.surface
    // Very light status tint over the theme surface, so text always keeps the
    // theme's own on-surface contrast (whatever the app/system theme is).
    val cardColor = statusColor.copy(alpha = 0.08f).compositeOver(surface)
    val shape = RoundedCornerShape(16.dp)
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 5.dp),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = cardColor, contentColor = MaterialTheme.colorScheme.onSurface),
        border = BorderStroke(1.dp, statusColor.copy(alpha = 0.28f))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min).clickable(onClick = onClick),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.padding(vertical = 12.dp, horizontal = 10.dp).width(4.dp).fillMaxHeight()
                    .background(statusColor, RoundedCornerShape(2.dp))
            )
            Column(modifier = Modifier.weight(1f).padding(vertical = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        entry.word,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(Modifier.width(8.dp))
                    Row(
                        modifier = Modifier
                            .background(statusColor.copy(alpha = 0.16f), RoundedCornerShape(50))
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Box(Modifier.size(6.dp).background(statusColor, RoundedCornerShape(50)))
                        Text(
                            stringResource(status.labelRes()),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    entry.sentence,
                    style = MaterialTheme.typography.bodySmall,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                val displayMeaning = entry.displayMeaning()
                if (showMeaning && displayMeaning != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(displayMeaning, style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Row(modifier = Modifier.width(92.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        repeat(5) { index ->
                            Surface(
                                modifier = Modifier.weight(1f).height(5.dp),
                                shape = RoundedCornerShape(50),
                                color = if (index < entry.leitnerBox) leitnerBoxColor(index + 1)
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                            ) {}
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        nextReviewText(entry),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(onClick = onToggleLearned) {
                    Icon(
                        if (entry.learned) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                        contentDescription = stringResource(
                            if (entry.learned) R.string.vocab_mark_learning else R.string.vocab_mark_known
                        ),
                        tint = if (entry.learned) statusColor else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onEdit) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = stringResource(R.string.action_edit),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = stringResource(R.string.accessibility_delete),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun nextReviewText(entry: VocabEntry): String = when (
    val bucket = dueBucket(entry.nextReviewAtMs, System.currentTimeMillis(), entry.status() == VocabStatus.LEARNED)
) {
    DueBucket.Learned -> stringResource(R.string.vocab_next_review_learned)
    DueBucket.Today -> stringResource(R.string.vocab_next_review_today)
    is DueBucket.InDays -> if (bucket.days == 1L) {
        stringResource(R.string.vocab_next_review_tomorrow)
    } else {
        stringResource(R.string.vocab_next_review_in_days, bucket.days)
    }
}
