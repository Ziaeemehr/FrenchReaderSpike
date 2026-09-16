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
import com.ziaee.frenchreader.content.ArticleImportRepository
import com.ziaee.frenchreader.content.ContentArticle
import com.ziaee.frenchreader.content.ContentResult
import com.ziaee.frenchreader.content.ContentSource
import com.ziaee.frenchreader.content.FranceInfoContentSource
import com.ziaee.frenchreader.content.RfiFacileContentSource
import com.ziaee.frenchreader.content.VikidiaContentSource
import com.ziaee.frenchreader.data.TextDocument
import com.ziaee.frenchreader.images.ArticleImageStore
import com.ziaee.frenchreader.util.SharedTextHolder
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/** State of the "پیدا کردن مطلب" search sheet -- see ROADMAP.md section 6.
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

/** Every registered content source -- see ROADMAP.md section 6. Adding a
 * future source (Wikisource, Gutenberg, ...) means implementing
 * ContentSource and adding it here; nothing else in this file changes. */
private val CONTENT_SOURCES: List<ContentSource> = listOf(VikidiaContentSource, RfiFacileContentSource, FranceInfoContentSource)

class TextsListViewModel(app: Application) : AndroidViewModel(app) {
    private val db = AppDatabase.get(app)
    private val importRepository = ArticleImportRepository(db.textDao(), CONTENT_SOURCES, ArticleImageStore(app))
    private val _texts = MutableStateFlow<List<TextDocument>>(emptyList())
    val texts: StateFlow<List<TextDocument>> = _texts.asStateFlow()

    var contentSearchState by mutableStateOf<ContentSearchUiState>(ContentSearchUiState.Idle)
        private set
    var contentImportingRef by mutableStateOf<String?>(null)
        private set
    var contentImportError by mutableStateOf<String?>(null)
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
        viewModelScope.launch { importRepository.deleteWithImage(doc) }
    }

    /** Queries every registered [ContentSource] in parallel and merges the
     * results into one list. A source that throws is simply omitted from
     * the results -- one source failing (e.g. no internet reaching one
     * site) shouldn't hide results the others found. Only if EVERY source
     * fails does this surface as an error. */
    fun searchContent(query: String) {
        if (query.isBlank()) return
        viewModelScope.launch {
            contentSearchState = ContentSearchUiState.Searching
            contentSearchState = try {
                val perSource = coroutineScope {
                    CONTENT_SOURCES.map { source ->
                        async {
                            try {
                                source.search(query)
                            } catch (e: Exception) {
                                null
                            }
                        }
                    }.map { it.await() }
                }
                if (perSource.all { it == null }) {
                    ContentSearchUiState.Error("هیچ منبعی در دسترس نبود")
                } else {
                    val merged = perSource.filterNotNull().flatten()
                    if (merged.isEmpty()) ContentSearchUiState.NoResults else ContentSearchUiState.Results(merged)
                }
            } catch (e: Exception) {
                ContentSearchUiState.Error(e.message ?: e.toString())
            }
        }
    }

    fun resetContentSearch() {
        contentSearchState = ContentSearchUiState.Idle
        contentImportError = null
    }

    /** Search failures go to a Snackbar, not inline in the sheet -- called
     * after the Snackbar has been shown. */
    fun dismissContentSearchError() {
        if (contentSearchState is ContentSearchUiState.Error) contentSearchState = ContentSearchUiState.Idle
    }

    fun dismissContentImportError() {
        contentImportError = null
    }

    /** Fetches [result]'s full article from whichever source produced it
     * and, on success, creates a TextDocument with that source's
     * attribution and opens it via [onOpen]. */
    fun importContent(result: ContentResult, onOpen: (Long) -> Unit) {
        viewModelScope.launch {
            contentImportingRef = result.ref
            contentImportError = null
            val source = CONTENT_SOURCES.find { it.id == result.sourceId }
            val article: ContentArticle? = try {
                source?.fetchArticle(result)
            } catch (e: Exception) {
                contentImportError = e.message ?: e.toString()
                null
            }
            contentImportingRef = null
            if (article == null) {
                if (contentImportError == null) contentImportError = "دریافت مقاله ممکن نشد"
                return@launch
            }
            val id = db.textDao().insert(
                TextDocument(
                    title = article.title,
                    rawText = article.text,
                    sourceUrl = article.sourceUrl,
                    sourceName = article.sourceName,
                    author = article.author,
                    license = article.license,
                    publishedAt = article.publishedAtMs
                )
            )
            resetContentSearch()
            onOpen(id)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextsListScreen(onOpenText: (Long) -> Unit, onOpenVocab: () -> Unit, onOpenSettings: () -> Unit) {
    val vm: TextsListViewModel = viewModel()
    val texts by vm.texts.collectAsState()
    val context = LocalContext.current

    var dialogPrefill by remember { mutableStateOf<Pair<String, String>?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var showVikidiaSheet by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(vm.contentSearchState) {
        val s = vm.contentSearchState
        if (s is ContentSearchUiState.Error) {
            snackbarHostState.showSnackbar("جست‌وجو ممکن نشد: ${s.message}")
            vm.dismissContentSearchError()
        }
    }
    LaunchedEffect(vm.contentImportError) {
        vm.contentImportError?.let { message ->
            snackbarHostState.showSnackbar("دریافت مقاله ممکن نشد: $message")
            vm.dismissContentImportError()
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
                    IconButton(onClick = { showVikidiaSheet = true }) {
                        Icon(Icons.Default.Search, contentDescription = "پیدا کردن مطلب")
                    }
                    IconButton(onClick = { filePicker.launch(arrayOf("text/plain", "text/markdown", "text/*")) }) {
                        Icon(Icons.Default.FileOpen, contentDescription = "افزودن از فایل (TXT/MD)")
                    }
                    IconButton(onClick = onOpenVocab) {
                        Icon(Icons.Default.MenuBook, contentDescription = "لغات ذخیره‌شده")
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "تنظیمات")
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
    if (showVikidiaSheet) {
        FindArticleSheet(
            searchState = vm.contentSearchState,
            importingRef = vm.contentImportingRef,
            onSearch = { vm.searchContent(it) },
            onSelect = { result -> vm.importContent(result) { id -> onOpenText(id) } },
            onDismiss = { showVikidiaSheet = false; vm.resetContentSearch() }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FindArticleSheet(
    searchState: ContentSearchUiState,
    importingRef: String?,
    onSearch: (String) -> Unit,
    onSelect: (ContentResult) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Text("پیدا کردن مطلب", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(10.dp))

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("موضوع را بنویسید") },
                singleLine = true,
                trailingIcon = {
                    IconButton(onClick = { onSearch(query) }) {
                        Icon(Icons.Default.Search, contentDescription = "جست‌وجو")
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(12.dp))

            // Search failures surface as a Snackbar (handled by the caller,
            // TextsListScreen), so there's no error branch to render here --
            // by the time this recomposes, the state's already back to Idle.
            when (searchState) {
                ContentSearchUiState.Idle -> {}
                ContentSearchUiState.Searching -> {
                    Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                ContentSearchUiState.NoResults -> {
                    Text("نتیجه‌ای برای «$query» پیدا نشد.")
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
