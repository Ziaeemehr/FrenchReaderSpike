package com.ziaee.frenchreader.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.Button
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ziaee.frenchreader.ui.theme.FrenchReaderDesign

/**
 * Shared, stateless Compose primitives for the editorial design system
 * (Phase 1 of docs/Redesign the application's user interfac.md). Every
 * primitive here consumes [MaterialTheme]/[FrenchReaderDesign] only -- no
 * screen state, repository access, or navigation controller.
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorialTopAppBar(
    title: String,
    modifier: Modifier = Modifier,
    titleModifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {}
) {
    TopAppBar(
        title = {
            Text(
                text = title,
                style = FrenchReaderDesign.editorialTypography.sectionTitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = titleModifier
            )
        },
        modifier = modifier,
        navigationIcon = navigationIcon,
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            // Opaque and matched to the page background rather than truly
            // transparent -- pull-to-refresh's idle indicator sits just
            // above its own bounds by design and needs an opaque bar over
            // it, or it bleeds through (see PullToRefreshContainer).
            containerColor = MaterialTheme.colorScheme.background,
            scrolledContainerColor = MaterialTheme.colorScheme.surface
        ),
        windowInsets = TopAppBarDefaults.windowInsets
    )
}

/**
 * A single clickable "looks like a search field" entry point -- not a real
 * text input. Opens the actual search sheet/screen via [onClick]. Exposed
 * as one semantics node so it can be found and clicked by its visible text.
 */
@Composable
fun EditorialSearchEntry(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = FrenchReaderDesign.sizes.touchTarget),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
    ) {
        Row(
            modifier = Modifier.padding(horizontal = FrenchReaderDesign.spacing.small),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                modifier = Modifier.size(FrenchReaderDesign.sizes.icon)
            )
            Spacer(FrenchReaderDesign.spacing.xSmall)
            Text(
                text = text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

@Composable
fun EditorialSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = FrenchReaderDesign.editorialTypography.sectionTitle,
            modifier = Modifier.weight(1f, fill = false)
        )
        trailing?.invoke()
    }
}

/**
 * A compact metadata pill (category, source, level, etc). Always shows text
 * -- optionally with a leading icon -- so meaning is never color-only.
 */
@Composable
fun MetadataBadge(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    containerColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onSecondaryContainer
) {
    Surface(
        modifier = modifier.defaultMinSize(minHeight = 24.dp),
        shape = MaterialTheme.shapes.extraSmall,
        color = containerColor,
        contentColor = contentColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = FrenchReaderDesign.spacing.xSmall, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(FrenchReaderDesign.sizes.iconSmall))
                Spacer(4.dp)
            }
            Text(text = text, style = MaterialTheme.typography.labelSmall)
        }
    }
}

/**
 * Wraps [LinearProgressIndicator] with a clamped value and a caller-supplied
 * visible label (e.g. "60 %") so progress is never communicated by the bar
 * alone.
 */
@Composable
fun EditorialProgressIndicator(
    progress: Float,
    modifier: Modifier = Modifier,
    label: String? = null
) {
    val clamped = progress.coerceIn(0f, 1f)
    val animated by animateFloatAsState(targetValue = clamped, label = "editorial-progress")
    Column(modifier = modifier) {
        LinearProgressIndicator(
            progress = { animated },
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 4.dp),
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
        if (label != null) {
            Spacer(4.dp)
            Text(text = label, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
fun EditorialPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false
) {
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        modifier = modifier.defaultMinSize(minHeight = FrenchReaderDesign.sizes.touchTarget)
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(FrenchReaderDesign.sizes.iconSmall),
                strokeWidth = 2.dp,
                color = LocalContentColor.current
            )
            Spacer(FrenchReaderDesign.spacing.xSmall)
        }
        Text(text = text)
    }
}

@Composable
fun EditorialIconButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier
) {
    IconButton(
        onClick = onClick,
        modifier = modifier.size(FrenchReaderDesign.sizes.touchTarget)
    ) {
        Icon(imageVector = icon, contentDescription = contentDescription)
    }
}

/**
 * Centers icon/title/body/an optional action within the available content
 * area -- never a fixed height, so it works on any screen size.
 */
@Composable
fun EditorialEmptyState(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(FrenchReaderDesign.spacing.medium),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(FrenchReaderDesign.sizes.touchTarget),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(FrenchReaderDesign.spacing.small)
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(FrenchReaderDesign.spacing.half)
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        if (actionLabel != null && onAction != null) {
            Spacer(FrenchReaderDesign.spacing.small)
            EditorialPrimaryButton(text = actionLabel, onClick = onAction)
        }
    }
}

@Composable
private fun Spacer(size: androidx.compose.ui.unit.Dp) {
    androidx.compose.foundation.layout.Spacer(modifier = Modifier.size(size))
}
