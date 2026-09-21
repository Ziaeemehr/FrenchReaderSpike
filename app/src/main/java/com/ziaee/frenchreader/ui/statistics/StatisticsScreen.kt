package com.ziaee.frenchreader.ui.statistics

import android.app.Application
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ziaee.frenchreader.R
import com.ziaee.frenchreader.data.AppDatabase
import com.ziaee.frenchreader.data.TextBodyStore
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

class StatisticsViewModel(app: Application) : AndroidViewModel(app) {
    private val db = AppDatabase.get(app)
    private val bodyStore = TextBodyStore(app)

    var uiState by mutableStateOf(StatisticsUiState())
        private set
    var loading by mutableStateOf(true)
        private set

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            val today = LocalDate.now()
            val texts = db.textDao().getAllOnce()
            val bodyByTextId = texts.associate { it.id to bodyStore.read(it) }
            val vocabEntries = db.vocabDao().getAllOnce()

            val reviewedToday = db.reviewLogDao().countSince(today.startOfDayMs())
            val reviewedThisWeek = db.reviewLogDao().countSince(today.minusDays(6).startOfDayMs())
            val knewCount = db.reviewLogDao().countKnew()
            val totalReviewCount = db.reviewLogDao().countTotal()

            val days = last7Days(today)
            val activityRows = db.activityLogDao().getForDates(days.map { it.toString() })
                .associateBy { it.date }
            val weeklyListening = days.map { day ->
                DailyListening(day, activityRows[day.toString()]?.listeningMs ?: 0L)
            }

            val monthDays = lastDays(today, 30)
            val monthRows = db.activityLogDao().getForDates(monthDays.map { it.toString() })
                .associateBy { it.date }
            val monthlyListening = monthDays.map { day ->
                DailyListening(day, monthRows[day.toString()]?.listeningMs ?: 0L)
            }
            val reviewLogs = db.reviewLogDao().getAll()

            val activeDates = loadActiveDates(
                reviewLogDates = { db.reviewLogDao().distinctActiveDates() },
                activityLogDates = { db.activityLogDao().activeDates() }
            )

            uiState = composeStatisticsState(
                texts = texts,
                bodyByTextId = bodyByTextId,
                vocabEntries = vocabEntries,
                reviewedToday = reviewedToday,
                reviewedThisWeek = reviewedThisWeek,
                knewCount = knewCount,
                totalReviewCount = totalReviewCount,
                weeklyListening = weeklyListening,
                activeDates = activeDates,
                today = today,
                reviewLogs = reviewLogs,
                monthlyListening = monthlyListening
            )
            loading = false
        }
    }
}

private fun LocalDate.startOfDayMs(): Long =
    atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen(onBack: () -> Unit) {
    val vm: StatisticsViewModel = viewModel()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.statistics_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.accessibility_back))
                    }
                }
            )
        }
    ) { padding ->
        if (vm.loading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        val state = vm.uiState
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            StreakSection(state.streakDays)
            WeeklyListeningSection(state.weeklyListening)
            VocabReviewSection(state)
            NewChartSections(state)
            TotalsSection(state)
        }
    }
}

@Composable
private fun StreakSection(streakDays: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.LocalFireDepartment, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            stringResource(R.string.statistics_streak_days, streakDays),
            style = MaterialTheme.typography.titleLarge
        )
    }
}

@Composable
private fun WeeklyListeningSection(days: List<DailyListening>) {
    Text(
        stringResource(R.string.statistics_weekly_chart_title),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = 20.dp)
    )
    val maxMs = (days.maxOfOrNull { it.listeningMs } ?: 0L).coerceAtLeast(1L)
    val barColor = MaterialTheme.colorScheme.primary
    val locale = Locale.getDefault()
    Row(
        modifier = Modifier.fillMaxWidth().height(120.dp).padding(top = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        days.forEach { day ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f).fillMaxHeight()
            ) {
                Canvas(modifier = Modifier.weight(1f).fillMaxWidth(0.5f)) {
                    val heightFraction = (day.listeningMs.toFloat() / maxMs.toFloat()).coerceIn(0f, 1f)
                    val barHeight = size.height * heightFraction
                    drawRect(
                        color = barColor,
                        topLeft = Offset(0f, size.height - barHeight),
                        size = Size(size.width, barHeight)
                    )
                }
                Text(day.date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun VocabReviewSection(state: StatisticsUiState) {
    Text(
        stringResource(R.string.statistics_vocab_section_title),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = 20.dp)
    )
    Text(stringResource(R.string.statistics_reviewed_today, state.reviewedToday))
    Text(stringResource(R.string.statistics_reviewed_week, state.reviewedThisWeek))
    Text(stringResource(R.string.statistics_accuracy_percent, state.accuracyPercent))
    LeitnerChart(state.leitnerBoxCounts)
}

@Composable
private fun LeitnerChart(counts: Map<Int, Int>) {
    Text(
        stringResource(R.string.statistics_leitner_chart_title),
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(top = 12.dp)
    )
    val maxCount = (counts.values.maxOrNull() ?: 0).coerceAtLeast(1)
    val barColor = MaterialTheme.colorScheme.secondary
    Row(
        modifier = Modifier.fillMaxWidth().height(100.dp).padding(top = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        (1..LEITNER_BOX_COUNT).forEach { box ->
            val count = counts[box] ?: 0
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f).fillMaxHeight()
            ) {
                Canvas(modifier = Modifier.weight(1f).fillMaxWidth(0.5f)) {
                    val heightFraction = count.toFloat() / maxCount.toFloat()
                    val barHeight = size.height * heightFraction
                    drawRect(
                        color = barColor,
                        topLeft = Offset(0f, size.height - barHeight),
                        size = Size(size.width, barHeight)
                    )
                }
                Text(box.toString(), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun TotalsSection(state: StatisticsUiState) {
    Text(
        stringResource(R.string.statistics_totals_title),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = 20.dp)
    )
    Text(stringResource(R.string.statistics_texts_saved, state.textsSaved))
    Text(stringResource(R.string.statistics_texts_completed, state.textsCompleted))
    Text(stringResource(R.string.statistics_words_saved, state.wordsSaved))
}

@Composable
private fun ChartTitle(res: Int) {
    Text(
        stringResource(res),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = 20.dp)
    )
}

@Composable
private fun NewChartSections(state: StatisticsUiState) {
    val primary = MaterialTheme.colorScheme.primary
    val dm = java.time.format.DateTimeFormatter.ofPattern("d/M")
    val noData = stringResource(R.string.statistics_no_data)

    ChartTitle(R.string.statistics_heatmap_title)
    if (state.reviewHeatmap.all { it.count == 0 }) Text(noData) else HeatmapGrid(state.reviewHeatmap)

    ChartTitle(R.string.statistics_forecast_title)
    if (state.dueForecast.sum() == 0) Text(noData) else BarChart(
        state.dueForecast.map { it.toFloat() },
        state.dueForecast.indices.map { if (it % 5 == 0) "+$it" else "" },
        primary
    )

    ChartTitle(R.string.statistics_accuracy_trend_title)
    if (state.accuracyTrend.all { it.answers == 0 }) Text(noData) else LineChart(
        state.accuracyTrend.map { it.percent.toFloat() },
        state.accuracyTrend.map { it.weekStart.format(dm) },
        primary
    )

    ChartTitle(R.string.statistics_words_added_title)
    if (state.wordsAddedPerWeek.all { it.count == 0 }) Text(noData) else BarChart(
        state.wordsAddedPerWeek.map { it.count.toFloat() },
        state.wordsAddedPerWeek.map { it.weekStart.format(dm) },
        primary
    )

    ChartTitle(R.string.statistics_retention_title)
    if (state.retentionByBox.all { it.answers == 0 }) Text(noData) else BarChart(
        state.retentionByBox.map { it.percent.toFloat() },
        state.retentionByBox.indices.map { (it + 1).toString() },
        MaterialTheme.colorScheme.secondary
    )

    ChartTitle(R.string.statistics_listening_month_title)
    if (state.monthlyListening.all { it.listeningMs == 0L }) Text(noData) else BarChart(
        state.monthlyListening.map { it.listeningMs / 60_000f },
        state.monthlyListening.mapIndexed { i, d -> if (i % 5 == 0) d.date.dayOfMonth.toString() else "" },
        primary
    )
}
