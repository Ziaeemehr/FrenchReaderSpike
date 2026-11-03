package com.ziaee.frenchreader.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FormatColorFill
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupPositionProvider
import com.ziaee.frenchreader.R
import com.ziaee.frenchreader.util.ProcessTextApp
import kotlin.math.roundToInt

/** Intercepts the framework's own text-selection toolbar trigger (fired by [BasicTextField]
 * when a selection is active) purely to read its on-screen anchor [rect] and its
 * ready-made [onCopyRequested] clipboard callback -- the actual toolbar UI shown is
 * [SelectionToolbarContent], not the platform's default copy/paste bubble. */
class SelectionToolbarController : TextToolbar {
    var rect by mutableStateOf<Rect?>(null)
        private set
    var onCopyRequested by mutableStateOf<(() -> Unit)?>(null)
        private set
    override var status: TextToolbarStatus by mutableStateOf(TextToolbarStatus.Hidden)
        private set

    override fun showMenu(
        rect: Rect,
        onCopyRequested: (() -> Unit)?,
        onPasteRequested: (() -> Unit)?,
        onCutRequested: (() -> Unit)?,
        onSelectAllRequested: (() -> Unit)?
    ) {
        this.rect = rect
        this.onCopyRequested = onCopyRequested
        status = TextToolbarStatus.Shown
    }

    override fun hide() {
        status = TextToolbarStatus.Hidden
    }
}

/** Positions a [androidx.compose.ui.window.Popup] just above [rect] (a global-coordinates
 * selection anchor from [SelectionToolbarController]), centered horizontally on it and
 * clamped to stay within the window. */
class SelectionRectPositionProvider(
    private val rect: Rect,
    private val marginPx: Float
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: androidx.compose.ui.unit.IntRect,
        windowSize: IntSize,
        layoutDirection: androidx.compose.ui.unit.LayoutDirection,
        popupContentSize: IntSize
    ): IntOffset {
        val maxX = (windowSize.width - popupContentSize.width).toFloat().coerceAtLeast(0f)
        val x = (rect.left + rect.width / 2f - popupContentSize.width / 2f).coerceIn(0f, maxX)
        val above = rect.top - popupContentSize.height - marginPx
        val below = rect.bottom + marginPx
        // Prefer above; go below when above would be clipped and below fits, so the selection stays visible.
        val y = if (above >= 0f || below + popupContentSize.height > windowSize.height) above.coerceAtLeast(0f) else below
        return IntOffset(x.roundToInt(), y.roundToInt())
    }
}

/** The floating toolbar shown for a multi-word selection. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SelectionToolbarContent(
    onCopy: () -> Unit,
    onSave: () -> Unit,
    onListen: () -> Unit,
    onHighlight: () -> Unit,
    onChooseHighlightColor: () -> Unit,
    onDeleteHighlight: (() -> Unit)?,
    onMore: () -> Unit,
    onTranslate: (() -> Unit)? = null,
    showReadingActions: Boolean = true,
    showListen: Boolean = showReadingActions
) {
    val actions = buildList {
        if (showReadingActions) {
            add(ToolbarItem(Icons.Default.FormatColorFill, stringResource(R.string.selection_action_highlight), onHighlight))
            add(ToolbarItem(Icons.Default.Palette, stringResource(R.string.highlight_choose_color), onChooseHighlightColor))
            onDeleteHighlight?.let { add(ToolbarItem(Icons.Default.Delete, stringResource(R.string.highlight_delete), it)) }
        }
        add(ToolbarItem(Icons.Default.ContentCopy, stringResource(R.string.selection_action_copy), onCopy))
        add(ToolbarItem(Icons.Default.BookmarkAdd, stringResource(R.string.selection_action_save), onSave))
        if (showListen) add(ToolbarItem(Icons.Default.VolumeUp, stringResource(R.string.selection_action_listen), onListen))
        onTranslate?.let { add(ToolbarItem(Icons.Default.Translate, stringResource(R.string.pdf_translate), it)) }
        if (showReadingActions) add(ToolbarItem(Icons.Default.MoreVert, stringResource(R.string.selection_action_more), onMore))
    }
    // Icon-only buttons on a half circle whose open side faces the selection, so no text is covered.
    androidx.compose.ui.layout.Layout(content = {
        actions.forEach { action ->
            Surface(onClick = action.onClick, shape = androidx.compose.foundation.shape.CircleShape,
                tonalElevation = 2.dp, shadowElevation = 3.dp, color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.size(40.dp).semantics { contentDescription = action.label }) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(action.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                }
            }
        }
    }) { measurables, constraints ->
        val placeables = measurables.map { it.measure(androidx.compose.ui.unit.Constraints()) }
        val button = placeables.maxOfOrNull { it.width } ?: 0
        val radius = semicircleRadius(placeables.size, 52.dp.toPx())
        val width = (2 * radius).toInt() + button
        val height = radius.toInt() + button
        layout(width, height) {
            placeables.forEachIndexed { index, placeable ->
                val (x, y) = semicirclePosition(index, placeables.size, radius)
                placeable.place(x.toInt(), y.toInt())
            }
        }
    }
}

private class ToolbarItem(val icon: androidx.compose.ui.graphics.vector.ImageVector, val label: String, val onClick: () -> Unit)

/** Smallest radius that keeps neighbouring button centres [spacing] apart along the arc. */
internal fun semicircleRadius(count: Int, spacing: Float): Float =
    if (count < 2) 0f else spacing / (2f * kotlin.math.sin(kotlin.math.PI.toFloat() / (2f * (count - 1))))

/** Top-left offset of button [index] on the upper half circle, left to right. */
internal fun semicirclePosition(index: Int, count: Int, radius: Float): Pair<Float, Float> {
    if (count < 2) return 0f to 0f
    val angle = kotlin.math.PI.toFloat() * (1f - index.toFloat() / (count - 1))
    return radius + radius * kotlin.math.cos(angle) to radius - radius * kotlin.math.sin(angle)
}

@Composable
fun HighlightPalettePopupContent(
    onColorSelected: (String) -> Unit,
    onBack: (() -> Unit)? = null
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        tonalElevation = 6.dp,
        shadowElevation = 6.dp,
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(
                text = stringResource(R.string.highlight_choose_color),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HIGHLIGHT_PALETTE.forEach { (key, color) ->
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(color)
                            .clickable { onColorSelected(key) }
                    )
                }
            }
            onBack?.let { callback ->
                IconButton(onClick = callback) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.accessibility_back))
                }
            }
        }
    }
}

/** The toolbar shown while a selection resolves to exactly one word: lets the user tap Define,
 * open the More actions, or keep dragging a handle to extend the selection into a phrase
 * (handled entirely by [SentenceFlowText] -- this toolbar doesn't need to do anything for
 * that case, it just doesn't get in the way). */
@Composable
@OptIn(ExperimentalLayoutApi::class)
fun DefineToolbarContent(
    onDefine: () -> Unit,
    onHighlight: () -> Unit,
    onChooseHighlightColor: () -> Unit,
    onDeleteHighlight: (() -> Unit)?,
    onMore: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        tonalElevation = 6.dp,
        shadowElevation = 6.dp,
        color = MaterialTheme.colorScheme.surface
    ) {
        // Wraps onto a second line instead of scrolling sideways (owner's rule).
        FlowRow(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
            verticalArrangement = Arrangement.Center
        ) {
            TextButton(onClick = onHighlight) {
                Icon(Icons.Default.FormatColorFill, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                Text(stringResource(R.string.selection_action_highlight))
            }
            IconButton(onClick = onChooseHighlightColor) {
                Icon(
                    Icons.Default.Palette,
                    contentDescription = stringResource(R.string.highlight_choose_color)
                )
            }
            onDeleteHighlight?.let { delete ->
                TextButton(onClick = delete) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                    Text(stringResource(R.string.highlight_delete))
                }
            }
            TextButton(onClick = onDefine) {
                Icon(Icons.Default.MenuBook, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                Text(stringResource(R.string.selection_action_define))
            }
            IconButton(onClick = onMore) {
                Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.selection_action_more))
            }
        }
    }
}

/** Replaces [SelectionToolbarContent]/[DefineToolbarContent] in the same popup when the user
 * taps More -- mirrors Android's own selection-toolbar overflow: a small anchored list of every
 * installed ACTION_PROCESS_TEXT app (Ask ChatGPT, Anki Card, Reverso Context, ...), with a Back
 * arrow to return to the main actions instead of a separate full-screen sheet. */
@Composable
fun ProcessTextAppsPopupContent(
    apps: List<ProcessTextApp>,
    onAppSelected: (ProcessTextApp) -> Unit,
    onBack: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        tonalElevation = 6.dp,
        shadowElevation = 6.dp,
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(modifier = Modifier.width(220.dp).padding(vertical = 4.dp)) {
            if (apps.isEmpty()) {
                Text(
                    text = stringResource(R.string.process_text_apps_empty),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    textAlign = TextAlign.Center
                )
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 240.dp)) {
                    items(apps) { app ->
                        TextButton(onClick = { onAppSelected(app) }, modifier = Modifier.fillMaxWidth()) {
                            Text(app.label, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Start)
                        }
                    }
                }
            }
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.accessibility_back))
            }
        }
    }
}
