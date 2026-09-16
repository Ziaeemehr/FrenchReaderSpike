package com.ziaee.frenchreader.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ziaee.frenchreader.data.AppDatabase
import com.ziaee.frenchreader.data.VocabEntry
import com.ziaee.frenchreader.data.VocabList
import com.ziaee.frenchreader.data.VocabPrefs
import com.ziaee.frenchreader.translate.TranslationRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.net.URLEncoder

/** Primary dictionary source per the design doc: WordReference French->English. */
private fun wordReferenceUrl(word: String): String =
    "https://www.wordreference.com/fren/" + URLEncoder.encode(word, "UTF-8")

class DictionaryViewModel(app: Application) : AndroidViewModel(app) {
    private val db = AppDatabase.get(app)

    private val _lists = MutableStateFlow<List<VocabList>>(emptyList())
    val lists: StateFlow<List<VocabList>> = _lists.asStateFlow()

    init {
        viewModelScope.launch {
            db.vocabListDao().observeAll().collect { _lists.value = it }
        }
    }

    fun createList(name: String, onCreated: (Long) -> Unit) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val id = db.vocabListDao().insert(VocabList(name = name.trim()))
            onCreated(id)
        }
    }

    fun save(textId: Long, word: String, sentence: String, meaning: String?, listId: Long?, onDone: () -> Unit) {
        viewModelScope.launch {
            // Repeating a word within the same sentence updates the
            // existing entry instead of creating a duplicate.
            val existing = db.vocabDao().findExisting(textId, word, sentence)
            if (existing != null) {
                db.vocabDao().update(
                    existing.copy(
                        meaning = meaning?.ifBlank { existing.meaning } ?: existing.meaning,
                        listId = listId
                    )
                )
            } else {
                db.vocabDao().insert(
                    VocabEntry(
                        word = word,
                        sentence = sentence,
                        textId = textId,
                        dictionaryUrl = wordReferenceUrl(word),
                        meaning = meaning?.ifBlank { null },
                        listId = listId
                    )
                )
            }
            onDone()
        }
    }
}

/**
 * Bottom sheet opened by a long-press on a word in the reading screen (or by
 * tapping an entry in the saved-vocab list to review/edit it). Kept
 * compact: word + a small "open in browser" icon on the same line, an
 * auto-filled/editable Persian meaning, a list picker + a small save icon
 * on one row, and the WordReference page always shown below (no extra
 * toggle -- the sentence itself isn't repeated here since it's already
 * visible on the reading screen behind the sheet, or in the vocab-list row
 * that opened it).
 *
 * [initialMeaning]/[initialListId] let the saved-vocab list reopen this
 * same sheet for an existing entry, pre-filled with what was saved before
 * (skipping the auto-translate call); saving again updates that entry in
 * place rather than creating a duplicate (same textId/word/sentence).
 *
 * [isNew] marks a brand-new word (opened from the reading screen, not from
 * the saved-vocab list): when true and no [initialListId] was given, the
 * list picker defaults to whichever list was chosen last time, so filing
 * several words from the same text into the same list doesn't require
 * re-picking it every time. Editing an existing entry ([isNew] = false)
 * always starts from that entry's own list instead, never from this.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DictionarySheet(
    textId: Long,
    word: String,
    sentence: String,
    initialMeaning: String? = null,
    initialListId: Long? = null,
    isNew: Boolean = true,
    onDismiss: () -> Unit
) {
    val vm: DictionaryViewModel = viewModel()
    val context = LocalContext.current
    val lists by vm.lists.collectAsState()

    var meaning by remember(word, sentence) { mutableStateOf(initialMeaning.orEmpty()) }
    var selectedListId by remember(word, sentence) {
        mutableStateOf(initialListId ?: if (isNew) VocabPrefs.getLastListId(context) else null)
    }
    var saved by remember(word, sentence) { mutableStateOf(false) }
    var autoTranslating by remember(word) { mutableStateOf(false) }
    var webViewFailed by remember(word) { mutableStateOf(false) }
    var listMenuExpanded by remember { mutableStateOf(false) }
    var showNewListDialog by remember { mutableStateOf(false) }
    val url = remember(word) { wordReferenceUrl(word) }

    // Auto-fill the meaning field with a Persian translation of just the
    // word, using the same free translation service/cache as paragraph
    // translation -- only when there's nothing saved for it yet, and only
    // if the person hasn't already started typing their own correction.
    LaunchedEffect(word, sentence, initialMeaning) {
        if (initialMeaning.isNullOrBlank()) {
            autoTranslating = true
            val repo = TranslationRepository(context.applicationContext)
            val result = repo.getOrTranslate(word, "fa")
            if (meaning.isBlank()) {
                result.onSuccess { meaning = it }
            }
            autoTranslating = false
        }
    }

    // Guards against defaulting to a "last used" list that's since been
    // deleted -- only relevant for the isNew/prefs-sourced default above.
    LaunchedEffect(lists, isNew) {
        if (isNew && selectedListId != null && lists.isNotEmpty() && lists.none { it.id == selectedListId }) {
            selectedListId = null
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(word, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                IconButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }) {
                    Icon(Icons.Default.OpenInBrowser, contentDescription = "باز کردن در مرورگر")
                }
            }

            Spacer(Modifier.height(6.dp))

            OutlinedTextField(
                value = meaning,
                onValueChange = { meaning = it; saved = false },
                label = { Text("معنی فارسی") },
                trailingIcon = {
                    if (autoTranslating) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(Modifier.height(10.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.weight(1f)) {
                    AssistChip(
                        onClick = { listMenuExpanded = true },
                        leadingIcon = { Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        label = { Text(lists.find { it.id == selectedListId }?.name ?: "بدون دسته") }
                    )
                    DropdownMenu(expanded = listMenuExpanded, onDismissRequest = { listMenuExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text("بدون دسته") },
                            onClick = { selectedListId = null; listMenuExpanded = false }
                        )
                        lists.forEach { l ->
                            DropdownMenuItem(
                                text = { Text(l.name) },
                                onClick = { selectedListId = l.id; listMenuExpanded = false }
                            )
                        }
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("+ لیست جدید") },
                            onClick = { listMenuExpanded = false; showNewListDialog = true }
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                FilledIconButton(
                    onClick = {
                        vm.save(textId, word, sentence, meaning, selectedListId) {
                            saved = true
                            VocabPrefs.setLastListId(context, selectedListId)
                        }
                    }
                ) {
                    Icon(
                        if (saved) Icons.Default.Check else Icons.Default.Save,
                        contentDescription = "ذخیره در لغات"
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            HorizontalDivider()
            Spacer(Modifier.height(10.dp))

            Box(modifier = Modifier.fillMaxWidth().height(340.dp)) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            // JS on for the site to render normally, but no
                            // file access and no JS bridge back into app
                            // code -- the design doc explicitly rules that out.
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.allowFileAccess = false
                            settings.allowContentAccess = false
                            webViewClient = object : WebViewClient() {
                                override fun onReceivedHttpError(
                                    view: WebView,
                                    request: WebResourceRequest,
                                    errorResponse: WebResourceResponse
                                ) {
                                    if (request.isForMainFrame) webViewFailed = true
                                }

                                override fun onReceivedError(
                                    view: WebView,
                                    request: WebResourceRequest,
                                    error: WebResourceError
                                ) {
                                    if (request.isForMainFrame) webViewFailed = true
                                }
                            }
                            loadUrl(url)
                        }
                    }
                )
                if (webViewFailed) {
                    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surfaceVariant) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize().padding(16.dp)) {
                            Text(
                                "نمایش داخلی دیکشنری در دسترس نیست؛ از دکمهٔ کنار کلمه، بالا، برای باز کردن در مرورگر استفاده کنید.",
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
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
                    vm.createList(newListName) { newId -> selectedListId = newId }
                    showNewListDialog = false
                }) { Text("ساخت") }
            },
            dismissButton = {
                TextButton(onClick = { showNewListDialog = false }) { Text("انصراف") }
            }
        )
    }
}
