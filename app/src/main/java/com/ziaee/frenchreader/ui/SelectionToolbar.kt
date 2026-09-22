package com.ziaee.frenchreader.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.width
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
import androidx.compose.material.icons.filled.VolumeUp
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
        val y = (rect.top - popupContentSize.height - marginPx).coerceAtLeast(0f)
        return IntOffset(x.roundToInt(), y.roundToInt())
    }
}

/** The floating toolbar shown for a multi-word selection. */
@Composable
fun SelectionToolbarContent(
    onCopy: () -> Unit,
    onSave: () -> Unit,
    onListen: () -> Unit,
    onHighlight: () -> Unit,
    onMore: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        tonalElevation = 6.dp,
        shadowElevation = 6.dp,
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)) {
            TextButton(onClick = onCopy) {
                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                Text(stringResource(R.string.selection_action_copy))
            }
            TextButton(onClick = onSave) {
                Icon(Icons.Default.BookmarkAdd, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                Text(stringResource(R.string.selection_action_save))
            }
            TextButton(onClick = onListen) {
                Icon(Icons.Default.VolumeUp, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                Text(stringResource(R.string.selection_action_listen))
            }
            TextButton(onClick = onHighlight) {
                Icon(Icons.Default.FormatColorFill, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                Text(stringResource(R.string.selection_action_highlight))
            }
            IconButton(onClick = onMore) {
                Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.selection_action_more))
            }
        }
    }
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

@Composable
fun HighlightActionsPopupContent(onChangeColor: () -> Unit, onDelete: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        tonalElevation = 6.dp,
        shadowElevation = 6.dp,
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(modifier = Modifier.width(190.dp).padding(vertical = 4.dp)) {
            TextButton(onClick = onChangeColor, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.FormatColorFill, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                Text(stringResource(R.string.highlight_change_color), modifier = Modifier.fillMaxWidth())
            }
            TextButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                Text(stringResource(R.string.highlight_delete), modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

/** The toolbar shown while a selection resolves to exactly one word: lets the user tap Define,
 * open the More actions, or keep dragging a handle to extend the selection into a phrase
 * (handled entirely by [SentenceFlowText] -- this toolbar doesn't need to do anything for
 * that case, it just doesn't get in the way). */
@Composable
fun DefineToolbarContent(onDefine: () -> Unit, onHighlight: () -> Unit, onMore: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        tonalElevation = 6.dp,
        shadowElevation = 6.dp,
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)) {
            TextButton(onClick = onDefine) {
                Icon(Icons.Default.MenuBook, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                Text(stringResource(R.string.selection_action_define))
            }
            TextButton(onClick = onHighlight) {
                Icon(Icons.Default.FormatColorFill, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                Text(stringResource(R.string.selection_action_highlight))
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
