package com.ziaee.frenchreader.ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ziaee.frenchreader.R
import com.ziaee.frenchreader.data.HeadlineEntity
import com.ziaee.frenchreader.data.TextDocument
import com.ziaee.frenchreader.news.RFI_FACILE_SOURCE_ID
import com.ziaee.frenchreader.ui.components.EditorialEmptyState
import com.ziaee.frenchreader.ui.components.EditorialPrimaryButton
import com.ziaee.frenchreader.ui.components.EditorialProgressIndicator
import com.ziaee.frenchreader.ui.components.EditorialSectionHeader
import com.ziaee.frenchreader.ui.components.MetadataBadge
import com.ziaee.frenchreader.ui.theme.FrenchReaderDesign
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun SectionHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

/** Compact real-data learning strip: streak, learned words, and saved
 * words/texts. Deliberately has no weekly-goal cell -- no stored or
 * configurable goal target exists anywhere in the app. */
@Composable
fun LearningSummaryStrip(
    streakDays: Int,
    learnedWordCount: Int,
    savedWordCount: Int,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = FrenchReaderDesign.elevations.card
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = FrenchReaderDesign.spacing.small, vertical = FrenchReaderDesign.spacing.xSmall),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            SummaryCell(
                icon = Icons.Default.LocalFireDepartment,
                iconTint = MaterialTheme.colorScheme.tertiary,
                value = streakDays.toString(),
                label = stringResource(R.string.home_summary_streak_days, streakDays),
                modifier = Modifier.weight(1f)
            )
            SummaryDivider()
            SummaryCell(
                icon = Icons.Default.MenuBook,
                iconTint = MaterialTheme.colorScheme.secondary,
                value = learnedWordCount.toString(),
                label = stringResource(R.string.home_summary_learned, learnedWordCount),
                modifier = Modifier.weight(1f)
            )
            SummaryDivider()
            SummaryCell(
                icon = Icons.Default.Article,
                iconTint = MaterialTheme.colorScheme.primary,
                value = savedWordCount.toString(),
                label = stringResource(R.string.home_summary_saved_words, savedWordCount),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun SummaryCell(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    value: String,
    label: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(FrenchReaderDesign.sizes.iconSmall))
            Spacer(Modifier.size(4.dp))
            Text(value, style = MaterialTheme.typography.titleMedium)
        }
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SummaryDivider() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(32.dp)
            .background(MaterialTheme.colorScheme.outlineVariant)
    )
}

/** Two explicit states: words are due (primary action to start), or the
 * user is caught up (a calm completed state, never a disabled-looking
 * empty rectangle). */
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
                    style = MaterialTheme.typography.titleSmall
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TodayNewsSection(
    headlines: List<HeadlineEntity>,
    hasPartialError: Boolean,
    isEmptyError: Boolean,
    onSelect: (HeadlineEntity) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        EditorialSectionHeader(
            title = stringResource(R.string.home_section_today_news),
            modifier = Modifier.padding(horizontal = FrenchReaderDesign.spacing.small, vertical = FrenchReaderDesign.spacing.xSmall)
        )
        when {
            isEmptyError -> HomeErrorState(
                message = stringResource(R.string.home_news_empty_error),
                onRetry = onRetry,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            else -> {
                if (hasPartialError) {
                    Text(
                        stringResource(R.string.home_news_partial_error),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
                val listState = rememberLazyListState()
                LazyRow(
                    state = listState,
                    flingBehavior = rememberSnapFlingBehavior(listState),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(headlines, key = { it.sourceId + it.externalId }) { headline ->
                        NewsCard(
                            headline,
                            onClick = { onSelect(headline) },
                            modifier = Modifier.fillParentMaxWidth(0.6f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeErrorState(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(message, style = MaterialTheme.typography.bodyMedium)
        TextButton(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
    }
}

/** An immersive card: the photo fills the whole card, with the headline and
 * source overlaid at the bottom on a functional gradient scrim (added only
 * to guarantee text contrast, never decorative). */
@Composable
fun NewsCard(headline: HeadlineEntity, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(modifier = modifier.clickable(onClick = onClick), shape = MaterialTheme.shapes.medium) {
        Box(modifier = Modifier.fillMaxWidth().aspectRatio(4f / 5f)) {
            NewsCardImage(headline, modifier = Modifier.fillMaxSize())
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f)),
                            startY = 0.35f
                        )
                    )
            )
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(FrenchReaderDesign.spacing.xSmall)
            ) {
                MetadataBadge(
                    text = headline.sourceLabel,
                    containerColor = Color.White.copy(alpha = 0.16f),
                    contentColor = Color.White
                )
                Spacer(Modifier.height(FrenchReaderDesign.spacing.half))
                // The headline title is French source content -- always LTR,
                // regardless of a Persian (RTL) interface, same as article
                // bodies on the Reading screen.
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Text(
                        headline.title,
                        style = FrenchReaderDesign.editorialTypography.articleHeadline,
                        color = Color.White,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
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
fun ContinueReadingCard(doc: TextDocument, body: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val metrics = remember(doc.id, body, doc.lastChunkIndex) { homeReadingMetrics(doc, body) }
    Column(modifier = modifier) {
        EditorialSectionHeader(
            title = stringResource(R.string.home_section_continue_reading),
            modifier = Modifier.padding(horizontal = FrenchReaderDesign.spacing.small, vertical = FrenchReaderDesign.spacing.xSmall)
        )
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
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
                    doc.sourceName?.let {
                        Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
}

@Composable
fun RecentTextsSection(
    documents: List<TextDocument>,
    bodyByTextId: Map<Long, String> = emptyMap(),
    onOpen: (TextDocument) -> Unit,
    onSeeAll: () -> Unit,
    onAddText: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        EditorialSectionHeader(
            title = stringResource(R.string.home_section_my_texts),
            modifier = Modifier.padding(horizontal = FrenchReaderDesign.spacing.small, vertical = FrenchReaderDesign.spacing.xSmall),
            trailing = { TextButton(onClick = onSeeAll) { Text(stringResource(R.string.home_action_see_all)) } }
        )
        if (documents.isEmpty()) {
            EditorialEmptyState(
                icon = Icons.Default.Article,
                title = stringResource(R.string.home_recent_empty),
                body = "",
                actionLabel = stringResource(R.string.action_add_text),
                onAction = onAddText
            )
        } else {
            documents.forEach { doc ->
                RecentTextRow(doc, bodyByTextId[doc.id].orEmpty(), onClick = { onOpen(doc) })
            }
        }
    }
}

@Composable
private fun RecentTextRow(doc: TextDocument, body: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DocumentThumbnail(doc, modifier = Modifier.size(FrenchReaderDesign.sizes.thumbnailSmall))
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Text(doc.title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(doc.createdAtMs))
            val minutes = remember(doc.id, body) { estimatedReadingMinutes(body) }
            val metadata = doc.sourceName?.let { "$it · $date" } ?: date
            Text(
                text = if (minutes > 0) "$metadata · " + stringResource(R.string.home_estimated_minutes, minutes) else metadata,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
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
