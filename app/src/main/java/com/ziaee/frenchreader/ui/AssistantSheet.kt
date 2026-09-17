package com.ziaee.frenchreader.ui

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewModelScope
import com.ziaee.frenchreader.R
import com.ziaee.frenchreader.data.LlmAssistantPrefs
import com.ziaee.frenchreader.data.VocabPrefs
import com.ziaee.frenchreader.llm.LlamaCppEngine
import com.ziaee.frenchreader.llm.LlmResult
import com.ziaee.frenchreader.llm.LocalLlmEngine
import com.ziaee.frenchreader.llm.Prompts
import com.ziaee.frenchreader.llm.VocabGrammar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal enum class AssistantAction { SUMMARIZE, GRAMMAR, SIMPLIFY, VOCAB }

/** Owns one visible assistant sheet session, including its sole in-flight request. */
internal class AssistantSession(
    private val engine: LocalLlmEngine,
    private val scope: CoroutineScope,
) {
    var textResult by mutableStateOf<String?>(null)
        private set
    var vocabResult by mutableStateOf<List<VocabGrammar.VocabItem>>(emptyList())
        private set
    var loading by mutableStateOf(false)
        private set
    var errorReason by mutableStateOf<LlmResult.FailureReason?>(null)
        private set

    private var activeJob: Job? = null
    private var requestId = 0L
    private var cleanedUp = false

    fun run(action: AssistantAction, sentence: String) {
        if (loading) return

        cleanedUp = false
        val currentRequestId = ++requestId
        loading = true
        errorReason = null
        textResult = null
        vocabResult = emptyList()
        activeJob = scope.launch {
            val prompt = when (action) {
                AssistantAction.SUMMARIZE -> Prompts.summarize(sentence)
                AssistantAction.GRAMMAR -> Prompts.explainGrammar(sentence)
                AssistantAction.SIMPLIFY -> Prompts.simplifyToA2(sentence)
                AssistantAction.VOCAB -> Prompts.extractVocabulary(sentence)
            }
            val result = engine.generate(prompt.systemPrompt, prompt.userPrompt, prompt.maxTokens, prompt.grammar)
            if (currentRequestId != requestId || cleanedUp) return@launch

            loading = false
            when (result) {
                is LlmResult.Success -> {
                    if (action == AssistantAction.VOCAB) {
                        vocabResult = VocabGrammar.parse(result.text)
                    } else {
                        textResult = result.text
                    }
                }
                is LlmResult.Failure -> errorReason = result.reason
            }
        }
    }

    fun cancelAndClear() {
        if (cleanedUp) return

        cleanedUp = true
        requestId += 1
        activeJob?.cancel()
        activeJob = null
        loading = false
        errorReason = null
        textResult = null
        vocabResult = emptyList()
        engine.unload()
    }
}

class AssistantViewModel(app: Application) : AndroidViewModel(app) {
    private val engine: LocalLlmEngine = LlamaCppEngine.getInstance {
        ModelDownloader.modelFile(getApplication()).absolutePath
    }
    private val session = AssistantSession(engine, viewModelScope)

    val textResult get() = session.textResult
    val vocabResult get() = session.vocabResult
    val loading get() = session.loading
    val errorReason get() = session.errorReason

    // Belt-and-suspenders alongside LlamaCppEngine's own idle timer (spec §3.6): once the
    // sheet's ViewModel is torn down (sheet dismissed and scope cleared), free the model
    // immediately rather than waiting out the idle timer.
    override fun onCleared() {
        session.cancelAndClear()
    }

    internal fun run(action: AssistantAction, sentence: String) = session.run(action, sentence)

    fun cancelAndClear() = session.cancelAndClear()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssistantSheet(sentence: String, textId: Long, onDismiss: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val vm: AssistantViewModel = viewModel()
    val dictionaryVm: DictionaryViewModel = viewModel()
    var showSetup by remember { mutableStateOf(!LlmAssistantPrefs.isModelDownloaded(context)) }

    // The ambient ViewModelStoreOwner outlives a ModalBottomSheet. Dispose each visible
    // sheet session explicitly so reopening for another sentence cannot inherit its work.
    DisposableEffect(vm, sentence, textId) {
        onDispose { vm.cancelAndClear() }
    }

    if (showSetup) {
        LlmModelSetupSheet(
            context = context,
            onDismiss = {
                vm.cancelAndClear()
                onDismiss()
            },
            onDownloaded = { showSetup = false },
        )
        return
    }

    ModalBottomSheet(onDismissRequest = {
        vm.cancelAndClear()
        onDismiss()
    }) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(stringResource(R.string.assistant_title))
            Spacer(modifier = Modifier.height(8.dp))
            Row {
                AssistChip(onClick = { vm.run(AssistantAction.SUMMARIZE, sentence) }, enabled = !vm.loading, label = { Text(stringResource(R.string.assistant_action_summarize)) })
                AssistChip(onClick = { vm.run(AssistantAction.GRAMMAR, sentence) }, enabled = !vm.loading, label = { Text(stringResource(R.string.assistant_action_grammar)) })
            }
            Row {
                AssistChip(onClick = { vm.run(AssistantAction.SIMPLIFY, sentence) }, enabled = !vm.loading, label = { Text(stringResource(R.string.assistant_action_simplify)) })
                AssistChip(onClick = { vm.run(AssistantAction.VOCAB, sentence) }, enabled = !vm.loading, label = { Text(stringResource(R.string.assistant_action_vocab)) })
            }
            Spacer(modifier = Modifier.height(16.dp))

            if (vm.loading) {
                CircularProgressIndicator()
            }

            vm.errorReason?.let { reason ->
                val messageRes = when (reason) {
                    LlmResult.FailureReason.NOT_DOWNLOADED -> R.string.assistant_error_not_downloaded
                    LlmResult.FailureReason.LOAD_FAILED -> R.string.assistant_error_load_failed
                    LlmResult.FailureReason.OUT_OF_MEMORY -> R.string.assistant_error_out_of_memory
                    LlmResult.FailureReason.TIMEOUT, LlmResult.FailureReason.GENERATION_FAILED -> R.string.assistant_error_generation_failed
                }
                Text(stringResource(messageRes))
            }

            vm.textResult?.let { text ->
                Text(stringResource(R.string.assistant_suggestion_label))
                Text(text)
            }

            if (vm.vocabResult.isNotEmpty()) {
                Text(stringResource(R.string.assistant_suggestion_label))
                LazyColumn {
                    items(vm.vocabResult) { item ->
                        VocabSuggestionRow(
                            textId = textId,
                            sentence = sentence,
                            item = item,
                            dictionaryVm = dictionaryVm,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VocabSuggestionRow(
    textId: Long,
    sentence: String,
    item: VocabGrammar.VocabItem,
    dictionaryVm: DictionaryViewModel,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var accepted by remember { mutableStateOf(false) }
    Row(modifier = Modifier.padding(vertical = 4.dp)) {
        Column {
            Text(item.mot)
            Text(item.definitionSimple)
        }
        if (!accepted) {
            Button(
                onClick = {
                    dictionaryVm.save(
                        textId = textId,
                        word = item.mot,
                        sentence = sentence,
                        meaning = item.definitionSimple,
                        listId = VocabPrefs.getLastListId(context),
                        onDone = { accepted = true },
                    )
                },
            ) {
                Text(stringResource(R.string.assistant_action_accept))
            }
        } else {
            Text(stringResource(R.string.assistant_action_added))
        }
    }
}
