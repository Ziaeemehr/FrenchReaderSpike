package com.ziaee.frenchreader.studylog

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ziaee.frenchreader.R
import com.ziaee.frenchreader.data.SourceKind
import com.ziaee.frenchreader.data.StudySource
import com.ziaee.frenchreader.data.StudySkill

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun StudySourcesScreen(
    onBack: () -> Unit,
    vm: StudyLogViewModel = viewModel()
) {
    val state by vm.sourceUiState.collectAsState()
    var editing by remember { mutableStateOf<StudySource?>(null) }
    var name by remember { mutableStateOf("") }
    var adding by remember { mutableStateOf(false) }
    var defaultSkill by remember { mutableStateOf<StudySkill?>(null) }
    var kind by remember { mutableStateOf(SourceKind.OTHER) }
    var duplicateError by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.study_log_sources)) },
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
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    name = ""
                    kind = SourceKind.OTHER
                    defaultSkill = null
                    duplicateError = false
                    adding = true
                }
            ) {
                Text("+")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
        ) {
            items(
                items = state.sources,
                key = { it.id }
            ) { source ->
                StudySourceRow(
                    source = source,
                    totalMinutes = state.totals[source.id] ?: 0,
                    onEdit = {
                        editing = source
                        name = source.name
                        defaultSkill = source.defaultSkill
                        kind = source.kind
                        duplicateError = false
                    },
                    onToggleArchived = {
                        vm.updateSource(source.copy(archived = !source.archived))
                    },
                    onDelete = { vm.deleteSource(source) }
                )
            }
        }
    }

    if (adding || editing != null) {
        SourceEditorDialog(
            name = name,
            kind = kind,
            defaultSkill = defaultSkill,
            duplicateError = duplicateError,
            onNameChange = {
                name = it
                duplicateError = false
            },
            onKindChange = { kind = it },
            onDefaultSkillChange = { defaultSkill = it },
            onDismiss = {
                adding = false
                editing = null
            },
            onConfirm = {
                editing?.let { source ->
                    vm.updateSource(
                        source.copy(
                            name = name,
                            kind = kind,
                            defaultSkill = defaultSkill
                        )
                    ) { succeeded ->
                        if (succeeded) {
                            adding = false
                            editing = null
                        } else {
                            duplicateError = true
                        }
                    }
                } ?: vm.addSource(
                    name = name,
                    kind = kind,
                    defaultSkill = defaultSkill
                ) { id ->
                    if (id != null) {
                        adding = false
                        editing = null
                    } else {
                        duplicateError = true
                    }
                }
            }
        )
    }
}

@Composable
private fun StudySourceRow(
    source: StudySource,
    totalMinutes: Int,
    onEdit: () -> Unit,
    onToggleArchived: () -> Unit,
    onDelete: () -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    ListItem(
        headlineContent = { Text(source.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Text(
                "${stringResource(source.kind.labelRes())} · ${formatMinutes(totalMinutes)}"
            )
        },
        trailingContent = {
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = null)
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.study_log_rename)) },
                        onClick = {
                            menu = false
                            onEdit()
                        }
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                stringResource(
                                    if (source.archived) {
                                        R.string.study_log_unarchive
                                    } else {
                                        R.string.study_log_archive
                                    }
                                )
                            )
                        },
                        onClick = {
                            menu = false
                            onToggleArchived()
                        }
                    )
                    if (totalMinutes == 0) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.study_log_delete)) },
                            onClick = {
                                menu = false
                                onDelete()
                            }
                        )
                    }
                }
            }
        },
        modifier = Modifier.clickable(onClick = onEdit)
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SourceEditorDialog(
    name: String,
    kind: SourceKind,
    defaultSkill: StudySkill?,
    duplicateError: Boolean,
    onNameChange: (String) -> Unit,
    onKindChange: (SourceKind) -> Unit,
    onDefaultSkillChange: (StudySkill?) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.study_log_source_name)) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = onNameChange
                )
                FlowRow {
                    SourceKind.entries.forEach { value ->
                        FilterChip(
                            selected = kind == value,
                            onClick = { onKindChange(value) },
                            label = { Text(stringResource(value.labelRes())) }
                        )
                    }
                }
                FlowRow {
                    StudySkill.entries.filter { !it.legacy || it == defaultSkill }.forEach { skill ->
                        FilterChip(
                            selected = defaultSkill == skill,
                            onClick = {
                                onDefaultSkillChange(
                                    if (defaultSkill == skill) null else skill
                                )
                            },
                            label = { Text(skill.code) }
                        )
                    }
                }
                if (duplicateError) {
                    Text(
                        text = stringResource(R.string.study_log_source_duplicate),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.study_log_save))
            }
        }
    )
}
