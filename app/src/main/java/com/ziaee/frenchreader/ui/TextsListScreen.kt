package com.ziaee.frenchreader.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ziaee.frenchreader.data.AppDatabase
import com.ziaee.frenchreader.data.NewsPrefs
import com.ziaee.frenchreader.data.TextDocument
import com.ziaee.frenchreader.news.NewsFetchResult
import com.ziaee.frenchreader.news.NewsFetcher
import com.ziaee.frenchreader.news.NewsSource
import com.ziaee.frenchreader.util.SharedTextHolder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/** Result of the last "دریافت خبر امروز" tap, surfaced as a Snackbar (or a
 * loading spinner while in flight) -- see [TextsListViewModel.fetchNews]. */
sealed class NewsFetchUiState {
    data object Idle : NewsFetchUiState()
    data object Loading : NewsFetchUiState()
    data class NoNewItem(val source: NewsSource) : NewsFetchUiState()
    data class FetchError(val source: NewsSource, val message: String) : NewsFetchUiState()
}

class TextsListViewModel(app: Application) : AndroidViewModel(app) {
    private val db = AppDatabase.get(app)
    private val _texts = MutableStateFlow<List<TextDocument>>(emptyList())
    val texts: StateFlow<List<TextDocument>> = _texts.asStateFlow()

    var newsFetchState by mutableStateOf<NewsFetchUiState>(NewsFetchUiState.Idle)
        private set

    init {
        viewModelScope.launch {
            db.textDao().observeAll().collect { _texts.value = it }
        }
    }

    fun addText(title: String, body: String, onDone: (Long) -> Unit) {
        viewModelScope.launch {
            val id = db.textDao().insert(
                TextDocument(title = title.ifBlank { "بدون عنوان" }, rawText = body)
            )
            onDone(id)
        }
    }

    fun delete(doc: TextDocument) {
        viewModelScope.launch { db.textDao().delete(doc) }
    }

    /**
     * Fetches [source]'s latest RSS item and, if it's new (see
     * [NewsPrefs]'s per-source guid tracking), creates a text from it and
     * opens it via [onOpen] -- exactly like opening a text imported from
     * Share/file. See ROADMAP.md section 1.
     */
    fun fetchNews(source: NewsSource, onOpen: (Long) -> Unit) {
        val context = getApplication<Application>()
        viewModelScope.launch {
            newsFetchState = NewsFetchUiState.Loading
            NewsPrefs.setLastSourceId(context, source.id)
            val lastGuid = NewsPrefs.getLastImportedGuid(context, source.id)
            when (val result = NewsFetcher.fetchLatest(source, lastGuid)) {
                is NewsFetchResult.NewItem -> {
                    val item = result.item
                    val dateLabel = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                    val id = db.textDao().insert(
                        TextDocument(title = "[$dateLabel] ${item.title}", rawText = item.body)
                    )
                    NewsPrefs.setLastImportedGuid(context, source.id, item.guid)
                    newsFetchState = NewsFetchUiState.Idle
                    onOpen(id)
                }
                NewsFetchResult.NoNewItem -> newsFetchState = NewsFetchUiState.NoNewItem(source)
                is NewsFetchResult.Error -> newsFetchState = NewsFetchUiState.FetchError(source, result.message)
            }
        }
    }

    fun dismissNewsMessage() {
        newsFetchState = NewsFetchUiState.Idle
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextsListScreen(onOpenText: (Long) -> Unit, onOpenVocab: () -> Unit) {
    val vm: TextsListViewModel = viewModel()
    val texts by vm.texts.collectAsState()
    val context = LocalContext.current

    var dialogPrefill by remember { mutableStateOf<Pair<String, String>?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var newsMenuExpanded by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    // Surfaces the outcome of "دریافت خبر امروز" as a one-off Snackbar; a
    // successful fetch instead navigates straight to the new text (handled
    // in the onClick below), so there's nothing to show for that case here.
    LaunchedEffect(vm.newsFetchState) {
        when (val s = vm.newsFetchState) {
            is NewsFetchUiState.NoNewItem -> {
                snackbarHostState.showSnackbar("خبر جدیدی از «${s.source.label}» منتشر نشده")
                vm.dismissNewsMessage()
            }
            is NewsFetchUiState.FetchError -> {
                snackbarHostState.showSnackbar("دریافت خبر از «${s.source.label}» ممکن نشد: ${s.message}")
                vm.dismissNewsMessage()
            }
            else -> {}
        }
    }

    // A share/open-with intent arriving in MainActivity lands here and
    // pre-fills the add-text dialog with the shared title/body.
    val incomingShare by SharedTextHolder.pending.collectAsState()
    LaunchedEffect(incomingShare) {
        incomingShare?.let {
            dialogPrefill = it.suggestedTitle to it.body
            showAddDialog = true
            SharedTextHolder.consume()
        }
    }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        val body = try {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
        } catch (e: Exception) {
            null
        }
        if (!body.isNullOrBlank()) {
            val title = queryDisplayName(context, uri) ?: "فایل وارد شده"
            dialogPrefill = title to body
            showAddDialog = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("متن‌ها") },
                actions = {
                    Box {
                        IconButton(
                            onClick = { newsMenuExpanded = true },
                            enabled = vm.newsFetchState !is NewsFetchUiState.Loading
                        ) {
                            if (vm.newsFetchState is NewsFetchUiState.Loading) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Newspaper, contentDescription = "دریافت خبر امروز")
                            }
                        }
                        DropdownMenu(
                            expanded = newsMenuExpanded,
                            onDismissRequest = { newsMenuExpanded = false }
                        ) {
                            NewsSource.entries.forEach { source ->
                                DropdownMenuItem(
                                    text = { Text(source.label) },
                                    onClick = {
                                        newsMenuExpanded = false
                                        vm.fetchNews(source) { id -> onOpenText(id) }
                                    }
                                )
                            }
                        }
                    }
                    IconButton(onClick = { filePicker.launch(arrayOf("text/plain", "text/markdown", "text/*")) }) {
                        Icon(Icons.Default.FileOpen, contentDescription = "افزودن از فایل (TXT/MD)")
                    }
                    IconButton(onClick = onOpenVocab) {
                        Icon(Icons.Default.MenuBook, contentDescription = "لغات ذخیره‌شده")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = { dialogPrefill = null; showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "افزودن متن")
            }
        }
    ) { padding ->
        if (texts.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("هنوز متنی اضافه نشده. با دکمهٔ + متن بچسبانید یا با دکمهٔ فایل بالا یک TXT/MD وارد کنید.")
            }
        } else {
            LazyColumn(modifier = Modifier.padding(padding).fillMaxSize()) {
                items(texts, key = { it.id }) { doc ->
                    TextRow(doc, onClick = { onOpenText(doc.id) }, onDelete = { vm.delete(doc) })
                    HorizontalDivider()
                }
            }
        }
    }

    if (showAddDialog) {
        AddTextDialog(
            initialTitle = dialogPrefill?.first.orEmpty(),
            initialBody = dialogPrefill?.second.orEmpty(),
            onDismiss = { showAddDialog = false; dialogPrefill = null },
            onSave = { title, body ->
                showAddDialog = false
                dialogPrefill = null
                vm.addText(title, body) { id -> onOpenText(id) }
            }
        )
    }
}

private fun queryDisplayName(context: Context, uri: Uri): String? = try {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
        if (c.moveToFirst()) {
            val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0) c.getString(idx)?.substringBeforeLast(".") else null
        } else null
    }
} catch (e: Exception) {
    null
}

@Composable
private fun TextRow(doc: TextDocument, onClick: () -> Unit, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(doc.title, style = MaterialTheme.typography.titleMedium)
            val preview = doc.rawText.take(80).replace("\n", " ")
            Text(preview, style = MaterialTheme.typography.bodySmall, maxLines = 1)
            val date = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(doc.createdAtMs))
            Text(date, style = MaterialTheme.typography.labelSmall)
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, contentDescription = "حذف")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddTextDialog(
    initialTitle: String = "",
    initialBody: String = "",
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var title by remember(initialTitle) { mutableStateOf(initialTitle) }
    var body by remember(initialBody) { mutableStateOf(initialBody) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialBody.isBlank()) "متن جدید (چسباندن متن)" else "بررسی متن وارد‌شده") },
        text = {
            Column {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("عنوان") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = body,
                    onValueChange = { body = it },
                    label = { Text("متن فرانسوی (Markdown مجاز است)") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { if (body.isNotBlank()) onSave(title, body) }) {
                Text("ذخیره و باز کردن")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("انصراف") }
        }
    )
}
