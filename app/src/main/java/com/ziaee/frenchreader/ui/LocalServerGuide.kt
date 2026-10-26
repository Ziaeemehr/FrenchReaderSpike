package com.ziaee.frenchreader.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ziaee.frenchreader.R

@Composable
internal fun LocalServerGuide(onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().systemBarsPadding().padding(20.dp)) {
                Text(stringResource(R.string.computer_setup_guide), style = MaterialTheme.typography.titleLarge)
                SelectionContainer(Modifier.weight(1f)) {
                    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        GuideFileActions("language-model-setup.md")
                        Text(stringResource(R.string.computer_guide_1))
                        Text(stringResource(R.string.computer_guide_2))
                        GuideCommand("brew install llama.cpp")
                        GuideCommand("https://github.com/ggml-org/llama.cpp/releases")
                        GuideCommand("llama-server -hf ggml-org/gemma-4-26B-A4B-it-GGUF:Q4_0 --alias gemma4 --port 8080")
                        Text(stringResource(R.string.computer_guide_3))
                        GuideCommand("tools/xtts_server")
                        Text(stringResource(R.string.computer_guide_4))
                        GuideCommand("https://github.com/Ziaeemehr/FrenchReaderSpike/tree/main/tools/local_server")
                        GuideCommand("python3 bridge.py")
                        Text(stringResource(R.string.computer_guide_5))
                        Text(stringResource(R.string.computer_guide_trusted_network))
                        Text(stringResource(R.string.computer_guide_6))
                        GuideCommand("adb reverse tcp:8080 tcp:8080")
                        Text(stringResource(R.string.computer_guide_bonjour))
                    }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.accessibility_back)) }
            }
        }
    }
}

@Composable
private fun GuideCommand(value: String) {
    Text(value, fontFamily = FontFamily.Monospace)
}
