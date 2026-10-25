package com.ziaee.frenchreader.watch

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.ziaee.frenchreader.R
import com.ziaee.frenchreader.shadowing.*
import com.ziaee.frenchreader.ui.ShadowingAlignment
import com.ziaee.frenchreader.ui.ShadowingModelDialog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Docked practice controls; stays composed beneath the dictionary, including pending requests. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VideoShadowingPane(
    controller: VideoShadowingController,
    recordRequest: Int?,
    onStep: (Int) -> Unit
) {
    val state by controller.state.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val manager = remember { VoskModelManager(context) }
    var showModel by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf<Float?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    // Any later unit selection or playback invalidates a pending permission/model action.
    var pendingGeneration by remember { mutableStateOf<Int?>(null) }
    val micDenied = stringResource(R.string.shadowing_mic_denied)
    val modelFailed = stringResource(R.string.shadowing_model_failed)
    fun recordIfCurrent() {
        if (pendingGeneration == controller.generation && !controller.state.value.playingUnit) controller.record()
        pendingGeneration = null
    }
    fun modelChecked() {
        if (ShadowingPrefs.getEngine(context) == SpeechEngineKind.VOSK && !manager.isInstalled()) showModel = true
        else recordIfCurrent()
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) modelChecked() else { error = micDenied; pendingGeneration = null }
    }
    fun requestRecord(originGeneration: Int = controller.generation) {
        val acceptedGeneration = controller.beginRecordingRequest(originGeneration) ?: return
        pendingGeneration = acceptedGeneration
        error = null
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) modelChecked()
        else permission.launch(Manifest.permission.RECORD_AUDIO)
    }
    DisposableEffect(controller) {
        onDispose { controller.release() }
    }
    LaunchedEffect(state.index) { error = null; pendingGeneration = null }
    LaunchedEffect(recordRequest) { recordRequest?.let { requestRecord(it) } }
    Column(Modifier.fillMaxWidth()) {
        val enabled = state.unit != null
        // Feedback is bounded so long recognition results leave room for the transcript.
        if (state.phase != ShadowPhase.Idle || error != null || !enabled) {
            Column(Modifier.fillMaxWidth().heightIn(max = 120.dp)
                .verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (!enabled) Text(stringResource(R.string.watch_shadowing_no_captions),
                    style = MaterialTheme.typography.bodySmall)
                when (val phase = state.phase) {
                    ShadowPhase.Recording -> Text(stringResource(R.string.shadowing_listening), style = MaterialTheme.typography.bodySmall)
                    ShadowPhase.Recognizing -> Text(stringResource(R.string.shadowing_recognizing), style = MaterialTheme.typography.bodySmall)
                    is ShadowPhase.Result -> {
                        Text(stringResource(R.string.shadowing_score, phase.alignment.matched, phase.alignment.total),
                            style = MaterialTheme.typography.bodySmall)
                        ShadowingAlignment(phase.alignment)
                    }
                    is ShadowPhase.Error -> Text(stringResource(if (phase.kind == ShadowError.NOT_HEARD)
                        R.string.shadowing_not_heard else R.string.shadowing_engine_failed),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    ShadowPhase.Idle -> Unit
                }
                error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            }
        }
        var speedMenu by remember { mutableStateOf(false) }
        val speedLabel = stringResource(R.string.watch_shadow_speed_compact, when (state.speed) {
            0.5f -> "0.5"
            0.75f -> "0.75"
            else -> "1"
        })
        val speedDescription = stringResource(R.string.watch_shadow_speed, state.speed)
        Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
            FlowRow(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(0.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)) {
                IconButton(onClick = { pendingGeneration = null; onStep(-1) }, enabled = enabled && state.index > 0, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Default.SkipPrevious, stringResource(R.string.watch_shadow_previous))
                }
                IconButton(onClick = { pendingGeneration = null; controller.playUnit() }, enabled = enabled, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Default.PlayArrow, stringResource(R.string.watch_shadow_play))
                }
                IconButton(onClick = { pendingGeneration = null; controller.repeat() }, enabled = enabled, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Default.Replay, stringResource(R.string.watch_shadow_repeat))
                }
                IconButton(onClick = { pendingGeneration = null; onStep(1) }, enabled = enabled && state.index < state.units.lastIndex, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Default.SkipNext, stringResource(R.string.shadowing_next))
                }
                Box {
                    TextButton(onClick = { speedMenu = true }, enabled = enabled,
                        contentPadding = PaddingValues(horizontal = 4.dp),
                        modifier = Modifier.widthIn(min = 40.dp).semantics { contentDescription = speedDescription }) {
                        Text(speedLabel)
                    }
                    DropdownMenu(expanded = speedMenu, onDismissRequest = { speedMenu = false }) {
                        listOf(0.5f, 0.75f, 1f).forEach { speed ->
                            DropdownMenuItem(text = { Text(stringResource(R.string.watch_shadow_speed, speed)) }, onClick = {
                                controller.setSpeed(speed); speedMenu = false
                            })
                        }
                    }
                }
                IconToggleButton(checked = state.autoRecord, onCheckedChange = controller::setAutoRecord, enabled = enabled, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Default.Autorenew, stringResource(R.string.watch_shadow_auto_record),
                        tint = if (state.autoRecord && enabled) MaterialTheme.colorScheme.primary else LocalContentColor.current)
                }
                val recording = state.phase == ShadowPhase.Recording
                IconButton(onClick = {
                    if (recording) { pendingGeneration = null; controller.stop() } else requestRecord()
                }, enabled = enabled && state.phase != ShadowPhase.Recognizing,
                    modifier = Modifier.size(40.dp).background(
                        if (recording) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
                        CircleShape)) {
                    Icon(if (recording) Icons.Default.Stop else Icons.Default.Mic,
                        stringResource(if (recording) R.string.watch_shadow_stop else R.string.watch_shadow_record),
                        tint = if (recording) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer)
                }
                IconButton(onClick = controller::playMyRecording, enabled = state.hasRecording && state.phase != ShadowPhase.Recording && state.phase != ShadowPhase.Recognizing,
                    modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Default.Hearing, stringResource(R.string.watch_shadow_play_recording))
                }
            }
        }
    }
    if (showModel) ShadowingModelDialog(progress, AndroidSpeechEngine.isAvailable(context),
        onDismiss = { showModel = false; pendingGeneration = null },
        onDownload = {
            progress = 0f
            scope.launch {
                try {
                    manager.download { progress = it }
                    showModel = false
                    recordIfCurrent()
                } catch (failure: CancellationException) { throw failure
                } catch (_: Exception) { error = modelFailed; showModel = false; pendingGeneration = null
                } finally { progress = null }
            }
        },
        onUseAndroid = {
            ShadowingPrefs.setEngine(context, SpeechEngineKind.ANDROID)
            showModel = false
            recordIfCurrent()
        })
}
