package com.ziaee.frenchreader.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ziaee.frenchreader.R

@Composable
fun ShadowingModelDialog(progress: Float?, androidAvailable: Boolean, onDismiss: () -> Unit,
    onDownload: () -> Unit, onUseAndroid: () -> Unit) {
    AlertDialog(
        onDismissRequest = { if (progress == null) onDismiss() },
        title = { Text(stringResource(R.string.shadowing_model_prompt_title)) },
        text = {
            if (progress == null) Text(stringResource(R.string.shadowing_model_prompt_body))
            else Column {
                Text(stringResource(R.string.shadowing_model_downloading, (progress * 100).toInt()))
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
            }
        },
        confirmButton = {
            TextButton(enabled = progress == null, onClick = onDownload) { Text(stringResource(R.string.shadowing_model_download)) }
        },
        dismissButton = {
            if (androidAvailable) TextButton(enabled = progress == null, onClick = onUseAndroid) {
                Text(stringResource(R.string.shadowing_use_android_engine))
            }
        }
    )
}
