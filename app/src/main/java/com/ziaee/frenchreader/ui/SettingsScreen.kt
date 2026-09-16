package com.ziaee.frenchreader.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.ziaee.frenchreader.R
import com.ziaee.frenchreader.data.AppLanguage
import com.ziaee.frenchreader.data.AppearancePrefs
import com.ziaee.frenchreader.data.LocalePrefs
import com.ziaee.frenchreader.data.applyAppLanguage
import com.ziaee.frenchreader.ui.theme.AppearanceState
import com.ziaee.frenchreader.ui.theme.FontScale
import com.ziaee.frenchreader.ui.theme.ReadingBackground
import com.ziaee.frenchreader.ui.theme.ThemeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val appLanguage = LocalePrefs.get(context)

    Scaffold(
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
        }
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
