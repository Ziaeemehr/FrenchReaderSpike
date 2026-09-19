package com.ziaee.frenchreader.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.ziaee.frenchreader.R
import com.ziaee.frenchreader.backup.AuthorizationOutcome
import com.ziaee.frenchreader.backup.BackupPrefs
import com.ziaee.frenchreader.backup.DriveBackupClient
import com.ziaee.frenchreader.backup.GoogleAuthManager
import com.ziaee.frenchreader.backup.LocalBackup
import com.ziaee.frenchreader.backup.formatLastBackupLabel
import com.ziaee.frenchreader.data.AppDatabase
import com.ziaee.frenchreader.data.AppLanguage
import com.ziaee.frenchreader.data.AppearancePrefs
import com.ziaee.frenchreader.data.LocalePrefs
import com.ziaee.frenchreader.data.applyAppLanguage
import com.ziaee.frenchreader.ui.theme.AppearanceState
import com.ziaee.frenchreader.ui.theme.FontScale
import com.ziaee.frenchreader.ui.theme.ReadingBackground
import com.ziaee.frenchreader.ui.theme.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val appLanguage = LocalePrefs.get(context)
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.accessibility_back))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            Text(stringResource(R.string.language_title), style = MaterialTheme.typography.titleMedium)
            AppLanguage.entries.forEach { language ->
                SettingsRadioRow(
                    label = stringResource(language.labelResource()),
                    selected = appLanguage == language,
                    onClick = {
                        LocalePrefs.set(context, language)
                        applyAppLanguage(language)
                    }
                )
            }

            Text(
                stringResource(R.string.theme_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 20.dp)
            )
            SettingsRadioRow(
                label = stringResource(R.string.theme_system),
                selected = AppearanceState.themeMode == ThemeMode.SYSTEM,
                onClick = {
                    AppearanceState.themeMode = ThemeMode.SYSTEM
                    AppearancePrefs.setThemeMode(context, ThemeMode.SYSTEM)
                }
            )
            SettingsRadioRow(
                label = stringResource(R.string.theme_light),
                selected = AppearanceState.themeMode == ThemeMode.LIGHT,
                onClick = {
                    AppearanceState.themeMode = ThemeMode.LIGHT
                    AppearancePrefs.setThemeMode(context, ThemeMode.LIGHT)
                }
            )
            SettingsRadioRow(
                label = stringResource(R.string.theme_dark),
                selected = AppearanceState.themeMode == ThemeMode.DARK,
                onClick = {
                    AppearanceState.themeMode = ThemeMode.DARK
                    AppearancePrefs.setThemeMode(context, ThemeMode.DARK)
                }
            )

            Text(stringResource(R.string.reading_background_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 20.dp))
            SettingsRadioRow(
                label = stringResource(R.string.reading_background_sepia),
                selected = AppearanceState.readingBackground == ReadingBackground.SEPIA,
                onClick = {
                    AppearanceState.readingBackground = ReadingBackground.SEPIA
                    AppearancePrefs.setReadingBackground(context, ReadingBackground.SEPIA)
                }
            )
            SettingsRadioRow(
                label = stringResource(R.string.reading_background_white),
                selected = AppearanceState.readingBackground == ReadingBackground.WHITE,
                onClick = {
                    AppearanceState.readingBackground = ReadingBackground.WHITE
                    AppearancePrefs.setReadingBackground(context, ReadingBackground.WHITE)
                }
            )
            SettingsRadioRow(
                label = stringResource(R.string.reading_background_dark),
                selected = AppearanceState.readingBackground == ReadingBackground.DARK,
                onClick = {
                    AppearanceState.readingBackground = ReadingBackground.DARK
                    AppearancePrefs.setReadingBackground(context, ReadingBackground.DARK)
                }
            )

            Text(stringResource(R.string.reading_font_scale_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 20.dp))
            FontScale.entries.forEach { scale ->
                SettingsRadioRow(
                    label = stringResource(scale.labelResource()),
                    selected = AppearanceState.fontScale == scale,
                    onClick = {
                        AppearanceState.fontScale = scale
                        AppearancePrefs.setFontScale(context, scale)
                    }
                )
            }

            SettingsSwitchRow(
                label = stringResource(R.string.highlight_saved_words),
                checked = AppearanceState.highlightSavedWords,
                onCheckedChange = { enabled ->
                    AppearanceState.highlightSavedWords = enabled
                    AppearancePrefs.setHighlightSavedWords(context, enabled)
                }
            )

            LocalBackupSection(context, scope, snackbarHostState)
            CloudBackupSection(context, scope, snackbarHostState)
        }
    }
}

@Composable
private fun LocalBackupSection(
    context: Context,
    scope: kotlinx.coroutines.CoroutineScope,
    snackbarHostState: SnackbarHostState
) {
    var restoreUri by remember { mutableStateOf<Uri?>(null) }

    fun showMessage(text: String) {
        scope.launch { snackbarHostState.showSnackbar(text) }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    LocalBackup.exportTo(context, uri)
                    BackupPrefs.setLastBackupAtMs(context, System.currentTimeMillis())
                    showMessage(context.getString(R.string.local_backup_save_success))
                } catch (e: Exception) {
                    showMessage(context.getString(R.string.local_backup_save_failure))
                }
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        restoreUri = uri
    }

    Text(
        stringResource(R.string.local_backup_section_title),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = 20.dp)
    )
    Button(onClick = {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
        exportLauncher.launch("french_reader_backup_$timestamp.db")
    }) {
        Text(stringResource(R.string.local_backup_save))
    }
    Button(onClick = { importLauncher.launch(arrayOf("*/*")) }) {
        Text(stringResource(R.string.local_backup_restore))
    }

    if (restoreUri != null) {
        AlertDialog(
            onDismissRequest = { restoreUri = null },
            title = { Text(stringResource(R.string.local_backup_restore_confirm_title)) },
            text = { Text(stringResource(R.string.local_backup_restore_confirm_message)) },
            confirmButton = {
                Button(onClick = {
                    val uri = restoreUri ?: return@Button
                    restoreUri = null
                    scope.launch {
                        try {
                            if (LocalBackup.importFrom(context, uri)) {
                                restartApp(context)
                            } else {
                                showMessage(context.getString(R.string.local_backup_invalid_file))
                            }
                        } catch (e: Exception) {
                            showMessage(context.getString(R.string.local_backup_restore_failure))
                        }
                    }
                }) { Text(stringResource(R.string.local_backup_restore)) }
            },
            dismissButton = {
                Button(onClick = { restoreUri = null }) {
                    Text(stringResource(R.string.accessibility_back))
                }
            }
        )
    }
}

@Composable
private fun SettingsSwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

private fun AppLanguage.labelResource(): Int = when (this) {
    AppLanguage.SYSTEM -> R.string.language_system
    AppLanguage.FA -> R.string.language_persian
    AppLanguage.FR -> R.string.language_french
    AppLanguage.EN -> R.string.language_english
}

private fun FontScale.labelResource(): Int = when (this) {
    FontScale.SMALL -> R.string.font_scale_small
    FontScale.MEDIUM -> R.string.font_scale_medium
    FontScale.LARGE -> R.string.font_scale_large
    FontScale.XLARGE -> R.string.font_scale_xlarge
}

@Composable
private fun SettingsRadioRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Column {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier
                .fillMaxWidth()
                .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(selected = selected, onClick = null, modifier = Modifier.padding(12.dp))
            Text(label)
        }
    }
}

@Composable
private fun CloudBackupSection(
    context: Context,
    scope: kotlinx.coroutines.CoroutineScope,
    snackbarHostState: SnackbarHostState
) {
    val authManager = remember { GoogleAuthManager(context) }
    var signedInEmail by remember { mutableStateOf(BackupPrefs.getSignedInEmail(context)) }
    var lastBackupAtMs by remember { mutableStateOf(BackupPrefs.getLastBackupAtMs(context)) }
    var showRestoreConfirm by remember { mutableStateOf(false) }
    var pendingAccessTokenAction by remember { mutableStateOf<((String) -> Unit)?>(null) }

    fun showMessage(text: String) {
        scope.launch { snackbarHostState.showSnackbar(text) }
    }

    val resolutionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        val data = result.data
        if (result.resultCode == Activity.RESULT_OK && data != null) {
            val token = authManager.extractAccessTokenFromResolutionResult(data)
            pendingAccessTokenAction?.invoke(token)
        } else {
            showMessage(context.getString(R.string.backup_failure))
        }
        pendingAccessTokenAction = null
    }

    fun withDriveAccessToken(onToken: (String) -> Unit) {
        scope.launch {
            when (val outcome = authManager.requestDriveAuthorization()) {
                is AuthorizationOutcome.Authorized -> onToken(outcome.accessToken)
                is AuthorizationOutcome.NeedsResolution -> {
                    pendingAccessTokenAction = onToken
                    try {
                        resolutionLauncher.launch(IntentSenderRequest.Builder(outcome.pendingIntent).build())
                    } catch (e: IntentSender.SendIntentException) {
                        showMessage(context.getString(R.string.backup_failure))
                    }
                }
            }
        }
    }

    Text(
        stringResource(R.string.backup_section_title),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = 20.dp)
    )

    if (signedInEmail == null) {
        Button(onClick = {
            scope.launch {
                try {
                    val account = authManager.signIn()
                    BackupPrefs.setSignedInEmail(context, account.email)
                    signedInEmail = account.email
                } catch (e: Exception) {
                    showMessage(context.getString(R.string.backup_failure))
                }
            }
        }) {
            Text(stringResource(R.string.backup_sign_in))
        }
    } else {
        Text(signedInEmail!!)
        Text(formatLastBackupLabel(context, lastBackupAtMs))

        Button(onClick = {
            withDriveAccessToken { token ->
                scope.launch {
                    try {
                        withContext(Dispatchers.IO) {
                            val db = AppDatabase.get(context)
                            AppDatabase.checkpointWal(db)
                            val dbFile = context.getDatabasePath("french_reader.db")
                            val existingId = DriveBackupClient.findBackupFileId(token)
                            DriveBackupClient.uploadBackup(token, existingId, dbFile)
                        }
                        val now = System.currentTimeMillis()
                        BackupPrefs.setLastBackupAtMs(context, now)
                        lastBackupAtMs = now
                        showMessage(context.getString(R.string.backup_success))
                    } catch (e: Exception) {
                        showMessage(context.getString(R.string.backup_failure))
                    }
                }
            }
        }) {
            Text(stringResource(R.string.backup_now))
        }

        Button(onClick = { showRestoreConfirm = true }) {
            Text(stringResource(R.string.backup_restore))
        }

        Button(onClick = {
            scope.launch {
                authManager.signOut()
                BackupPrefs.setSignedInEmail(context, null)
                signedInEmail = null
            }
        }) {
            Text(stringResource(R.string.backup_sign_out))
        }
    }

    if (showRestoreConfirm) {
        AlertDialog(
            onDismissRequest = { showRestoreConfirm = false },
            title = { Text(stringResource(R.string.backup_restore_confirm_title)) },
            text = { Text(stringResource(R.string.backup_restore_confirm_message)) },
            confirmButton = {
                Button(onClick = {
                    showRestoreConfirm = false
                    withDriveAccessToken { token ->
                        scope.launch {
                            try {
                                val restored = withContext(Dispatchers.IO) {
                                    val fileId = DriveBackupClient.findBackupFileId(token)
                                        ?: return@withContext false
                                    val bytes = DriveBackupClient.downloadBackup(token, fileId)
                                    val dbFile = context.getDatabasePath("french_reader.db")
                                    AppDatabase.closeForRestore()
                                    File(dbFile.path + "-wal").delete()
                                    File(dbFile.path + "-shm").delete()
                                    dbFile.writeBytes(bytes)
                                    true
                                }
                                if (!restored) {
                                    showMessage(context.getString(R.string.backup_restore_not_found))
                                    return@launch
                                }
                                restartApp(context)
                            } catch (e: Exception) {
                                showMessage(context.getString(R.string.backup_restore_failure))
                            }
                        }
                    }
                }) { Text(stringResource(R.string.backup_restore)) }
            },
            dismissButton = {
                Button(onClick = { showRestoreConfirm = false }) {
                    Text(stringResource(R.string.accessibility_back))
                }
            }
        )
    }
}

private fun restartApp(context: Context) {
    val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
    intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    context.startActivity(intent)
    Runtime.getRuntime().exit(0)
}
