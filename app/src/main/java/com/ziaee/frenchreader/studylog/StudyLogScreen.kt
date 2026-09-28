package com.ziaee.frenchreader.studylog

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ziaee.frenchreader.R
import com.ziaee.frenchreader.data.StudySession
import com.ziaee.frenchreader.data.StudySkill
import java.time.LocalDate
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.study_log_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null
                        )
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
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
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
                            actionLabel = "Undo"
                        )
                        if (result == SnackbarResult.ActionPerformed) {
                            vm.restore(deleted)
                        }
                    }
                }
            }
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
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = stringResource(
                    R.string.study_log_this_week,
                    formatMinutes(state.totalMinutes)
                ),
                style = MaterialTheme.typography.headlineSmall
            )
        }
        items(StudySkill.entries) { skill ->
            val value = state.totalsBySkill[skill] ?: 0
            Text("${skill.code}: ${formatMinutes(value)}")
            LinearProgressIndicator(
                progress = if (state.totalMinutes == 0) {
                    0f
                } else {
                    value.toFloat() / state.totalMinutes
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            val leastStudied = leastStudiedSkill(state.totalsBySkill)
            Text(
                text = leastStudied?.let {
                    stringResource(R.string.study_log_least_studied, it.code)
                } ?: stringResource(R.string.study_log_no_data)
            )
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
        state.recentSessions.groupBy { it.date }.forEach { (day, rows) ->
            item {
                Text(
                    text = "${LocalDate.ofEpochDay(day)} · ${formatMinutes(rows.sumOf { it.durationMin })}",
                    style = MaterialTheme.typography.titleMedium
                )
            }
            items(
                items = rows,
                key = { it.id }
            ) { row ->
                StudySessionRow(
                    session = row,
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
    onEdit: (Long) -> Unit,
    onDelete: (StudySession) -> Unit
) {
    ListItem(
        headlineContent = {
            Text("${session.skill.code} · ${formatMinutes(session.durationMin)}")
        },
        supportingContent = {
            session.note?.let { Text(it) }
        },
        modifier = Modifier.clickable { onEdit(session.id) },
        trailingContent = {
            TextButton(onClick = { onDelete(session) }) {
                Text(stringResource(R.string.study_log_delete))
            }
        }
    )
}

@Composable
private fun DeleteSessionDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.study_log_delete_confirm)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.study_log_delete))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.study_log_cancel))
            }
        }
    )
}
