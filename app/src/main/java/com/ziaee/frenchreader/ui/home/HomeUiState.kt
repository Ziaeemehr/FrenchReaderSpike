package com.ziaee.frenchreader.ui.home

import com.ziaee.frenchreader.data.HeadlineEntity
import com.ziaee.frenchreader.data.TextDocument

internal const val MAX_RECENT_TEXTS = 5

/** Number of independent Home news sources (RFI Français Facile, France
 * Info) -- see [NewsRepository][com.ziaee.frenchreader.news.NewsRepository].
 * Used only to tell a partial refresh failure from a total one. */
internal const val HOME_NEWS_SOURCE_COUNT = 2

/**
 * Immutable Home snapshot. [headlines] and [recentTexts] stay whatever was
 * last cached/loaded even while [isRefreshing] is true, so Home never blanks
 * out during a refresh -- only [sourceErrors] and [isRefreshing] change.
 */
data class HomeUiState(
    val headlines: List<HeadlineEntity> = emptyList(),
    val recentTexts: List<TextDocument> = emptyList(),
    val continueReading: TextDocument? = null,
    val isRefreshing: Boolean = false,
    val sourceErrors: List<String> = emptyList(),
    val importingKey: String? = null
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
    recentTexts: List<TextDocument>,
    continueReading: TextDocument?,
    isRefreshing: Boolean,
    sourceErrors: List<String>,
    importingKey: String?
): HomeUiState = HomeUiState(
    headlines = headlines,
    recentTexts = recentTexts.take(MAX_RECENT_TEXTS),
    continueReading = continueReading,
    isRefreshing = isRefreshing,
    sourceErrors = sourceErrors,
    importingKey = importingKey
)
