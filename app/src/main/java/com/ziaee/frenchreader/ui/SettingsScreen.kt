package com.ziaee.frenchreader.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
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
import androidx.compose.ui.unit.dp
import com.ziaee.frenchreader.data.AppearancePrefs
import com.ziaee.frenchreader.ui.theme.AppearanceState
import com.ziaee.frenchreader.ui.theme.FontScale
import com.ziaee.frenchreader.ui.theme.ReadingBackground
import com.ziaee.frenchreader.ui.theme.ThemeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("تنظیمات") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "بازگشت")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxWidth().padding(padding).padding(20.dp)) {
            Text("تم", style = MaterialTheme.typography.titleMedium)
            SettingsRadioRow(
                label = "پیروی از سیستم",
                selected = AppearanceState.themeMode == ThemeMode.SYSTEM,
                onClick = {
                    AppearanceState.themeMode = ThemeMode.SYSTEM
                    AppearancePrefs.setThemeMode(context, ThemeMode.SYSTEM)
                }
            )
            SettingsRadioRow(
                label = "روشن",
                selected = AppearanceState.themeMode == ThemeMode.LIGHT,
                onClick = {
                    AppearanceState.themeMode = ThemeMode.LIGHT
                    AppearancePrefs.setThemeMode(context, ThemeMode.LIGHT)
                }
            )
            SettingsRadioRow(
                label = "تیره",
                selected = AppearanceState.themeMode == ThemeMode.DARK,
                onClick = {
                    AppearanceState.themeMode = ThemeMode.DARK
                    AppearancePrefs.setThemeMode(context, ThemeMode.DARK)
                }
            )

            Text("رنگ پس‌زمینهٔ خوانش", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 20.dp))
            SettingsRadioRow(
                label = "کاغذی (پیش‌فرض)",
                selected = AppearanceState.readingBackground == ReadingBackground.SEPIA,
                onClick = {
                    AppearanceState.readingBackground = ReadingBackground.SEPIA
                    AppearancePrefs.setReadingBackground(context, ReadingBackground.SEPIA)
                }
            )
            SettingsRadioRow(
                label = "سفید",
                selected = AppearanceState.readingBackground == ReadingBackground.WHITE,
                onClick = {
                    AppearanceState.readingBackground = ReadingBackground.WHITE
                    AppearancePrefs.setReadingBackground(context, ReadingBackground.WHITE)
                }
            )
            SettingsRadioRow(
                label = "تیره (شبانه)",
                selected = AppearanceState.readingBackground == ReadingBackground.DARK,
                onClick = {
                    AppearanceState.readingBackground = ReadingBackground.DARK
                    AppearancePrefs.setReadingBackground(context, ReadingBackground.DARK)
                }
            )

            Text("اندازهٔ فونت خوانش", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 20.dp))
            FontScale.entries.forEach { scale ->
                SettingsRadioRow(
                    label = scale.label,
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

@Composable
private fun SettingsRadioRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Column {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier
                .fillMaxWidth()
                .selectable(selected = selected, onClick = onClick),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(selected = selected, onClick = onClick)
            Text(label)
        }
    }
}
