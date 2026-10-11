package com.ziaee.frenchreader.exams

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ziaee.frenchreader.R
import com.ziaee.frenchreader.data.ExamAttempt
import com.ziaee.frenchreader.ui.components.MetadataBadge
import com.ziaee.frenchreader.ui.theme.FrenchReaderDesign
import java.text.DateFormat
import java.util.Date

private const val NEW_ATTEMPT = 0L

/** One practice subject with every attempt the learner saved for it: answer, score out of 20 and a note. */
@Composable
fun ExamQuestionScreen(examId: String, questionId: String, onBack: () -> Unit) {
    val vm: ExamPracticeViewModel = viewModel(key = "question-$examId-$questionId")
    LaunchedEffect(examId) { vm.load(examId) }
    val state by vm.state.collectAsState()
    val task = state.questions?.tasks?.firstOrNull { t -> t.items.any { it.id == questionId } }
    val question = task?.items?.firstOrNull { it.id == questionId }
    val attempts = state.attempts.filter { it.questionId == questionId }
    var persian by rememberSaveable { mutableStateOf(true) }
    var editingId by rememberSaveable { mutableStateOf<Long?>(null) }
    var deletingId by rememberSaveable { mutableStateOf<Long?>(null) }
    // Saved with each attempt, so the answer stays readable if a later export drops or rewords the subject.
    val snapshot = question?.texts?.mapIndexed { index, text ->
        if (question.texts.size > 1) "Document ${index + 1}\n${text.fr}" else text.fr
    }?.joinToString("\n\n") ?: attempts.firstOrNull()?.questionFr
    Scaffold { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(FrenchReaderDesign.spacing.small),
            verticalArrangement = Arrangement.spacedBy(FrenchReaderDesign.spacing.small)
        ) {
            item(key = "back") {
                BackRow(onBack, task?.let { stringResource(R.string.exam_task, it.skill, it.task) })
            }
            if (state.loading) item { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
            else if (snapshot == null) item {
                Text(stringResource(if (state.loadFailed) R.string.exam_load_failed else R.string.exams_not_found))
            } else {
                item(key = "subject") {
                    Column(verticalArrangement = Arrangement.spacedBy(FrenchReaderDesign.spacing.xSmall)) {
                        task?.let {
                            FrenchText(it.titleFr, style = MaterialTheme.typography.labelLarge.copy(color = MaterialTheme.colorScheme.onSurfaceVariant))
                        }
                        Card(
                            Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            SelectionContainer {
                                Column(Modifier.padding(FrenchReaderDesign.spacing.small)) {
                                    if (question != null) QuestionTexts(question.texts, persian) else FrenchText(snapshot)
                                }
                            }
                        }
                        if (question != null && task != null) {
                            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(FrenchReaderDesign.spacing.xSmall)
                                ) {
                                    QuestionFrequency(question)
                                    Spacer(Modifier.weight(1f))
                                    if (question.texts.any { it.fa.isNotBlank() }) {
                                        FilterChip(persian, { persian = !persian }, { Text(stringResource(R.string.exam_persian)) })
                                    }
                                }
                            }
                            state.questions?.let { QuestionSourceLine(it, task) }
                        }
                    }
                }
                item(key = "attempts-title") {
                    Column(verticalArrangement = Arrangement.spacedBy(FrenchReaderDesign.spacing.xSmall)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.exam_my_attempts), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                            Button(onClick = { editingId = NEW_ATTEMPT }) {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Text(stringResource(R.string.exam_new_attempt), Modifier.padding(start = FrenchReaderDesign.spacing.half))
                            }
                        }
                        if (attempts.isEmpty()) Text(
                            stringResource(R.string.exam_attempts_empty),
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (state.saveFailed) Text(stringResource(R.string.exam_save_failed), color = MaterialTheme.colorScheme.error)
                    }
                }
                // Newest first, numbered in the order they were made.
                itemsIndexed(attempts.asReversed(), key = { _, attempt -> attempt.id }) { index, attempt ->
                    AttemptCard(attempts.size - index, attempt, onEdit = { editingId = attempt.id }, onDelete = { deletingId = attempt.id })
                }
            }
        }
    }
    deletingId?.let { id ->
        AlertDialog(
            onDismissRequest = { deletingId = null },
            title = { Text(stringResource(R.string.exam_delete_title)) },
            text = { Text(stringResource(R.string.exam_delete_message)) },
            confirmButton = { TextButton(onClick = { vm.delete(id); deletingId = null }) { Text(stringResource(R.string.exam_delete)) } },
            dismissButton = { TextButton(onClick = { deletingId = null }) { Text(stringResource(R.string.exam_cancel)) } }
        )
    }
    editingId?.let { id ->
        if (snapshot != null) key(id) {
            val existing = attempts.firstOrNull { it.id == id }
            AttemptEditor(existing, snapshot, onDismiss = { editingId = null }) { answer, score, note, saved ->
                val now = System.currentTimeMillis()
                vm.save(
                    ExamAttempt(
                        id, examId, questionId, existing?.questionFr ?: snapshot, answer.trim(), score, note.trim(),
                        existing?.createdAtMs ?: now, now
                    )
                ) { saved(it); if (it) editingId = null }
            }
        }
    }
}

@Composable
private fun AttemptCard(number: Int, attempt: ExamAttempt, onEdit: () -> Unit, onDelete: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(
                start = FrenchReaderDesign.spacing.small, end = FrenchReaderDesign.spacing.small,
                top = FrenchReaderDesign.spacing.small, bottom = FrenchReaderDesign.spacing.half
            ),
            verticalArrangement = Arrangement.spacedBy(FrenchReaderDesign.spacing.xSmall)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.exam_attempt_number, number), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                attempt.score?.let {
                    MetadataBadge(
                        stringResource(R.string.exam_score_value, formatScore(it)),
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            Text(
                listOfNotNull(
                    DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(attempt.createdAtMs)),
                    attempt.answer.takeIf { it.isNotBlank() }?.let { stringResource(R.string.exam_word_count, frenchWordCount(it)) }
                ).joinToString(" · "),
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (attempt.answer.isNotBlank()) SelectionContainer { FrenchText(attempt.answer) }
            if (attempt.note.isNotBlank()) Column {
                Text(
                    stringResource(R.string.exam_note_label),
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                SelectionContainer { Text(attempt.note, Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodyMedium) }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onEdit) { Text(stringResource(R.string.exam_edit)) }
                TextButton(onClick = onDelete) { Text(stringResource(R.string.exam_delete), color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}

/** Full-screen editor for a new or existing attempt, with the subject kept in view above the answer. */
@Composable
private fun AttemptEditor(
    attempt: ExamAttempt?, subject: String, onDismiss: () -> Unit,
    onSave: (String, Double?, String, (Boolean) -> Unit) -> Unit
) {
    val originalAnswer = attempt?.answer.orEmpty()
    val originalScore = attempt?.score?.let(::formatScore).orEmpty()
    val originalNote = attempt?.note.orEmpty()
    var answer by rememberSaveable { mutableStateOf(originalAnswer) }
    var score by rememberSaveable { mutableStateOf(originalScore) }
    var note by rememberSaveable { mutableStateOf(originalNote) }
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var saveFailed by remember { mutableStateOf(false) }
    val parsed = parseScore(score)
    val dirty = answer != originalAnswer || score != originalScore || note != originalNote
    val canSave = !saving && parsed.valid && dirty && listOf(answer, score, note).any { it.isNotBlank() }
    fun dismiss() { if (!saving) { if (dirty) confirmDiscard = true else onDismiss() } }
    fun save() {
        saving = true
        onSave(answer, parsed.value, note) { success -> saving = false; saveFailed = !success }
    }
    // System back goes through onDismissRequest, so unsaved input gets the same confirmation as Cancel.
    Dialog(onDismissRequest = ::dismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize()) {
            LazyColumn(
                // Status bar, navigation bar and keyboard: the list never runs under any of them.
                modifier = Modifier.fillMaxSize().safeDrawingPadding(),
                contentPadding = PaddingValues(FrenchReaderDesign.spacing.small),
                verticalArrangement = Arrangement.spacedBy(FrenchReaderDesign.spacing.small)
            ) {
                item {
                    BackRow(::dismiss, stringResource(if (attempt == null) R.string.exam_new_attempt else R.string.exam_edit_attempt)) {
                        TextButton(onClick = ::save, enabled = canSave) { Text(stringResource(R.string.exam_save)) }
                    }
                }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(FrenchReaderDesign.spacing.half)) {
                        Text(
                            stringResource(R.string.exam_subject),
                            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        FrenchText(subject, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                item {
                    OutlinedTextField(
                        answer, { answer = it }, Modifier.fillMaxWidth(), minLines = 8,
                        label = { Text(stringResource(R.string.exam_answer)) },
                        textStyle = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Ltr, textAlign = TextAlign.Left),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        supportingText = { Text(stringResource(R.string.exam_word_count, frenchWordCount(answer))) }
                    )
                }
                item {
                    OutlinedTextField(
                        score, { score = it }, Modifier.fillMaxWidth(), singleLine = true,
                        label = { Text(stringResource(R.string.exam_score_label)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), isError = !parsed.valid,
                        supportingText = { if (!parsed.valid) Text(stringResource(R.string.exam_score_error)) }
                    )
                }
                item {
                    OutlinedTextField(
                        note, { note = it }, Modifier.fillMaxWidth(), minLines = 3,
                        label = { Text(stringResource(R.string.exam_note)) },
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
                    )
                }
                if (saveFailed) item { Text(stringResource(R.string.exam_save_failed), color = MaterialTheme.colorScheme.error) }
                item {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(FrenchReaderDesign.spacing.small, Alignment.End)
                    ) {
                        TextButton(onClick = ::dismiss, enabled = !saving) { Text(stringResource(R.string.exam_cancel)) }
                        Button(onClick = ::save, enabled = canSave) { Text(stringResource(R.string.exam_save)) }
                    }
                }
            }
        }
        if (confirmDiscard) AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text(stringResource(R.string.exam_discard_title)) },
            text = { Text(stringResource(R.string.exam_discard_message)) },
            confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.exam_discard)) } },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text(stringResource(R.string.exam_cancel)) } }
        )
    }
}
