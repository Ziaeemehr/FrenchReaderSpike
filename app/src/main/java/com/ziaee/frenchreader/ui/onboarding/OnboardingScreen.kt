package com.ziaee.frenchreader.ui.onboarding

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ziaee.frenchreader.R
import com.ziaee.frenchreader.data.LanguagePrefs
import com.ziaee.frenchreader.language.LanguageCatalog

@StringRes
fun languageNameResource(code: String): Int = when (code) {
    "fr" -> R.string.language_name_fr
    "de" -> R.string.language_name_de
    "fa" -> R.string.language_name_fa
    "en" -> R.string.language_name_en
    else -> R.string.language_name_en
}

@Composable
fun OnboardingScreen(onContinue: () -> Unit) {
    val context = LocalContext.current
    var targetLanguage by remember { mutableStateOf(LanguageCatalog.DEFAULT_TARGET) }
    var knownLanguage by remember { mutableStateOf("fa") }
    val knownLanguages = LanguageCatalog.knownLanguages.filterNot { it == targetLanguage }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp, vertical = 32.dp),
        ) {
            Text(stringResource(R.string.onboarding_title), style = MaterialTheme.typography.headlineMedium)
            Text(
                stringResource(R.string.onboarding_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = 8.dp, bottom = 32.dp),
            )
            LanguageChoices(
                title = stringResource(R.string.onboarding_target_title),
                languages = LanguageCatalog.targets.map { it.code },
                selected = targetLanguage,
                onSelect = { selected ->
                    targetLanguage = selected
                    if (knownLanguage == selected) {
                        knownLanguage = LanguageCatalog.knownLanguages.first { it != selected }
                    }
                },
            )
            LanguageChoices(
                title = stringResource(R.string.onboarding_known_title),
                languages = knownLanguages,
                selected = knownLanguage,
                onSelect = { knownLanguage = it },
                modifier = Modifier.padding(top = 24.dp),
            )
            Spacer(Modifier.weight(1f))
            Button(
                onClick = {
                    LanguagePrefs.setLanguages(context, targetLanguage, knownLanguage)
                    onContinue()
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.onboarding_continue))
            }
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun LanguageChoices(
    title: String,
    languages: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        FlowRow(
            modifier = Modifier.padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            languages.forEach { code ->
                FilterChip(
                    selected = code == selected,
                    onClick = { onSelect(code) },
                    label = { Text(stringResource(languageNameResource(code))) },
                )
            }
        }
    }
}

@Composable
fun LanguagePickerDialog(
    title: String,
    languages: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                languages.forEach { code ->
                    TextButton(
                        onClick = { onSelect(code) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = code == selected, onClick = null)
                            Text(stringResource(languageNameResource(code)))
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.language_picker_cancel))
            }
        },
    )
}
