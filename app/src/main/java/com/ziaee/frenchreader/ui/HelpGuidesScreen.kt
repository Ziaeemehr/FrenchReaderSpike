package com.ziaee.frenchreader.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ziaee.frenchreader.R

@Composable
fun HelpGuidesScreen(
    onOpenGestures: () -> Unit,
    onOpenLanguageModel: () -> Unit,
    onOpenVoice: () -> Unit,
    onBack: () -> Unit
) {
    GuideScaffold(R.string.help_guides_title, onBack) {
        Card(onClick = onOpenGestures, modifier = Modifier.fillMaxWidth()) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.gesture_guide_title)) },
                supportingContent = { Text(stringResource(R.string.help_gestures_description)) },
                leadingContent = { Icon(Icons.Default.HelpOutline, contentDescription = null) }
            )
        }
        Card(onClick = onOpenLanguageModel, modifier = Modifier.fillMaxWidth()) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.help_language_model_title)) },
                supportingContent = { Text(stringResource(R.string.help_language_model_description)) },
                leadingContent = { Icon(Icons.Default.Translate, contentDescription = null) }
            )
        }
        Card(onClick = onOpenVoice, modifier = Modifier.fillMaxWidth()) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.help_voice_title)) },
                supportingContent = { Text(stringResource(R.string.help_voice_description)) },
                leadingContent = { Icon(Icons.Default.RecordVoiceOver, contentDescription = null) }
            )
        }
    }
}

@Composable
fun GuideSetupScreen(voice: Boolean, onBack: () -> Unit) {
    GuideScaffold(if (voice) R.string.help_voice_title else R.string.help_language_model_title, onBack) {
        Text(stringResource(if (voice) R.string.help_voice_explanation else R.string.help_language_model_explanation),
            style = MaterialTheme.typography.bodyLarge)
        GuideFileActions(if (voice) "voice-xtts-setup.md" else "language-model-setup.md")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GuideScaffold(title: Int, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Scaffold(topBar = {
        TopAppBar(title = { Text(stringResource(title)) }, navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.accessibility_back))
            }
        })
    }) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(20.dp)) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
            }
        }
    }
}
