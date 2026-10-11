package com.ziaee.frenchreader.grammar

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.ziaee.frenchreader.R
import com.ziaee.frenchreader.text.isRtlText
import com.ziaee.frenchreader.ui.components.EditorialSectionHeader
import com.ziaee.frenchreader.ui.components.MetadataBadge
import com.ziaee.frenchreader.ui.theme.FrenchReaderDesign
import com.ziaee.frenchreader.ui.theme.LocalGrammarColors
import com.ziaee.frenchreader.ui.theme.Vazirmatn
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Labels inside a note follow the content language (Persian), not the app language. */
@Composable
private fun contentString(id: Int): String {
    val context = LocalContext.current
    return remember(context, id) {
        val config = Configuration(context.resources.configuration).apply { setLocale(Locale("fa")) }
        context.createConfigurationContext(config).getString(id)
    }
}

@Composable
private fun GrammarSectionHeader(id: Int) {
    GrammarText(
        GrammarCell(contentString(id)), rtl = true,
        style = MaterialTheme.typography.titleLarge, color = LocalGrammarColors.current.accent
    )
}

@Composable
private fun grammarNotes(): State<List<GrammarNote>?> {
    val assets = LocalContext.current.assets
    return produceState<List<GrammarNote>?>(null, assets) {
        value = withContext(Dispatchers.IO) { runCatching { loadGrammarNotes(assets) }.getOrDefault(emptyList()) }
    }
}

@Composable
fun GrammarListScreen(onBack: () -> Unit, onOpenNote: (String) -> Unit) {
    val notes by grammarNotes()
    val title = stringResource(R.string.grammar_title)
    GrammarPage { padding ->
        if (notes == null) {
            CircularProgressIndicator(Modifier.padding(padding).padding(FrenchReaderDesign.spacing.small))
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(FrenchReaderDesign.spacing.small),
                verticalArrangement = Arrangement.spacedBy(FrenchReaderDesign.spacing.xSmall)
            ) {
                item { GrammarBackRow(onBack, title) }
                if (notes!!.isEmpty()) item { Text(stringResource(R.string.grammar_empty)) }
                notes!!.groupBy { it.group }.forEach { (group, entries) ->
                    item { EditorialSectionHeader(stringResource(groupTitle(group))) }
                    items(entries, key = { it.id }) { note ->
                        Card(onClick = { onOpenNote(note.id) }, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(FrenchReaderDesign.spacing.small), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                GrammarText(GrammarCell(note.titleFr), rtl = false)
                                GrammarText(GrammarCell(note.titleFa), rtl = true, secondary = true)
                                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) { MetadataBadge(note.level) }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun groupTitle(group: String): Int = when (group) {
    "basics" -> R.string.grammar_group_basics
    "tenses" -> R.string.grammar_group_tenses
    "pronouns" -> R.string.grammar_group_pronouns
    "structures" -> R.string.grammar_group_structures
    else -> R.string.grammar_group_comparisons
}

@Composable
fun GrammarNoteScreen(id: String, onBack: () -> Unit) {
    val notes by grammarNotes()
    val note = notes?.firstOrNull { it.id == id }
    GrammarPage { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(FrenchReaderDesign.spacing.small),
            verticalArrangement = Arrangement.spacedBy(FrenchReaderDesign.spacing.small)
        ) {
            item { GrammarBackRow(onBack) }
            if (notes == null) item { CircularProgressIndicator() }
            else if (note == null) item { Text(stringResource(R.string.grammar_not_found)) }
            else {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        GrammarText(GrammarCell(note.titleFr), rtl = false, style = MaterialTheme.typography.headlineSmall)
                        GrammarText(GrammarCell(note.titleFa), rtl = true, secondary = true)
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) { MetadataBadge(note.level) }
                    }
                }
                items(note.blocks) { GrammarBlockContent(it) }
            }
        }
    }
}

@Composable
private fun GrammarPage(content: @Composable (PaddingValues) -> Unit) {
    Scaffold(content = content)
}

/** A compact back row that scrolls away with the page instead of a pinned app bar. */
@Composable
private fun GrammarBackRow(onBack: () -> Unit, title: String? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.accessibility_back))
        }
        if (title != null) Text(title, style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun GrammarBlockContent(block: GrammarBlock) {
    val colors = LocalGrammarColors.current
    Column(verticalArrangement = Arrangement.spacedBy(FrenchReaderDesign.spacing.xSmall)) {
        when (block) {
            is GrammarBlock.Prose -> {
                if (block.type == "summary") {
                    Surface(color = colors.note, shape = MaterialTheme.shapes.medium) {
                        Column(Modifier.padding(FrenchReaderDesign.spacing.small)) { GrammarProse(block.markdown) }
                    }
                } else {
                    GrammarSectionHeader(if (block.type == "usage") R.string.grammar_usage else R.string.grammar_more)
                    GrammarProse(block.markdown)
                }
            }
            is GrammarBlock.Structure -> {
                GrammarSectionHeader(R.string.grammar_structure)
                block.patterns.forEach { pattern ->
                    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.small) {
                        GrammarText(GrammarCell(pattern), rtl = false, formula = true, modifier = Modifier.padding(FrenchReaderDesign.spacing.xSmall))
                    }
                }
                GrammarProse(block.text)
            }
            is GrammarBlock.Examples -> {
                GrammarSectionHeader(R.string.grammar_examples)
                block.items.forEach { example ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(FrenchReaderDesign.spacing.small), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            GrammarText(example.fr, rtl = false)
                            GrammarText(GrammarCell(example.fa), rtl = true, secondary = true, markdown = true)
                        }
                    }
                }
            }
            is GrammarBlock.Mistake -> {
                GrammarSectionHeader(R.string.grammar_mistake)
                Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceVariant) {
                    Column(Modifier.padding(FrenchReaderDesign.spacing.small), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        MistakeLine(block.wrong, false)
                        MistakeLine(block.right, true)
                        GrammarText(GrammarCell(block.why), rtl = true, markdown = true)
                    }
                }
            }
            is GrammarBlock.Infographic -> {
                GrammarSectionHeader(when (block.kind) {
                    "timeline" -> R.string.grammar_timeline
                    "decision" -> R.string.grammar_decision
                    "steps" -> R.string.grammar_steps
                    else -> R.string.grammar_comparison
                })
                GrammarTable(block)
            }
        }
    }
}

@Composable
private fun GrammarProse(paragraphs: List<GrammarParagraph>) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Column(verticalArrangement = Arrangement.spacedBy(FrenchReaderDesign.spacing.xSmall)) {
            paragraphs.forEach { paragraph ->
                Row {
                    if (paragraph.kind == "li") Text("•", modifier = Modifier.padding(end = 8.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        splitLongCode(paragraph.text).forEach { segment ->
                            if (segment.longCode) {
                                GrammarText(GrammarCell(segment.text), rtl = false, color = LocalGrammarColors.current.target)
                            } else {
                                GrammarText(GrammarCell(segment.text), rtl = true, markdown = true)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MistakeLine(text: String, correct: Boolean) {
    val color = if (correct) LocalGrammarColors.current.correct else LocalGrammarColors.current.wrong
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(if (correct) Icons.Default.CheckCircle else Icons.Default.Cancel, contentDescription = null, tint = color)
            Text(contentString(if (correct) R.string.grammar_correct else R.string.grammar_wrong), color = color, fontFamily = Vazirmatn)
        }
        GrammarText(GrammarCell(text), rtl = false)
    }
}

@Composable
private fun GrammarText(
    cell: GrammarCell, rtl: Boolean, modifier: Modifier = Modifier,
    secondary: Boolean = false, formula: Boolean = false, markdown: Boolean = false,
    style: TextStyle = MaterialTheme.typography.bodyLarge, color: Color = Color.Unspecified
) {
    val colors = LocalGrammarColors.current
    val codeBackground = MaterialTheme.colorScheme.surfaceVariant
    val text = remember(cell, rtl, markdown, colors, codeBackground) {
        grammarAnnotatedText(cell, rtl, colors.target, codeBackground, markdown)
    }
    CompositionLocalProvider(LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
        Text(
            text = text,
            modifier = modifier.fillMaxWidth(),
            style = style.copy(
                textDirection = if (rtl) TextDirection.Rtl else TextDirection.Ltr,
                textAlign = if (rtl) TextAlign.Right else TextAlign.Left,
                fontFamily = when {
                    formula -> FontFamily.Monospace
                    rtl -> Vazirmatn
                    else -> FontFamily.SansSerif
                }
            ),
            color = when {
                color != Color.Unspecified -> color
                secondary -> MaterialTheme.colorScheme.onSurfaceVariant
                else -> MaterialTheme.colorScheme.onSurface
            }
        )
    }
}

@Composable
private fun GrammarTable(table: GrammarBlock.Infographic) {
    // Persian row labels or headers put the first column on the right.
    val rtl = (table.header + table.rows.mapNotNull { it.firstOrNull() }).any { isRtlText(it.text) }
    val rows = listOf(table.header) + table.rows
    val columns = table.header.size
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val available = maxWidth
        // A column is as wide as its longest cell, so short phrases stay on one line; only cells
        // longer than the cap wrap. Tables wider than the screen scroll sideways.
        val widestCell = 200.dp * fontScale.coerceAtLeast(1f)
        CompositionLocalProvider(LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
            Box(Modifier.horizontalScroll(rememberScrollState())) {
                // Measure each row first, then give every cell that row's maximum height.
                Layout(content = {
                    rows.forEachIndexed { row, cells ->
                        cells.forEach { cell ->
                            Box(Modifier.background(
                                when {
                                    row == 0 -> LocalGrammarColors.current.accentContainer
                                    row % 2 == 0 -> MaterialTheme.colorScheme.surfaceVariant
                                    else -> MaterialTheme.colorScheme.surface
                                }
                            ).border(0.5.dp, MaterialTheme.colorScheme.outlineVariant).padding(8.dp)) {
                                GrammarText(cell, rtl = isRtlText(cell.text))
                            }
                        }
                    }
                }) { measurables, _ ->
                    val natural = List(columns) { column ->
                        measurables.filterIndexed { index, _ -> index % columns == column }
                            .maxOf { it.maxIntrinsicWidth(Constraints.Infinity) + 2 }
                            .coerceIn(64.dp.roundToPx(), widestCell.roundToPx())
                    }
                    val fill = available.roundToPx()
                    val columnWidths = if (natural.sum() < fill) natural.map { it * fill / natural.sum() } else natural
                    val heights = measurables.chunked(columns).map { row ->
                        row.withIndex().maxOf { (column, cell) -> cell.maxIntrinsicHeight(columnWidths[column]) }
                    }
                    val placeables = measurables.mapIndexed { index, measurable ->
                        measurable.measure(Constraints.fixed(columnWidths[index % columns], heights[index / columns]))
                    }
                    layout(columnWidths.sum(), heights.sum()) {
                        var y = 0
                        heights.forEachIndexed { row, height ->
                            var x = 0
                            repeat(columns) { column ->
                                placeables[row * columns + column].placeRelative(x, y)
                                x += columnWidths[column]
                            }
                            y += height
                        }
                    }
                }
            }
        }
    }
}
