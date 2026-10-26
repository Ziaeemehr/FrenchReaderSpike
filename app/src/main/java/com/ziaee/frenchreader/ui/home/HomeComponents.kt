package com.ziaee.frenchreader.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Spellcheck
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.ziaee.frenchreader.R
import com.ziaee.frenchreader.data.HeadlineEntity
import com.ziaee.frenchreader.data.TextDocument
import com.ziaee.frenchreader.news.RFI_FACILE_SOURCE_ID
import com.ziaee.frenchreader.ui.components.EditorialPrimaryButton
import com.ziaee.frenchreader.ui.theme.FrenchReaderDesign
import com.ziaee.frenchreader.ui.theme.LocalGrammarColors
import java.io.File

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProgressCard(
    state: HomeUiState,
    studyLogMinutes: Pair<Int, Int>,
    onOpen: () -> Unit,
    onAdd: () -> Unit,
    onSavedWords: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(onClick = onOpen, modifier = modifier) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.progress_title), Modifier.weight(1f),
                    style = FrenchReaderDesign.editorialTypography.sectionTitle.copy(fontSize = 20.sp, lineHeight = 24.sp))
                IconButton(onClick = onAdd) {
                    Icon(Icons.Default.Add, stringResource(R.string.study_log_add_time))
                }
            }
            Text(stringResource(R.string.home_summary_streak_days, state.streakDays), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(R.string.activity_goal_progress, state.todayActivityMinutes.toInt(), state.activityGoalMinutes))
            if (state.todayActivityMinutes >= state.activityGoalMinutes) {
                Text(stringResource(R.string.activity_goal_done), color = MaterialTheme.colorScheme.primary)
            }
            LinearProgressIndicator(
                progress = (state.todayActivityMinutes / state.activityGoalMinutes.coerceAtLeast(1)).toFloat().coerceIn(0f, 1f),
                modifier = Modifier.fillMaxWidth().height(4.dp)
            )
            Text(stringResource(R.string.study_log_title), style = MaterialTheme.typography.labelMedium)
            Text(stringResource(R.string.study_log_home_summary,
                com.ziaee.frenchreader.studylog.formatMinutes(studyLogMinutes.first),
                com.ziaee.frenchreader.studylog.formatMinutes(studyLogMinutes.second)),
                style = MaterialTheme.typography.bodySmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.home_summary_learned, state.learnedWordCount),
                    modifier = Modifier.padding(vertical = 12.dp), style = MaterialTheme.typography.labelLarge)
                TextButton(onClick = onSavedWords) {
                    Text(stringResource(R.string.home_summary_saved_words, state.savedWordCount))
                }
            }
        }
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
fun TodayNewsSection(
    headlines: List<HeadlineEntity>,
    hasPartialError: Boolean,
    isEmptyError: Boolean,
    onSelect: (HeadlineEntity) -> Unit,
    onRetry: () -> Unit,
    onSeeAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.home_section_today_news), Modifier.weight(1f),
                    style = FrenchReaderDesign.editorialTypography.sectionTitle.copy(fontSize = 20.sp, lineHeight = 24.sp))
                TextButton(onClick = onSeeAll) {
                    Text(stringResource(R.string.news_all_count, headlines.size))
                    Icon(Icons.Default.ChevronRight, contentDescription = null)
                }
            }
            NewsStatus(hasPartialError, isEmptyError, onRetry)
            headlines.sortedByDescending { it.publishedAtMs ?: 0L }.take(3).forEach { headline ->
                NewsHeadlineRow(headline, onClick = { onSelect(headline) })
            }
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
fun ContinueWatchingCard(
    video: com.ziaee.frenchreader.watch.RecentVideo,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(onClick = onClick, modifier = modifier) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.videos_continue),
                style = FrenchReaderDesign.editorialTypography.sectionTitle.copy(fontSize = 20.sp, lineHeight = 24.sp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AsyncImage(model = "https://i.ytimg.com/vi/${video.videoId}/mqdefault.jpg", contentDescription = null,
                    contentScale = ContentScale.Crop, modifier = Modifier.size(80.dp, 45.dp).clip(MaterialTheme.shapes.small))
                Column(Modifier.weight(1f)) {
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Text(video.title.ifBlank { video.videoId }, maxLines = 2, overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleSmall)
                    }
                    val seconds = video.positionMs / 1000
                    Text(stringResource(R.string.videos_progress, seconds / 60, seconds % 60),
                        style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
