package com.ziaee.frenchreader.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ziaee.frenchreader.R
import com.ziaee.frenchreader.comprehension.ComprehensionLevel
import com.ziaee.frenchreader.comprehension.comprehensionLevel

@Composable
fun ComprehensionBadge(percent: Int, modifier: Modifier = Modifier) {
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val level = comprehensionLevel(percent)
    val container = comprehensionContainerColor(level, dark)
    val content = comprehensionContentColor(level, dark)
    val description = stringResource(R.string.comprehension_known_description, percent)
    Surface(
        modifier = modifier.semantics { contentDescription = description },
        shape = MaterialTheme.shapes.extraSmall,
        color = container,
        contentColor = content
    ) {
        Text(
            text = stringResource(R.string.comprehension_percent, percent),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

private fun comprehensionContainerColor(level: ComprehensionLevel, dark: Boolean): Color = when (level) {
    ComprehensionLevel.EASY -> if (dark) Color(0xFF193824) else Color(0xFFE8F7EC)
    ComprehensionLevel.MANAGEABLE -> if (dark) Color(0xFF3D2E18) else Color(0xFFFFF3DC)
    ComprehensionLevel.HARD -> if (dark) Color(0xFF48241F) else Color(0xFFFFE9E4)
}

private fun comprehensionContentColor(level: ComprehensionLevel, dark: Boolean): Color = when (level) {
    ComprehensionLevel.EASY -> if (dark) Color(0xFF86EFAC) else Color(0xFF15803D)
    ComprehensionLevel.MANAGEABLE -> if (dark) Color(0xFFFBBF24) else Color(0xFFB45309)
    ComprehensionLevel.HARD -> if (dark) Color(0xFFFF8A65) else Color(0xFFD84315)
}
