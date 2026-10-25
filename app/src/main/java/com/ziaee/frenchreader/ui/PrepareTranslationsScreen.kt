package com.ziaee.frenchreader.ui

import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.work.WorkManager
import androidx.work.WorkInfo
import com.ziaee.frenchreader.R
import com.ziaee.frenchreader.translate.TranslationOverrides
import com.ziaee.frenchreader.translate.llm.*
import com.ziaee.frenchreader.translate.prepare.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class PreparationPreview(val missing: Int = 0, val cached: Int = 0, val overrides: Int = 0,
    val included: Int = 0, val seconds: Long = 0, val bytes: Long = 0, val cacheBytes: Long = 0)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PrepareTranslationsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { PreparationStore(context) }
    val cache = remember { LlmCache(context) }
    val bundled = remember { com.ziaee.frenchreader.content.BundledSentenceTranslations.forContext(context) }
    var regenerateBundled by remember { mutableStateOf(store.regenerateBundledExamples()) }
    val snackbar = remember { SnackbarHostState() }
    var sources by remember { mutableStateOf<List<PreparationSource>>(emptyList()) }
    var selected by remember { mutableStateOf(store.selection()) }
    var selectedDecks by remember { mutableStateOf(store.selectedDecks()) }
    val decks = sources.firstOrNull { it.id == "meanings" }?.decks.orEmpty()
    var search by rememberSaveable { mutableStateOf("") }
    val visibleTexts = sources.filter { it.id.startsWith("text:") && preparationTitleMatches(it.title, search) }
    val visibleVideos = sources.filter { it.id.startsWith("video:") && preparationTitleMatches(it.title, search) }
    val visibleDecks = decks.filter { preparationTitleMatches(it.title, search) }
    val visibleDeckIds = visibleDecks.map { it.id }.toSet()
    val currentDeckIds = selectedDecks ?: decks.map { it.id }.toSet()
    var loading by remember { mutableStateOf(true) }
    var estimating by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var model by remember { mutableStateOf<String?>(null) }
    var progress by remember { mutableStateOf<PreparationProgress?>(store.progress()) }
    var preview by remember { mutableStateOf(PreparationPreview()) }
    var confirmClear by remember { mutableStateOf(false) }
    var revision by remember { mutableIntStateOf(0) }
    var workState by remember { mutableStateOf<WorkInfo.State?>(null) }
    val engine = LlmPrefs(context).engine
    val locked = progress != null && progress?.status != PreparationStatus.COMPLETE
    BackHandler(onBack = onBack)

    fun action(block: suspend () -> Unit) {
        if (busy) return
        busy = true
        scope.launch {
            // Finish durable state/enqueue even when navigation disposes this screen.
            try { withContext(NonCancellable + Dispatchers.IO) { block() } }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { snackbar.showSnackbar(context.getString(R.string.prepare_error)) }
            finally {
                progress = store.progress()
                estimating = true
                revision++
                busy = false
            }
        }
    }
    fun choose(ids: Set<String>) {
        selected = ids
        estimating = true
        // Apply on the UI thread preserves the order of rapid checkbox changes; disk writes are asynchronous.
        store.select(ids)
    }

    fun chooseDecks(ids: Set<Long?>?) {
        selectedDecks = ids
        estimating = true
        store.selectDecks(ids)
    }

    LaunchedEffect(Unit) {
        try {
            sources = loadPreparationSources(context)
            withContext(Dispatchers.IO) {
                // Recover a process death between saving approval and enqueueing WorkManager.
                val saved = store.progress()
                if (saved != null && saved.status in setOf(PreparationStatus.QUEUED, PreparationStatus.RUNNING, PreparationStatus.RETRY))
                    PreparationWork.enqueue(context, saved.id)
            }
        }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { snackbar.showSnackbar(context.getString(R.string.prepare_error)) }
        finally { loading = false }
    }
    LaunchedEffect(selected, selectedDecks, regenerateBundled, sources, revision, busy) {
        if (busy) return@LaunchedEffect
        val actionRevision = revision
        var reportedError = false
        while (true) {
            try {
                val state = withContext(Dispatchers.IO) {
                    val translator = LlmTranslator.forContext(context)
                    val chosen = selectedItems(sources, selected, selectedDecks)
                    val overrides = TranslationOverrides(context)
                    fun included(item: PreparationItem) = !regenerateBundled && item.bundledTranslation(bundled) != null &&
                        (translator == null || cache.getRegenerated(item.text, item.language, translator.cacheId) == null)
                    fun ready(item: PreparationItem) = included(item) ||
                        (translator != null && item.prepared(cache, translator.cacheId, bundled, regenerateBundled))
                    val corrected = chosen.count { it.overridden(overrides) }
                    val includedCount = chosen.count { !it.overridden(overrides) && included(it) }
                    val already = chosen.count { !it.overridden(overrides) && !included(it) && ready(it) }
                    val pending = pendingItems(chosen) { it.overridden(overrides) || ready(it) }
                    val estimate = estimatePreparation(pending)
                    Triple(translator?.cacheId, store.progress(), PreparationPreview(pending.size, already, corrected,
                        includedCount, estimate.seconds, estimate.bytes, cache.sizeBytes()))
                }
                if (busy || revision != actionRevision) return@LaunchedEffect
                model = state.first; progress = state.second; preview = state.third
                estimating = false
                workState = withContext(Dispatchers.IO) {
                    WorkManager.getInstance(context).getWorkInfosForUniqueWork(PreparationWork.NAME).get().let { infos ->
                        (infos.firstOrNull { !it.state.isFinished } ?: infos.firstOrNull())?.state
                    }
                }
                reportedError = false
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) {
                model = null
                estimating = true
                if (!reportedError) {
                    reportedError = true
                    snackbar.showSnackbar(context.getString(R.string.prepare_error))
                }
            }
            delay(1500)
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }, topBar = {
        TopAppBar(title = { Text(stringResource(R.string.prepare_title)) }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.accessibility_back)) }
        })
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                OutlinedTextField(value = search, onValueChange = { search = it },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    label = { Text(stringResource(R.string.prepare_search)) },
                    trailingIcon = {
                        if (search.isNotEmpty()) TextButton(onClick = { search = "" }) {
                            Text(stringResource(R.string.prepare_search_clear))
                        }
                    })
            }
            item {
                Text(stringResource(R.string.prepare_description))
                Text(stringResource(R.string.prepare_model, LlmPrefs(context).currentModel.orEmpty()),
                    style = MaterialTheme.typography.labelLarge)
                if (model == null) Text(stringResource(R.string.prepare_requires_llm), color = MaterialTheme.colorScheme.error)
                if (engine == TranslationEngine.ONLINE) Text(stringResource(R.string.translation_experimental_message),
                    style = MaterialTheme.typography.bodySmall)
                if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            item {
                Text(stringResource(R.string.prepare_library), style = MaterialTheme.typography.titleMedium)
                // Vertical controls also fit narrow displays and large font sizes.
                TextButton(enabled = !locked && !busy && !loading, onClick = {
                    choose(selected + visibleTexts.map { it.id })
                }) { Text(stringResource(R.string.prepare_all)) }
                TextButton(enabled = !locked && !busy && !loading, onClick = {
                    choose(selected - visibleTexts.map { it.id }.toSet())
                }) { Text(stringResource(R.string.prepare_none)) }
            }
            if (!loading && search.isNotBlank() && visibleTexts.isEmpty()) item {
                Text(stringResource(R.string.prepare_search_no_results))
            }
            items(visibleTexts, key = { it.id }) { source ->
                PreparationChoice(source.title, source.id in selected, !locked && !busy) {
                    choose(if (it) selected + source.id else selected - source.id)
                }
            }
            item { Text(stringResource(R.string.prepare_videos), style = MaterialTheme.typography.titleMedium) }
            if (!loading && search.isBlank() && sources.none { it.id.startsWith("video:") }) item { Text(stringResource(R.string.prepare_no_videos)) }
            if (!loading && search.isNotBlank() && visibleVideos.isEmpty()) item {
                Text(stringResource(R.string.prepare_search_no_results))
            }
            items(visibleVideos, key = { it.id }) { source ->
                PreparationChoice(source.title, source.id in selected, !locked && !busy) {
                    choose(if (it) selected + source.id else selected - source.id)
                }
            }
            item { Text(stringResource(R.string.prepare_flashcards), style = MaterialTheme.typography.titleMedium) }
            item {
                PreparationChoice(stringResource(R.string.prepare_regenerate_bundled), regenerateBundled, !locked && !busy) {
                    regenerateBundled = it
                    estimating = true
                    store.setRegenerateBundledExamples(it)
                }
            }
            item {
                PreparationChoice(stringResource(R.string.prepare_all_decks, visibleDecks.sumOf { it.cardCount }),
                    visibleDecks.isNotEmpty() && visibleDeckIds.all { it in currentDeckIds },
                    !locked && !busy && !loading && visibleDecks.isNotEmpty()) { checked ->
                    chooseDecks(if (checked) currentDeckIds + visibleDeckIds else currentDeckIds - visibleDeckIds)
                }
                TextButton(enabled = !locked && !busy && !loading, onClick = { chooseDecks(currentDeckIds + visibleDeckIds) }) {
                    Text(stringResource(R.string.prepare_all_deck_choices))
                }
                TextButton(enabled = !locked && !busy && !loading, onClick = { chooseDecks(currentDeckIds - visibleDeckIds) }) {
                    Text(stringResource(R.string.prepare_no_deck_choices))
                }
            }
            if (!loading && search.isNotBlank() && visibleDecks.isEmpty()) item {
                Text(stringResource(R.string.prepare_search_no_results))
            }
            items(visibleDecks, key = { "deck:${it.id ?: "unfiled"}" }) { deck ->
                PreparationChoice(stringResource(R.string.prepare_deck_count, deck.title, deck.cardCount),
                    selectedDecks == null || deck.id in selectedDecks.orEmpty(), !locked && !busy && !loading) { checked ->
                    val current = selectedDecks ?: decks.map { it.id }.toSet()
                    chooseDecks(if (checked) current + setOf(deck.id) else current - setOf(deck.id))
                }
            }
            items(sources.filter { it.id in setOf("meanings", "examples") }, key = { it.id }) { source ->
                PreparationChoice(source.title, source.id in selected, !locked && !busy) {
                    choose(if (it) selected + source.id else selected - source.id)
                }
            }
            item {
                Text(stringResource(R.string.prepare_counts, preview.missing, preview.cached, preview.overrides))
                Text(stringResource(R.string.prepare_included, preview.included))
                Text(stringResource(R.string.prepare_estimate, (preview.seconds + 59) / 60,
                    Formatter.formatFileSize(context, preview.bytes)))
                Text(stringResource(R.string.prepare_languages), style = MaterialTheme.typography.bodySmall)
                Button(enabled = !loading && !estimating && !busy && !locked && model != null && preview.missing > 0,
                    modifier = Modifier.fillMaxWidth(), onClick = { action {
                        val translator = LlmTranslator.forContext(context) ?: error("Engine unavailable")
                        // Retire a just-finished WorkManager run before KEEP can accept the new snapshot.
                        PreparationWork.pause(context)
                        val items = selectedItems(sources, selected, selectedDecks)
                        val job = store.create(translator.cacheId, items, selectedDecks, regenerateBundled)
                        PreparationWork.enqueue(context, job.id)
                    } }) { Text(stringResource(R.string.prepare_start)) }
            }
            progress?.let { current -> item {
                val status = if (current.status in setOf(PreparationStatus.QUEUED, PreparationStatus.RUNNING) &&
                    workState != WorkInfo.State.RUNNING) PreparationStatus.QUEUED else current.status
                Text(stringResource(when (status) {
                    PreparationStatus.QUEUED -> R.string.prepare_queued
                    PreparationStatus.RUNNING -> R.string.prepare_running
                    PreparationStatus.RETRY -> R.string.prepare_retry
                    PreparationStatus.PAUSED -> R.string.prepare_paused
                    PreparationStatus.COMPLETE -> R.string.prepare_complete
                    PreparationStatus.ENGINE_CHANGED -> R.string.prepare_engine_changed
                }))
                Text(stringResource(R.string.prepare_progress, current.done, current.total))
                LinearProgressIndicator(progress = { if (current.total == 0) 0f else current.done.toFloat() / current.total },
                    modifier = Modifier.fillMaxWidth())
                if (current.status != PreparationStatus.COMPLETE) {
                    if (current.status in setOf(PreparationStatus.QUEUED, PreparationStatus.RUNNING, PreparationStatus.RETRY)) {
                        OutlinedButton(enabled = !busy, modifier = Modifier.fillMaxWidth(), onClick = {
                            action { PreparationWork.pause(context) }
                        }) { Text(stringResource(R.string.prepare_pause)) }
                    }
                    if (current.status in setOf(PreparationStatus.PAUSED, PreparationStatus.ENGINE_CHANGED)) {
                        Button(enabled = !busy && model != null, modifier = Modifier.fillMaxWidth(), onClick = { action {
                            PreparationWork.pause(context)
                            val job = store.job() ?: return@action
                            val translator = LlmTranslator.forContext(context)
                            if (translator?.cacheId != job.model) {
                                store.update(job.id, PreparationStatus.QUEUED)
                                store.update(job.id, PreparationStatus.ENGINE_CHANGED)
                            } else {
                                store.update(job.id, PreparationStatus.QUEUED)
                                PreparationWork.enqueue(context, job.id)
                            }
                        } }) { Text(stringResource(R.string.prepare_resume)) }
                    }
                    TextButton(enabled = !busy, modifier = Modifier.fillMaxWidth(), onClick = { action {
                        PreparationWork.pause(context); store.discard()
                    } }) { Text(stringResource(R.string.prepare_stop)) }
                }
            } }
            item {
                HorizontalDivider()
                Text(stringResource(R.string.prepare_cache_size, Formatter.formatFileSize(context, preview.cacheBytes)))
                TextButton(enabled = !busy && preview.cacheBytes > 0, modifier = Modifier.fillMaxWidth(),
                    onClick = { confirmClear = true }) { Text(stringResource(R.string.prepare_clear)) }
            }
        }
    }
    if (confirmClear) AlertDialog(onDismissRequest = { confirmClear = false },
        title = { Text(stringResource(R.string.prepare_clear)) }, text = { Text(stringResource(R.string.prepare_clear_confirm)) },
        confirmButton = { TextButton(onClick = {
            confirmClear = false
            action { PreparationWork.pause(context); store.clearCache(cache); store.discard() }
        }) { Text(stringResource(R.string.prepare_clear)) } },
        dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.translation_cancel)) } })
}

@Composable
private fun PreparationChoice(title: String, checked: Boolean, enabled: Boolean, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().toggleable(checked, enabled, Role.Checkbox, onChecked).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked, onCheckedChange = null, enabled = enabled)
        Text(title, Modifier.weight(1f).padding(start = 8.dp))
    }
}
