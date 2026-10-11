@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.ziaee.frenchreader.exams

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ziaee.frenchreader.R
import com.ziaee.frenchreader.ui.components.MetadataBadge
import com.ziaee.frenchreader.ui.theme.FrenchReaderDesign

/** French text is left-to-right whatever the app language is. */
@Composable
internal fun FrenchText(
    text: String, modifier: Modifier = Modifier, maxLines: Int = Int.MAX_VALUE,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyLarge
) {
    Text(
        text, modifier.fillMaxWidth(), maxLines = maxLines, overflow = TextOverflow.Ellipsis,
        style = style.copy(textDirection = TextDirection.Ltr, textAlign = TextAlign.Left)
    )
}

/** The subject: one text, or two labelled documents for the synthesis task. [compact] is the list form. */
@Composable
internal fun QuestionTexts(texts: List<ExamQuestionText>, persian: Boolean, compact: Boolean = false) {
    val lines = if (!compact) Int.MAX_VALUE else if (texts.size > 1) 2 else 4
    Column(verticalArrangement = Arrangement.spacedBy(FrenchReaderDesign.spacing.half)) {
        texts.forEachIndexed { index, text ->
            if (texts.size > 1) Text(
                stringResource(R.string.exam_document, index + 1),
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary
            )
            FrenchText(text.fr, maxLines = lines)
            if (persian && text.fa.isNotBlank()) PersianText(
                text.fa, Modifier.fillMaxWidth(), MaterialTheme.typography.bodyMedium, secondary = true, maxLines = lines
            )
        }
    }
}

/** How often the subject was reported and when it was last seen; always left-to-right (digits and a date). */
@Composable
internal fun RowScope.QuestionFrequency(item: ExamQuestionItem) {
    MetadataBadge(stringResource(R.string.exam_frequency, item.count))
    Text(
        stringResource(R.string.exam_last_seen, item.lastSeen),
        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/** One-line credit for where the subjects and counts come from; tapping it opens the site. */
@Composable
internal fun QuestionSourceLine(set: ExamQuestionSet, task: ExamQuestionTask) {
    val uri = LocalUriHandler.current
    Text(
        stringResource(R.string.exam_source_line, set.source.title, task.exportedOn),
        modifier = Modifier.fillMaxWidth().clickable { runCatching { uri.openUri(set.source.url) } },
        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/** Chips wrap onto further lines: every option stays visible, nothing scrolls sideways. */
@Composable
internal fun ChipRow(content: @Composable FlowRowScope.() -> Unit) {
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(FrenchReaderDesign.spacing.xSmall),
        content = content
    )
}

@Composable
fun ExamPracticeTab(examId: String, onOpenQuestion: (String) -> Unit, modifier: Modifier = Modifier) {
    val vm: ExamPracticeViewModel = viewModel(key = "practice-$examId")
    LaunchedEffect(examId) { vm.load(examId) }
    val state by vm.state.collectAsState()
    var taskId by rememberSaveable { mutableStateOf<String?>(null) }
    var status by rememberSaveable { mutableStateOf(ExamQuestionStatus.ALL) }
    var sort by rememberSaveable { mutableStateOf(ExamQuestionSort.FREQUENT) }
    var persian by rememberSaveable { mutableStateOf(true) }
    var query by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()
    val set = state.questions
    val task = set?.tasks?.firstOrNull { it.id == taskId } ?: set?.tasks?.firstOrNull()
    val progress = remember(state.attempts) { examProgress(state.attempts) }
    val shown = remember(task, progress, query, status, sort) {
        filterExamQuestions(task?.items.orEmpty(), progress, query, status, sort)
    }
    // A new task or filter starts at the top; coming back from a subject keeps the position.
    var listKey by rememberSaveable { mutableStateOf("") }
    val currentKey = "${task?.id}|$status|$sort|$query"
    LaunchedEffect(currentKey) {
        if (listKey.isNotEmpty() && listKey != currentKey) listState.scrollToItem(0)
        listKey = currentKey
    }
    if (state.loading) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    if (set == null || task == null) {
        Text(
            stringResource(if (state.loadFailed) R.string.exam_load_failed else R.string.exams_questions_empty),
            modifier.fillMaxWidth().padding(top = FrenchReaderDesign.spacing.large),
            textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }
    LazyColumn(
        modifier.fillMaxSize(), state = listState,
        contentPadding = PaddingValues(vertical = FrenchReaderDesign.spacing.small),
        verticalArrangement = Arrangement.spacedBy(FrenchReaderDesign.spacing.xSmall)
    ) {
        item(key = "controls") {
            Column(verticalArrangement = Arrangement.spacedBy(FrenchReaderDesign.spacing.xSmall)) {
                ChipRow {
                    set.tasks.forEach { t ->
                        FilterChip(t.id == task.id, { taskId = t.id }, { Text(stringResource(R.string.exam_task, t.skill, t.task)) })
                    }
                }
                val done = task.items.count { it.id in progress }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FrenchText(task.titleFr, Modifier.weight(1f), maxLines = 1, style = MaterialTheme.typography.titleSmall)
                    Text(
                        stringResource(R.string.exam_practised, done, task.items.size),
                        style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary
                    )
                }
                LinearProgressIndicator(progress = { done.toFloat() / task.items.size }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    query, { query = it }, Modifier.fillMaxWidth(), singleLine = true,
                    placeholder = { Text(stringResource(R.string.exam_search)) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) IconButton(onClick = { query = "" }) {
                            Icon(Icons.Default.Clear, stringResource(R.string.exam_clear_search))
                        }
                    }
                )
                ChipRow {
                    ExamQuestionStatus.entries.forEach { value ->
                        FilterChip(status == value, { status = value }, {
                            Text(stringResource(when (value) {
                                ExamQuestionStatus.ALL -> R.string.exam_all
                                ExamQuestionStatus.TODO -> R.string.exam_todo
                                ExamQuestionStatus.DONE -> R.string.exam_done
                            }))
                        })
                    }
                }
                ChipRow {
                    ExamQuestionSort.entries.forEach { value ->
                        FilterChip(sort == value, { sort = value }, {
                            Text(stringResource(when (value) {
                                ExamQuestionSort.FREQUENT -> R.string.exam_frequent
                                ExamQuestionSort.RECENT -> R.string.exam_recent
                                ExamQuestionSort.WEAKEST -> R.string.exam_weakest
                            }))
                        })
                    }
                    FilterChip(persian, { persian = !persian }, { Text(stringResource(R.string.exam_persian)) })
                }
                Text(
                    stringResource(R.string.exam_shown, shown.size),
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                QuestionSourceLine(set, task)
            }
        }
        if (shown.isEmpty()) item(key = "none") {
            Text(
                stringResource(R.string.exam_no_matches),
                Modifier.fillMaxWidth().padding(top = FrenchReaderDesign.spacing.large),
                textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        items(shown, key = { it.id }) { item ->
            val itemProgress = progress[item.id]
            Card(onClick = { onOpenQuestion(item.id) }, modifier = Modifier.fillMaxWidth()) {
                Column(
                    Modifier.padding(FrenchReaderDesign.spacing.small),
                    verticalArrangement = Arrangement.spacedBy(FrenchReaderDesign.spacing.xSmall)
                ) {
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(FrenchReaderDesign.spacing.xSmall)
                        ) {
                            QuestionFrequency(item)
                            Spacer(Modifier.weight(1f))
                            if (itemProgress != null) MetadataBadge(
                                itemProgress.lastScore?.let { stringResource(R.string.exam_score_value, formatScore(it)) }
                                    ?: stringResource(R.string.exam_done),
                                icon = Icons.Default.Check,
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                    QuestionTexts(item.texts, persian, compact = true)
                    if (itemProgress != null) Text(
                        buildList {
                            add(pluralStringResource(R.plurals.exam_attempt_count, itemProgress.attempts, itemProgress.attempts))
                            itemProgress.lastScore?.let { add(stringResource(R.string.exam_last_score, formatScore(it))) }
                            itemProgress.bestScore?.let { add(stringResource(R.string.exam_best_score, formatScore(it))) }
                        }.joinToString(" · "),
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}
