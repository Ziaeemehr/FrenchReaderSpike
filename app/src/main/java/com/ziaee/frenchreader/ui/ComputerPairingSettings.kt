package com.ziaee.frenchreader.ui

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ziaee.frenchreader.R
import com.ziaee.frenchreader.net.*
import kotlinx.coroutines.*

@Composable
internal fun ComputerPairingSettings(context: Context, scope: CoroutineScope, snackbar: SnackbarHostState) {
    val prefs = remember { LocalServerPrefs(context) }
    var paired by remember { mutableStateOf(prefs.paired) }
    var searching by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<List<FoundComputer>?>(null) }
    var verifying by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<Int?>(null) }
    var guide by remember { mutableStateOf(false) }
    if (paired != null) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.computer_paired, paired!!.name), modifier = Modifier.weight(1f))
            TextButton(onClick = { prefs.forget(); paired = null; results = null }) {
                Text(stringResource(R.string.computer_forget))
            }
        }
    } else {
        FilledTonalButton(enabled = !searching && !verifying, onClick = {
            searching = true
            results = null
            error = null
            scope.launch {
                try { results = LocalServerFinder.discover(context) }
                finally { searching = false }
            }
        }) { Text(stringResource(R.string.computer_find)) }
        if (searching) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CircularProgressIndicator(Modifier.size(24.dp))
            Text(stringResource(R.string.computer_searching))
        }
        if (results?.isEmpty() == true) Text(stringResource(R.string.computer_not_found))
        results?.forEach { computer ->
            TextButton(enabled = !verifying, onClick = {
                verifying = true
                error = null
                scope.launch {
                    try {
                        withContext(Dispatchers.IO) { verifyComputer(computer); prefs.pair(computer) }
                        paired = prefs.paired
                        results = null
                        snackbar.showSnackbar(context.getString(R.string.computer_paired, computer.name))
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { error = R.string.computer_unreachable }
                    finally { verifying = false }
                }
            }, modifier = Modifier.fillMaxWidth()) {
                Text(computer.name, modifier = Modifier.fillMaxWidth())
            }
        }
    }
    if (verifying) CircularProgressIndicator(Modifier.size(24.dp))
    error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
    TextButton(onClick = { guide = true }) { Text(stringResource(R.string.computer_setup_guide)) }
    if (guide) LocalServerGuide { guide = false }
}
