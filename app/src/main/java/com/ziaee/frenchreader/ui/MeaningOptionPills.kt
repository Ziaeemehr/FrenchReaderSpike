package com.ziaee.frenchreader.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.ziaee.frenchreader.translate.MeaningOption

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MeaningOptionPills(
    options: List<MeaningOption>,
    selectedMeaning: String?,
    targetLang: String,
    enabled: Boolean = true,
    quietMeaning: String? = null,
    onSelect: (MeaningOption) -> Unit
) {
    CompositionLocalProvider(LocalLayoutDirection provides if (targetLang == "fa") LayoutDirection.Rtl else LayoutDirection.Ltr) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            options.forEach { option ->
                val selected = option.text == selectedMeaning
                Surface(modifier = Modifier.clip(RoundedCornerShape(50)).clickable(enabled = enabled) {
                    onSelect(option)
                }, shape = RoundedCornerShape(50),
                    color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                        else if (option.text == quietMeaning) MaterialTheme.colorScheme.surfaceContainer
                        else MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = if (selected) MaterialTheme.colorScheme.primary
                        else if (option.text == quietMeaning) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.onSurface) {
                    Row(Modifier.padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(option.text, style = MaterialTheme.typography.bodyMedium, maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        option.gloss?.let { gloss ->
                            Text(" · $gloss", style = MaterialTheme.typography.labelSmall, maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                color = if (selected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                        }
                    }
                }
            }
        }
    }
}
