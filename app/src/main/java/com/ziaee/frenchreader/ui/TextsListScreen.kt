package com.ziaee.frenchreader.ui

import android.app.Application
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ziaee.frenchreader.R
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
import com.ziaee.frenchreader.ui.shared.AddTextHost
import com.ziaee.frenchreader.ui.shared.ContentSearchUiState
import com.ziaee.frenchreader.ui.shared.FindArticleSheet
import com.ziaee.frenchreader.ui.shared.rememberAddTextUiState
import com.ziaee.frenchreader.ui.shared.rememberFilePickerLauncher
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/** Every registered content source -- see ROADMAP.md section 6. Adding a
 * future source (Wikisource, Gutenberg, ...) means implementing
 * ContentSource and adding it here; nothing else in this file changes. */
private val CONTENT_SOURCES: List<ContentSource> = listOf(VikidiaContentSource, RfiFacileContentSource, FranceInfoContentSource)

/**
 * @deprecated Superseded by the Home dashboard and Library screens (see
 * `ui/home` and the implementation plan's Task 7); kept only as a
 * compatibility wrapper until final navigation switches over (Task 8).
 */
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

    val addTextState = rememberAddTextUiState()
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

    val openFilePicker = rememberFilePickerLauncher(addTextState)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("متن‌ها") },
                actions = {
                    IconButton(onClick = { showVikidiaSheet = true }) {
                        Icon(Icons.Default.Search, contentDescription = stringResource(R.string.content_search_title))
                    }
                    IconButton(onClick = openFilePicker) {
                        Icon(Icons.Default.FileOpen, contentDescription = stringResource(R.string.accessibility_import_file))
                    }
                    IconButton(onClick = onOpenVocab) {
                        Icon(Icons.Default.MenuBook, contentDescription = stringResource(R.string.accessibility_vocabulary))
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.accessibility_settings))
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = { addTextState.openBlank() }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.accessibility_add_text))
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
                Text(stringResource(R.string.home_recent_empty))
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

    AddTextHost(addTextState) { title, body -> vm.addText(title, body) { id -> onOpenText(id) } }

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
            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.accessibility_delete))
        }
    }
}
