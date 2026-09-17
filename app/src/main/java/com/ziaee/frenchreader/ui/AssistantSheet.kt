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
import com.ziaee.frenchreader.llm.LlamaCppEngine
import com.ziaee.frenchreader.llm.LlmResult
import com.ziaee.frenchreader.llm.LocalLlmEngine
import com.ziaee.frenchreader.llm.Prompts
import com.ziaee.frenchreader.llm.VocabGrammar
import kotlinx.coroutines.launch

enum class AssistantAction { SUMMARIZE, GRAMMAR, SIMPLIFY, VOCAB }

class AssistantViewModel(app: Application) : AndroidViewModel(app) {
    private val engine: LocalLlmEngine = LlamaCppEngine(
        modelPathProvider = { ModelDownloader.modelFile(getApplication()).absolutePath },
    )

    var textResult by mutableStateOf<String?>(null)
        private set
    var vocabResult by mutableStateOf<List<VocabGrammar.VocabItem>>(emptyList())
        private set
    var loading by mutableStateOf(false)
        private set
    var errorReason by mutableStateOf<LlmResult.FailureReason?>(null)
        private set

    // Belt-and-suspenders alongside LlamaCppEngine's own idle timer (spec §3.6): once the
    // sheet's ViewModel is torn down (sheet dismissed and scope cleared), free the model
    // immediately rather than waiting out the idle timer.
    override fun onCleared() {
        engine.unload()
    }

    fun run(action: AssistantAction, sentence: String) {
        loading = true
        errorReason = null
        textResult = null
        vocabResult = emptyList()
        viewModelScope.launch {
            val prompt = when (action) {
                AssistantAction.SUMMARIZE -> Prompts.summarize(sentence)
                AssistantAction.GRAMMAR -> Prompts.explainGrammar(sentence)
                AssistantAction.SIMPLIFY -> Prompts.simplifyToA2(sentence)
                AssistantAction.VOCAB -> Prompts.extractVocabulary(sentence)
            }
            val result = engine.generate(prompt.systemPrompt, prompt.userPrompt, prompt.maxTokens, prompt.grammar)
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssistantSheet(sentence: String, textId: Long, onDismiss: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val vm: AssistantViewModel = viewModel()
    var showSetup by remember { mutableStateOf(!LlmAssistantPrefs.isModelDownloaded(context)) }

    if (showSetup) {
        LlmModelSetupSheet(
            context = context,
            onDismiss = onDismiss,
            onDownloaded = { showSetup = false },
        )
        return
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(stringResource(R.string.assistant_title))
            Spacer(modifier = Modifier.height(8.dp))
            Row {
                AssistChip(onClick = { vm.run(AssistantAction.SUMMARIZE, sentence) }, label = { Text(stringResource(R.string.assistant_action_summarize)) })
                AssistChip(onClick = { vm.run(AssistantAction.GRAMMAR, sentence) }, label = { Text(stringResource(R.string.assistant_action_grammar)) })
            }
            Row {
                AssistChip(onClick = { vm.run(AssistantAction.SIMPLIFY, sentence) }, label = { Text(stringResource(R.string.assistant_action_simplify)) })
                AssistChip(onClick = { vm.run(AssistantAction.VOCAB, sentence) }, label = { Text(stringResource(R.string.assistant_action_vocab)) })
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
                        VocabSuggestionRow(textId = textId, item = item)
                    }
                }
            }
        }
    }
}

@Composable
private fun VocabSuggestionRow(textId: Long, item: VocabGrammar.VocabItem) {
    var accepted by remember { mutableStateOf(false) }
    Row(modifier = Modifier.padding(vertical = 4.dp)) {
        Column {
            Text(item.mot)
            Text(item.definitionSimple)
        }
        if (!accepted) {
            Button(onClick = { accepted = true }) { Text("Accepter") }
        } else {
            Text("Ajouté")
        }
    }
}
