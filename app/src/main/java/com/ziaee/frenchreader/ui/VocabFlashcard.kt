package com.ziaee.frenchreader.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ziaee.frenchreader.translate.ExampleTranslation
import com.ziaee.frenchreader.translate.ExampleSource
import com.ziaee.frenchreader.translate.MeaningOption
import com.ziaee.frenchreader.R
import com.ziaee.frenchreader.data.VocabEntry
import com.ziaee.frenchreader.data.displayMeaning
import com.ziaee.frenchreader.data.isLikelyFrench
import com.ziaee.frenchreader.ui.components.TappableFrenchText

internal fun flashcardWordAudioText(entry: VocabEntry): String? =
    entry.word.takeIf(::isLikelyFrench)

internal fun flashcardSentenceAudioText(entry: VocabEntry): String? =
    entry.sentence.takeIf { it.isNotBlank() }

@Composable
internal fun FlipCard(
    revealed: Boolean,
    onFlip: () -> Unit,
    interactive: Boolean = true,
    flipBackEnabled: Boolean = false,
    front: @Composable () -> Unit,
    back: @Composable () -> Unit
) {
    val rotation by animateFloatAsState(if (revealed) 180f else 0f, tween(350), label = "flip")
    val density = LocalDensity.current.density
    Card(
        Modifier.fillMaxWidth().graphicsLayer { rotationY = rotation; cameraDistance = 12f * density }
            .clickable(
                enabled = interactive && (!revealed || flipBackEnabled),
                role = Role.Button,
                onClick = onFlip
            )
    ) {
        if (rotation <= 90f) front()
        else Box(Modifier.graphicsLayer { rotationY = 180f }) { back() }
    }
}

@Composable
private fun WordAudioButton(
    entry: VocabEntry,
    loading: Boolean,
    error: Boolean,
    interactive: Boolean,
    onPlay: () -> Unit
) {
    if (flashcardWordAudioText(entry) == null) return
    IconButton(onClick = onPlay, enabled = interactive) {
        if (loading) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
        } else {
            Icon(Icons.Default.VolumeUp, stringResource(R.string.accessibility_play_word))
        }
    }
    if (error) {
        Text(
            stringResource(R.string.error_audio_generation),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun EntryFlashcard(
    entry: VocabEntry,
    revealed: Boolean,
    interactive: Boolean,
    onFlip: () -> Unit,
    onPlayWord: () -> Unit,
    onPlaySentence: () -> Unit,
    wordAudioLoading: Boolean,
    wordAudioError: Boolean,
    sentenceAudioLoading: Boolean,
    sentenceAudioError: Boolean,
    sentenceTranslation: ExampleTranslation?,
    sentenceTranslationLoading: Boolean,
    sentenceTranslationError: Boolean,
    onWordTap: (String) -> Unit,
    modifier: Modifier = Modifier,
    onRetryModel: () -> Unit = {},
    onDictionary: (() -> Unit)? = null,
    onEdit: (() -> Unit)? = null,
    flipBackEnabled: Boolean = false,
    meaningOptions: List<MeaningOption> = emptyList(),
    meaningTarget: String = "fa",
    onMeaningSelected: (String) -> Unit = {}
) {
    var appliedMeaning by remember(entry.id, entry.word, entry.sentence, meaningTarget) { mutableStateOf<String?>(null) }
    var previousMeaning by remember(entry.id, entry.word, entry.sentence, meaningTarget) { mutableStateOf<String?>(null) }
    var showModelError by remember(entry.id, entry.word, entry.sentence, sentenceTranslation, meaningTarget) { mutableStateOf(false) }
    val wordTap: (String) -> Unit = { word -> if (interactive) onWordTap(word) }
    Box(modifier) {
        FlipCard(
            revealed = revealed,
            onFlip = onFlip,
            interactive = interactive,
            flipBackEnabled = flipBackEnabled,
            front = {
                Column(Modifier.fillMaxWidth().padding(26.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(entry.word, style = MaterialTheme.typography.headlineMedium.copy(textDirection = TextDirection.Ltr), textAlign = TextAlign.Center)
                    WordAudioButton(entry, wordAudioLoading, wordAudioError, interactive, onPlayWord)
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.review_tap_to_reveal), style = MaterialTheme.typography.labelSmall)
                }
            },
            back = {
                Column(Modifier.fillMaxWidth().padding(26.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    TappableFrenchText(entry.word, wordTap, selectable = true, style = MaterialTheme.typography.headlineMedium.copy(textDirection = TextDirection.Ltr), textAlign = TextAlign.Center)
                    WordAudioButton(entry, wordAudioLoading, wordAudioError, interactive, onPlayWord)
                    Spacer(Modifier.height(14.dp)); HorizontalDivider(); Spacer(Modifier.height(12.dp))
                    if (onDictionary != null || onEdit != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            onDictionary?.let { callback ->
                                TextButton(onClick = callback, enabled = interactive) {
                                    Icon(Icons.Default.Search, null)
                                    Spacer(Modifier.width(6.dp))
                                    Text(stringResource(R.string.vocab_open_dictionary))
                                }
                            }
                            onEdit?.let { callback ->
                                IconButton(onClick = callback, enabled = interactive) {
                                    Icon(Icons.Default.Edit, stringResource(R.string.action_edit))
                                }
                            }
                        }
                    }
                    entry.displayMeaning()?.let {
                        Text(it, style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                    }
                    if (revealed && meaningOptions.isNotEmpty()) {
                        val options = reviewMeaningOptions(meaningOptions, entry.displayMeaning(), appliedMeaning, previousMeaning)
                        MeaningOptionPills(options, appliedMeaning, meaningTarget, enabled = interactive,
                            quietMeaning = previousMeaning) { option ->
                            val replacement = replaceMeaningLine(entry.meaning, option.text)
                            if (replacement.meaning != entry.meaning) {
                                previousMeaning = replacement.previousLine
                                appliedMeaning = option.text
                                onMeaningSelected(replacement.meaning)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    if (entry.sentence.isNotBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TappableFrenchText(
                                entry.sentence,
                                wordTap,
                                selectable = true,
                                style = LocalTextStyle.current.copy(textDirection = TextDirection.Ltr),
                                fontStyle = FontStyle.Italic,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = onPlaySentence, enabled = interactive) {
                                if (sentenceAudioLoading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                else Icon(Icons.Default.VolumeUp, stringResource(R.string.accessibility_play_sentence))
                            }
                        }
                        when {
                            sentenceTranslationLoading && sentenceTranslation == null -> Text(stringResource(R.string.translation_loading), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                            sentenceTranslationError -> Text(stringResource(R.string.error_translation_unavailable), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                            sentenceTranslation != null -> Text(sentenceTranslation.text, color = MaterialTheme.colorScheme.onSurfaceVariant, fontStyle = FontStyle.Italic, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                        }
                        sentenceTranslation?.let { translation ->
                            val source = when (translation.source) {
                                ExampleSource.MODEL -> Icons.Default.AutoAwesome to R.string.example_source_model
                                ExampleSource.BUNDLED -> Icons.AutoMirrored.Filled.MenuBook to R.string.example_source_bundled
                                ExampleSource.GOOGLE -> Icons.Default.Translate to R.string.example_source_google
                                ExampleSource.EDITED -> null
                            }
                            val modelUnavailable = translation.source == ExampleSource.GOOGLE && translation.modelError != null
                            val reason = if (modelUnavailable) stringResource(R.string.example_model_unavailable,
                                stringResource(translationErrorReason(translation.modelError))) else null
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                source?.let { (icon, caption) ->
                                    Icon(icon, contentDescription = stringResource(caption),
                                        modifier = Modifier.size(16.dp).align(Alignment.CenterVertically),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (reason != null) {
                                    IconButton(onClick = { showModelError = !showModelError }, enabled = interactive,
                                        modifier = Modifier.size(48.dp).align(Alignment.CenterVertically)) {
                                        Icon(Icons.Default.CloudOff, contentDescription = reason,
                                            modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    val retryDescription = stringResource(R.string.example_retry_model)
                                    IconButton(onClick = onRetryModel, enabled = interactive && !sentenceTranslationLoading,
                                        modifier = Modifier.size(48.dp).align(Alignment.CenterVertically)
                                            .semantics { contentDescription = retryDescription }) {
                                        if (sentenceTranslationLoading) CircularProgressIndicator(
                                            Modifier.size(16.dp), strokeWidth = 2.dp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        else Icon(Icons.Default.Refresh, contentDescription = null,
                                            modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                            if (reason != null && showModelError) {
                                Text(reason, style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                            }
                        }
                        if (sentenceAudioError) Text(stringResource(R.string.error_audio_generation), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        )
    }
}
