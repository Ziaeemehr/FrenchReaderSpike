package com.ziaee.frenchreader.studylog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ziaee.frenchreader.R
import com.ziaee.frenchreader.data.StudySkill
import com.ziaee.frenchreader.ui.statistics.StackedBarChart
import java.time.DayOfWeek
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudyLogChartsScreen(
    onBack: () -> Unit,
    vm: StudyLogViewModel = viewModel()
) {
    val state by vm.uiState.collectAsState()
    val colors = listOf(
        Color(0xff3366cc),
        Color(0xffdc3912),
        Color(0xffff9900),
        Color(0xff109618),
        Color(0xff990099)
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.study_log_charts)) },
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
        ChartsContent(
            state = state,
            colors = colors,
            onPeriodChange = vm::setPeriod,
            onFirstDayChange = vm::setFirstDayOfWeek,
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChartsContent(
    state: StudyLogUiState,
    colors: List<Color>,
    onPeriodChange: (StudyPeriod) -> Unit,
    onFirstDayChange: (DayOfWeek) -> Unit,
    modifier: Modifier = Modifier
) {
    val shares = largestRemainderShares(state.totalsBySkill)
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SingleChoiceSegmentedButtonRow {
            StudyPeriod.entries.forEachIndexed { index, period ->
                SegmentedButton(
                    selected = state.period == period,
                    onClick = { onPeriodChange(period) },
                    shape = SegmentedButtonDefaults.itemShape(
                        index = index,
                        count = StudyPeriod.entries.size
                    )
                ) {
                    Text(
                        stringResource(
                            if (period == StudyPeriod.WEEK) {
                                R.string.study_log_week
                            } else {
                                R.string.study_log_month
                            }
                        )
                    )
                }
            }
        }
        StackedBarChart(
            valuesByBar = state.buckets.map { bucket ->
                StudySkill.entries.map { skill ->
                    (bucket.minutesBySkill[skill] ?: 0).toFloat()
                }
            },
            labels = state.buckets.map {
                LocalDate.ofEpochDay(it.epochDay).dayOfMonth.toString()
            },
            colors = colors
        )
        Text(
            text = stringResource(R.string.study_log_skill_share),
            style = MaterialTheme.typography.titleMedium
        )
        if (state.totalMinutes == 0) {
            Text(stringResource(R.string.study_log_no_data))
        } else {
            StudySkill.entries.forEach { skill ->
                Text("${skill.code}: ${shares[skill]}%")
            }
        }
        Text(
            text = stringResource(R.string.study_log_by_source),
            style = MaterialTheme.typography.titleMedium
        )
        state.sourceTotals.forEach { (id, minutes) ->
            val sourceName = state.sources.firstOrNull { it.id == id }?.name
                ?: stringResource(R.string.study_log_no_source)
            Text("$sourceName: ${formatMinutes(minutes)}")
        }
        Text(stringResource(R.string.study_log_first_day))
        Row {
            listOf(DayOfWeek.SATURDAY, DayOfWeek.MONDAY).forEach { day ->
                FilterChip(
                    selected = state.firstDayOfWeek == day,
                    onClick = { onFirstDayChange(day) },
                    label = {
                        Text(day.name.lowercase().replaceFirstChar(Char::uppercase))
                    }
                )
            }
        }
    }
}
