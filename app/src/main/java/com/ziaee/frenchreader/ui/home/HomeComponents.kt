package com.ziaee.frenchreader.ui.home

import androidx.compose.foundation.background
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.aspectRatio
import com.ziaee.frenchreader.ui.TextLevelBadge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Spellcheck
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.ziaee.frenchreader.R
import com.ziaee.frenchreader.data.HeadlineEntity
import com.ziaee.frenchreader.data.TextDocument
import com.ziaee.frenchreader.news.RFI_FACILE_SOURCE_ID
import com.ziaee.frenchreader.ui.components.EditorialProgressIndicator
import com.ziaee.frenchreader.watch.RecentVideo
import com.ziaee.frenchreader.ui.components.EditorialPrimaryButton
import com.ziaee.frenchreader.ui.theme.FrenchReaderDesign
import com.ziaee.frenchreader.ui.theme.LocalGrammarColors
import java.io.File
import java.text.NumberFormat

@Composable
fun ProgressCard(
    state: HomeUiState,
    onOpen: () -> Unit,
    onAdd: () -> Unit,
    onSavedWords: () -> Unit,
    modifier: Modifier = Modifier
) {
    val locale = LocalConfiguration.current.locales[0]
    val numbers = remember(locale) { NumberFormat.getIntegerInstance(locale).apply { isGroupingUsed = false } }
    val progress = (state.todayActivityMinutes / state.activityGoalMinutes.coerceAtLeast(1)).toFloat().coerceIn(0f, 1f)
    val goalReached = progress >= 1f
    val colors = MaterialTheme.colorScheme
    Card(onClick = onOpen, modifier = modifier) {
        Column(Modifier.padding(start = 14.dp, end = 6.dp, top = 12.dp, bottom = 6.dp)) {
            Row(Modifier.fillMaxWidth().padding(end = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ProgressStat(Icons.Default.LocalFireDepartment, Color(0xFFF2994A), numbers.format(state.streakDays),
                    pluralStringResource(R.plurals.home_streak_days, state.streakDays), Modifier.weight(1f))
                // The week's total keeps growing; today's goal is the bar below.
                ProgressStat(Icons.Default.Timer, colors.primary, numbers.format(state.weekActivityMinutes.toInt()),
                    stringResource(R.string.home_minutes_week), Modifier.weight(1f))
                // Learned out of saved: saving is one tap, learning is the progress that matters.
                ProgressStat(Icons.Default.School, colors.tertiary,
                    stringResource(R.string.home_minutes_ratio, numbers.format(state.learnedWordCount), numbers.format(state.savedWordCount)),
                    stringResource(R.string.review_learned_words),
                    Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).clickable(onClick = onSavedWords))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                LinearProgressIndicator(
                    progress = progress,
                    color = if (goalReached) colors.tertiary else colors.primary,
                    trackColor = colors.onSurface.copy(alpha = 0.10f),
                    modifier = Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp))
                )
                if (goalReached) Icon(Icons.Default.CheckCircle, stringResource(R.string.activity_goal_done),
                    tint = colors.tertiary, modifier = Modifier.padding(start = 8.dp).size(16.dp))
                else Text(stringResource(R.string.home_goal_caption,
                    numbers.format(state.todayActivityMinutes.toInt()), numbers.format(state.activityGoalMinutes)),
                    style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant,
                    maxLines = 1, modifier = Modifier.padding(start = 8.dp))
                IconButton(onClick = onAdd, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Default.AddCircleOutline, stringResource(R.string.study_log_add_time),
                        tint = colors.onSurfaceVariant, modifier = Modifier.size(22.dp))
                }
            }
        }
    }
}

/** One quiet stat: a softly tinted icon badge, a bold value, and a one-line caption. */
@Composable
private fun ProgressStat(
    icon: ImageVector,
    accent: Color,
    value: String,
    label: String,
    modifier: Modifier = Modifier
) {
    Column(modifier.padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(28.dp).clip(CircleShape).background(accent.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(16.dp))
            }
            Spacer(Modifier.width(8.dp))
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun DailyReviewCard(
    dueCount: Int,
    onStart: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(FrenchReaderDesign.spacing.small),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (dueCount > 0) Icons.Default.MenuBook else Icons.Default.CheckCircle,
                contentDescription = null,
                tint = if (dueCount > 0) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(FrenchReaderDesign.sizes.touchTarget)
            )
            Spacer(Modifier.width(FrenchReaderDesign.spacing.xSmall))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.home_section_daily_review),
                    style = FrenchReaderDesign.editorialTypography.sectionTitle.copy(fontSize = 20.sp, lineHeight = 24.sp)
                )
                Text(
                    text = if (dueCount > 0) {
                        stringResource(R.string.home_review_due, dueCount)
                    } else {
                        stringResource(R.string.home_review_complete)
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = if (dueCount > 0) {
                        stringResource(R.string.home_review_due_support)
                    } else {
                        stringResource(R.string.home_review_complete_support)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (dueCount > 0) {
                Spacer(Modifier.width(FrenchReaderDesign.spacing.xSmall))
                EditorialPrimaryButton(text = stringResource(R.string.home_review_start), onClick = onStart)
            }
        }
    }
}

/** Entry point to the bundled starter decks and graded stories (see DatasetScreen). */
@Composable
fun DatasetCard(onOpen: () -> Unit, modifier: Modifier = Modifier) {
    Card(onClick = onOpen, modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(FrenchReaderDesign.spacing.small),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.LibraryBooks,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(FrenchReaderDesign.sizes.touchTarget)
            )
            Spacer(Modifier.width(FrenchReaderDesign.spacing.xSmall))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.dataset_settings_entry),
                    style = FrenchReaderDesign.editorialTypography.sectionTitle.copy(fontSize = 20.sp, lineHeight = 24.sp)
                )
                Text(
                    text = stringResource(R.string.home_dataset_support),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null)
        }
    }
}

/** Entry point to the built-in grammar reference (see GrammarListScreen). */
@Composable
fun GrammarCard(onOpen: () -> Unit, modifier: Modifier = Modifier) {
    Card(onClick = onOpen, modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(FrenchReaderDesign.spacing.small),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Spellcheck,
                contentDescription = null,
                tint = LocalGrammarColors.current.accent,
                modifier = Modifier.size(FrenchReaderDesign.sizes.touchTarget)
            )
            Spacer(Modifier.width(FrenchReaderDesign.spacing.xSmall))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.grammar_title),
                    style = FrenchReaderDesign.editorialTypography.sectionTitle.copy(fontSize = 20.sp, lineHeight = 24.sp)
                )
                Text(
                    text = stringResource(R.string.grammar_entry_description),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null)
        }
    }
}

@Composable
fun ExamsCard(onOpen: () -> Unit, modifier: Modifier = Modifier, practiceCounts: Pair<Int, Int>? = null) {
    Card(onClick = onOpen, modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(FrenchReaderDesign.spacing.small),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.School,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(FrenchReaderDesign.sizes.touchTarget)
            )
            Spacer(Modifier.width(FrenchReaderDesign.spacing.xSmall))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.exams_title),
                    style = FrenchReaderDesign.editorialTypography.sectionTitle.copy(fontSize = 20.sp, lineHeight = 24.sp)
                )
                Text(
                    text = practiceCounts?.let { stringResource(R.string.exam_home_progress, it.first, it.second) }
                        ?: stringResource(R.string.exams_entry_description),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null)
        }
    }
}

@Composable
fun TodayNewsCard(
    headlineCount: Int,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier
) {
    val locale = LocalConfiguration.current.locales[0]
    Card(onClick = onOpen, modifier = modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(FrenchReaderDesign.spacing.small), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Newspaper, contentDescription = null, tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(FrenchReaderDesign.sizes.touchTarget))
            Spacer(Modifier.width(FrenchReaderDesign.spacing.xSmall))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.home_section_today_news),
                    style = FrenchReaderDesign.editorialTypography.sectionTitle.copy(fontSize = 20.sp, lineHeight = 24.sp))
                Text(
                    if (headlineCount > 0) pluralStringResource(R.plurals.home_news_headlines, headlineCount,
                        NumberFormat.getIntegerInstance(locale).format(headlineCount))
                    else stringResource(R.string.home_news_description),
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2, overflow = TextOverflow.Ellipsis
                )
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null)
        }
    }
}

@Composable
internal fun NewsStatus(hasPartialError: Boolean, isEmptyError: Boolean, onRetry: () -> Unit) {
    if (hasPartialError || isEmptyError) {
        Text(stringResource(if (isEmptyError) R.string.home_news_empty_error else R.string.home_news_partial_error),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        TextButton(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
    }
}

@Composable
internal fun NewsHeadlineRow(headline: HeadlineEntity, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (!headline.imageUrl.isNullOrBlank()) NewsCardImage(headline, Modifier.size(56.dp).clip(MaterialTheme.shapes.small))
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Column(Modifier.weight(1f)) {
                Text(headline.sourceLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(headline.title, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
internal fun NewsCardImage(headline: HeadlineEntity, modifier: Modifier = Modifier) {
    var loadFailed by remember(headline.imageUrl) { mutableStateOf(false) }
    val imageUrl = headline.imageUrl
    if (imageUrl != null && !loadFailed) {
        AsyncImage(
            model = imageUrl,
            contentDescription = stringResource(R.string.accessibility_article_image),
            contentScale = ContentScale.Crop,
            onError = { loadFailed = true },
            modifier = modifier
        )
    } else {
        NewsSourcePlaceholder(headline.sourceId, modifier)
    }
}

@Composable
private fun NewsSourcePlaceholder(sourceId: String, modifier: Modifier) {
    val color = if (sourceId == RFI_FACILE_SOURCE_ID) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.secondaryContainer
    }
    Box(modifier = modifier.background(color), contentAlignment = Alignment.Center) {
        Icon(
            Icons.Default.Article,
            contentDescription = stringResource(R.string.home_news_image_unavailable),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
internal fun DocumentThumbnail(doc: TextDocument, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val imageFile = doc.imagePath?.let { File(context.filesDir, it) }?.takeIf { it.exists() }
    val shape = RoundedCornerShape(8.dp)
    if (imageFile != null) {
        AsyncImage(
            model = imageFile,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier.clip(shape)
        )
    } else {
        Box(
            modifier = modifier.clip(shape).background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Article, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun WatchVideosCard(onOpen: () -> Unit, modifier: Modifier = Modifier) {
    Card(onClick = onOpen, modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(FrenchReaderDesign.spacing.small),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.PlayCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(FrenchReaderDesign.sizes.touchTarget)
            )
            Spacer(Modifier.width(FrenchReaderDesign.spacing.xSmall))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.videos_title),
                    style = FrenchReaderDesign.editorialTypography.sectionTitle.copy(fontSize = 20.sp, lineHeight = 24.sp)
                )
                Text(
                    text = stringResource(R.string.videos_home_support),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null)
        }
    }
}

@Composable
fun ContinueReadingCard(
    doc: TextDocument,
    body: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val metrics = remember(doc.id, body, doc.lastChunkIndex) { homeReadingMetrics(doc, body) }
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            DocumentThumbnail(doc, modifier = Modifier.size(FrenchReaderDesign.sizes.thumbnailMedium))
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Text(
                        doc.title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    doc.sourceName?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Spacer(Modifier.width(FrenchReaderDesign.spacing.half))
                    }
                    doc.level?.let { TextLevelBadge(it) }
                }
                Spacer(Modifier.height(FrenchReaderDesign.spacing.half))
                EditorialProgressIndicator(
                    progress = metrics.progressFraction,
                    label = stringResource(R.string.home_progress_percent, metrics.progressPercent)
                )
                Text(
                    text = if (metrics.estimatedRemainingMinutes > 0) {
                        stringResource(R.string.home_remaining_minutes, metrics.estimatedRemainingMinutes)
                    } else {
                        stringResource(R.string.home_reading_complete)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(FrenchReaderDesign.spacing.xSmall))
            EditorialPrimaryButton(text = stringResource(R.string.home_action_resume), onClick = onClick)
        }
    }
}

@Composable
fun ContinueWatchingCard(
    video: RecentVideo,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val seconds = video.positionMs / 1000
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = "https://i.ytimg.com/vi/${video.videoId}/mqdefault.jpg",
                contentDescription = null,
                contentScale = ContentScale.Crop,
                // Same rounded, tinted frame as DocumentThumbnail while the thumbnail loads or when offline.
                modifier = Modifier.width(FrenchReaderDesign.sizes.thumbnailMedium).aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Text(
                        video.title.ifBlank { video.videoId },
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = stringResource(R.string.videos_progress, seconds / 60, seconds % 60) +
                        video.channel.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(FrenchReaderDesign.spacing.xSmall))
            EditorialPrimaryButton(text = stringResource(R.string.home_action_resume), onClick = onClick)
        }
    }
}
