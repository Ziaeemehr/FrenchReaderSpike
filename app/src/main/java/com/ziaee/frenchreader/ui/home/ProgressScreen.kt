package com.ziaee.frenchreader.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.ziaee.frenchreader.R
import com.ziaee.frenchreader.studylog.StudyLogContent
import com.ziaee.frenchreader.ui.statistics.StatisticsContent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgressScreen(
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (Long) -> Unit,
    onSources: () -> Unit,
    onCharts: () -> Unit,
    initialTab: Int = 0
) {
    var tab by rememberSaveable { mutableStateOf(initialTab.coerceIn(0, 1)) }
    Scaffold(topBar = {
        TopAppBar(title = { Text(stringResource(R.string.progress_title)) }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.accessibility_back)) }
        })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text(stringResource(R.string.progress_overview)) })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text(stringResource(R.string.progress_study_time)) })
            }
            Box(Modifier.weight(1f)) {
                if (tab == 0) StatisticsContent(onOpenStudyLog = { tab = 1 })
                else StudyLogContent(onAdd, onEdit, onSources, onCharts)
            }
        }
    }
}
