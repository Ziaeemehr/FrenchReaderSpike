package com.ziaee.frenchreader.grammar

import android.content.Intent
import android.content.res.Configuration
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.ziaee.frenchreader.R
import com.ziaee.frenchreader.text.isRtlText
import com.ziaee.frenchreader.ui.components.MetadataBadge
import com.ziaee.frenchreader.ui.theme.FrenchReaderDesign
import com.ziaee.frenchreader.ui.theme.LocalGrammarColors
import com.ziaee.frenchreader.ui.theme.Vazirmatn
import java.util.Locale
import kotlinx.coroutines.launch
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
        style = MaterialTheme.typography.titleMedium, color = LocalGrammarColors.current.accent
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
    val context = LocalContext.current
    remember(context) { GrammarPrefs.load(context) }
    val prefs by GrammarPrefs.state.collectAsState()
    val title = stringResource(R.string.grammar_title)
    var query by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()
    val keyboard = LocalSoftwareKeyboardController.current
    val index = remember(notes) { GrammarSearchIndex(notes.orEmpty()) }
    // Empty means every level; the levels are few, so a plain list is enough to save and restore.
    var levels by rememberSaveable { mutableStateOf(listOf<String>()) }
    val allLevels = remember(notes) { notes.orEmpty().map { it.level }.distinct().sorted() }
    fun shown(note: GrammarNote) = levels.isEmpty() || note.level in levels
    val results by remember(index) { derivedStateOf { index.search(query).filter { shown(it.note) } } }
    GrammarPage { padding ->
        if (notes == null) {
            CircularProgressIndicator(Modifier.padding(padding).padding(FrenchReaderDesign.spacing.small))
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(FrenchReaderDesign.spacing.small),
                verticalArrangement = Arrangement.spacedBy(FrenchReaderDesign.spacing.xSmall)
            ) {
                item(key = "back") { GrammarBackRow(onBack, title) }
                item(key = "search") {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Content),
                        placeholder = { Text(stringResource(R.string.grammar_search_placeholder)) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = if (query.isNotEmpty()) {
                            {
                                IconButton(onClick = { query = "" }) {
                                    Icon(Icons.Default.Close, stringResource(R.string.grammar_search_clear))
                                }
                            }
                        } else null,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() })
                    )
                }
                if (allLevels.size > 1) item(key = "levels") {
                    // Level names are Latin (A1, B1), so the row reads left to right in every UI language.
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            allLevels.forEach { level ->
                                FilterChip(
                                    selected = level in levels,
                                    onClick = { levels = if (level in levels) levels - level else levels + level },
                                    label = { Text(level) }
                                )
                            }
                        }
                    }
                }
                if (query.isBlank()) {
                    if (notes!!.isEmpty()) item(key = "empty") { Text(stringResource(R.string.grammar_empty)) }
                    val favourites = notes!!.filter { it.id in prefs.favourites && shown(it) }
                    val byId = notes!!.associateBy { it.id }
                    val recents = prefs.recents.mapNotNull { byId[it.id] }.filter(::shown).take(5)
                    if (favourites.isNotEmpty()) {
                        item(key = "fav-header") { GrammarListHeader(stringResource(R.string.grammar_favourites)) }
                        items(favourites, key = { "fav:${it.id}" }) { note ->
                            GrammarNoteCard(note, onOpenNote, favourite = true)
                        }
                    }
                    if (recents.isNotEmpty()) {
                        item(key = "recent-header") { GrammarListHeader(stringResource(R.string.grammar_recently_opened)) }
                        items(recents, key = { "recent:${it.id}" }) { note ->
                            GrammarNoteCard(note, onOpenNote, favourite = note.id in prefs.favourites)
                        }
                    }
                    notes!!.filter(::shown).groupBy { it.group }.forEach { (group, entries) ->
                        item(key = "group-header:$group") { GrammarListHeader(stringResource(groupTitle(group))) }
                        items(entries, key = { "group:${it.id}" }) { note ->
                            GrammarNoteCard(note, onOpenNote, favourite = note.id in prefs.favourites)
                        }
                    }
                } else {
                    item(key = "result-count") {
                        Text(
                            pluralStringResource(R.plurals.grammar_search_results, results.size, results.size),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (results.isEmpty()) item(key = "no-results") {
                        Column {
                            Text(stringResource(R.string.grammar_search_no_results))
                            TextButton(onClick = { query = "" }) { Text(stringResource(R.string.grammar_search_clear)) }
                            if (levels.isNotEmpty()) {
                                TextButton(onClick = { levels = emptyList() }) { Text(stringResource(R.string.grammar_filter_clear)) }
                            }
                        }
                    }
                    items(results, key = { "search:${it.note.id}" }) { result ->
                        GrammarNoteCard(result.note, onOpenNote, result.snippet, result.note.id in prefs.favourites)
                    }
                }
            }
        }
    }
}

/** A quiet section label for the list: small accent text with a hairline, instead of a display heading. */
@Composable
private fun GrammarListHeader(title: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = FrenchReaderDesign.spacing.xSmall),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(FrenchReaderDesign.spacing.xSmall)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = LocalGrammarColors.current.accent
        )
        HorizontalDivider(Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun GrammarNoteCard(
    note: GrammarNote, onOpenNote: (String) -> Unit, snippet: GrammarCell? = null, favourite: Boolean = false
) {
    Card(onClick = { onOpenNote(note.id) }, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(FrenchReaderDesign.spacing.small), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GrammarText(GrammarCell(note.titleFr), rtl = false, modifier = Modifier.weight(1f))
                if (favourite) Icon(
                    Icons.Default.Star, stringResource(R.string.grammar_favourites),
                    modifier = Modifier.size(18.dp), tint = LocalGrammarColors.current.accent
                )
            }
            GrammarText(GrammarCell(note.titleFa), rtl = true, secondary = true)
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) { MetadataBadge(note.level) }
            if (snippet != null) GrammarText(snippet, rtl = isRtlText(snippet.text), secondary = true)
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
    val context = LocalContext.current
    remember(context) { GrammarPrefs.load(context) }
    val prefs by GrammarPrefs.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val labels = GrammarExportLabels(
        contentString(R.string.grammar_usage), contentString(R.string.grammar_structure),
        contentString(R.string.grammar_examples), contentString(R.string.grammar_mistake),
        contentString(R.string.grammar_more), contentString(R.string.grammar_comparison),
        contentString(R.string.grammar_timeline), contentString(R.string.grammar_decision),
        contentString(R.string.grammar_steps), contentString(R.string.grammar_correct),
        contentString(R.string.grammar_wrong), stringResource(R.string.app_name)
    )
    val markdown = remember(note, labels) { note?.let { grammarNoteToMarkdown(it, labels) } }
    var pendingMarkdown by rememberSaveable { mutableStateOf<String?>(null) }
    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/markdown")) { uri ->
        val text = pendingMarkdown
        pendingMarkdown = null
        if (uri != null && text != null) scope.launch {
            val saved = runCatching {
                withContext(Dispatchers.IO) {
                    val output = context.contentResolver.openOutputStream(uri) ?: error("Unable to open destination")
                    output.bufferedWriter(Charsets.UTF_8).use { it.write(text) }
                }
            }.isSuccess
            snackbar.showSnackbar(context.getString(if (saved) R.string.grammar_save_success else R.string.grammar_save_failure))
        }
    }
    LaunchedEffect(note?.id) {
        if (note != null) GrammarPrefs.opened(context, note.id)
    }
    GrammarPage(snackbar) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(FrenchReaderDesign.spacing.small),
            verticalArrangement = Arrangement.spacedBy(FrenchReaderDesign.spacing.small)
        ) {
            item {
                GrammarBackRow(onBack) {
                    if (note != null && markdown != null) {
                        IconButton(onClick = { GrammarPrefs.toggleFavourite(context, note.id) }) {
                            val favourite = note.id in prefs.favourites
                            Icon(
                                if (favourite) Icons.Default.Star else Icons.Outlined.StarBorder,
                                stringResource(if (favourite) R.string.grammar_remove_favourite else R.string.grammar_add_favourite),
                                tint = if (favourite) LocalGrammarColors.current.accent else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        var expanded by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { expanded = true }) {
                                Icon(Icons.Default.Share, stringResource(R.string.grammar_export))
                            }
                            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                DropdownMenuItem(text = { Text(stringResource(R.string.grammar_share_text)) }, onClick = {
                                    expanded = false
                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, markdown)
                                        putExtra(Intent.EXTRA_SUBJECT, note.titleFr)
                                    }
                                    context.startActivity(Intent.createChooser(intent, context.getString(R.string.grammar_share_text)))
                                })
                                DropdownMenuItem(text = { Text(stringResource(R.string.grammar_save_markdown)) }, onClick = {
                                    expanded = false
                                    pendingMarkdown = markdown
                                    saveLauncher.launch("${note.id}.md")
                                })
                            }
                        }
                    }
                }
            }
            if (notes == null) item { CircularProgressIndicator() }
            else if (note == null) item { Text(stringResource(R.string.grammar_not_found)) }
            else {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        GrammarText(GrammarCell(note.titleFr), rtl = false, style = MaterialTheme.typography.titleLarge)
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
private fun GrammarPage(snackbar: SnackbarHostState? = null, content: @Composable (PaddingValues) -> Unit) {
    Scaffold(snackbarHost = { if (snackbar != null) SnackbarHost(snackbar) }, content = content)
}

/** A compact back row that scrolls away with the page instead of a pinned app bar. */
@Composable
private fun GrammarBackRow(onBack: () -> Unit, title: String? = null, actions: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.accessibility_back))
        }
        if (title != null) Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.weight(1f))
        actions()
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
