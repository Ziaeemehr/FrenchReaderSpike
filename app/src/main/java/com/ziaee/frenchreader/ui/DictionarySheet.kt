package com.ziaee.frenchreader.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ziaee.frenchreader.R
import com.ziaee.frenchreader.data.AppDatabase
import com.ziaee.frenchreader.data.VocabList
import com.ziaee.frenchreader.data.VocabPrefs
import com.ziaee.frenchreader.data.VocabRepository
import com.ziaee.frenchreader.data.MANUAL_VOCAB_TEXT_ID
import com.ziaee.frenchreader.data.DictionarySavedState
import com.ziaee.frenchreader.data.dictionarySavedState
import com.ziaee.frenchreader.translate.TranslationRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.net.URLEncoder

internal fun manualDictionaryWord(raw: String): String? = raw.trim().takeIf { it.isNotEmpty() }

/** Primary dictionary source per the design doc: WordReference French->English. */
internal fun wordReferenceUrl(word: String): String =
    "https://www.wordreference.com/fren/" + URLEncoder.encode(word, "UTF-8")

/** One free, no-API-key dictionary source shown as a switchable tab in the
 * lookup panel below the WebView -- see ROADMAP.md. URL patterns verified
 * against live pages during design; Reverso Context was considered and
 * dropped because it's behind a Cloudflare bot challenge that blocked even
 * full-browser-header requests. */
private data class DictionaryProvider(val id: String, val label: String, val urlFor: (String) -> String)

private val DICTIONARY_PROVIDERS = listOf(
    DictionaryProvider("wordreference", "WordReference") { wordReferenceUrl(it) },
    DictionaryProvider("bamooz", "B-amooz") {
        "https://dic.b-amooz.com/fr/dictionary/w?word=" + URLEncoder.encode(it, "UTF-8")
    },
    DictionaryProvider("larousse", "Larousse") {
        "https://www.larousse.fr/dictionnaires/francais/" + URLEncoder.encode(it, "UTF-8")
    },
    DictionaryProvider("linguee", "Linguee") {
        "https://www.linguee.com/french-english/search?source=auto&query=" + URLEncoder.encode(it, "UTF-8")
    },
    DictionaryProvider("wiktionary", "Wiktionary") {
        "https://fr.wiktionary.org/wiki/" + URLEncoder.encode(it, "UTF-8")
    }
)

/** Domains for ad networks confirmed to serve ads on Larousse (seen by
 * name in that page's own HTML comments: "PUB PAVE (Moneytizer & Prisma)",
 * "smartadserver"), plus a handful of other very common ad-tech domains
 * that show up across many sites via programmatic/header-bidding setups.
 * Requests to these are blocked outright in the dictionary WebView rather
 * than just hiding the resulting content with CSS, which doesn't reliably
 * catch ad-network-injected elements. */
private val AD_BLOCK_HOSTS = setOf(
    "smartadserver.com", "themoneytizer.com",
    "doubleclick.net", "googlesyndication.com", "googleadservices.com",
    "adnxs.com", "criteo.com", "criteo.net", "outbrain.com", "taboola.com",
    "pubmatic.com", "rubiconproject.com", "casalemedia.com", "adform.net",
    "amazon-adsystem.com", "adsafeprotected.com", "moatads.com", "scorecardresearch.com"
)

private fun isAdHost(host: String): Boolean =
    AD_BLOCK_HOSTS.any { host == it || host.endsWith(".$it") }

@Composable
fun ManualDictionaryHost(open: Boolean, onDismiss: () -> Unit) {
    var lookupWord by rememberSaveable(open) { mutableStateOf<String?>(null) }
    if (!open) return

    val word = lookupWord
    if (word == null) {
        ManualDictionaryDialog(
            onDismiss = onDismiss,
            onLookup = { lookupWord = it }
        )
    } else {
        DictionarySheet(
            textId = MANUAL_VOCAB_TEXT_ID,
            word = word,
            sentence = "",
            onDismiss = onDismiss
        )
    }
}

@Composable
fun ManualDictionaryDialog(onDismiss: () -> Unit, onLookup: (String) -> Unit) {
    var rawWord by rememberSaveable { mutableStateOf("") }
    val word = manualDictionaryWord(rawWord)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.manual_dictionary_title)) },
        text = {
            OutlinedTextField(
                value = rawWord,
                onValueChange = { rawWord = it },
                label = { Text(stringResource(R.string.manual_dictionary_word_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { word?.let(onLookup) }, enabled = word != null) {
                Text(stringResource(R.string.manual_dictionary_lookup))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}

class DictionaryViewModel(app: Application) : AndroidViewModel(app) {
    private val db = AppDatabase.get(app)
    private val repository = VocabRepository(db.vocabDao())

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

    fun observeSavedState(textId: Long, word: String, sentence: String): Flow<DictionarySavedState> =
        db.vocabDao().observeAll().map { entries ->
            dictionarySavedState(entries, textId, word, sentence)
        }

    fun save(textId: Long, word: String, sentence: String, meaning: String?, listId: Long?, onDone: () -> Unit) {
        val normalizedWord = manualDictionaryWord(word) ?: return
        viewModelScope.launch {
            repository.save(
                textId = textId,
                word = normalizedWord,
                sentence = sentence,
                dictionaryUrl = wordReferenceUrl(normalizedWord),
                meaning = meaning,
                listId = listId
            )
            onDone()
        }
    }

    fun delete(entry: com.ziaee.frenchreader.data.VocabEntry, onDone: () -> Unit) {
        viewModelScope.launch {
            db.vocabDao().delete(entry)
            onDone()
        }
    }
}

/**
 * Bottom sheet opened by a long-press on a word in the reading screen (or by
 * tapping an entry in the saved-vocab list to review/edit it).
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
    val savedStateFlow = remember(vm, textId, word, sentence) {
        vm.observeSavedState(textId, word, sentence)
    }
    val savedState by savedStateFlow.collectAsState(initial = DictionarySavedState())

    var meaning by remember(word, sentence) { mutableStateOf(initialMeaning.orEmpty()) }
    var selectedListId by remember(word, sentence) {
        mutableStateOf(initialListId ?: if (isNew) VocabPrefs.getLastListId(context) else null)
    }
    var saved by remember(word, sentence, isNew) { mutableStateOf(!isNew) }
    var autoTranslating by remember(word) { mutableStateOf(false) }
    var webViewFailed by remember(word) { mutableStateOf(false) }
    var listMenuExpanded by remember { mutableStateOf(false) }
    var showNewListDialog by remember { mutableStateOf(false) }
    var selectedProvider by remember(word) { mutableStateOf(DICTIONARY_PROVIDERS.first()) }
    val url = remember(word, selectedProvider) { selectedProvider.urlFor(word) }
    val meaningLanguage = VocabPrefs.getMeaningLanguage(context)
    val meaningTarget = if (meaningLanguage == VocabPrefs.MeaningLanguage.PERSIAN) "fa" else "en"

    // Auto-fill the meaning field with a Persian translation of just the
    // word, using the same free translation service/cache as paragraph
    // translation -- only when there's nothing saved for it yet, and only
    // if the person hasn't already started typing their own correction.
    LaunchedEffect(savedState.exactEntry?.id) {
        savedState.exactEntry?.let { existing ->
            meaning = existing.meaning.orEmpty()
            selectedListId = existing.listId
            saved = true
        }
    }

    LaunchedEffect(word, sentence, initialMeaning, meaningTarget, savedState.isLoaded) {
        if (savedState.isLoaded && savedState.exactEntry == null && initialMeaning.isNullOrBlank()) {
            autoTranslating = true
            val repo = TranslationRepository(context.applicationContext)
            val result = repo.getOrTranslate(word, meaningTarget)
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

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, windowInsets = WindowInsets(0)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
                .imePadding()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(word, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                IconButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }) {
                    Icon(Icons.Default.OpenInBrowser, contentDescription = stringResource(R.string.accessibility_open_in_browser))
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.accessibility_close))
                }
            }

            Spacer(Modifier.height(6.dp))

            OutlinedTextField(
                value = meaning,
                onValueChange = { meaning = it; saved = false },
                label = { Text(stringResource(if (meaningLanguage == VocabPrefs.MeaningLanguage.PERSIAN) R.string.dictionary_meaning_persian else R.string.dictionary_meaning_english)) },
                trailingIcon = {
                    if (autoTranslating) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(Modifier.height(10.dp))

            if (savedState.otherContextCount > 0) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Icon(Icons.Default.BookmarkAdded, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(
                                R.string.dictionary_saved_elsewhere,
                                savedState.otherContextCount
                            ),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.weight(1f)) {
                    AssistChip(
                        onClick = { listMenuExpanded = true },
                        leadingIcon = { Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        label = { Text(lists.find { it.id == selectedListId }?.name ?: stringResource(R.string.vocab_list_uncategorized)) }
                    )
                    DropdownMenu(expanded = listMenuExpanded, onDismissRequest = { listMenuExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.vocab_list_uncategorized)) },
                            onClick = { selectedListId = null; saved = false; listMenuExpanded = false }
                        )
                        lists.forEach { l ->
                            DropdownMenuItem(
                                text = { Text(l.name) },
                                onClick = { selectedListId = l.id; saved = false; listMenuExpanded = false }
                            )
                        }
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("+ " + stringResource(R.string.vocab_list_new)) },
                            onClick = { listMenuExpanded = false; showNewListDialog = true }
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                if (saved && savedState.exactEntry != null) {
                    IconButton(
                        onClick = {
                            savedState.exactEntry?.let { entry ->
                                vm.delete(entry) {
                                    saved = false
                                }
                            }
                        }
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = stringResource(R.string.accessibility_delete)
                        )
                    }
                    Spacer(Modifier.width(4.dp))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
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
                            contentDescription = stringResource(
                                if (saved) R.string.accessibility_vocabulary_saved
                                else R.string.accessibility_save_vocabulary
                            )
                        )
                    }
                    if (saved) {
                        Text(
                            stringResource(R.string.dictionary_saved_exact),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            HorizontalDivider()
            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DICTIONARY_PROVIDERS.forEach { provider ->
                    FilterChip(
                        selected = selectedProvider.id == provider.id,
                        onClick = { selectedProvider = provider; webViewFailed = false },
                        label = { Text(provider.label) }
                    )
                }
            }

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
                                override fun shouldInterceptRequest(
                                    view: WebView,
                                    request: WebResourceRequest
                                ): WebResourceResponse? {
                                    val host = request.url.host
                                    if (host != null && isAdHost(host)) {
                                        return WebResourceResponse("text/plain", "utf-8", null)
                                    }
                                    return super.shouldInterceptRequest(view, request)
                                }
                                override fun onPageFinished(view: WebView, url: String) {
                                    // Some dictionary sites (confirmed: Larousse) show
                                    // intrusive ads in known placeholder slots -- hide them
                                    // once the page finishes loading. Class names verified
                                    // against Larousse's real markup; harmless no-op on
                                    // sites that don't use them (WordReference/Linguee/
                                    // Wiktionary).
                                    view.evaluateJavascript(
                                        """
                                        (function() {
                                            var style = document.createElement('style');
                                            style.innerHTML = '.pub-top, .pub-bottom, .pub-pave, .pub-gtm, .ads-core-placer { display: none !important; }';
                                            document.head.appendChild(style);
                                        })();
                                        """.trimIndent(),
                                        null
                                    )
                                }
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
                            // WebView sits inside ModalBottomSheet's own drag-to-dismiss
                            // gesture handling; without this, the sheet's outer touch
                            // handling intercepts vertical drags before the WebView's own
                            // page scrolling ever receives them, so the dictionary page
                            // can only ever show its very top and never scroll.
                            setOnTouchListener { view, event ->
                                if (event.action == android.view.MotionEvent.ACTION_MOVE) {
                                    view.parent.requestDisallowInterceptTouchEvent(true)
                                }
                                false
                            }
                            loadUrl(url)
                        }
                    },
                    update = { webView ->
                        // AndroidView's factory only runs once; without this,
                        // switching dictionary-source chips would never
                        // reload the WebView. Comparing against the WebView's
                        // own current url avoids reloading on every unrelated
                        // recomposition (e.g. typing in the meaning field).
                        if (webView.url != url) webView.loadUrl(url)
                    }
                )
                if (webViewFailed) {
                    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surfaceVariant) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize().padding(16.dp)) {
                            Text(
                                stringResource(R.string.dictionary_webview_unavailable),
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
                    vm.createList(newListName) { newId -> selectedListId = newId; saved = false }
                    showNewListDialog = false
                }) { Text(stringResource(R.string.action_create)) }
            },
            dismissButton = {
                TextButton(onClick = { showNewListDialog = false }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }
}
