package com.ziaee.frenchreader.ui.shared

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ziaee.frenchreader.R
import com.ziaee.frenchreader.text.TEXT_LEVELS

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TextLevelSelector(level: String?, onSelect: (String?) -> Unit) {
    Column {
        Text(stringResource(R.string.text_level_label), style = MaterialTheme.typography.labelMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (TEXT_LEVELS + listOf(null)).forEach { option ->
                FilterChip(
                    selected = option == level,
                    onClick = { onSelect(option) },
                    label = { Text(option ?: stringResource(R.string.text_level_unspecified)) }
                )
            }
        }
    }
}

@Composable
fun SetTextLevelDialog(level: String?, onDismiss: () -> Unit, onSelect: (String?) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.text_level_set)) },
        text = { TextLevelSelector(level, onSelect) },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )
}
