package com.ziaee.frenchreader.ui

import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.ziaee.frenchreader.R
import com.ziaee.frenchreader.data.VocabPrefs
import com.ziaee.frenchreader.translate.TranslationService
import com.ziaee.frenchreader.translate.llm.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TranslationEngineSettings(context: Context, scope: CoroutineScope, snackbarHostState: SnackbarHostState) {
    val prefs = remember { LlmPrefs(context) }
    val secrets = remember { LlmSecrets(context) }
    var engine by remember { mutableStateOf(prefs.engine) }
    var confirmExperimental by remember { mutableStateOf(false) }
    var computerUrl by remember { mutableStateOf(prefs.computerUrl) }
    var computerModel by remember { mutableStateOf(prefs.computerModel) }
    var provider by remember { mutableStateOf(prefs.onlineProvider) }
    var customUrl by remember { mutableStateOf(prefs.customBaseUrl) }
    var onlineModel by remember { mutableStateOf(prefs.onlineModel) }
    var providerExpanded by remember { mutableStateOf(false) }
    var modelExpanded by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }
    // Typed but unsaved key or token, saved by Test connection so a test always uses what is on screen.
    var draftSecret by remember { mutableStateOf("") }
    var secretVersion by remember { mutableIntStateOf(0) }

    CompactChoiceColumn(
        entries = TranslationEngine.entries,
        selected = engine,
        label = { stringResource(when (it) {
            TranslationEngine.GOOGLE -> R.string.translation_engine_google
            TranslationEngine.COMPUTER -> R.string.translation_engine_computer
            TranslationEngine.ONLINE -> R.string.translation_engine_online
        }) },
        trailing = {
            if (it == TranslationEngine.ONLINE) {
                Text(stringResource(R.string.translation_experimental), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary)
            }
        }
    ) { selected ->
        if (selected == TranslationEngine.ONLINE && !prefs.experimentalAccepted) confirmExperimental = true
        else { engine = selected; prefs.engine = selected }
    }

    if (confirmExperimental) {
        AlertDialog(
            onDismissRequest = { confirmExperimental = false },
            title = { Text(stringResource(R.string.translation_experimental_title)) },
            text = { Text(stringResource(R.string.translation_experimental_message)) },
            confirmButton = {
                TextButton(onClick = {
                    prefs.experimentalAccepted = true
                    prefs.engine = TranslationEngine.ONLINE
                    engine = TranslationEngine.ONLINE
                    confirmExperimental = false
                }) { Text(stringResource(R.string.translation_understand)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmExperimental = false }) { Text(stringResource(R.string.translation_cancel)) }
            }
        )
    }

    when (engine) {
        TranslationEngine.GOOGLE -> Unit
        TranslationEngine.COMPUTER -> {
            OutlinedTextField(computerUrl, { computerUrl = it; prefs.computerUrl = it },
                label = { Text(stringResource(R.string.translation_server_url)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(computerModel, { computerModel = it; prefs.computerModel = it },
                label = { Text(stringResource(R.string.translation_model)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            LlmSecretField(secrets, "computer_token", R.string.translation_token, draftSecret, { draftSecret = it },
                secretVersion, scope, snackbarHostState, context)
            Text(stringResource(R.string.translation_computer_help), style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(vertical = 8.dp))
        }
        TranslationEngine.ONLINE -> {
            ExposedDropdownMenuBox(expanded = providerExpanded, onExpandedChange = { providerExpanded = it }) {
                OutlinedTextField(value = providerLabel(provider), onValueChange = {}, readOnly = true,
                    label = { Text(stringResource(R.string.translation_provider)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(providerExpanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth())
                ExposedDropdownMenu(expanded = providerExpanded, onDismissRequest = { providerExpanded = false }) {
                    LlmProvider.entries.forEach { item ->
                        DropdownMenuItem(text = { Text(providerLabel(item)) }, onClick = {
                            provider = item
                            prefs.onlineProvider = item
                            onlineModel = prefs.onlineModel
                            providerExpanded = false
                        })
                    }
                }
            }
            if (provider == LlmProvider.CUSTOM) {
                OutlinedTextField(customUrl, { customUrl = it; prefs.customBaseUrl = it },
                    label = { Text(stringResource(R.string.translation_base_url)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            } else {
                Text(provider.baseUrl, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 8.dp))
            }
            ExposedDropdownMenuBox(expanded = modelExpanded, onExpandedChange = { modelExpanded = it }) {
                OutlinedTextField(onlineModel, { onlineModel = it; prefs.onlineModel = it },
                    label = { Text(stringResource(R.string.translation_model)) }, singleLine = true,
                    trailingIcon = { if (provider.suggestedModels.isNotEmpty()) ExposedDropdownMenuDefaults.TrailingIcon(modelExpanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth())
                if (provider.suggestedModels.isNotEmpty()) {
                    ExposedDropdownMenu(expanded = modelExpanded, onDismissRequest = { modelExpanded = false }) {
                        provider.suggestedModels.forEach { model ->
                            DropdownMenuItem(text = { Text(model) }, onClick = {
                                onlineModel = model; prefs.onlineModel = model; modelExpanded = false
                            })
                        }
                    }
                }
            }
            key(provider) {
                LlmSecretField(secrets, provider.name, R.string.translation_api_key, draftSecret, { draftSecret = it },
                    secretVersion, scope, snackbarHostState, context)
            }
        }
    }

    Button(enabled = !testing, onClick = {
        val secretName = when (engine) {
            TranslationEngine.COMPUTER -> "computer_token"
            TranslationEngine.ONLINE -> provider.name
            TranslationEngine.GOOGLE -> null
        }
        if (secretName != null && draftSecret.isNotBlank()) {
            try {
                secrets.set(secretName, draftSecret.trim())
                draftSecret = ""
                secretVersion++
            } catch (_: Exception) {
                scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.translation_error_save_key)) }
                return@Button
            }
        }
        testing = true
        scope.launch {
            val message = try {
                val translation = withContext(Dispatchers.IO) {
                    val lang = meaningTargetLanguage(VocabPrefs.getMeaningLanguage(context))
                    if (engine == TranslationEngine.GOOGLE) TranslationService.translate("Bonjour, comment ça va ?", lang)
                    else {
                        val translator = LlmTranslator.forContext(context) ?: throw LlmError(
                            if (engine == TranslationEngine.ONLINE && secrets.get(provider.name).isNullOrBlank())
                                LlmError.Kind.MISSING_KEY else LlmError.Kind.UNREACHABLE)
                        translator.translateLines(listOf("Bonjour, comment ça va ?"), null, null, lang).single()
                    }
                }
                context.getString(R.string.translation_connected, translation)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                val reason = context.getString(when ((error as? LlmError)?.kind) {
                    LlmError.Kind.MISSING_KEY -> R.string.translation_error_missing_key
                    LlmError.Kind.UNAUTHORIZED -> R.string.translation_error_key
                    LlmError.Kind.PAYMENT -> R.string.translation_error_payment
                    LlmError.Kind.FORBIDDEN -> R.string.translation_error_forbidden
                    LlmError.Kind.NOT_FOUND -> R.string.translation_error_model
                    LlmError.Kind.RATE_LIMIT -> R.string.translation_error_rate
                    LlmError.Kind.UNREACHABLE -> R.string.translation_error_unreachable
                    else -> R.string.translation_error_response
                })
                // The provider's own words help with region, credit and model problems.
                (error as? LlmError)?.detail?.let { "$reason\n$it" } ?: reason
            } finally {
                testing = false
            }
            snackbarHostState.showSnackbar(message, withDismissAction = true, duration = SnackbarDuration.Long)
        }
    }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Text(stringResource(if (testing) R.string.translation_testing else R.string.translation_test_connection))
    }
}

@Composable
private fun providerLabel(provider: LlmProvider): String = when (provider) {
    LlmProvider.OPENROUTER -> "OpenRouter"
    LlmProvider.DEEPSEEK -> "DeepSeek"
    LlmProvider.QWEN -> "Qwen"
    LlmProvider.KIMI -> "Kimi"
    LlmProvider.CUSTOM -> stringResource(R.string.translation_custom_provider)
}

@Composable
private fun LlmSecretField(
    secrets: LlmSecrets, name: String, label: Int, value: String, onValueChange: (String) -> Unit,
    version: Int, scope: CoroutineScope, snackbarHostState: SnackbarHostState, context: Context
) {
    var last4 by remember(name, version) { mutableStateOf(secrets.last4(name)) }
    var replacing by remember(name, version) { mutableStateOf(false) }
    // A draft typed for another field (provider or engine changed) is not this field's secret.
    LaunchedEffect(name) { onValueChange("") }
    if (last4 != null) {
        Text(stringResource(R.string.translation_saved_key, last4.orEmpty()),
            style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { replacing = true }) { Text(stringResource(R.string.translation_replace)) }
            TextButton(onClick = {
                secrets.remove(name); last4 = null; onValueChange(""); replacing = false
            }) { Text(stringResource(R.string.translation_remove)) }
        }
    }
    if (last4 == null || replacing) {
        fun save() {
            if (value.isBlank()) return
            try {
                secrets.set(name, value.trim())
                onValueChange("")
                last4 = secrets.last4(name)
                replacing = false
            } catch (_: Exception) {
                scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.translation_error_save_key)) }
            }
        }
        // The keyboard's Done key saves too: a typed but unsaved key was easy to miss.
        OutlinedTextField(value, onValueChange, label = { Text(stringResource(label)) },
            visualTransformation = PasswordVisualTransformation(), singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { save() }),
            modifier = Modifier.fillMaxWidth())
        FilledTonalButton(enabled = value.isNotBlank(), onClick = { save() },
            modifier = Modifier.padding(top = 4.dp)) { Text(stringResource(R.string.translation_save)) }
    }
}
