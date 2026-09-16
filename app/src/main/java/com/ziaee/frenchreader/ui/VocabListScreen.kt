package com.ziaee.frenchreader.ui

import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ziaee.frenchreader.data.AppDatabase
import com.ziaee.frenchreader.data.VocabEntry
import com.ziaee.frenchreader.data.VocabList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

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

    fun deleteList(list: VocabList) {
        viewModelScope.launch {
            db.vocabDao().clearListId(list.id)
            db.vocabListDao().delete(list)
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
    var selectedScope by remember { mutableStateOf(VOCAB_SCOPE_ALL) }
    var showNewListDialog by remember { mutableStateOf(false) }

    val scoped = remember(entries, selectedScope) {
        when (selectedScope) {
            VOCAB_SCOPE_ALL -> entries
            VOCAB_SCOPE_UNFILED -> entries.filter { it.listId == null }
            else -> entries.filter { it.listId == selectedScope }
        }
    }
    val filtered = remember(scoped, query) {
        if (query.isBlank()) scoped
        else scoped.filter {
            it.word.contains(query, ignoreCase = true) || it.sentence.contains(query, ignoreCase = true)
        }
    }
    val dueCount = remember(scoped) {
        val now = System.currentTimeMillis()
        scoped.count { !it.learned && it.nextReviewAtMs <= now }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("لغات ذخیره‌شده") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "بازگشت")
                    }
                },
                actions = {
                    IconButton(onClick = { meaningsVisible = !meaningsVisible }) {
                        Icon(
                            if (meaningsVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = "نمایش/پنهان‌کردن معنی"
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                item {
                    FilterChip(
                        selected = selectedScope == VOCAB_SCOPE_ALL,
                        onClick = { selectedScope = VOCAB_SCOPE_ALL },
                        label = { Text("همه") }
                    )
                }
                item {
                    FilterChip(
                        selected = selectedScope == VOCAB_SCOPE_UNFILED,
                        onClick = { selectedScope = VOCAB_SCOPE_UNFILED },
                        label = { Text("بدون دسته") }
                    )
                }
                items(lists, key = { it.id }) { list ->
                    InputChip(
                        selected = selectedScope == list.id,
                        onClick = { selectedScope = list.id },
                        label = { Text(list.name) },
                        trailingIcon = {
                            IconButton(
                                onClick = {
                                    if (selectedScope == list.id) selectedScope = VOCAB_SCOPE_ALL
                                    vm.deleteList(list)
                                },
                                modifier = Modifier.size(18.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "حذف لیست «${list.name}»")
                            }
                        }
                    )
                }
                item {
                    AssistChip(
                        onClick = { showNewListDialog = true },
                        leadingIcon = { Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        label = { Text("لیست جدید") }
                    )
                }
            }

            Card(
                onClick = { if (dueCount > 0) onOpenReview(selectedScope) },
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
                    Text(
                        if (dueCount > 0) "مرور امروز ($dueCount کلمه آمادهٔ مرور)" else "چیزی برای مرور نیست",
                        modifier = Modifier.weight(1f)
                    )
                    if (dueCount > 0) Icon(Icons.Default.ChevronRight, contentDescription = null)
                }
            }

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("جست‌وجو در لغات") },
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                singleLine = true
            )
            if (filtered.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        if (entries.isEmpty())
                            "هنوز لغتی ذخیره نشده. در صفحهٔ مطالعه، روی کلمه‌ای نگه دارید تا دیکشنری باز شود."
                        else "چیزی پیدا نشد."
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(filtered, key = { it.id }) { entry ->
                        VocabRow(
                            entry = entry,
                            showMeaning = meaningsVisible,
                            onClick = { editing = entry },
                            onToggleLearned = { vm.setLearned(entry, !entry.learned) },
                            onDelete = { vm.delete(entry) }
                        )
                        HorizontalDivider()
                    }
                }
            }
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

    if (showNewListDialog) {
        var newListName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showNewListDialog = false },
            title = { Text("لیست جدید") },
            text = {
                OutlinedTextField(
                    value = newListName,
                    onValueChange = { newListName = it },
                    label = { Text("نام لیست") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.createList(newListName) { newId -> selectedScope = newId }
                    showNewListDialog = false
                }) { Text("ساخت") }
            },
            dismissButton = {
                TextButton(onClick = { showNewListDialog = false }) { Text("انصراف") }
            }
        )
    }
}

@Composable
private fun VocabRow(
    entry: VocabEntry,
    showMeaning: Boolean,
    onClick: () -> Unit,
    onToggleLearned: () -> Unit,
    onDelete: () -> Unit
) {
    val isDue = !entry.learned && entry.nextReviewAtMs <= System.currentTimeMillis()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(entry.word, style = MaterialTheme.typography.titleMedium)
                if (entry.learned) {
                    Spacer(Modifier.width(8.dp))
                    AssistChip(onClick = onToggleLearned, label = { Text("یاد گرفتم") })
                }
            }
            Text(
                entry.sentence,
                style = MaterialTheme.typography.bodySmall,
                fontStyle = FontStyle.Italic
            )
            if (showMeaning && !entry.meaning.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(entry.meaning, style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(2.dp))
            Text(
                "جعبه ${entry.leitnerBox}" + if (isDue) " • آمادهٔ مرور" else "",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (!entry.learned) {
            TextButton(onClick = onToggleLearned) { Text("یاد گرفتم") }
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, contentDescription = "حذف")
        }
    }
}
