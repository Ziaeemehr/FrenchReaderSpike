package com.ziaee.frenchreader.ui

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.ziaee.frenchreader.R
import com.ziaee.frenchreader.shadowing.PaceRating
import com.ziaee.frenchreader.shadowing.ShadowError
import com.ziaee.frenchreader.shadowing.ShadowPhase
import com.ziaee.frenchreader.shadowing.ShadowingState
import com.ziaee.frenchreader.ui.theme.ReadingPalette

private val MatchedColor = Color(0xFF2E7D32)
private val MissedColor = Color(0xFFC62828)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ShadowingPanel(
    state: ShadowingState,
    isPlaying: Boolean,
    palette: ReadingPalette,
    onPlayOriginal: () -> Unit,
    onReplayMine: () -> Unit,
    onRetry: () -> Unit,
    onNext: () -> Unit,
    onPressStart: () -> Boolean,
    onPressEnd: () -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        // French sentence always LTR, even in the Persian UI.
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            val result = state.phase as? ShadowPhase.Result
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (result == null) {
                    Text(state.sentenceText, color = palette.ink, style = MaterialTheme.typography.bodyLarge)
                } else {
                    result.alignment.words.forEach { w ->
                        Text(
                            w.word,
                            color = if (w.matched) MatchedColor else MissedColor,
                            fontWeight = if (w.matched) FontWeight.Normal else FontWeight.SemiBold,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
        }
        val status = when (val p = state.phase) {
            ShadowPhase.Recording -> stringResource(R.string.shadowing_listening)
            ShadowPhase.Recognizing -> stringResource(R.string.shadowing_recognizing)
            is ShadowPhase.Result -> stringResource(R.string.shadowing_score, p.alignment.matched, p.alignment.total)
            is ShadowPhase.Error -> stringResource(
                if (p.kind == ShadowError.NOT_HEARD) R.string.shadowing_not_heard else R.string.shadowing_engine_failed
            )
            ShadowPhase.Idle -> if (state.sentenceText.isEmpty()) stringResource(R.string.shadowing_waiting_for_audio) else ""
        }
        if (status.isNotEmpty()) {
            Text(status, style = MaterialTheme.typography.titleMedium, color = palette.accent,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 6.dp))
        }
        (state.phase as? ShadowPhase.Result)?.pace?.let { pace ->
            val ratio = String.format(java.util.Locale.ROOT, "%.1f", pace.ratio)
            Text(
                stringResource(
                    when (pace.rating) {
                        PaceRating.FAST -> R.string.shadowing_pace_fast
                        PaceRating.GOOD -> R.string.shadowing_pace_good
                        PaceRating.SLOW -> R.string.shadowing_pace_slow
                    },
                    ratio
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = if (pace.rating == PaceRating.GOOD) MatchedColor else palette.inkFaded,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            TextButton(onClick = onPlayOriginal) {
                Icon(Icons.AutoMirrored.Filled.VolumeUp, null); Spacer(Modifier.width(4.dp)); Text(stringResource(R.string.shadowing_play_original))
            }
            TextButton(onClick = onReplayMine, enabled = state.hasRecording) {
                Icon(Icons.Default.Headphones, null); Spacer(Modifier.width(4.dp)); Text(stringResource(R.string.shadowing_replay_mine))
            }
            TextButton(onClick = onRetry) {
                Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(4.dp)); Text(stringResource(R.string.shadowing_retry))
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.weight(1f))
            val recording = state.phase == ShadowPhase.Recording
            Surface(
                shape = CircleShape,
                color = if (recording) MissedColor else palette.accent.copy(alpha = if (isPlaying) 0.4f else 1f),
                modifier = Modifier.size(72.dp).pointerInput(isPlaying) {
                    awaitEachGesture {
                        awaitFirstDown()
                        val started = onPressStart()
                        waitForUpOrCancellation()
                        if (started) onPressEnd()
                    }
                }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Mic, stringResource(R.string.shadowing_hold_to_speak), tint = Color.White, modifier = Modifier.size(32.dp))
                }
            }
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                TextButton(onClick = onNext) {
                    Text(stringResource(R.string.shadowing_next)); Icon(Icons.AutoMirrored.Filled.ArrowForward, null)
                }
            }
        }
    }
}
