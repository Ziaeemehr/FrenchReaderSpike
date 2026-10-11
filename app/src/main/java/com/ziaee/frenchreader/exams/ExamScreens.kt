@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.ziaee.frenchreader.exams

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.ziaee.frenchreader.R
import com.ziaee.frenchreader.share.shareFileIntent
import com.ziaee.frenchreader.share.shareSignatureLines
import com.ziaee.frenchreader.share.shareSignatureMarkdown
import com.ziaee.frenchreader.share.writePdf
import com.ziaee.frenchreader.ui.theme.FrenchReaderDesign
import com.ziaee.frenchreader.ui.theme.Vazirmatn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


@Composable
private fun exams(): State<List<Exam>?> {
    val assets = LocalContext.current.assets
    return produceState<List<Exam>?>(null, assets) {
        value = withContext(Dispatchers.IO) { runCatching { loadExams(assets) }.getOrDefault(emptyList()) }
    }
}

@Composable
internal fun BackRow(onBack: () -> Unit, title: String? = null, actions: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.accessibility_back))
        }
        if (title != null) Text(
            title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium,
            maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        ) else Spacer(Modifier.weight(1f))
        actions()
    }
}

/** Exam content is Persian whatever the app language is, so it is always laid out right-to-left. */
@Composable
internal fun PersianText(
    text: String, modifier: Modifier = Modifier, style: TextStyle = MaterialTheme.typography.bodyLarge,
    secondary: Boolean = false, bold: Boolean = false, maxLines: Int = Int.MAX_VALUE
) {
    Text(
        text, modifier = modifier, maxLines = maxLines, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        style = style.copy(
            fontFamily = Vazirmatn, textDirection = TextDirection.Rtl, textAlign = TextAlign.Right,
            fontWeight = if (bold) FontWeight.Medium else style.fontWeight
        ),
        color = if (secondary) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
    )
}

@Composable
fun ExamListScreen(onBack: () -> Unit, onOpenExam: (String) -> Unit) {
    val exams by exams()
    Scaffold { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(FrenchReaderDesign.spacing.small),
            verticalArrangement = Arrangement.spacedBy(FrenchReaderDesign.spacing.xSmall)
        ) {
            item(key = "back") { BackRow(onBack, stringResource(R.string.exams_title)) }
            if (exams == null) item { CircularProgressIndicator() }
            else if (exams.orEmpty().isEmpty()) item { Text(stringResource(R.string.exams_empty)) }
            items(exams.orEmpty(), key = { it.id }) { exam ->
                Card(onClick = { onOpenExam(exam.id) }, modifier = Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.fillMaxWidth().padding(FrenchReaderDesign.spacing.small),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            Text(exam.titleFr, Modifier.fillMaxWidth(), style = MaterialTheme.typography.titleMedium)
                        }
                        PersianText(exam.summaryFa, Modifier.fillMaxWidth(), MaterialTheme.typography.bodyMedium, secondary = true)
                    }
                }
            }
        }
    }
}

@Composable
fun ExamScreen(id: String, onBack: () -> Unit, onOpenQuestion: (String) -> Unit = {}) {
    val exams by exams()
    val exam = exams?.firstOrNull { it.id == id }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var rulesSelected by rememberSaveable { mutableStateOf(false) }
    val showQuestions = exam?.questionsFile != null && !rulesSelected
    val appName = stringResource(R.string.app_name)
    val sourcesLabel = stringResource(R.string.exams_sources)
    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = FrenchReaderDesign.spacing.small)) {
            // French title, so it reads left-to-right under the Persian interface too.
            BackRow(onBack, exam?.titleFr?.let { "\u2066$it\u2069" }) {
                if (exam != null && !showQuestions) {
                    var expanded by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { expanded = true }) {
                            Icon(Icons.Default.Share, stringResource(R.string.exams_share))
                        }
                        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.grammar_share_text)) }, onClick = {
                                expanded = false
                                val text = examToMarkdown(exam, sourcesLabel) + "\n---\n\n${shareSignatureMarkdown(appName)}\n"
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, text)
                                    putExtra(Intent.EXTRA_SUBJECT, exam.titleFr)
                                }
                                context.startActivity(Intent.createChooser(intent, context.getString(R.string.exams_share)))
                            })
                            DropdownMenuItem(text = { Text(stringResource(R.string.grammar_share_pdf)) }, onClick = {
                                expanded = false
                                scope.launch {
                                    runCatching {
                                        withContext(Dispatchers.IO) {
                                            shareFileIntent(context, "${exam.id}.pdf", "application/pdf", exam.titleFr) {
                                                writePdf(context, examToPdfBlocks(exam, sourcesLabel), shareSignatureLines(appName), it)
                                            }
                                        }
                                    }.onSuccess {
                                        context.startActivity(Intent.createChooser(it, context.getString(R.string.exams_share)))
                                    }.onFailure {
                                        snackbar.showSnackbar(context.getString(R.string.grammar_share_failure))
                                    }
                                }
                            })
                        }
                    }
                }
            }
            if (exams == null) CircularProgressIndicator()
            else if (exam == null) Text(stringResource(R.string.exams_not_found))
            else {
                ChipRow {
                    if (exam.questionsFile != null) FilterChip(showQuestions, { rulesSelected = false }, { Text(stringResource(R.string.exam_practice)) })
                    FilterChip(!showQuestions, { rulesSelected = true }, { Text(stringResource(R.string.exams_tab_rules)) })
                }
                if (showQuestions) ExamPracticeTab(id, onOpenQuestion, Modifier.weight(1f))
                else LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(FrenchReaderDesign.spacing.small)) {
                    item { PersianText(exam.summaryFa, Modifier.fillMaxWidth(), secondary = true) }
                    items(exam.sections) { ExamSectionContent(exam, it) }
                    item(key = "sources") { ExamSources(exam) }
                }
            }
        }
    }
}


@Composable
private fun ExamSectionContent(exam: Exam, section: ExamSection) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val tableSources = (section as? ExamSection.Table)?.sources.orEmpty()
            PersianText(
                section.titleFa + referenceLabel(exam.sourceNumbers(tableSources), isolate = true), Modifier.fillMaxWidth(),
                MaterialTheme.typography.titleMedium, bold = true
            )
            when (section) {
                is ExamSection.Text -> section.paragraphs.forEach { paragraph ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (paragraph.kind == "li") PersianText("•")
                        PersianText(paragraph.text + referenceLabel(exam.sourceNumbers(paragraph.sources), isolate = true), Modifier.weight(1f))
                    }
                }
                is ExamSection.Table -> ExamTable(section)
            }
        }
    }
}

/**
 * Tables always fit the phone width. Up to three columns are a plain grid of equal columns; wider
 * ones (the comparison of four exam versions) become one block per row, listing each column as
 * "header: value", because five columns of sentences cannot be read side by side.
 */
@Composable
private fun ExamTable(table: ExamSection.Table) {
    val outline = MaterialTheme.colorScheme.outlineVariant
    if (table.header.size <= 3) {
        Column(Modifier.fillMaxWidth().border(1.dp, outline)) {
            (listOf(table.header) + table.rows).forEachIndexed { index, row ->
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                    row.forEach { cell ->
                        Box(Modifier.weight(1f).fillMaxHeight().border(0.5.dp, outline).padding(8.dp)) {
                            PersianText(cell, Modifier.fillMaxWidth(), MaterialTheme.typography.bodyMedium, bold = index == 0)
                        }
                    }
                }
            }
        }
    } else {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            table.rows.forEach { row ->
                Column(Modifier.fillMaxWidth().border(1.dp, outline, MaterialTheme.shapes.small).padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    PersianText(row.first(), Modifier.fillMaxWidth(), MaterialTheme.typography.titleSmall, bold = true)
                    row.drop(1).forEachIndexed { index, cell ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PersianText(table.header[index + 1], Modifier.weight(0.32f), MaterialTheme.typography.bodyMedium, secondary = true)
                            PersianText(cell, Modifier.weight(0.68f), MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExamSources(exam: Exam) {
    val context = LocalContext.current
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(stringResource(R.string.exams_sources), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(R.string.exams_checked_on, exam.checkedOn),
            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        // Source titles are French: left-to-right even under the Persian interface.
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Column(Modifier.fillMaxWidth()) {
                exam.sources.forEachIndexed { index, source ->
                    TextButton(
                        onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(source.url))) } },
                        contentPadding = PaddingValues(horizontal = 4.dp)
                    ) {
                        Text("[${index + 1}] ${source.title}", Modifier.fillMaxWidth(), textAlign = TextAlign.Left)
                    }
                }
            }
        }
    }
}
