package com.ziaee.frenchreader.ui.home

import com.ziaee.frenchreader.data.HeadlineEntity
import com.ziaee.frenchreader.data.TextDocument
import com.ziaee.frenchreader.data.VocabEntry
import com.ziaee.frenchreader.text.TextChunker
import com.ziaee.frenchreader.text.BlockType
import com.ziaee.frenchreader.text.MarkdownParser
import com.ziaee.frenchreader.ui.statistics.computeStreak
import java.time.LocalDate
import kotlin.math.ceil

internal const val MAX_RECENT_TEXTS = 5

/** Number of independent Home news sources (RFI Français Facile, France
 * Info) -- see [NewsRepository][com.ziaee.frenchreader.news.NewsRepository].
 * Used only to tell a partial refresh failure from a total one. */
internal const val HOME_NEWS_SOURCE_COUNT = 2

/**
 * Immutable Home snapshot. [headlines] and [recentTexts] stay whatever was
 * last cached/loaded even while [isRefreshing] is true, so Home never blanks
 * out during a refresh -- only [sourceErrors] and [isRefreshing] change.
 *
 * The learning-summary fields are all derived from real stored rows
 * ([VocabEntry], [TextDocument], review/activity log dates) -- never
 * fabricated. There is deliberately no weekly-goal field: no stored or
 * configurable goal target exists anywhere in the app.
 */
data class HomeUiState(
    val headlines: List<HeadlineEntity> = emptyList(),
    val recentTexts: List<TextDocument> = emptyList(),
    val continueReading: TextDocument? = null,
    val bodyByTextId: Map<Long, String> = emptyMap(),
    val isRefreshing: Boolean = false,
    val sourceErrors: List<String> = emptyList(),
    val importingKey: String? = null,
    val savedTextCount: Int = 0,
    val savedWordCount: Int = 0,
    val learnedWordCount: Int = 0,
    val dueReviewCount: Int = 0,
    val streakDays: Int = 0
) {
    /** One source failed but there's still something to show -- a
     * non-blocking warning, not a full error screen. */
    val hasPartialError: Boolean get() = sourceErrors.isNotEmpty() && headlines.isNotEmpty()

    /** Nothing cached and every source failed -- Home has nothing usable to
     * render and needs the dedicated empty/offline state with Retry. */
    val isEmptyError: Boolean get() = headlines.isEmpty() && sourceErrors.size >= HOME_NEWS_SOURCE_COUNT
}

internal fun composeHomeState(
    headlines: List<HeadlineEntity>,
    allTexts: List<TextDocument>,
    vocabEntries: List<VocabEntry>,
    continueReading: TextDocument?,
    bodyByTextId: Map<Long, String> = emptyMap(),
    isRefreshing: Boolean,
    sourceErrors: List<String>,
    importingKey: String?,
    activeDates: Set<LocalDate> = emptySet(),
    nowMs: Long = System.currentTimeMillis(),
    today: LocalDate = LocalDate.now()
): HomeUiState = HomeUiState(
    headlines = headlines,
    recentTexts = allTexts.take(MAX_RECENT_TEXTS),
    continueReading = continueReading,
    bodyByTextId = bodyByTextId,
    isRefreshing = isRefreshing,
    sourceErrors = sourceErrors,
    importingKey = importingKey,
    savedTextCount = allTexts.size,
    savedWordCount = vocabEntries.size,
    learnedWordCount = vocabEntries.count { it.learned },
    dueReviewCount = vocabEntries.count { !it.learned && it.nextReviewAtMs <= nowMs },
    streakDays = computeStreak(activeDates, today)
)

/** Locally derived reading-time/progress estimate for one [TextDocument]. */
data class HomeReadingMetrics(
    val progressFraction: Float,
    val progressPercent: Int,
    val estimatedTotalMinutes: Int,
    val estimatedRemainingMinutes: Int
)

/** Rounds non-empty reading time up so short texts never show "0 min". */
internal fun estimatedReadingMinutes(rawText: String, wordsPerMinute: Int = 200): Int {
    val wordCount = rawText.trim().split(Regex("\\s+")).count { it.isNotBlank() }
    if (wordCount == 0) return 0
    return ceil(wordCount.toDouble() / wordsPerMinute).toInt().coerceAtLeast(1)
}

internal fun homeReadingMetrics(doc: TextDocument, body: String, wordsPerMinute: Int = 200): HomeReadingMetrics {
    val chunks = TextChunker.chunk(body)
    val spokenChunkIndices = chunks.indices.filter {
        MarkdownParser.parse(chunks[it]).type != BlockType.IMAGE
    }
    val totalMinutes = estimatedReadingMinutes(body, wordsPerMinute)
    if (spokenChunkIndices.isEmpty()) {
        return HomeReadingMetrics(0f, 0, totalMinutes, totalMinutes)
    }
    val currentChunk = doc.lastChunkIndex.coerceIn(0, chunks.lastIndex)
    val reachedChunks = spokenChunkIndices.count { it <= currentChunk }
    val fraction = (reachedChunks.toFloat() / spokenChunkIndices.size).coerceIn(0f, 1f)
    val remainingChunks = (spokenChunkIndices.size - reachedChunks).coerceAtLeast(0)
    val remainingMinutes = if (remainingChunks == 0) {
        0
    } else {
        ceil(totalMinutes.toDouble() * remainingChunks / spokenChunkIndices.size).toInt().coerceAtLeast(1)
    }
    return HomeReadingMetrics(
        progressFraction = fraction,
        progressPercent = (fraction * 100).toInt(),
        estimatedTotalMinutes = totalMinutes,
        estimatedRemainingMinutes = remainingMinutes
    )
}
