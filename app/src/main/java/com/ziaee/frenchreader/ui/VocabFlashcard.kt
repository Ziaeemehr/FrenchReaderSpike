package com.ziaee.frenchreader.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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
    sentenceTranslation: String?,
    sentenceTranslationLoading: Boolean,
    sentenceTranslationError: Boolean,
    onWordTap: (String) -> Unit,
    modifier: Modifier = Modifier,
    onDictionary: (() -> Unit)? = null,
    onEdit: (() -> Unit)? = null,
    flipBackEnabled: Boolean = false
) {
    val wordTap: (String) -> Unit = { word -> if (interactive) onWordTap(word) }
    Box(modifier) {
        FlipCard(
            revealed = revealed,
            onFlip = onFlip,
            interactive = interactive,
            flipBackEnabled = flipBackEnabled,
            front = {
                Column(Modifier.fillMaxWidth().padding(26.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(entry.word, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
                    WordAudioButton(entry, wordAudioLoading, wordAudioError, interactive, onPlayWord)
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.review_tap_to_reveal), style = MaterialTheme.typography.labelSmall)
                }
            },
            back = {
                Column(Modifier.fillMaxWidth().padding(26.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    TappableFrenchText(entry.word, wordTap, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
                    WordAudioButton(entry, wordAudioLoading, wordAudioError, interactive, onPlayWord)
                    Spacer(Modifier.height(14.dp)); HorizontalDivider(); Spacer(Modifier.height(12.dp))
                    if (onDictionary != null || onEdit != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            onDictionary?.let { callback ->
                                TextButton(onClick = callback, enabled = interactive) {
                                    Icon(Icons.Default.Translate, null)
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
                    if (entry.sentence.isNotBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TappableFrenchText(
                                entry.sentence,
                                wordTap,
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
                            sentenceTranslationLoading -> Text(stringResource(R.string.translation_loading), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                            sentenceTranslationError -> Text(stringResource(R.string.error_translation_unavailable), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                            sentenceTranslation != null -> Text(sentenceTranslation, color = MaterialTheme.colorScheme.onSurfaceVariant, fontStyle = FontStyle.Italic, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                        }
                        if (sentenceAudioError) Text(stringResource(R.string.error_audio_generation), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        )
    }
}
