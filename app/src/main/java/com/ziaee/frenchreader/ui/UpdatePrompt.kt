package com.ziaee.frenchreader.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ziaee.frenchreader.R
import com.ziaee.frenchreader.update.ReleaseInfo
import com.ziaee.frenchreader.update.UPDATE_DOWNLOAD_URL
import com.ziaee.frenchreader.update.UpdatePrefs
import com.ziaee.frenchreader.update.fetchLatestRelease
import com.ziaee.frenchreader.update.installedVersionName
import com.ziaee.frenchreader.update.plainReleaseNotes
import com.ziaee.frenchreader.update.shouldAutoCheck
import com.ziaee.frenchreader.update.shouldPrompt

/** Checks GitHub for a newer release once a day on launch (unless turned off in Settings). */
@Composable
fun AutoUpdatePrompt() {
    val context = LocalContext.current
    var release by remember { mutableStateOf<ReleaseInfo?>(null) }
    LaunchedEffect(Unit) {
        val now = System.currentTimeMillis()
        if (!shouldAutoCheck(
                UpdatePrefs.isAutoCheckEnabled(context), now,
                UpdatePrefs.lastCheckMs(context), UpdatePrefs.snoozedUntilMs(context)
            )
        ) return@LaunchedEffect
        val latest = fetchLatestRelease() ?: return@LaunchedEffect
        UpdatePrefs.setLastCheckMs(context, now)
        if (shouldPrompt(latest, installedVersionName(context), UpdatePrefs.skippedVersion(context))) release = latest
    }
    release?.let { UpdateDialog(it, onDismiss = { release = null }) }
}

/** "Version X is available": release notes, then Download (the APK, via the browser), Later, Skip. */
@Composable
fun UpdateDialog(release: ReleaseInfo, onDismiss: () -> Unit, showSkip: Boolean = true) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.update_available_title, release.version)) },
        text = {
            Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.update_available_body))
                plainReleaseNotes(release.notes).takeIf { it.isNotBlank() }?.let { notes ->
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.update_whats_new), style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(4.dp))
                    Text(notes, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(UPDATE_DOWNLOAD_URL)))
                }
                onDismiss()
            }) { Text(stringResource(R.string.update_download)) }
        },
        dismissButton = {
            if (showSkip) {
                TextButton(onClick = { UpdatePrefs.skip(context, release.version); onDismiss() }) {
                    Text(stringResource(R.string.update_skip))
                }
            }
            TextButton(onClick = { UpdatePrefs.snooze(context, System.currentTimeMillis()); onDismiss() }) {
                Text(stringResource(R.string.update_later))
            }
        }
    )
}
