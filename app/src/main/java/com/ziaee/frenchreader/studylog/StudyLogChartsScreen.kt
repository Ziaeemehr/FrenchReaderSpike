package com.ziaee.frenchreader.studylog

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ziaee.frenchreader.R
import com.ziaee.frenchreader.data.StudySession
import com.ziaee.frenchreader.data.StudySkill
import com.ziaee.frenchreader.ui.statistics.MultiLineChart
import com.ziaee.frenchreader.ui.statistics.StackedBarChart
import java.text.NumberFormat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudyLogChartsScreen(onBack: () -> Unit, vm: StudyLogViewModel = viewModel()) {
    val state by vm.uiState.collectAsState()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.study_log_charts)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                }
            )
        }
    ) { padding ->
        ChartsContent(
            state = state,
            onPeriodChange = vm::setPeriod,
            modifier = Modifier.padding(padding).padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChartsContent(
    state: StudyLogUiState,
    onPeriodChange: (StudyPeriod) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = studySkillColors()
    val formatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT).withLocale(appLocale())
    val shares = largestRemainderShares(state.totalsBySkill)
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            StudyPeriod.entries.forEachIndexed { index, period ->
                SegmentedButton(
                    selected = state.period == period,
                    onClick = { onPeriodChange(period) },
                    shape = SegmentedButtonDefaults.itemShape(index, StudyPeriod.entries.size)
                ) {
                    Text(stringResource(period.labelRes()))
                }
            }
        }
        if (!state.hasEntries) {
            EmptyStudyLog()
            return@Column
        }
        SummaryTiles(state)
        ChartCard(stringResource(R.string.study_log_time_by_skill)) {
            StackedBarChart(
                valuesByBar = state.buckets.map { bucket ->
                    StudySkill.entries.map { skill ->
                        (bucket.minutesBySkill[skill] ?: 0).toFloat()
                    }
                },
                labels = sparseLabels(state.buckets.map { it.epochDay }, formatter),
                colors = StudySkill.entries.map { colors.getValue(it) }
            )
            SkillLegend(colors)
        }
        ChartCard(stringResource(R.string.study_log_cumulative)) {
            MultiLineChart(
                valuesBySeries = StudySkill.entries.map { skill ->
                    state.cumulative[skill].orEmpty().map { it.minutes.toFloat() / 60f }
                },
                labels = sparseLabels(state.buckets.map { it.epochDay }, formatter),
                colors = StudySkill.entries.map { colors.getValue(it) }
            )
            SkillLegend(colors)
        }
        ChartCard(stringResource(R.string.study_log_activity)) {
            ActivityMap(state, colors)
        }
        ChartCard(stringResource(R.string.study_log_week_comparison)) {
            DeltaRow(stringResource(R.string.study_log_total), state.comparison.totalDeltaMinutes)
            StudySkill.entries.forEach { skill ->
                DeltaRow(skillFullName(skill), state.comparison.deltaBySkill[skill] ?: 0)
            }
        }
        ChartCard(stringResource(R.string.study_log_skill_share)) {
            if (state.totalMinutes == 0) {
                Text(stringResource(R.string.study_log_no_data))
            } else {
                StudySkill.entries.forEach { skill ->
                    LegendRow(colors.getValue(skill), stringResource(skill.labelRes()), "${shares[skill]}%")
                }
            }
        }
        ChartCard(stringResource(R.string.study_log_by_source)) {
            val max = state.sourceTotals.values.maxOrNull() ?: 0
            if (max == 0) Text(stringResource(R.string.study_log_no_data))
            state.sourceTotals.entries.sortedByDescending { it.value }.forEach { (id, minutes) ->
                val name = state.sources.firstOrNull { it.id == id }?.name
                    ?: stringResource(R.string.study_log_no_source)
                Text("$name · ${formatMinutes(minutes)}")
                LinearProgressIndicator(
                    progress = relativeProgress(minutes, max),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

private enum class ActivityPeriod {
    YEAR,
    MONTH
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActivityMap(
    state: StudyLogUiState,
    skillColors: Map<StudySkill, Color>
) {
    val today = remember { LocalDate.now() }
    val firstDate = state.activitySessions.minOfOrNull { it.date }
        ?.let { LocalDate.ofEpochDay(it) }
        ?: today
    var period by remember { mutableStateOf(ActivityPeriod.YEAR) }
    var selectedYear by remember { mutableIntStateOf(today.year) }
    var selectedMonth by remember { mutableStateOf(YearMonth.from(today)) }
    var selectedDay by remember { mutableStateOf<ActivityDay?>(null) }
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        ActivityPeriod.entries.forEachIndexed { index, value ->
            SegmentedButton(
                selected = period == value,
                onClick = {
                    period = value
                    selectedDay = null
                },
                shape = SegmentedButtonDefaults.itemShape(index, ActivityPeriod.entries.size)
            ) {
                Text(
                    stringResource(
                        if (value == ActivityPeriod.YEAR) {
                            R.string.study_log_year
                        } else {
                            R.string.study_log_month
                        }
                    )
                )
            }
        }
    }
    if (period == ActivityPeriod.YEAR) {
        YearActivityMap(
            sessions = state.activitySessions,
            year = selectedYear,
            firstYear = firstDate.year,
            currentYear = today.year,
            firstDayOfWeek = state.firstDayOfWeek,
            today = today,
            onYearChange = {
                selectedYear = it
                selectedDay = null
            },
            onDaySelected = { selectedDay = it }
        )
        val summary = yearSummary(state.activitySessions, selectedYear)
        Text(
            stringResource(
                R.string.study_log_year_summary,
                formatMinutes(summary.totalMinutes),
                selectedYear,
                summary.activeDays,
                summary.longestStreak
            ),
            style = MaterialTheme.typography.bodySmall
        )
    } else {
        MonthActivityMap(
            sessions = state.activitySessions,
            month = selectedMonth,
            firstMonth = YearMonth.from(firstDate),
            currentMonth = YearMonth.from(today),
            firstDayOfWeek = state.firstDayOfWeek,
            today = today,
            onMonthChange = {
                selectedMonth = it
                selectedDay = null
            },
            onDaySelected = { selectedDay = it }
        )
    }
    ActivityLegend()
    selectedDay?.let { day ->
        ActivityDayDetails(day, state.activitySessions, skillColors)
    }
}

@Composable
private fun YearActivityMap(
    sessions: List<StudySession>,
    year: Int,
    firstYear: Int,
    currentYear: Int,
    firstDayOfWeek: DayOfWeek,
    today: LocalDate,
    onYearChange: (Int) -> Unit,
    onDaySelected: (ActivityDay) -> Unit
) {
    val locale = appLocale()
    val grid = remember(sessions, year, firstDayOfWeek, today) {
        yearGrid(sessions, year, firstDayOfWeek, today.toEpochDay())
    }
    val firstWeek = weekStart(LocalDate.of(year, 1, 1).toEpochDay(), firstDayOfWeek)
    val scrollState = rememberScrollState()
    LaunchedEffect(year, grid.size, scrollState.maxValue) {
        if (year == today.year) {
            val todayWeek = grid.indexOfFirst { week ->
                week.any { it?.epochDay == today.toEpochDay() }
            }.coerceAtLeast(0)
            val target = scrollState.maxValue * todayWeek / (grid.size - 1).coerceAtLeast(1)
            scrollState.scrollTo(target)
        }
    }
    ActivitySelector(
        label = NumberFormat.getIntegerInstance(locale).format(year),
        previousEnabled = year > firstYear,
        nextEnabled = year < currentYear,
        onPrevious = { onYearChange(year - 1) },
        onNext = { onYearChange(year + 1) }
    )
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        WeekdayLabels(firstDayOfWeek, locale, includeAll = false, topPadding = 20.dp)
        Row(
            modifier = Modifier.horizontalScroll(scrollState),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            grid.forEachIndexed { weekIndex, week ->
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    val firstOfMonth = (0L..6L)
                        .map { LocalDate.ofEpochDay(firstWeek + weekIndex * 7L + it) }
                        .firstOrNull { it.year == year && it.dayOfMonth == 1 }
                    Box(modifier = Modifier.width(16.dp).height(17.dp)) {
                        firstOfMonth?.let { date ->
                            Text(
                                date.month.getDisplayName(TextStyle.SHORT, locale),
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                modifier = Modifier.requiredWidth(44.dp)
                            )
                        }
                    }
                    week.forEach { day ->
                        ActivityCell(day, 16.dp, onDaySelected)
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthActivityMap(
    sessions: List<StudySession>,
    month: YearMonth,
    firstMonth: YearMonth,
    currentMonth: YearMonth,
    firstDayOfWeek: DayOfWeek,
    today: LocalDate,
    onMonthChange: (YearMonth) -> Unit,
    onDaySelected: (ActivityDay) -> Unit
) {
    val locale = appLocale()
    val grid = remember(sessions, month, firstDayOfWeek, today) {
        monthGrid(sessions, month, firstDayOfWeek, today.toEpochDay())
    }
    val label = month.format(DateTimeFormatter.ofPattern("LLLL yyyy", locale))
    ActivitySelector(
        label = label,
        previousEnabled = month > firstMonth,
        nextEnabled = month < currentMonth,
        onPrevious = { onMonthChange(month.minusMonths(1)) },
        onNext = { onMonthChange(month.plusMonths(1)) }
    )
    Row(modifier = Modifier.fillMaxWidth()) {
        orderedWeekdays(firstDayOfWeek).forEach { day ->
            Text(
                day.getDisplayName(TextStyle.SHORT, locale),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
    }
    grid.forEach { week ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            week.forEach { day ->
                Box(modifier = Modifier.weight(1f)) {
                    ActivityCell(
                        day = day,
                        size = null,
                        onDaySelected = onDaySelected,
                        showDayNumber = true,
                        modifier = Modifier.fillMaxWidth().aspectRatio(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun ActivitySelector(
    label: String,
    previousEnabled: Boolean,
    nextEnabled: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    val rtl = androidx.compose.ui.platform.LocalLayoutDirection.current ==
        androidx.compose.ui.unit.LayoutDirection.Rtl
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onPrevious, enabled = previousEnabled) {
            Text(if (rtl) "›" else "‹", style = MaterialTheme.typography.titleLarge)
        }
        Text(label, style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center)
        IconButton(onClick = onNext, enabled = nextEnabled) {
            Text(if (rtl) "‹" else "›", style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Composable
private fun WeekdayLabels(
    firstDayOfWeek: DayOfWeek,
    locale: Locale,
    includeAll: Boolean,
    topPadding: androidx.compose.ui.unit.Dp
) {
    Column(
        modifier = Modifier.padding(top = topPadding),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        orderedWeekdays(firstDayOfWeek).forEachIndexed { index, day ->
            Box(modifier = Modifier.width(28.dp).height(16.dp), contentAlignment = Alignment.CenterStart) {
                if (includeAll || index % 2 == 0) {
                    Text(
                        day.getDisplayName(TextStyle.SHORT, locale),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

private fun orderedWeekdays(firstDayOfWeek: DayOfWeek): List<DayOfWeek> =
    List(7) { firstDayOfWeek.plus(it.toLong()) }

@Composable
private fun ActivityCell(
    day: ActivityDay?,
    size: androidx.compose.ui.unit.Dp?,
    onDaySelected: (ActivityDay) -> Unit,
    showDayNumber: Boolean = false,
    modifier: Modifier = Modifier
) {
    val colors = activityColors()
    val color = day?.let { colors[it.level] } ?: Color.Transparent
    val contentColor = if (color.luminance() < .45f) Color.White else MaterialTheme.colorScheme.onSurface
    val sizedModifier = if (size == null) modifier else modifier.size(size)
    Box(
        modifier = sizedModifier
            .background(color, RoundedCornerShape(2.dp))
            .then(
                day?.let { selected ->
                    Modifier.clickable { onDaySelected(selected) }
                } ?: Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        if (showDayNumber && day != null) {
            Text(
                NumberFormat.getIntegerInstance(appLocale()).format(
                    LocalDate.ofEpochDay(day.epochDay).dayOfMonth
                ),
                color = contentColor,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

@Composable
private fun activityColors(): List<Color> {
    val scheme = MaterialTheme.colorScheme
    return listOf(
        scheme.surfaceVariant,
        scheme.primary.copy(alpha = .22f).compositeOver(scheme.surface),
        scheme.primary.copy(alpha = .42f).compositeOver(scheme.surface),
        scheme.primary.copy(alpha = .68f).compositeOver(scheme.surface),
        scheme.primary
    )
}

@Composable
private fun ActivityLegend() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(stringResource(R.string.study_log_less), style = MaterialTheme.typography.labelSmall)
        activityColors().forEach { color ->
            Box(
                modifier = Modifier
                    .padding(horizontal = 1.dp)
                    .size(12.dp)
                    .background(color, RoundedCornerShape(2.dp))
            )
        }
        Text(stringResource(R.string.study_log_more), style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun ActivityDayDetails(
    day: ActivityDay,
    sessions: List<StudySession>,
    skillColors: Map<StudySkill, Color>
) {
    val date = LocalDate.ofEpochDay(day.epochDay)
        .format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(appLocale()))
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            if (day.minutes == 0) {
                "$date · ${stringResource(R.string.study_log_no_study)}"
            } else {
                "$date · ${formatMinutes(day.minutes)}"
            },
            style = MaterialTheme.typography.bodySmall
        )
        if (day.minutes > 0) {
            val totals = totalsBySkill(sessions.filter { it.date == day.epochDay })
            StudySkill.entries.filter { totals.getValue(it) > 0 }.forEach { skill ->
                LegendRow(
                    color = skillColors.getValue(skill),
                    label = skillFullName(skill),
                    value = formatMinutes(totals.getValue(skill))
                )
            }
        }
    }
}

@Composable
private fun SummaryTiles(state: StudyLogUiState) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SummaryTile(stringResource(R.string.study_log_total), formatMinutes(state.totalMinutes), Modifier.weight(1f))
        SummaryTile(
            stringResource(R.string.study_log_average),
            formatMinutes(averagePerElapsedDay(state.totalMinutes, state.range)),
            Modifier.weight(1f)
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SummaryTile(
            stringResource(R.string.study_log_active_days),
            "${state.activeDays} / ${state.range.elapsedDays}",
            Modifier.weight(1f)
        )
        SummaryTile(
            stringResource(R.string.study_log_streaks),
            stringResource(R.string.study_log_streak_values, state.streaks.longest, state.streaks.current),
            Modifier.weight(1f)
        )
    }
}

@Composable
private fun SummaryTile(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(value, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun ChartCard(title: String, content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun SkillLegend(colors: Map<StudySkill, Color>) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        StudySkill.entries.forEach { skill ->
            LegendRow(colors.getValue(skill), skillFullName(skill), null)
        }
    }
}

@Composable
private fun skillFullName(skill: StudySkill): String =
    stringResource(R.string.study_log_skill_full, stringResource(skill.labelRes()), skill.code)

@Composable
private fun LegendRow(color: Color, label: String, value: String?) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(10.dp).background(color, CircleShape))
        Text(label, modifier = Modifier.weight(1f))
        value?.let { Text(it) }
    }
}

@Composable
private fun DeltaRow(label: String, delta: Int) {
    val sign = if (delta > 0) "+" else if (delta < 0) "−" else ""
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(label, modifier = Modifier.weight(1f))
        Text(sign + formatMinutes(kotlin.math.abs(delta)))
    }
}

@Composable
private fun EmptyStudyLog() {
    Card(modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.study_log_empty_hint), modifier = Modifier.padding(24.dp))
    }
}

private fun sparseLabels(days: List<Long>, formatter: DateTimeFormatter): List<String> {
    val step = (days.size / 6).coerceAtLeast(1)
    return days.mapIndexed { index, day ->
        if (index % step == 0 || index == days.lastIndex) {
            LocalDate.ofEpochDay(day).format(formatter)
        } else {
            ""
        }
    }
}

@Composable
internal fun studySkillColors(): Map<StudySkill, Color> {
    val dark = MaterialTheme.colorScheme.background.red < .5f
    val light = listOf(
        Color(0xff1565c0),
        Color(0xff2e7d32),
        Color(0xffad1457),
        Color(0xff6a1b9a),
        Color(0xffe65100)
    )
    val darkColors = listOf(
        Color(0xff90caf9),
        Color(0xff81c784),
        Color(0xfff48fb1),
        Color(0xffce93d8),
        Color(0xffffb74d)
    )
    return StudySkill.entries.zip(if (dark) darkColors else light).toMap()
}

@Composable
internal fun appLocale(): Locale {
    val tag = LocalConfiguration.current.locales[0].toLanguageTag()
    return Locale.forLanguageTag(tag)
}

private fun StudyPeriod.labelRes() = when (this) {
    StudyPeriod.WEEK -> R.string.study_log_week
    StudyPeriod.MONTH -> R.string.study_log_month
    StudyPeriod.ALL -> R.string.study_log_all
}
