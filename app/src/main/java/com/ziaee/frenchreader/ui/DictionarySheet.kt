package com.ziaee.frenchreader.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.annotation.DrawableRes
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
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
import com.ziaee.frenchreader.comprehension.FrenchLemmaLexicon
import com.ziaee.frenchreader.comprehension.LemmaLexicon
import com.ziaee.frenchreader.comprehension.normalizeFrench
import com.ziaee.frenchreader.translate.MeaningOption
import com.ziaee.frenchreader.translate.TranslationRepository
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.net.URLEncoder

internal fun manualDictionaryWord(raw: String): String? = raw.trim().takeIf { it.isNotEmpty() }

/** Dictionary forms (e.g. the infinitive) of a looked-up form, offered as one-tap replacements. */
internal fun lemmaSuggestions(word: String, lexicon: FrenchLemmaLexicon): List<String> {
    val form = normalizeFrench(word.trim())
    if (form.isEmpty() || form.any(Char::isWhitespace)) return emptyList()
    return lexicon.lemmas(form).filter { it != form }.distinct().take(3)
}

/** Primary dictionary source per the design doc: WordReference French->English. */
internal fun wordReferenceUrl(word: String): String =
    "https://www.wordreference.com/fren/" + URLEncoder.encode(word, "UTF-8")

/** One free, no-API-key dictionary source shown as a switchable tab in the
 * lookup panel below the WebView -- see ROADMAP.md. URL patterns verified
 * against live pages during design; Reverso Context was considered and
 * dropped because it's behind a Cloudflare bot challenge that blocked even
 * full-browser-header requests, but Reverso Conjugator is not challenged and
 * is included. */
private data class DictionaryProvider(
    val id: String,
    val label: String,
    @DrawableRes val iconRes: Int,
    val urlFor: (String) -> String
)

private val DICTIONARY_PROVIDERS = listOf(
    DictionaryProvider("wordreference", "WordReference", R.drawable.ic_dict_wordreference) { wordReferenceUrl(it) },
    DictionaryProvider("reverso_conj", "Reverso Conjugaison", R.drawable.ic_dict_reverso) {
        "https://conjugator.reverso.net/conjugation-french-verb-" +
            URLEncoder.encode(it, "UTF-8").replace("+", "%20") + ".html"
    },
    DictionaryProvider("bamooz", "B-amooz", R.drawable.ic_dict_bamooz) {
        "https://dic.b-amooz.com/fr/dictionary/w?word=" + URLEncoder.encode(it, "UTF-8")
    },
    // Path segment, not a query: Glosbe 404s on "+" for spaces.
    DictionaryProvider("glosbe", "Glosbe", R.drawable.ic_dict_glosbe) {
        "https://glosbe.com/fr/fa/" + URLEncoder.encode(it, "UTF-8").replace("+", "%20")
    },
    DictionaryProvider("larousse", "Larousse", R.drawable.ic_dict_larousse) {
        "https://www.larousse.fr/dictionnaires/francais/" + URLEncoder.encode(it, "UTF-8")
    },
    DictionaryProvider("linguee", "Linguee", R.drawable.ic_dict_linguee) {
        "https://www.linguee.com/french-english/search?source=auto&query=" + URLEncoder.encode(it, "UTF-8")
    },
    DictionaryProvider("wiktionary", "Wiktionary", R.drawable.ic_dict_wiktionary) {
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

    /** Changes the word of a saved card in place, keeping its review progress. */
    fun rename(
        entry: com.ziaee.frenchreader.data.VocabEntry,
        word: String,
        meaning: String?,
        listId: Long?,
        onDone: (com.ziaee.frenchreader.data.VocabEntry) -> Unit
    ) {
        val normalizedWord = manualDictionaryWord(word) ?: return
        val renamed = entry.copy(
            word = normalizedWord,
            dictionaryUrl = wordReferenceUrl(normalizedWord),
            meaning = meaning?.ifBlank { entry.meaning } ?: entry.meaning,
            listId = listId
        )
        viewModelScope.launch {
            db.vocabDao().update(renamed)
            onDone(renamed)
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
    onCardRenamed: (word: String, meaning: String?) -> Unit = { _, _ -> },
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        windowInsets = WindowInsets(0),
        // A slim handle: the default one pads 22dp above and below.
        dragHandle = {
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.padding(top = 8.dp, bottom = 2.dp).size(width = 32.dp, height = 4.dp)
            ) {}
        }
    ) {
        DictionaryPane(textId, word, sentence, initialMeaning, initialListId, isNew, onCardRenamed, onDismiss, embedded = false)
    }
}

/** Dictionary lookup content. [embedded] uses the parent's bounded height for the WebView. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DictionaryPane(
    textId: Long,
    word: String,
    sentence: String,
    initialMeaning: String? = null,
    initialListId: Long? = null,
    isNew: Boolean = true,
    onCardRenamed: (word: String, meaning: String?) -> Unit = { _, _ -> },
    onDismiss: () -> Unit = {},
    embedded: Boolean = true,
    modifier: Modifier = Modifier
) {
    val vm: DictionaryViewModel = viewModel()
    val context = LocalContext.current
    // A new lookup can be edited (e.g. a conjugated form to its infinitive, or into a phrase);
    // everything below looks up and saves the edited word.
    val originalWord = word
    var currentWord by rememberSaveable(originalWord) { mutableStateOf(originalWord) }
    var lemmas by remember(originalWord) { mutableStateOf(emptyList<String>()) }
    LaunchedEffect(originalWord) {
        lemmas = runCatching { lemmaSuggestions(originalWord, LemmaLexicon.get(context).get()) }
            .getOrDefault(emptyList())
    }
    val word = currentWord
    val isOriginal = word == originalWord
    val initialMeaning = initialMeaning.takeIf { isOriginal }
    val initialListId = initialListId.takeIf { isOriginal }
    val lists by vm.lists.collectAsState()
    val savedStateFlow = remember(vm, textId, word, sentence) {
        vm.observeSavedState(textId, word, sentence)
    }
    val savedState by savedStateFlow.collectAsState(initial = DictionarySavedState())
    // Reopening a saved card: editing its word renames that card instead of adding another.
    var editedEntry by remember(originalWord) { mutableStateOf<com.ziaee.frenchreader.data.VocabEntry?>(null) }
    if (!isNew && isOriginal && editedEntry == null) savedState.exactEntry?.let { editedEntry = it }

    var meaningEdited by remember(word, sentence) { mutableStateOf(false) }
    var alternatives by remember(word, sentence) { mutableStateOf(emptyList<MeaningOption>()) }
    var meaning by remember(word, sentence) { mutableStateOf(initialMeaning.orEmpty()) }
    var selectedListId by remember(word, sentence) {
        mutableStateOf(initialListId ?: if (isNew) VocabPrefs.getLastListId(context) else null)
    }
    var saved by remember(word, sentence, isNew) { mutableStateOf(!isNew && isOriginal) }
    var autoTranslating by remember(word) { mutableStateOf(false) }
    var webViewFailed by remember(word) { mutableStateOf(false) }
    var listMenuExpanded by remember { mutableStateOf(false) }
    var showNewListDialog by remember { mutableStateOf(false) }
    var selectedProvider by remember(word) {
        mutableStateOf(DICTIONARY_PROVIDERS.firstOrNull {
            embedded && it.id == VocabPrefs.getLastDictionaryProvider(context)
        } ?: DICTIONARY_PROVIDERS.first())
    }
    var providerMenuExpanded by remember { mutableStateOf(false) }
    val url = remember(word, selectedProvider) { selectedProvider.urlFor(word) }
    val meaningLanguage = VocabPrefs.getMeaningLanguage(context)
    val meaningTarget = if (meaningLanguage == VocabPrefs.MeaningLanguage.PERSIAN) "fa" else "en"

    // Only auto-fill an unsaved meaning before the person starts editing it.
    LaunchedEffect(savedState.exactEntry?.id) {
        savedState.exactEntry?.let { existing ->
            meaning = existing.meaning.orEmpty()
            selectedListId = existing.listId
            saved = true
        }
    }

    LaunchedEffect(word, sentence, initialMeaning, meaningTarget, savedState.isLoaded, savedState.exactEntry?.id, lemmas) {
        if (savedState.isLoaded && savedState.exactEntry == null && initialMeaning.isNullOrBlank() && !meaningEdited) {
            autoTranslating = true
            try {
                val repo = TranslationRepository(context.applicationContext)
                // An edited lookup word needs its own dictionary forms, not the tapped word's.
                val wordLemmas = if (isOriginal) lemmas else runCatching {
                    lemmaSuggestions(word, LemmaLexicon.get(context).get())
                }.getOrDefault(emptyList())
                val result = repo.meaningCandidates(word, wordLemmas, sentence, meaningTarget)
                if (!meaningEdited && savedState.exactEntry == null) {
                    result.best?.let { meaning = it }
                    alternatives = result.options
                }
            } finally {
                autoTranslating = false
            }
        }
    }

    // Guards against defaulting to a "last used" list that's since been
    // deleted -- only relevant for the isNew/prefs-sourced default above.
    LaunchedEffect(lists, isNew) {
        if (isNew && selectedListId != null && lists.isNotEmpty() && lists.none { it.id == selectedListId }) {
            selectedListId = null
        }
    }

    BoxWithConstraints(modifier) {
        val controlsModifier = if (embedded) {
            Modifier.heightIn(max = maxHeight * 0.65f).verticalScroll(rememberScrollState())
        } else Modifier
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = if (embedded) 12.dp else 20.dp)
                .padding(bottom = if (embedded) 0.dp else 24.dp)
                .imePadding()
        ) {
            // Only the controls may scroll on short screens; the WebView has its own bounded viewport.
            Column(controlsModifier) {
            // The word itself is the editable field: type a base form or a whole structure, then
            // Done (or ✓) looks it up and makes it what gets saved.
            val focusManager = LocalFocusManager.current
            var draft by remember(word) { mutableStateOf(TextFieldValue(word, TextRange(word.length))) }
            val draftWord = manualDictionaryWord(draft.text)
            val applyDraft = {
                draftWord?.let { currentWord = it }
                focusManager.clearFocus()
            }
            val wordInteraction = remember { MutableInteractionSource() }
            val wordFocused by wordInteraction.collectIsFocusedAsState()
            val shownLemmas = lemmas.filter { it != word }
            val wordField: @Composable (Modifier) -> Unit = { fieldModifier ->
                BasicTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    singleLine = true,
                    textStyle = (if (embedded) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineSmall)
                        .copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { applyDraft() }),
                    interactionSource = wordInteraction,
                    modifier = fieldModifier,
                    decorationBox = { inner ->
                        Column {
                            Box(Modifier.padding(vertical = 6.dp)) { inner() }
                            HorizontalDivider(
                                thickness = if (wordFocused) 2.dp else 1.dp,
                                color = if (wordFocused) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outlineVariant
                            )
                        }
                    }
                )
            }
            val wordActions: @Composable () -> Unit = {
                if (draftWord != null && draftWord != word) {
                    IconButton(onClick = applyDraft) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = stringResource(R.string.dictionary_apply_word),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                // Embedded under a video, space is short: the provider picker is a compact menu in this row.
                if (embedded) Box {
                    TextButton(onClick = { providerMenuExpanded = true }) {
                        Text(selectedProvider.label, maxLines = 1)
                        Icon(Icons.Default.ArrowDropDown, contentDescription = stringResource(R.string.dictionary_provider))
                    }
                    DropdownMenu(expanded = providerMenuExpanded, onDismissRequest = { providerMenuExpanded = false }) {
                        DICTIONARY_PROVIDERS.forEach { provider ->
                            DropdownMenuItem(
                                text = { Text(provider.label) },
                                onClick = {
                                    selectedProvider = provider
                                    VocabPrefs.setLastDictionaryProvider(context, provider.id)
                                    webViewFailed = false
                                    providerMenuExpanded = false
                                }
                            )
                        }
                    }
                }
                IconButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }) {
                    Icon(Icons.Default.OpenInBrowser, contentDescription = stringResource(R.string.accessibility_open_in_browser))
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.accessibility_close))
                }
            }
            val wordPills: @Composable (Boolean) -> Unit = { compact ->
                shownLemmas.forEach { lemma ->
                    WordPill("→ $lemma", compact = compact) { currentWord = lemma }
                }
                if (!isOriginal) {
                    val resetLabel = stringResource(R.string.dictionary_reset_word)
                    WordPill("↺ $originalWord", Modifier.semantics { contentDescription = "$resetLabel: $originalWord" }, compact = compact) {
                        currentWord = originalWord
                    }
                }
            }
            if (embedded) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    wordField(Modifier.widthIn(min = 80.dp, max = 200.dp).width(IntrinsicSize.Min).align(Alignment.CenterVertically))
                    wordPills(true)
                    Row(verticalAlignment = Alignment.CenterVertically) { wordActions() }
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    wordField(Modifier.weight(1f))
                    wordActions()
                }
                if (shownLemmas.isNotEmpty() || !isOriginal) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) { wordPills(false) }
                }
            }

            val saveWord: () -> Unit = {
                        val onSaved = {
                            saved = true
                            VocabPrefs.setLastListId(context, selectedListId)
                        }
                        val card = editedEntry
                        if (card != null && !isOriginal) vm.rename(card, word, meaning, selectedListId) {
                            onSaved()
                            onCardRenamed(it.word, it.meaning)
                        }
                        else vm.save(textId, word, sentence, meaning, selectedListId, onSaved)
                    }
            val meaningAlternatives: @Composable () -> Unit = {
                if (alternatives.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    MeaningOptionPills(alternatives.take(if (embedded) 5 else 8), meaning, meaningTarget) { option ->
                        meaning = option.text
                        meaningEdited = true
                        saved = false
                    }
                }
            }
            if (embedded) {
                // Under a video every line counts: meaning, deck and save share one slim row.
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                    BasicTextField(
                        value = meaning,
                        onValueChange = { meaning = it; meaningEdited = true; saved = false },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = Modifier.weight(1f),
                        decorationBox = { inner ->
                            Column {
                                Box(Modifier.padding(vertical = 6.dp)) {
                                    if (meaning.isEmpty()) Text(
                                        stringResource(if (meaningLanguage == VocabPrefs.MeaningLanguage.PERSIAN) R.string.dictionary_meaning_persian else R.string.dictionary_meaning_english),
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    inner()
                                }
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            }
                        }
                    )
                    if (autoTranslating) CircularProgressIndicator(modifier = Modifier.padding(horizontal = 6.dp).size(16.dp), strokeWidth = 2.dp)
                    Box {
                        IconButton(onClick = { listMenuExpanded = true }, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Default.Folder,
                                contentDescription = lists.find { it.id == selectedListId }?.name ?: stringResource(R.string.vocab_list_uncategorized),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                        }
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
                    IconButton(onClick = saveWord, modifier = Modifier.size(36.dp)) {
                        Icon(
                            if (saved) Icons.Default.BookmarkAdded else Icons.Default.BookmarkAdd,
                            contentDescription = stringResource(
                                if (saved) R.string.accessibility_vocabulary_saved
                                else R.string.accessibility_save_vocabulary
                            ),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                meaningAlternatives()
            } else {
            Spacer(Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = meaning,
                    onValueChange = { meaning = it; meaningEdited = true; saved = false },
                    placeholder = { Text(stringResource(if (meaningLanguage == VocabPrefs.MeaningLanguage.PERSIAN) R.string.dictionary_meaning_persian else R.string.dictionary_meaning_english)) },
                    trailingIcon = {
                        if (autoTranslating) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                Spacer(Modifier.width(10.dp))
                FilledIconButton(
                    onClick = saveWord,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        if (saved) Icons.Default.Check else Icons.Default.Save,
                        contentDescription = stringResource(
                            if (saved) R.string.accessibility_vocabulary_saved
                            else R.string.accessibility_save_vocabulary
                        )
                    )
                }
            }

            meaningAlternatives()

            // One quiet line of details: the list it's filed in, where else it was saved, delete.
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Box {
                    TextButton(
                        onClick = { listMenuExpanded = true },
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                    ) {
                        Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            lists.find { it.id == selectedListId }?.name ?: stringResource(R.string.vocab_list_uncategorized),
                            style = MaterialTheme.typography.labelLarge
                        )
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
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
                if (savedState.otherContextCount > 0) {
                    val savedElsewhere = stringResource(R.string.dictionary_saved_elsewhere, savedState.otherContextCount)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .semantics(mergeDescendants = true) { contentDescription = savedElsewhere }
                    ) {
                        Icon(
                            Icons.Default.BookmarkAdded,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(Modifier.width(3.dp))
                        Text(
                            savedState.otherContextCount.toString(),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                if (saved && savedState.exactEntry != null) {
                    IconButton(
                        onClick = {
                            savedState.exactEntry?.let { entry ->
                                vm.delete(entry) {
                                    saved = false
                                }
                            }
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Default.DeleteOutline,
                            contentDescription = stringResource(R.string.accessibility_delete),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            }

            if (!embedded) {
            // Dictionaries as tabs sitting directly on the page they switch.
            TabRow(
                selectedTabIndex = DICTIONARY_PROVIDERS.indexOfFirst { it.id == selectedProvider.id }.coerceAtLeast(0),
                containerColor = Color.Transparent,
                divider = { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant) }
            ) {
                DICTIONARY_PROVIDERS.forEach { provider ->
                    Tab(
                        selected = selectedProvider.id == provider.id,
                        onClick = { selectedProvider = provider; webViewFailed = false },
                        modifier = Modifier.height(40.dp),
                        unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ) {
                        Image(
                            painter = painterResource(provider.iconRes),
                            contentDescription = provider.label,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            }

            }
            Box(modifier = Modifier.fillMaxWidth().then(
                if (embedded) Modifier.weight(1f) else Modifier.height(420.dp)
            )) {
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
                    },
                    onRelease = { webView ->
                        webView.stopLoading()
                        webView.loadUrl("about:blank")
                        webView.removeAllViews()
                        webView.destroy()
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

@Composable
private fun WordPill(text: String, modifier: Modifier = Modifier, compact: Boolean = false, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = modifier
    ) {
        Text(
            text,
            style = if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = if (compact) 4.dp else 6.dp)
        )
    }
}
