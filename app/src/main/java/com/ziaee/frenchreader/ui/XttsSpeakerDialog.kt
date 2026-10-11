package com.ziaee.frenchreader.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ziaee.frenchreader.R
import com.ziaee.frenchreader.data.XttsPrefs
import com.ziaee.frenchreader.tts.XttsClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Picks the local XTTS server's speaker right from the reading screen. The
 * speaker saved in Settings is always offered, so the local server can still
 * be chosen when its speaker list can't be loaded.
 */
@Composable
fun XttsSpeakerDialog(onSelect: (String) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val current = remember { XttsPrefs.getSpeaker(context) }
    var speakers by remember { mutableStateOf<List<String>?>(null) }

    LaunchedEffect(Unit) {
        speakers = withContext(Dispatchers.IO) {
            try {
                XttsClient.listSpeakers(context)
            } catch (_: Exception) {
                emptyList()
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.xtts_choose_speaker_title)) },
        text = {
            val loaded = speakers
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                if (loaded == null) {
                    item { CircularProgressIndicator(Modifier.padding(16.dp).size(24.dp)) }
                } else {
                    if (loaded.isEmpty()) {
                        item {
                            Text(
                                stringResource(R.string.xtts_speakers_unavailable),
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }
                    }
                    items(if (current in loaded) loaded else listOf(current) + loaded) { speaker ->
                        Row(
                            Modifier.fillMaxWidth().clickable { onSelect(speaker) }.padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (speaker == current) {
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(20.dp))
                            } else {
                                Spacer(Modifier.size(20.dp))
                            }
                            Spacer(Modifier.width(12.dp))
                            Text(speaker)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.accessibility_back)) }
        }
    )
}
