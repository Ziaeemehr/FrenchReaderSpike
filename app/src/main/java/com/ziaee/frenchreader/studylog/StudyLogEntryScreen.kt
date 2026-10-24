package com.ziaee.frenchreader.studylog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ziaee.frenchreader.R
import com.ziaee.frenchreader.data.StudyLogPrefs
import com.ziaee.frenchreader.data.StudySession
import com.ziaee.frenchreader.data.StudySkill
import com.ziaee.frenchreader.data.StudySource
import java.time.LocalDate
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun StudyLogEntryScreen(
    entryId: Long?,
    onBack: () -> Unit,
    vm: StudyLogViewModel = viewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sources by vm.sources.collectAsState()
    val today by vm.currentDay.collectAsState()
    var originalId by rememberSaveable(entryId) { mutableStateOf<Long?>(null) }
    var date by rememberSaveable(entryId) { mutableLongStateOf(LocalDate.now().toEpochDay()) }
    var hours by rememberSaveable(entryId) { mutableStateOf("0") }
    var minutes by rememberSaveable(entryId) { mutableStateOf("30") }
    var skill by rememberSaveable(entryId) { mutableStateOf(StudyLogPrefs.getLastSkill(context)) }
    var sourceId by rememberSaveable(entryId) { mutableStateOf<Long?>(null) }
    var note by rememberSaveable(entryId) { mutableStateOf("") }
    var warning by rememberSaveable(entryId) { mutableStateOf(false) }
    var error by remember { mutableStateOf<SessionValidation?>(null) }
    var saving by remember { mutableStateOf(false) }
    var newSource by rememberSaveable(entryId) { mutableStateOf(false) }
    var newSourceName by rememberSaveable(entryId) { mutableStateOf("") }
    var newSourceError by remember { mutableStateOf(false) }
    var showDatePicker by rememberSaveable(entryId) { mutableStateOf(false) }
    val parsedDuration = parseDuration(hours, minutes)
    val durationInvalid = parsedDuration == null || parsedDuration !in 1..1440
    val dateInvalid = date > today

    LaunchedEffect(entryId) {
        if (entryId == null || originalId != null) return@LaunchedEffect
        val row = vm.getSession(entryId)
        if (row == null) {
            error = SessionValidation.MissingEntry
        } else {
            originalId = row.id
            date = row.date
            hours = (row.durationMin / 60).toString()
            minutes = (row.durationMin % 60).toString()
            skill = row.skill
            sourceId = row.sourceId
            note = row.note.orEmpty()
        }
    }

    fun submit(confirmed: Boolean = false) {
        if (saving || entryId != null && originalId == null) return
        val duration = parseDuration(hours, minutes)
        if (duration == null) {
            error = SessionValidation.InvalidDuration
            return
        }
        saving = true
        scope.launch {
            val session = StudySession(
                id = originalId ?: 0,
                date = date,
                durationMin = duration,
                skill = skill,
                sourceId = sourceId,
                note = note.trim().ifBlank { null }
            )
            try {
                when (val result = vm.save(session, confirmed)) {
                    SaveResult.Saved -> onBack()
                    SaveResult.NeedsDailyLimitConfirmation -> warning = true
                    is SaveResult.Invalid -> error = result.reason
                    SaveResult.InFlight -> Unit
                }
            } finally {
                saving = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (entryId == null || originalId == 0L) {
                                R.string.study_log_add_time
                            } else {
                                R.string.study_log_edit_entry
                            }
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null
                        )
                    }
                }
            )
        }
    ) { padding ->
        if (entryId != null && originalId == null) {
            Box(Modifier.padding(padding).padding(16.dp)) {
                if (error == null) {
                    CircularProgressIndicator()
                } else {
                    Text(stringResource(error!!.messageRes()), color = MaterialTheme.colorScheme.error)
                }
            }
            return@Scaffold
        }
        EntryForm(
            date = date,
            hours = hours,
            minutes = minutes,
            skill = skill,
            sourceId = sourceId,
            note = note,
            sources = sources,
            validationError = error,
            durationInvalid = durationInvalid,
            dateInvalid = dateInvalid,
            isEditing = originalId != null && originalId != 0L,
            saveEnabled = !saving && (entryId == null || originalId != null),
            onShowDatePicker = { showDatePicker = true },
            onHoursChange = {
                hours = it
                error = null
            },
            onMinutesChange = {
                minutes = it
                error = null
            },
            onSkillChange = {
                skill = it
                error = null
            },
            onSourceChange = { selectedId, defaultSkill ->
                sourceId = selectedId
                defaultSkill?.let { skill = it }
                error = null
            },
            onNewSource = {
                newSourceName = ""
                newSourceError = false
                error = null
                newSource = true
            },
            onNoteChange = {
                note = it
                error = null
            },
            onSubmit = { submit() },
            onDuplicate = {
                originalId = 0L
            },
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        )
    }

    if (showDatePicker) {
        StudyDatePickerDialog(
            epochDay = date,
            onDismiss = { showDatePicker = false },
            onSelect = {
                date = it
                error = null
                showDatePicker = false
            }
        )
    }
    if (warning) {
        DailyLimitDialog(
            onDismiss = { warning = false },
            onConfirm = {
                warning = false
                submit(true)
            }
        )
    }
    if (newSource) {
        NewSourceDialog(
            name = newSourceName,
            hasError = newSourceError,
            onNameChange = {
                newSourceName = it
                newSourceError = false
            },
            onDismiss = {
                newSource = false
                newSourceError = false
            },
            onConfirm = {
                vm.addSource(
                    name = newSourceName,
                    defaultSkill = skill
                ) { id ->
                    if (id != null) {
                        sourceId = id
                        newSource = false
                    } else {
                        newSourceError = true
                    }
                }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EntryForm(
    date: Long,
    hours: String,
    minutes: String,
    skill: StudySkill,
    sourceId: Long?,
    note: String,
    sources: List<StudySource>,
    validationError: SessionValidation?,
    durationInvalid: Boolean,
    dateInvalid: Boolean,
    isEditing: Boolean,
    saveEnabled: Boolean,
    onShowDatePicker: () -> Unit,
    onHoursChange: (String) -> Unit,
    onMinutesChange: (String) -> Unit,
    onSkillChange: (StudySkill) -> Unit,
    onSourceChange: (Long?, StudySkill?) -> Unit,
    onNewSource: () -> Unit,
    onNoteChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onDuplicate: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedButton(onClick = onShowDatePicker) {
            Text(LocalDate.ofEpochDay(date).toString())
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = hours,
                onValueChange = onHoursChange,
                label = { Text(stringResource(R.string.study_log_hours)) },
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = minutes,
                onValueChange = onMinutesChange,
                label = { Text(stringResource(R.string.study_log_minutes)) },
                modifier = Modifier.weight(1f)
            )
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf(15, 30, 45, 60, 90).forEach { value ->
                AssistChip(
                    onClick = {
                        onHoursChange((value / 60).toString())
                        onMinutesChange((value % 60).toString())
                    },
                    label = { Text("$value") }
                )
            }
        }
        Text(stringResource(R.string.study_log_skill))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            StudySkill.entries.filter { !it.legacy || it == skill }.forEach { value ->
                FilterChip(
                    selected = skill == value,
                    onClick = { onSkillChange(value) },
                    label = { Text(stringResource(value.labelRes())) }
                )
            }
        }
        SourcePicker(
            sourceId = sourceId,
            sources = sources,
            onSourceChange = onSourceChange,
            onNewSource = onNewSource
        )
        OutlinedTextField(
            value = note,
            onValueChange = onNoteChange,
            label = { Text(stringResource(R.string.study_log_note)) },
            modifier = Modifier.fillMaxWidth()
        )
        if (durationInvalid) {
            Text(
                text = stringResource(R.string.study_log_duration_error),
                color = MaterialTheme.colorScheme.error
            )
        }
        if (dateInvalid) {
            Text(
                text = stringResource(R.string.study_log_future_date_error),
                color = MaterialTheme.colorScheme.error
            )
        }
        if (validationError != null && !durationInvalid && !dateInvalid) {
            val message = stringResource(validationError.messageRes())
            Text(
                text = if (validationError == SessionValidation.MissingSource) {
                    "${stringResource(R.string.study_log_source_name)} · $message"
                } else {
                    message
                },
                color = MaterialTheme.colorScheme.error
            )
        }
        Button(
            onClick = onSubmit,
            enabled = saveEnabled && !durationInvalid && !dateInvalid,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.study_log_save))
        }
        if (isEditing) {
            OutlinedButton(
                onClick = onDuplicate,
                enabled = saveEnabled,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.study_log_duplicate))
            }
        }
    }
}

@Composable
private fun SourcePicker(
    sourceId: Long?,
    sources: List<StudySource>,
    onSourceChange: (Long?, StudySkill?) -> Unit,
    onNewSource: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedName = sources.firstOrNull { it.id == sourceId }?.name
        ?: stringResource(R.string.study_log_no_source)

    Box {
        OutlinedButton(onClick = { expanded = true }) {
            Text(selectedName)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.study_log_no_source)) },
                onClick = {
                    onSourceChange(null, null)
                    expanded = false
                }
            )
            activeSourcesForPicker(sources).forEach { source ->
                DropdownMenuItem(
                    text = { Text(source.name) },
                    onClick = {
                        onSourceChange(source.id, source.defaultSkill)
                        expanded = false
                    }
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.study_log_new_source)) },
                onClick = {
                    expanded = false
                    onNewSource()
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StudyDatePickerDialog(
    epochDay: Long,
    onDismiss: () -> Unit,
    onSelect: (Long) -> Unit
) {
    val picker = rememberDatePickerState(initialSelectedDateMillis = epochDay * MILLIS_PER_DAY)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    picker.selectedDateMillis?.let { onSelect(it / MILLIS_PER_DAY) }
                }
            ) {
                Text(stringResource(R.string.study_log_ok))
            }
        }
    ) {
        DatePicker(state = picker)
    }
}

@Composable
private fun DailyLimitDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.study_log_over_day_title)) },
        text = { Text(stringResource(R.string.study_log_over_day_message)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.study_log_save_anyway))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.study_log_cancel))
            }
        }
    )
}

@Composable
private fun NewSourceDialog(
    name: String,
    hasError: Boolean,
    onNameChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.study_log_new_source)) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = onNameChange,
                    label = { Text(stringResource(R.string.study_log_source_name)) },
                    isError = hasError
                )
                if (hasError) {
                    Text(
                        text = stringResource(R.string.study_log_source_duplicate),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.study_log_add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.study_log_cancel))
            }
        }
    )
}

private const val MILLIS_PER_DAY = 86_400_000L
