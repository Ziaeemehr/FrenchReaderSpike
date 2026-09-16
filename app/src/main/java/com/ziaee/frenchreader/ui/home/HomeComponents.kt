package com.ziaee.frenchreader.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Article
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
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
import coil.compose.AsyncImage
import com.ziaee.frenchreader.R
import com.ziaee.frenchreader.data.HeadlineEntity
import com.ziaee.frenchreader.data.TextDocument
import com.ziaee.frenchreader.news.RFI_FACILE_SOURCE_ID
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
        SectionHeader(stringResource(R.string.home_section_today_news))
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
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(headlines, key = { it.sourceId + it.externalId }) { headline ->
                        NewsCard(headline, onClick = { onSelect(headline) })
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

@Composable
fun NewsCard(headline: HeadlineEntity, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(modifier = modifier.width(220.dp).clickable(onClick = onClick)) {
        Column {
            NewsCardImage(headline, modifier = Modifier.fillMaxWidth().height(120.dp))
            Column(Modifier.padding(12.dp)) {
                // The headline title is French source content -- always LTR,
                // regardless of a Persian (RTL) interface, same as article
                // bodies on the Reading screen.
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Text(
                        headline.title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    headline.sourceLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun ContinueReadingCard(doc: TextDocument, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        SectionHeader(stringResource(R.string.home_section_continue_reading))
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clickable(onClick = onClick)
        ) {
            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                DocumentThumbnail(doc, modifier = Modifier.size(56.dp))
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(doc.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    doc.sourceName?.let {
                        Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
fun RecentTextsSection(
    documents: List<TextDocument>,
    onOpen: (TextDocument) -> Unit,
    onSeeAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(R.string.home_section_my_texts), style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = onSeeAll) { Text(stringResource(R.string.home_action_see_all)) }
        }
        if (documents.isEmpty()) {
            Text(
                stringResource(R.string.home_recent_empty),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        } else {
            documents.forEach { doc ->
                RecentTextRow(doc, onClick = { onOpen(doc) })
            }
        }
    }
}

@Composable
private fun RecentTextRow(doc: TextDocument, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DocumentThumbnail(doc, modifier = Modifier.size(48.dp))
        Spacer(Modifier.width(12.dp))
        Column {
            Text(doc.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(doc.createdAtMs))
            Text(date, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
