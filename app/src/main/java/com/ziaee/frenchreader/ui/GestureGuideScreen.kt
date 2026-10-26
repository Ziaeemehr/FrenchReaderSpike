package com.ziaee.frenchreader.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ziaee.frenchreader.R

private enum class GestureKind(val icon: ImageVector, val label: Int) {
    TAP(Icons.Outlined.TouchApp, R.string.gesture_kind_tap),
    LONG_PRESS(Icons.Outlined.PanTool, R.string.gesture_kind_long_press),
    DRAG(Icons.Outlined.Swipe, R.string.gesture_kind_drag),
    VERTICAL_SWIPE(Icons.Outlined.SwipeVertical, R.string.gesture_kind_vertical_swipe),
    HORIZONTAL_SWIPE(Icons.Outlined.SwipeRight, R.string.gesture_kind_horizontal_swipe)
}

private data class GestureRow(val kind: GestureKind, val text: Int)

private data class GestureSection(val key: String, val title: Int, val rows: List<GestureRow>)

@Composable
private fun GestureGuideRow(kind: GestureKind, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
        Icon(kind.icon, stringResource(kind.label), modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.primary)
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun GestureGuideScreen(section: String = "", onBack: () -> Unit) {
    val sections = listOf(
        GestureSection("reading", R.string.gesture_reading_title, listOf(
            GestureRow(GestureKind.TAP, R.string.gesture_reading_row_1),
            GestureRow(GestureKind.LONG_PRESS, R.string.gesture_reading_row_2),
            GestureRow(GestureKind.TAP, R.string.gesture_reading_row_4),
            GestureRow(GestureKind.HORIZONTAL_SWIPE, R.string.gesture_reading_row_8),
            GestureRow(GestureKind.TAP, R.string.gesture_reading_row_10)
        )),
        GestureSection("watch", R.string.gesture_watch_title, listOf(
            GestureRow(GestureKind.VERTICAL_SWIPE, R.string.gesture_watch_row_2),
            GestureRow(GestureKind.TAP, R.string.gesture_watch_row_3),
            GestureRow(GestureKind.DRAG, R.string.gesture_watch_row_4),
            GestureRow(GestureKind.TAP, R.string.gesture_watch_row_6),
            GestureRow(GestureKind.LONG_PRESS, R.string.gesture_watch_row_7)
        )),
        GestureSection("vocabulary", R.string.gesture_vocab_title, listOf(
            GestureRow(GestureKind.TAP, R.string.gesture_vocab_row_1),
            GestureRow(GestureKind.LONG_PRESS, R.string.gesture_vocab_row_2),
            GestureRow(GestureKind.TAP, R.string.gesture_vocab_row_3),
            GestureRow(GestureKind.TAP, R.string.gesture_vocab_row_4),
            GestureRow(GestureKind.TAP, R.string.gesture_vocab_row_5)
        )),
        GestureSection("library", R.string.gesture_library_title, listOf(
            GestureRow(GestureKind.TAP, R.string.gesture_library_row_1),
            GestureRow(GestureKind.LONG_PRESS, R.string.gesture_library_row_2),
            GestureRow(GestureKind.TAP, R.string.gesture_library_row_3),
            GestureRow(GestureKind.VERTICAL_SWIPE, R.string.gesture_library_row_4)
        )),
        GestureSection("home", R.string.gesture_home_title, listOf(
            GestureRow(GestureKind.TAP, R.string.gesture_home_row_1),
            GestureRow(GestureKind.VERTICAL_SWIPE, R.string.gesture_home_row_4)
        )),
        GestureSection("shadowing", R.string.gesture_shadowing_title, listOf(
            GestureRow(GestureKind.LONG_PRESS, R.string.gesture_shadowing_row_1),
            GestureRow(GestureKind.LONG_PRESS, R.string.gesture_shadowing_row_3)
        )),
        GestureSection("dictionary", R.string.gesture_dictionary_title, listOf(
            GestureRow(GestureKind.TAP, R.string.gesture_dictionary_row_1),
            GestureRow(GestureKind.LONG_PRESS, R.string.gesture_dictionary_row_2),
            GestureRow(GestureKind.DRAG, R.string.gesture_dictionary_row_3)
        )),
        GestureSection("statistics", R.string.gesture_statistics_title, listOf(
            GestureRow(GestureKind.VERTICAL_SWIPE, R.string.gesture_statistics_row_1)
        ))
    )
    val sectionIndex = sections.indexOfFirst { it.key == section }
    val state = rememberLazyListState(initialFirstVisibleItemIndex = if (sectionIndex >= 0) sectionIndex + 1 else 0)
    Scaffold(topBar = {
        TopAppBar(title = { Text(stringResource(R.string.gesture_guide_title)) }, navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.accessibility_back))
            }
        })
    }) { padding ->
        LazyColumn(state = state, modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            item(key = "legend") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    GestureKind.entries.forEach { kind ->
                        GestureGuideRow(kind, stringResource(kind.label))
                    }
                }
            }
            sections.forEach { (key, title, rows) ->
                item(key = key) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
                        rows.forEach { row ->
                            GestureGuideRow(row.kind, stringResource(row.text))
                        }
                    }
                }
            }
            item { GuideFileActions("gestures.md") }
        }
    }
}
