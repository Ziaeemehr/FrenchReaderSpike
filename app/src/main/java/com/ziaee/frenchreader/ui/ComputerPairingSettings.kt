package com.ziaee.frenchreader.ui

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
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
    var selected by remember { mutableStateOf<FoundComputer?>(null) }
    var code by remember { mutableStateOf("") }
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
        FilledTonalButton(enabled = !searching, onClick = {
            searching = true
            results = null
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
            TextButton(onClick = { selected = computer; code = ""; error = null }, modifier = Modifier.fillMaxWidth()) {
                Text(computer.name, modifier = Modifier.fillMaxWidth())
            }
        }
    }
    TextButton(onClick = { guide = true }) { Text(stringResource(R.string.computer_setup_guide)) }
    if (guide) LocalServerGuide { guide = false }
    selected?.let { computer ->
        AlertDialog(
            onDismissRequest = { if (!verifying) selected = null },
            title = { Text(stringResource(R.string.computer_pair_title, computer.name)) },
            text = {
                Column {
                    OutlinedTextField(code, { value -> code = value.filter { it in '0'..'9' }.take(6); error = null },
                        label = { Text(stringResource(R.string.computer_code)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        visualTransformation = PasswordVisualTransformation(), singleLine = true, enabled = !verifying)
                    error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
                    if (verifying) CircularProgressIndicator(Modifier.size(24.dp))
                }
            },
            confirmButton = {
                TextButton(enabled = validComputerCode(code) && !verifying, onClick = {
                    verifying = true
                    val enteredCode = code
                    scope.launch {
                        try {
                            withContext(Dispatchers.IO) { verifyComputer(computer, enteredCode); prefs.pair(computer, enteredCode) }
                            paired = prefs.paired
                            selected = null
                            code = ""
                            snackbar.showSnackbar(context.getString(R.string.computer_paired, computer.name))
                        } catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: WrongComputerCode) { error = R.string.computer_wrong_code }
                        catch (_: Exception) { error = R.string.computer_unreachable }
                        finally { verifying = false }
                    }
                }) { Text(stringResource(R.string.computer_pair)) }
            },
            dismissButton = {
                TextButton(enabled = !verifying, onClick = { selected = null; code = "" }) {
                    Text(stringResource(R.string.translation_cancel))
                }
            }
        )
    }
}
