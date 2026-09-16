package com.ziaee.frenchreader.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ziaee.frenchreader.R
import com.ziaee.frenchreader.data.HeadlineEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Shows a selected headline's metadata without fetching its full article
 * body (opening this sheet never downloads anything). The primary action
 * downloads and opens the article, or -- when [isAlreadyDownloaded] -- just
 * reopens the existing document; a failure keeps the sheet open with a
 * localized, retryable error instead of dismissing it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewsPreviewSheet(
    headline: HeadlineEntity,
    isImporting: Boolean,
    hasError: Boolean,
    isAlreadyDownloaded: Boolean,
    onDownloadOrOpen: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            NewsCardImage(headline, modifier = Modifier.fillMaxWidth().height(180.dp))
            Spacer(Modifier.height(16.dp))

            Text(headline.title, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            Text(headline.snippet, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(12.dp))

            Row {
                Text(headline.sourceLabel, style = MaterialTheme.typography.labelMedium)
                headline.publishedAtMs?.let { publishedAtMs ->
                    Text(
                        " · " + SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(publishedAtMs)),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
            Spacer(Modifier.height(16.dp))

            if (hasError) {
                Text(
                    stringResource(R.string.error_generic),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(8.dp))
            }

            Button(onClick = onDownloadOrOpen, enabled = !isImporting, modifier = Modifier.fillMaxWidth()) {
                if (isImporting) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text(
                        stringResource(
                            if (isAlreadyDownloaded) R.string.preview_action_open_library
                            else R.string.preview_action_download_read
                        )
                    )
                }
            }
        }
    }
}
