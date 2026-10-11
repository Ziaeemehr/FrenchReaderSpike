package com.ziaee.frenchreader.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.ziaee.frenchreader.R
import com.ziaee.frenchreader.data.VocabEntry
import com.ziaee.frenchreader.data.isLikelyFrench
import com.ziaee.frenchreader.shadowing.ShadowError
import com.ziaee.frenchreader.shadowing.ShadowPhase
import com.ziaee.frenchreader.shadowing.ShadowingState
import kotlin.math.roundToInt

internal fun flashcardShadowText(entry: VocabEntry): String? =
    flashcardSentenceAudioText(entry)?.takeIf(::isLikelyFrench) ?: flashcardWordAudioText(entry)

internal data class CardPronunciationStats(val matched: Int = 0, val total: Int = 0) {
    val accuracy: Int? get() = if (total == 0) null else (matched * 100f / total).roundToInt()
    fun after(matched: Int, total: Int) = CardPronunciationStats(this.matched + matched, this.total + total)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CardShadowingRow(
    state: ShadowingState,
    onPressStart: () -> Boolean,
    onPressEnd: () -> Unit,
    onPressCancel: () -> Unit,
    onReplayMine: () -> Unit
) {
    val matchedColor = Color(0xFF2E7D32)
    val missedColor = Color(0xFFC62828)
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Surface(
            shape = CircleShape,
            color = if (state.phase == ShadowPhase.Recording) missedColor else MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(56.dp).pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown()
                    val started = onPressStart()
                    var released = false
                    try {
                        released = waitForUpOrCancellation() != null
                    } finally {
                        if (started) {
                            if (released) onPressEnd() else onPressCancel()
                        }
                    }
                }
            }
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Mic, stringResource(R.string.shadowing_hold_to_speak), tint = Color.White)
            }
        }
        Column(Modifier.weight(1f)) {
            val result = state.phase as? ShadowPhase.Result
            if (result != null) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        result.alignment.words.forEach { word ->
                            Text(word.word, color = if (word.matched) matchedColor else missedColor,
                                style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
            val status = when (val phase = state.phase) {
                ShadowPhase.Idle -> stringResource(R.string.shadowing_hold_to_speak)
                ShadowPhase.Recording -> stringResource(R.string.shadowing_listening)
                ShadowPhase.Recognizing -> stringResource(R.string.shadowing_recognizing)
                is ShadowPhase.Result -> stringResource(R.string.shadowing_score, phase.alignment.matched, phase.alignment.total)
                is ShadowPhase.Error -> stringResource(
                    if (phase.kind == ShadowError.NOT_HEARD) R.string.shadowing_not_heard else R.string.shadowing_engine_failed)
            }
            Text(status, style = MaterialTheme.typography.labelMedium)
        }
        if (state.hasRecording) {
            IconButton(onClick = onReplayMine) {
                Icon(Icons.Default.Headphones, stringResource(R.string.shadowing_replay_mine))
            }
        }
    }
}
