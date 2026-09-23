package com.ziaee.frenchreader.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.ziaee.frenchreader.R
import com.ziaee.frenchreader.shadowing.SentenceRef

@Composable
fun ShadowSummaryDialog(
    ui: ReadingViewModel.ShadowSummaryUi,
    onPractice: (SentenceRef) -> Unit,
    onDismiss: () -> Unit
) {
    val s = ui.summary
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.shadowing_summary_title)) },
        text = {
            Column {
                Text(
                    stringResource(R.string.shadowing_summary_accuracy, s.sentences, s.accuracyPercent),
                    style = MaterialTheme.typography.bodyLarge
                )
                if (s.averagePaceRatio != null && s.goodPacePercent != null) {
                    Text(
                        stringResource(
                            R.string.shadowing_summary_pace,
                            String.format(java.util.Locale.ROOT, "%.1f", s.averagePaceRatio),
                            s.goodPacePercent
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                if (s.weakest.isNotEmpty()) {
                    Text(
                        stringResource(R.string.shadowing_summary_weakest),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                    // French sentences stay LTR in the Persian UI.
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Column {
                            s.weakest.forEach { w ->
                                TextButton(onClick = { onPractice(w.ref) }) {
                                    Text(
                                        "${w.accuracyPercent}% · ${ui.weakTexts[w.ref].orEmpty()}",
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.shadowing_summary_close))
            }
        }
    )
}
