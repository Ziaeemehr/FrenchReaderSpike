package com.ziaee.frenchreader.ui

import android.app.Activity
import android.app.TimePickerDialog
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import com.ziaee.frenchreader.R
import com.ziaee.frenchreader.backup.AuthorizationOutcome
import com.ziaee.frenchreader.backup.BackupPrefs
import com.ziaee.frenchreader.backup.DriveBackupClient
import com.ziaee.frenchreader.backup.GoogleAuthManager
import com.ziaee.frenchreader.backup.LocalBackup
import com.ziaee.frenchreader.backup.formatLastBackupLabel
import com.ziaee.frenchreader.data.AppLanguage
import com.ziaee.frenchreader.data.AppearancePrefs
import com.ziaee.frenchreader.data.LocalePrefs
import com.ziaee.frenchreader.data.NewsPrefs
import com.ziaee.frenchreader.data.TtsCachePrefs
import com.ziaee.frenchreader.data.VocabPrefs
import com.ziaee.frenchreader.data.VocabReviewReminder
import com.ziaee.frenchreader.data.XttsPrefs
import com.ziaee.frenchreader.data.applyAppLanguage
import com.ziaee.frenchreader.tts.XttsClient
import com.ziaee.frenchreader.tts.TtsChunkRepository
import com.ziaee.frenchreader.news.NEWS_SOURCES
import com.ziaee.frenchreader.news.NewsCategory
import com.ziaee.frenchreader.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val appLanguage = LocalePrefs.get(context)
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.accessibility_back))
                    }
                }
            )
        }
    ) { padding ->
        val tabTitles = listOf(
            R.string.settings_section_appearance,
            R.string.settings_section_vocabulary,
            R.string.settings_section_news,
            R.string.settings_section_xtts,
            R.string.settings_section_tts_cache,
            R.string.settings_section_backup
        )
        var selectedTab by rememberSaveable { mutableIntStateOf(0) }

        Column(Modifier.fillMaxWidth().padding(padding)) {
            SettingsTabRow(tabTitles = tabTitles, selectedTab = selectedTab, onSelect = { selectedTab = it })
            Column(
                Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                when (selectedTab) {
                    0 -> {
                        CompactSettingLabel(R.string.language_title)
                        CompactChoiceRow(AppLanguage.entries, appLanguage, { stringResource(it.labelResource()) }) { language ->
                            LocalePrefs.set(context, language)
                            applyAppLanguage(language)
                        }
                        CompactSettingLabel(R.string.theme_title)
                        CompactChoiceRow(ThemeMode.entries, AppearanceState.themeMode, { stringResource(it.labelResource()) }) { mode ->
                            AppearanceState.themeMode = mode
                            AppearancePrefs.setThemeMode(context, mode)
                        }
                        CompactSettingLabel(R.string.reading_background_title)
                        ColorGrid(
                            entries = ReadingBackground.entries,
                            selected = AppearanceState.readingBackground,
                            color = ::backgroundSwatch,
                            label = { stringResource(it.labelResource()) }
                        ) { background ->
                            AppearanceState.readingBackground = background
                            AppearancePrefs.setReadingBackground(context, background)
                        }
                        CompactSettingLabel(R.string.sync_highlight_color)
                        ColorGrid(
                            entries = HighlightColor.entries,
                            selected = AppearanceState.highlightColor,
                            color = { highlightSwatch(it, AppearanceState.readingBackground in listOf(ReadingBackground.DARK, ReadingBackground.BLACK)) },
                            label = { stringResource(it.labelResource()) }
                        ) { color ->
                            AppearanceState.highlightColor = color
                            AppearancePrefs.setHighlightColor(context, color)
                        }
                        CompactSettingLabel(R.string.reading_font_scale_title)
                        CompactChoiceRow(FontScale.entries, AppearanceState.fontScale, { stringResource(it.labelResource()) }) { scale ->
                            AppearanceState.fontScale = scale
                            AppearancePrefs.setFontScale(context, scale)
                        }
                    }
                    1 -> {
                        SettingsSwitchRow(
                            label = stringResource(R.string.highlight_saved_words),
                            checked = AppearanceState.highlightSavedWords
                        ) { enabled ->
                            AppearanceState.highlightSavedWords = enabled
                            AppearancePrefs.setHighlightSavedWords(context, enabled)
                        }
                        VocabularyReviewSettings(context)
                    }
                    2 -> NewsSettings(context)
                    3 -> LocalXttsSettings(context, scope, snackbarHostState)
                    4 -> TtsCacheSettings(context, scope, snackbarHostState)
                    5 -> {
                        LocalBackupSection(context, scope, snackbarHostState)
                        CloudBackupSection(context, scope, snackbarHostState)
                    }
                }
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}

@Composable
private fun SettingsTabRow(tabTitles: List<Int>, selectedTab: Int, onSelect: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp)) {
        tabTitles.chunked(3).forEachIndexed { rowIndex, row ->
            if (rowIndex > 0) Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                row.forEachIndexed { columnIndex, resource ->
                    val index = rowIndex * 3 + columnIndex
                    val selected = selectedTab == index
                    val background by animateColorAsState(
                        if (selected) MaterialTheme.colorScheme.tertiaryContainer else Color.Transparent,
                        label = "settingsTabBackground"
                    )
                    val contentColor by animateColorAsState(
                        if (selected) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        label = "settingsTabContent"
                    )
                    Box(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(50))
                            .background(background)
                            .border(
                                width = if (selected) 0.dp else 1.dp,
                                color = MaterialTheme.colorScheme.outlineVariant,
                                shape = RoundedCornerShape(50)
                            )
                            .clickable { onSelect(index) }
                            .padding(horizontal = 4.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            stringResource(resource),
                            style = MaterialTheme.typography.labelSmall,
                            color = contentColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun NewsSettings(context: Context) {
    var enabledIds by remember { mutableStateOf(NewsPrefs.getEnabledSourceIds(context)) }
    var keywords by remember { mutableStateOf(NewsPrefs.getKeywords(context)) }
    var matchingFirst by remember { mutableStateOf(NewsPrefs.getMatchingFirst(context)) }

    CompactSettingLabel(R.string.news_sources_title)
    NEWS_SOURCES.groupBy { it.category }.forEach { (category, sources) ->
        Text(
            stringResource(category.labelResource()),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.padding(top = 8.dp)
        )
        sources.forEach { source ->
            Row(
                Modifier.fillMaxWidth().heightIn(min = 40.dp).clickable {
                    enabledIds = if (source.id in enabledIds) enabledIds - source.id else enabledIds + source.id
                    NewsPrefs.setEnabledSourceIds(context, enabledIds)
                },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = source.id in enabledIds,
                    onCheckedChange = { checked ->
                        enabledIds = if (checked) enabledIds + source.id else enabledIds - source.id
                        NewsPrefs.setEnabledSourceIds(context, enabledIds)
                    }
                )
                Text(source.label, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
    OutlinedTextField(
        value = keywords,
        onValueChange = { keywords = it; NewsPrefs.setKeywords(context, it) },
        label = { Text(stringResource(R.string.news_keywords_label)) },
        supportingText = { Text(stringResource(R.string.news_keywords_hint)) },
        minLines = 2,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
    )
    SettingsSwitchRow(stringResource(R.string.news_only_matching), !matchingFirst) { onlyMatching ->
        matchingFirst = !onlyMatching
        NewsPrefs.setMatchingFirst(context, matchingFirst)
    }
}

private fun NewsCategory.labelResource(): Int = when (this) {
    NewsCategory.GENERAL -> R.string.news_category_general
    NewsCategory.INTERNATIONAL_EUROPE -> R.string.news_category_international_europe
    NewsCategory.POLITICS -> R.string.news_category_politics
    NewsCategory.ECONOMY -> R.string.news_category_economy
    NewsCategory.TECHNOLOGY -> R.string.news_category_technology
    NewsCategory.SCIENCE -> R.string.news_category_science
    NewsCategory.HEALTH -> R.string.news_category_health
    NewsCategory.PSYCHOLOGY -> R.string.news_category_psychology
    NewsCategory.CULTURE -> R.string.news_category_culture
    NewsCategory.SPORT -> R.string.news_category_sport
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LocalXttsSettings(context: Context, scope: kotlinx.coroutines.CoroutineScope, snackbarHostState: SnackbarHostState) {
    var serverUrl by remember { mutableStateOf(XttsPrefs.getServerUrl(context)) }
    var speaker by remember { mutableStateOf(XttsPrefs.getSpeaker(context)) }
    var speakers by remember { mutableStateOf(emptyList<String>()) }
    var speakersExpanded by remember { mutableStateOf(false) }
    var token by remember { mutableStateOf(XttsPrefs.getToken(context)) }
    val connectionOk = stringResource(R.string.xtts_connection_ok)
    val connectionFailed = stringResource(R.string.xtts_connection_failed)

    fun refreshSpeakers() {
        scope.launch {
            speakers = withContext(Dispatchers.IO) {
                try {
                    XttsClient.listSpeakers(context)
                } catch (_: Exception) {
                    emptyList()
                }
            }
        }
    }

    LaunchedEffect(serverUrl) {
        delay(600)
        speakers = withContext(Dispatchers.IO) {
            try {
                XttsClient.listSpeakers(context)
            } catch (_: Exception) {
                emptyList()
            }
        }
    }

    OutlinedTextField(
        value = serverUrl,
        onValueChange = { serverUrl = it; XttsPrefs.setServerUrl(context, it) },
        label = { Text(stringResource(R.string.xtts_server_url)) },
        placeholder = { Text("http://192.168.1.10:8020") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ExposedDropdownMenuBox(
            expanded = speakersExpanded,
            onExpandedChange = { speakersExpanded = it },
            modifier = Modifier.weight(1f)
        ) {
            OutlinedTextField(
                value = speaker,
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.xtts_speaker)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = speakersExpanded) },
                singleLine = true,
                modifier = Modifier.menuAnchor().fillMaxWidth()
            )
            ExposedDropdownMenu(
                expanded = speakersExpanded,
                onDismissRequest = { speakersExpanded = false }
            ) {
                if (speakers.isEmpty()) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.xtts_speakers_unavailable)) },
                        onClick = {},
                        enabled = false
                    )
                } else {
                    speakers.forEach { availableSpeaker ->
                        DropdownMenuItem(
                            text = { Text(availableSpeaker) },
                            onClick = {
                                speaker = availableSpeaker
                                XttsPrefs.setSpeaker(context, availableSpeaker)
                                speakersExpanded = false
                            }
                        )
                    }
                }
            }
        }
        TextButton(onClick = { refreshSpeakers() }) {
            Text(stringResource(R.string.xtts_refresh_speakers))
        }
    }
    OutlinedTextField(
        value = token,
        onValueChange = { token = it; XttsPrefs.setToken(context, it) },
        label = { Text(stringResource(R.string.xtts_token)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
    )
    OutlinedButton(
        onClick = {
            scope.launch {
                val ok = withContext(Dispatchers.IO) { XttsClient.ping(context) }
                snackbarHostState.showSnackbar(if (ok) connectionOk else connectionFailed)
            }
        },
        modifier = Modifier.padding(top = 8.dp)
    ) { Text(stringResource(R.string.xtts_test_connection)) }
}

@Composable
private fun TtsCacheSettings(
    context: Context,
    scope: kotlinx.coroutines.CoroutineScope,
    snackbarHostState: SnackbarHostState
) {
    val repository = remember(context) { TtsChunkRepository(context) }
    var cacheSizeBytes by remember { mutableLongStateOf(0L) }
    var maxSizeMb by remember { mutableIntStateOf(TtsCachePrefs.getMaxSizeMb(context)) }
    var maxAgeDays by remember { mutableIntStateOf(TtsCachePrefs.getMaxAgeDays(context)) }
    var showClearConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(repository) {
        cacheSizeBytes = withContext(Dispatchers.IO) { repository.cacheSizeBytes() }
    }

    Text(
        stringResource(R.string.tts_cache_current_size, cacheSizeBytes / (1024.0 * 1024.0)),
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(vertical = 8.dp)
    )
    StepperSetting(stringResource(R.string.tts_cache_max_size), maxSizeMb, 50, 5000) {
        maxSizeMb = it
        TtsCachePrefs.setMaxSizeMb(context, it)
    }
    StepperSetting(stringResource(R.string.tts_cache_max_age), maxAgeDays, 1, 365) {
        maxAgeDays = it
        TtsCachePrefs.setMaxAgeDays(context, it)
    }
    Text(
        stringResource(R.string.tts_cache_pinned_note),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 8.dp)
    )
    OutlinedButton(onClick = { showClearConfirm = true }) {
        Text(stringResource(R.string.tts_cache_clear))
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text(stringResource(R.string.tts_cache_clear_confirm_title)) },
            text = { Text(stringResource(R.string.tts_cache_clear_confirm_message)) },
            confirmButton = {
                OutlinedButton(onClick = {
                    showClearConfirm = false
                    scope.launch {
                        cacheSizeBytes = withContext(Dispatchers.IO) {
                            repository.clearCache()
                            repository.cacheSizeBytes()
                        }
                        snackbarHostState.showSnackbar(context.getString(R.string.tts_cache_clear_done))
                    }
                }) { Text(stringResource(R.string.tts_cache_clear)) }
            },
            dismissButton = {
                OutlinedButton(onClick = { showClearConfirm = false }) {
                    Text(stringResource(R.string.accessibility_back))
                }
            }
        )
    }
}

@Composable
private fun VocabularyReviewSettings(context: Context) {
    var reminder by remember { mutableStateOf(VocabPrefs.getReminderEnabled(context)) }
    var hour by remember { mutableIntStateOf(VocabPrefs.getReminderHour(context)) }
    var minute by remember { mutableIntStateOf(VocabPrefs.getReminderMinute(context)) }
    var maxNew by remember { mutableIntStateOf(VocabPrefs.getMaxNewCards(context)) }
    var dailyGoal by remember { mutableIntStateOf(VocabPrefs.getDailyGoal(context)) }
    var autoplay by remember { mutableStateOf(VocabPrefs.getAudioAutoplay(context)) }
    var meaningLanguage by remember { mutableStateOf(VocabPrefs.getMeaningLanguage(context)) }
    var intervals by remember { mutableStateOf(VocabPrefs.getIntervals(context)) }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    SettingsSwitchRow(stringResource(R.string.review_reminder_toggle), reminder) {
        reminder = it
        VocabPrefs.setReminderEnabled(context, it)
        if (it) {
            if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            VocabReviewReminder.schedule(context)
        } else VocabReviewReminder.cancel(context)
    }
    if (reminder) {
        TextButton(onClick = {
            TimePickerDialog(context, { _, h, m ->
                hour = h; minute = m
                VocabPrefs.setReminderTime(context, h, m)
                VocabReviewReminder.schedule(context)
            }, hour, minute, true).show()
        }) { Text(stringResource(R.string.review_reminder_time, hour, minute)) }
    }
    StepperSetting(stringResource(R.string.review_max_new), maxNew, 0, 100) {
        maxNew = it; VocabPrefs.setMaxNewCards(context, it)
    }
    StepperSetting(stringResource(R.string.review_daily_goal), dailyGoal, 1, 200) {
        dailyGoal = it; VocabPrefs.setDailyGoal(context, it)
    }
    SettingsSwitchRow(stringResource(R.string.review_audio_autoplay), autoplay) {
        autoplay = it; VocabPrefs.setAudioAutoplay(context, it)
    }
    CompactSettingLabel(R.string.review_meaning_language)
    CompactChoiceRow(
        VocabPrefs.MeaningLanguage.entries,
        meaningLanguage,
        { stringResource(if (it == VocabPrefs.MeaningLanguage.PERSIAN) R.string.language_persian else R.string.language_english) }
    ) { meaningLanguage = it; VocabPrefs.setMeaningLanguage(context, it) }
    CompactSettingLabel(R.string.review_intervals)
    intervals.forEachIndexed { index, days ->
        StepperSetting(stringResource(R.string.review_box_interval, index + 1), days.toInt(), 1, 365) { value ->
            intervals = intervals.toMutableList().also { it[index] = value.toLong() }
            VocabPrefs.setIntervals(context, intervals)
        }
    }
    TextButton(onClick = {
        VocabPrefs.resetIntervals(context)
        intervals = VocabPrefs.getIntervals(context)
    }) { Text(stringResource(R.string.review_intervals_reset)) }
}

@Composable
private fun StepperSetting(label: String, value: Int, minimum: Int, maximum: Int, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        IconButton(onClick = { onChange((value - 1).coerceAtLeast(minimum)) }, enabled = value > minimum) {
            Text("−", style = MaterialTheme.typography.titleLarge)
        }
        Text(value.toString(), style = MaterialTheme.typography.bodyMedium)
        IconButton(onClick = { onChange((value + 1).coerceAtMost(maximum)) }, enabled = value < maximum) {
            Text("+", style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Composable
private fun SettingsSectionHeader(resource: Int) {
    Text(
        stringResource(resource),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp, bottom = 6.dp)
    )
}

@Composable
private fun CompactSettingLabel(resource: Int) {
    Text(stringResource(resource), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp, bottom = 3.dp))
}

@Composable
private fun <T> CompactChoiceRow(
    entries: List<T>, selected: T, label: @Composable (T) -> String, onSelect: (T) -> Unit
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        entries.forEach { entry ->
            FilterChip(
                selected = entry == selected,
                onClick = { onSelect(entry) },
                label = { Text(label(entry), style = MaterialTheme.typography.labelSmall) }
            )
        }
    }
}

@Composable
private fun <T> ColorGrid(
    entries: List<T>, selected: T, color: (T) -> Color,
    label: @Composable (T) -> String, onSelect: (T) -> Unit
) {
    entries.chunked(6).forEach { rowEntries ->
        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            rowEntries.forEach { entry ->
                val description = label(entry)
                Box(
                    Modifier.size(34.dp).clip(CircleShape).background(color(entry))
                        .then(if (entry == selected) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, CircleShape) else Modifier.border(1.dp, MaterialTheme.colorScheme.outline, CircleShape))
                        .clickable { onSelect(entry) }
                        .semantics { contentDescription = description; role = Role.RadioButton }
                )
            }
        }
    }
}

@Composable
private fun SettingsSwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

private fun AppLanguage.labelResource(): Int = when (this) {
    AppLanguage.SYSTEM -> R.string.language_system
    AppLanguage.FA -> R.string.language_persian
    AppLanguage.FR -> R.string.language_french
    AppLanguage.EN -> R.string.language_english
}
private fun ThemeMode.labelResource(): Int = when (this) {
    ThemeMode.SYSTEM -> R.string.theme_system
    ThemeMode.LIGHT -> R.string.theme_light
    ThemeMode.DARK -> R.string.theme_dark
}
private fun FontScale.labelResource(): Int = when (this) {
    FontScale.SMALL -> R.string.font_scale_small
    FontScale.MEDIUM -> R.string.font_scale_medium
    FontScale.LARGE -> R.string.font_scale_large
    FontScale.XLARGE -> R.string.font_scale_xlarge
}
private fun ReadingBackground.labelResource(): Int = when (this) {
    ReadingBackground.WHITE -> R.string.reading_background_white
    ReadingBackground.SEPIA -> R.string.reading_background_sepia
    ReadingBackground.PAPER -> R.string.reading_background_paper
    ReadingBackground.SAND -> R.string.reading_background_sand
    ReadingBackground.GRAY -> R.string.reading_background_gray
    ReadingBackground.MINT -> R.string.reading_background_mint
    ReadingBackground.SAGE -> R.string.reading_background_sage
    ReadingBackground.BLUE_TINT -> R.string.reading_background_blue
    ReadingBackground.ROSE -> R.string.reading_background_rose
    ReadingBackground.DARK -> R.string.reading_background_dark
    ReadingBackground.BLACK -> R.string.reading_background_black
}
private fun HighlightColor.labelResource(): Int = when (this) {
    HighlightColor.YELLOW -> R.string.highlight_yellow
    HighlightColor.GREEN -> R.string.highlight_green
    HighlightColor.BLUE -> R.string.highlight_blue
    HighlightColor.PINK -> R.string.highlight_pink
    HighlightColor.ORANGE -> R.string.highlight_orange
    HighlightColor.PURPLE -> R.string.highlight_purple
    HighlightColor.TEAL -> R.string.highlight_teal
    HighlightColor.RED -> R.string.highlight_red
}

@Composable
private fun LocalBackupSection(
    context: Context,
    scope: kotlinx.coroutines.CoroutineScope,
    snackbarHostState: SnackbarHostState
) {
    var restoreUri by remember { mutableStateOf<Uri?>(null) }

    fun showMessage(text: String) {
        scope.launch { snackbarHostState.showSnackbar(text) }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    LocalBackup.exportTo(context, uri)
                    BackupPrefs.setLastBackupAtMs(context, System.currentTimeMillis())
                    showMessage(context.getString(R.string.local_backup_save_success))
                } catch (e: Exception) {
                    showMessage(context.getString(R.string.local_backup_save_failure))
                }
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        restoreUri = uri
    }

    Text(
        stringResource(R.string.local_backup_section_title),
        style = MaterialTheme.typography.labelMedium,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
    )
    OutlinedButton(onClick = {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
        exportLauncher.launch("french_reader_backup_$timestamp.zip")
    }) {
        Text(stringResource(R.string.local_backup_save))
    }
    OutlinedButton(onClick = { importLauncher.launch(arrayOf("*/*")) }) {
        Text(stringResource(R.string.local_backup_restore))
    }

    if (restoreUri != null) {
        AlertDialog(
            onDismissRequest = { restoreUri = null },
            title = { Text(stringResource(R.string.local_backup_restore_confirm_title)) },
            text = { Text(stringResource(R.string.local_backup_restore_confirm_message)) },
            confirmButton = {
                OutlinedButton(onClick = {
                    val uri = restoreUri ?: return@OutlinedButton
                    restoreUri = null
                    scope.launch {
                        try {
                            if (LocalBackup.importFrom(context, uri)) {
                                restartApp(context)
                            } else {
                                showMessage(context.getString(R.string.local_backup_invalid_file))
                            }
                        } catch (e: Exception) {
                            showMessage(context.getString(R.string.local_backup_restore_failure))
                        }
                    }
                }) { Text(stringResource(R.string.local_backup_restore)) }
            },
            dismissButton = {
                OutlinedButton(onClick = { restoreUri = null }) {
                    Text(stringResource(R.string.accessibility_back))
                }
            }
        )
    }
}

@Composable
private fun CloudBackupSection(
    context: Context,
    scope: kotlinx.coroutines.CoroutineScope,
    snackbarHostState: SnackbarHostState
) {
    val authManager = remember { GoogleAuthManager(context) }
    var signedInEmail by remember { mutableStateOf(BackupPrefs.getSignedInEmail(context)) }
    var lastBackupAtMs by remember { mutableStateOf(BackupPrefs.getLastBackupAtMs(context)) }
    var showRestoreConfirm by remember { mutableStateOf(false) }
    var pendingAccessTokenAction by remember { mutableStateOf<((String) -> Unit)?>(null) }

    fun showMessage(text: String) {
        scope.launch { snackbarHostState.showSnackbar(text) }
    }

    val resolutionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        val data = result.data
        if (result.resultCode == Activity.RESULT_OK && data != null) {
            val token = authManager.extractAccessTokenFromResolutionResult(data)
            pendingAccessTokenAction?.invoke(token)
        } else {
            showMessage(context.getString(R.string.backup_failure))
        }
        pendingAccessTokenAction = null
    }

    fun withDriveAccessToken(onToken: (String) -> Unit) {
        scope.launch {
            when (val outcome = authManager.requestDriveAuthorization()) {
                is AuthorizationOutcome.Authorized -> onToken(outcome.accessToken)
                is AuthorizationOutcome.NeedsResolution -> {
                    pendingAccessTokenAction = onToken
                    try {
                        resolutionLauncher.launch(IntentSenderRequest.Builder(outcome.pendingIntent).build())
                    } catch (e: IntentSender.SendIntentException) {
                        showMessage(context.getString(R.string.backup_failure))
                    }
                }
            }
        }
    }

    Text(
        stringResource(R.string.backup_section_title),
        style = MaterialTheme.typography.labelMedium,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
    )

    if (signedInEmail == null) {
        OutlinedButton(onClick = {
            scope.launch {
                try {
                    val account = authManager.signIn()
                    BackupPrefs.setSignedInEmail(context, account.email)
                    signedInEmail = account.email
                } catch (e: Exception) {
                    showMessage(context.getString(R.string.backup_failure))
                }
            }
        }) {
            Text(stringResource(R.string.backup_sign_in))
        }
    } else {
        Text(signedInEmail!!)
        Text(formatLastBackupLabel(context, lastBackupAtMs))

        OutlinedButton(onClick = {
            withDriveAccessToken { token ->
                scope.launch {
                    try {
                        withContext(Dispatchers.IO) {
                            val archive = LocalBackup.createArchive(context)
                            val existingId = DriveBackupClient.findBackupFileId(token)
                            try {
                                DriveBackupClient.uploadBackup(token, existingId, archive)
                            } finally {
                                archive.delete()
                            }
                        }
                        val now = System.currentTimeMillis()
                        BackupPrefs.setLastBackupAtMs(context, now)
                        lastBackupAtMs = now
                        showMessage(context.getString(R.string.backup_success))
                    } catch (e: Exception) {
                        showMessage(context.getString(R.string.backup_failure))
                    }
                }
            }
        }) {
            Text(stringResource(R.string.backup_now))
        }

        OutlinedButton(onClick = { showRestoreConfirm = true }) {
            Text(stringResource(R.string.backup_restore))
        }

        OutlinedButton(onClick = {
            scope.launch {
                authManager.signOut()
                BackupPrefs.setSignedInEmail(context, null)
                signedInEmail = null
            }
        }) {
            Text(stringResource(R.string.backup_sign_out))
        }
    }

    if (showRestoreConfirm) {
        AlertDialog(
            onDismissRequest = { showRestoreConfirm = false },
            title = { Text(stringResource(R.string.backup_restore_confirm_title)) },
            text = { Text(stringResource(R.string.backup_restore_confirm_message)) },
            confirmButton = {
                OutlinedButton(onClick = {
                    showRestoreConfirm = false
                    withDriveAccessToken { token ->
                        scope.launch {
                            try {
                                val restored = withContext(Dispatchers.IO) {
                                    val fileId = DriveBackupClient.findBackupFileId(token)
                                        ?: return@withContext false
                                    val bytes = DriveBackupClient.downloadBackup(token, fileId)
                                    LocalBackup.importBytes(context, bytes)
                                }
                                if (!restored) {
                                    showMessage(context.getString(R.string.backup_restore_not_found))
                                    return@launch
                                }
                                restartApp(context)
                            } catch (e: Exception) {
                                showMessage(context.getString(R.string.backup_restore_failure))
                            }
                        }
                    }
                }) { Text(stringResource(R.string.backup_restore)) }
            },
            dismissButton = {
                OutlinedButton(onClick = { showRestoreConfirm = false }) {
                    Text(stringResource(R.string.accessibility_back))
                }
            }
        )
    }
}

private fun restartApp(context: Context) {
    val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
    intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    context.startActivity(intent)
    Runtime.getRuntime().exit(0)
}
