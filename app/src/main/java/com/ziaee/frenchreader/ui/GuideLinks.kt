package com.ziaee.frenchreader.ui

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import com.ziaee.frenchreader.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GuideFileActions(assetName: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val error = stringResource(R.string.guide_file_error)
    val prompt = stringResource(R.string.guide_share_prompt)
    val chooser = stringResource(R.string.guide_share)
    val url = "https://github.com/Ziaeemehr/FrenchReaderSpike/blob/main/app/src/main/assets/guides/$assetName"
    var reading by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    fun perform(action: suspend () -> Unit) {
        scope.launch {
            busy = true
            try {
                action()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                snackbar.showSnackbar(error)
            } finally {
                busy = false
            }
        }
    }

    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/markdown")) { uri ->
        if (uri != null) perform {
            withContext(Dispatchers.IO) {
                context.contentResolver.openOutputStream(uri)?.use { output ->
                    context.assets.open("guides/$assetName").use { it.copyTo(output) }
                } ?: throw java.io.IOException("Cannot open document")
            }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.guide_ai_explanation), style = MaterialTheme.typography.bodySmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { reading = true }) { Text(stringResource(R.string.guide_read)) }
            OutlinedButton(enabled = !busy, onClick = { perform {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            } }) { Text(stringResource(R.string.guide_github)) }
            OutlinedButton(enabled = !busy, onClick = { perform { save.launch(assetName) } }) {
                Text(stringResource(R.string.guide_save))
            }
            OutlinedButton(enabled = !busy, onClick = { perform {
                val file = withContext(Dispatchers.IO) {
                    File(context.cacheDir, "shared/guides/$assetName").also { target ->
                        check(target.parentFile!!.isDirectory || target.parentFile!!.mkdirs())
                        context.assets.open("guides/$assetName").use { input ->
                            target.outputStream().use { input.copyTo(it) }
                        }
                    }
                }
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.share", file)
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/markdown"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_TEXT, "$prompt\n$url")
                    clipData = ClipData.newRawUri(assetName, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(intent, chooser))
            } }) { Text(chooser) }
        }
        SnackbarHost(snackbar)
    }
    if (reading) GuideAssetDialog(assetName) { reading = false }
}

@Composable
private fun GuideAssetDialog(assetName: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var content by remember(assetName) { mutableStateOf<String?>(null) }
    var failed by remember(assetName) { mutableStateOf(false) }
    LaunchedEffect(assetName) {
        try {
            content = withContext(Dispatchers.IO) {
                context.assets.open("guides/$assetName").bufferedReader().use { it.readText() }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            failed = true
        }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().systemBarsPadding().padding(20.dp)) {
                Text(stringResource(R.string.guide_read), style = MaterialTheme.typography.titleLarge)
                SelectionContainer(Modifier.weight(1f)) {
                    // The guide files are English: keep them left-to-right even in the Persian UI.
                    Text(content ?: if (failed) stringResource(R.string.guide_file_error) else "",
                        style = LocalTextStyle.current.copy(
                            textDirection = if (content != null) androidx.compose.ui.text.style.TextDirection.Ltr
                                else androidx.compose.ui.text.style.TextDirection.Content,
                            textAlign = if (content != null) androidx.compose.ui.text.style.TextAlign.Left
                                else androidx.compose.ui.text.style.TextAlign.Start),
                        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()))
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.accessibility_back)) }
            }
        }
    }
}
