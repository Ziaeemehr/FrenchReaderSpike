package com.ziaee.frenchreader.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.ziaee.frenchreader.R
import com.ziaee.frenchreader.ui.components.MetadataBadge

@Composable
fun TextLevelBadge(level: String?, modifier: Modifier = Modifier) {
    MetadataBadge(
        text = level ?: stringResource(R.string.text_level_unspecified),
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant.let {
            if (level == null) it.copy(alpha = 0.65f) else it
        }
    )
}
