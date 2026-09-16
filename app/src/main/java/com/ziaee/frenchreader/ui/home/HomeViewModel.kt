package com.ziaee.frenchreader.ui.home

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ziaee.frenchreader.content.ArticleImportRepository
import com.ziaee.frenchreader.content.ArticleImportResult
import com.ziaee.frenchreader.content.ContentArticle
import com.ziaee.frenchreader.content.ContentResult
import com.ziaee.frenchreader.content.ContentSource
import com.ziaee.frenchreader.content.FranceInfoContentSource
import com.ziaee.frenchreader.content.RfiFacileContentSource
import com.ziaee.frenchreader.content.VikidiaContentSource
import com.ziaee.frenchreader.data.AppDatabase
import com.ziaee.frenchreader.data.HeadlineEntity
import com.ziaee.frenchreader.data.TextDocument
import com.ziaee.frenchreader.images.ArticleImageStore
import com.ziaee.frenchreader.news.NewsRepository
import com.ziaee.frenchreader.news.normalizeArticleUrl
import com.ziaee.frenchreader.ui.shared.ContentSearchUiState
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Every source the Home topic-search field can query -- broader than the
 * two dashboard news sources used by the news dashboard above. */
private val TOPIC_SEARCH_SOURCES: List<ContentSource> =
    listOf(VikidiaContentSource, RfiFacileContentSource, FranceInfoContentSource)

class HomeViewModel(app: Application) : AndroidViewModel(app) {
    private val db = AppDatabase.get(app)
    private val newsRepository = NewsRepository(db.headlineDao())
    private val importRepository = ArticleImportRepository(
        db.textDao(),
        listOf(RfiFacileContentSource, FranceInfoContentSource),
        ArticleImageStore(app)
    )

    var contentSearchState by mutableStateOf<ContentSearchUiState>(ContentSearchUiState.Idle)
        private set
    var contentImportingRef by mutableStateOf<String?>(null)
        private set
    var contentImportError by mutableStateOf<String?>(null)
        private set

    private val isRefreshing = MutableStateFlow(false)
    private val sourceErrors = MutableStateFlow<List<String>>(emptyList())
    private val importingKey = MutableStateFlow<String?>(null)

    val uiState: StateFlow<HomeUiState> = combine(
        newsRepository.observeHeadlines(),
        db.textDao().observeRecent(),
        db.textDao().observeMostRecentlyAccessed(),
        isRefreshing,
        sourceErrors,
        importingKey
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        composeHomeState(
            headlines = values[0] as List<HeadlineEntity>,
            recentTexts = values[1] as List<TextDocument>,
            continueReading = values[2] as TextDocument?,
            isRefreshing = values[3] as Boolean,
            sourceErrors = values[4] as List<String>,
            importingKey = values[5] as String?
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    var selectedHeadline by mutableStateOf<HeadlineEntity?>(null)
        private set

    /** Whether the last import attempt for [selectedHeadline] failed. Kept
     * as a flag rather than a message so the UI always shows a localized
     * string (see [com.ziaee.frenchreader.R.string.error_generic]). */
    var previewImportError by mutableStateOf(false)
        private set

    /** Non-null once a duplicate check finds [selectedHeadline] already
     * downloaded, so the preview can offer "Open in Library" up front
     * instead of only after a redundant download attempt. */
    var existingDocumentId by mutableStateOf<Long?>(null)
        private set

    init {
        refresh(force = false)
    }

    /** Pull-to-refresh always passes true; the initial load passes false so
     * a still-fresh cache from a recent session skips the network entirely. */
    fun refresh(force: Boolean) {
        viewModelScope.launch {
            isRefreshing.value = true
            val result = newsRepository.refresh(force)
            sourceErrors.value = result.failedSources
            isRefreshing.value = false
        }
    }

    fun selectHeadline(headline: HeadlineEntity) {
        selectedHeadline = headline
        previewImportError = false
        existingDocumentId = null
        viewModelScope.launch {
            existingDocumentId = db.textDao().findByExternalKey(normalizeArticleUrl(headline.articleUrl))?.id
        }
    }

    fun dismissPreview() {
        selectedHeadline = null
        previewImportError = false
        existingDocumentId = null
    }

    /** Downloads (or reopens) the currently previewed headline. Failure
     * keeps the preview sheet open with a retryable error instead of
     * dismissing it. */
    fun importSelected(onOpen: (Long) -> Unit) {
        val headline = selectedHeadline ?: return
        viewModelScope.launch {
            importingKey.value = headline.articleUrl
            previewImportError = false
            val result = try {
                importRepository.import(headline)
            } catch (e: Exception) {
                null
            }
            importingKey.value = null
            when (result) {
                is ArticleImportResult.Imported -> {
                    dismissPreview()
                    onOpen(result.id)
                }
                is ArticleImportResult.OpenExisting -> {
                    dismissPreview()
                    onOpen(result.id)
                }
                null -> previewImportError = true
            }
        }
    }

    /** Persists a pasted/file-imported/shared text as-is -- the Add Text
     * flow, unrelated to headline or topic-search imports. */
    fun pasteText(title: String, body: String, onDone: (Long) -> Unit) {
        viewModelScope.launch {
            val id = db.textDao().insert(TextDocument(title = title, rawText = body))
            onDone(id)
        }
    }

    /** Topic search across every content source (Vikidia, RFI, France
     * Info) -- the existing "find content" experience Home's search field
     * opens, unrelated to the two-source news dashboard above. */
    fun searchContent(query: String) {
        if (query.isBlank()) return
        viewModelScope.launch {
            contentSearchState = ContentSearchUiState.Searching
            contentSearchState = try {
                val perSource = coroutineScope {
                    TOPIC_SEARCH_SOURCES.map { source ->
                        async {
                            try {
                                source.search(query)
                            } catch (e: Exception) {
                                null
                            }
                        }
                    }.map { it.await() }
                }
                if (perSource.all { it == null }) {
                    ContentSearchUiState.Error("no_source_available")
                } else {
                    val merged = perSource.filterNotNull().flatten()
                    if (merged.isEmpty()) ContentSearchUiState.NoResults else ContentSearchUiState.Results(merged)
                }
            } catch (e: Exception) {
                ContentSearchUiState.Error(e.message ?: e.toString())
            }
        }
    }

    fun resetContentSearch() {
        contentSearchState = ContentSearchUiState.Idle
        contentImportError = null
    }

    fun dismissContentSearchError() {
        if (contentSearchState is ContentSearchUiState.Error) contentSearchState = ContentSearchUiState.Idle
    }

    fun dismissContentImportError() {
        contentImportError = null
    }

    /** Fetches [result]'s full article from whichever source produced it
     * and, on success, creates a plain TextDocument (no dedup/image --
     * that's only for headline-based imports via [importSelected]) and
     * opens it via [onOpen]. */
    fun importTopicResult(result: ContentResult, onOpen: (Long) -> Unit) {
        viewModelScope.launch {
            contentImportingRef = result.ref
            contentImportError = null
            val source = TOPIC_SEARCH_SOURCES.find { it.id == result.sourceId }
            val article: ContentArticle? = try {
                source?.fetchArticle(result)
            } catch (e: Exception) {
                contentImportError = e.message ?: e.toString()
                null
            }
            contentImportingRef = null
            if (article == null) {
                if (contentImportError == null) contentImportError = ""
                return@launch
            }
            val id = db.textDao().insert(
                TextDocument(
                    title = article.title,
                    rawText = article.text,
                    sourceUrl = article.sourceUrl,
                    sourceName = article.sourceName,
                    author = article.author,
                    license = article.license,
                    publishedAt = article.publishedAtMs
                )
            )
            resetContentSearch()
            onOpen(id)
        }
    }
}
