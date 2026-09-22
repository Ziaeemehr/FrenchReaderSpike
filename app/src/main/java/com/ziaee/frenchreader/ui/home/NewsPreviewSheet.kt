package com.ziaee.frenchreader.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.ziaee.frenchreader.R
import com.ziaee.frenchreader.data.HeadlineEntity
import com.ziaee.frenchreader.ui.components.EditorialPrimaryButton
import com.ziaee.frenchreader.ui.components.MetadataBadge
import com.ziaee.frenchreader.ui.theme.FrenchReaderDesign
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
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
                .navigationBarsPadding()
        ) {
            NewsCardImage(headline, modifier = Modifier.fillMaxWidth().height(180.dp))
            Spacer(Modifier.height(16.dp))

            // French source content -- always LTR regardless of a Persian
            // (RTL) interface, same as NewsCard and Reading-screen bodies.
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Column {
                    Text(headline.title, style = FrenchReaderDesign.editorialTypography.sectionTitle)
                    Spacer(Modifier.height(8.dp))
                    Text(headline.snippet, style = MaterialTheme.typography.bodyMedium)
                }
            }
            Spacer(Modifier.height(FrenchReaderDesign.spacing.xSmall))

            Row {
                val dateSuffix = headline.publishedAtMs?.let {
                    " · " + SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(it))
                }.orEmpty()
                MetadataBadge(text = headline.sourceLabel + dateSuffix)
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

            EditorialPrimaryButton(
                text = stringResource(
                    if (isAlreadyDownloaded) R.string.preview_action_open_library
                    else R.string.preview_action_download_read
                ),
                onClick = onDownloadOrOpen,
                enabled = !isImporting,
                loading = isImporting,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
