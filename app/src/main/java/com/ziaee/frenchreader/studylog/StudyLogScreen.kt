package com.ziaee.frenchreader.studylog

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ziaee.frenchreader.R
import com.ziaee.frenchreader.data.StudySession
import com.ziaee.frenchreader.data.StudySkill
import com.ziaee.frenchreader.data.StudySource
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudyLogScreen(
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (Long) -> Unit,
    onSources: () -> Unit,
    onCharts: () -> Unit,
    vm: StudyLogViewModel = viewModel()
) {
    val state by vm.uiState.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var deleting by remember { mutableStateOf<StudySession?>(null) }
    var menu by remember { mutableStateOf(false) }
    var settings by remember { mutableStateOf(false) }
    val undoLabel = stringResource(R.string.study_log_undo)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.study_log_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { menu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = null)
                        }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.study_log_settings)) },
                                onClick = {
                                    menu = false
                                    settings = true
                                }
                            )
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = onAdd) {
                Text(stringResource(R.string.study_log_add_time))
            }
        }
    ) { padding ->
        StudyLogList(
            state = state,
            onEdit = onEdit,
            onSources = onSources,
            onCharts = onCharts,
            onDelete = { deleting = it },
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)
        )
    }
    deleting?.let { row ->
        DeleteSessionDialog(
            onDismiss = { deleting = null },
            onConfirm = {
                deleting = null
                vm.delete(row) { deleted ->
                    scope.launch {
                        val result = snackbar.showSnackbar(
                            message = formatMinutes(deleted.durationMin),
                            actionLabel = undoLabel
                        )
                        if (result == SnackbarResult.ActionPerformed) vm.restore(deleted)
                    }
                }
            }
        )
    }
    if (settings) {
        StudyLogSettingsDialog(
            state = state,
            onDismiss = { settings = false },
            onFirstDayChange = vm::setFirstDayOfWeek,
            onTargetChange = vm::setWeeklyTarget
        )
    }
}

@Composable
private fun StudyLogList(
    state: StudyLogUiState,
    onEdit: (Long) -> Unit,
    onSources: () -> Unit,
    onCharts: () -> Unit,
    onDelete: (StudySession) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = studySkillColors()
    val largest = state.totalsBySkill.values.maxOrNull() ?: 0
    LazyColumn(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        stringResource(R.string.study_log_this_week, formatMinutes(state.totalMinutes)),
                        style = MaterialTheme.typography.titleLarge
                    )
                    StudySkill.entries.forEach { skill ->
                        val value = state.totalsBySkill[skill] ?: 0
                        val target = state.weeklyTargets[skill] ?: 0
                        val detail = if (target > 0) {
                            "${formatMinutes(value)} / ${formatMinutes(target)}"
                        } else {
                            formatMinutes(value)
                        }
                        Text("${stringResource(skill.labelRes())} (${skill.code}) · $detail")
                        LinearProgressIndicator(
                            progress = if (target > 0) {
                                targetProgress(value, target)
                            } else {
                                relativeProgress(value, largest)
                            },
                            color = colors.getValue(skill),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    val least = leastStudiedSkill(state.totalsBySkill, state.weeklyTargets)
                    Text(
                        least?.let {
                            val target = state.weeklyTargets[it] ?: 0
                            val value = state.totalsBySkill[it] ?: 0
                            val label = if (target > 0) "${value * 100 / target}%" else formatMinutes(value)
                            stringResource(R.string.study_log_least_studied_value, it.code, label)
                        } ?: stringResource(R.string.study_log_no_data)
                    )
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onSources) {
                    Text(stringResource(R.string.study_log_sources))
                }
                OutlinedButton(onClick = onCharts) {
                    Text(stringResource(R.string.study_log_charts))
                }
            }
        }
        if (state.recentSessions.isEmpty()) {
            item { Text(stringResource(R.string.study_log_empty_hint)) }
        }
        state.recentSessions.groupBy { it.date }.forEach { (day, rows) ->
            item {
                Text(
                    "${dayHeader(day)} · ${formatMinutes(rows.sumOf { it.durationMin })}",
                    style = MaterialTheme.typography.titleMedium
                )
            }
            items(rows, key = { it.id }) { row ->
                StudySessionRow(
                    session = row,
                    source = state.sources.firstOrNull { it.id == row.sourceId },
                    color = colors.getValue(row.skill),
                    onEdit = onEdit,
                    onDelete = onDelete
                )
            }
        }
    }
}

@Composable
private fun StudySessionRow(
    session: StudySession,
    source: StudySource?,
    color: Color,
    onEdit: (Long) -> Unit,
    onDelete: (StudySession) -> Unit
) {
    ListItem(
        leadingContent = { Box(Modifier.size(10.dp).background(color, CircleShape)) },
        headlineContent = {
            val sourceName = source?.name ?: stringResource(R.string.study_log_no_source)
            Text("$sourceName · ${stringResource(session.skill.labelRes())} (${session.skill.code})")
        },
        supportingContent = {
            session.note?.let { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        },
        trailingContent = {
            Column(horizontalAlignment = Alignment.End) {
                Text(formatMinutes(session.durationMin))
                TextButton(onClick = { onDelete(session) }) {
                    Text(stringResource(R.string.study_log_delete))
                }
            }
        },
        modifier = Modifier.clickable { onEdit(session.id) }
    )
}

@Composable
private fun dayHeader(epochDay: Long): String {
    val date = LocalDate.ofEpochDay(epochDay)
    val today = LocalDate.now()
    return when (date) {
        today -> stringResource(R.string.study_log_today)
        today.minusDays(1) -> stringResource(R.string.study_log_yesterday)
        else -> date.format(
            DateTimeFormatter.ofPattern("EEEE, d MMM", appLocale())
        )
    }
}

@Composable
private fun StudyLogSettingsDialog(
    state: StudyLogUiState,
    onDismiss: () -> Unit,
    onFirstDayChange: (DayOfWeek) -> Unit,
    onTargetChange: (StudySkill, Int) -> Unit
) {
    var values by remember(state.weeklyTargets) {
        mutableStateOf(state.weeklyTargets.mapValues { (_, value) -> if (value == 0) "" else value.toString() })
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.study_log_settings)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.study_log_first_day))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(DayOfWeek.SATURDAY, DayOfWeek.MONDAY).forEach { day ->
                        FilterChip(
                            selected = state.firstDayOfWeek == day,
                            onClick = { onFirstDayChange(day) },
                            label = { Text(dayLabel(day)) }
                        )
                    }
                }
                Text(stringResource(R.string.study_log_weekly_targets))
                StudySkill.entries.forEach { skill ->
                    OutlinedTextField(
                        value = values[skill].orEmpty(),
                        onValueChange = { text ->
                            if (text.all(Char::isDigit)) values = values + (skill to text)
                        },
                        label = { Text("${stringResource(skill.labelRes())} (${skill.code})") },
                        suffix = { Text(stringResource(R.string.study_log_minutes_short)) },
                        singleLine = true
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                StudySkill.entries.forEach { skill ->
                    onTargetChange(skill, values[skill]?.toIntOrNull() ?: 0)
                }
                onDismiss()
            }) {
                Text(stringResource(R.string.study_log_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.study_log_cancel)) }
        }
    )
}

@Composable
private fun dayLabel(day: DayOfWeek): String = when (day) {
    DayOfWeek.SATURDAY -> stringResource(R.string.study_log_saturday)
    else -> stringResource(R.string.study_log_monday)
}

@Composable
private fun DeleteSessionDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.study_log_delete_confirm)) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.study_log_delete)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.study_log_cancel)) }
        }
    )
}
