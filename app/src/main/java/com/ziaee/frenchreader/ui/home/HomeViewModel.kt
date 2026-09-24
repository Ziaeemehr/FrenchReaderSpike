package com.ziaee.frenchreader.ui.home

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.room.invalidationTrackerFlow
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
import com.ziaee.frenchreader.content.EpubImportRepository
import com.ziaee.frenchreader.content.EpubImportResult
import com.ziaee.frenchreader.content.BatchImportRepository
import com.ziaee.frenchreader.content.BatchImportResult
import com.ziaee.frenchreader.content.RfiFacileContentSource
import com.ziaee.frenchreader.content.VikidiaContentSource
import com.ziaee.frenchreader.content.WikisourceContentSource
import com.ziaee.frenchreader.comprehension.ComprehensionRepository
import com.ziaee.frenchreader.data.AppDatabase
import com.ziaee.frenchreader.data.HeadlineEntity
import com.ziaee.frenchreader.data.NewsPrefs
import com.ziaee.frenchreader.data.TextDocument
import com.ziaee.frenchreader.data.TextBodyStore
import com.ziaee.frenchreader.data.insertTextDocument
import com.ziaee.frenchreader.data.VocabEntry
import com.ziaee.frenchreader.data.VocabPrefs
import com.ziaee.frenchreader.images.ArticleImageStore
import com.ziaee.frenchreader.news.NewsRepository
import com.ziaee.frenchreader.news.normalizeArticleUrl
import com.ziaee.frenchreader.news.personalizeHeadlines
import com.ziaee.frenchreader.ui.shared.ContentSearchUiState
import com.ziaee.frenchreader.ui.statistics.loadActiveDates
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Every source the Home topic-search field can query -- broader than the
 * two dashboard news sources used by the news dashboard above. */
private val TOPIC_SEARCH_SOURCES: List<ContentSource> =
    listOf(VikidiaContentSource, RfiFacileContentSource, FranceInfoContentSource, WikisourceContentSource)

class HomeViewModel(app: Application) : AndroidViewModel(app) {
    private val db = AppDatabase.get(app)
    private val bodyStore = TextBodyStore(app)
    private val comprehensionRepository = ComprehensionRepository.get(app)
    val comprehensionScores: StateFlow<Map<Long, Int?>> = comprehensionRepository.scores
    private val newsRepository = NewsRepository(
        db.headlineDao(),
        enabledSourceIds = { NewsPrefs.getEnabledSourceIds(app) }
    )
    private val epubImportRepository = EpubImportRepository(app, db)
    private val batchImportRepository = BatchImportRepository(app, db, bodyStore, epubImportRepository)
    private val importRepository = ArticleImportRepository(
        db.textDao(),
        listOf(RfiFacileContentSource, FranceInfoContentSource),
        ArticleImageStore(app),
        bodyStore,
        db.libraryOrganizerDao()
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
    private val timeRefresh = MutableStateFlow(0L)
    private var headlineLookupJob: Job? = null
    private var searchJob: Job? = null

    private data class TextsWithBodies(
        val documents: List<TextDocument>,
        val bodies: Map<Long, String>
    )

    private val textsWithBodies = db.textDao().observeAll().mapLatest { documents ->
        TextsWithBodies(
            documents,
            documents.take(MAX_RECENT_TEXTS).associate { it.id to bodyStore.read(it) }
        )
    }

    private val continueWithBody = db.textDao().observeMostRecentlyAccessed().mapLatest { document ->
        document to document?.let { bodyStore.read(it) }
    }

    /** Re-derives the real, non-fabricated reading streak whenever a review
     * or a listening session is logged -- driven by Room's own invalidation
     * tracker rather than a timer, so it stays correct without polling. */
    private val activeDates = db.invalidationTrackerFlow("review_log", "activity_log", emitInitialState = true)
        .map {
            loadActiveDates(
                reviewLogDates = { db.reviewLogDao().distinctActiveDates() },
                activityLogDates = { db.activityLogDao().activeDates() }
            )
        }

    val uiState: StateFlow<HomeUiState> = combine(
        newsRepository.observeHeadlines(),
        textsWithBodies,
        db.vocabDao().observeAll(),
        continueWithBody,
        isRefreshing,
        sourceErrors,
        importingKey,
        activeDates,
        timeRefresh
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val texts = values[1] as TextsWithBodies
        val continuing = values[3] as Pair<TextDocument?, String?>
        composeHomeState(
            headlines = personalizeHeadlines(
                values[0] as List<HeadlineEntity>,
                NewsPrefs.getKeywords(app),
                NewsPrefs.getMatchingFirst(app)
            ),
            allTexts = texts.documents,
            vocabEntries = values[2] as List<VocabEntry>,
            continueReading = continuing.first,
            bodyByTextId = texts.bodies + listOfNotNull(
                continuing.first?.id?.let { id -> continuing.second?.let { id to it } }
            ),
            isRefreshing = values[4] as Boolean,
            sourceErrors = values[5] as List<String>,
            importingKey = values[6] as String?,
            activeDates = values[7] as Set<LocalDate>,
            nowMs = System.currentTimeMillis(),
            today = LocalDate.now(),
            remainingNewCards = (VocabPrefs.getMaxNewCards(app) -
                VocabPrefs.getNewReviewedToday(app, LocalDate.now().toString())).coerceAtLeast(0)
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

    fun requestComprehension(document: TextDocument) {
        comprehensionRepository.request(document)
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
        headlineLookupJob?.cancel()
        headlineLookupJob = viewModelScope.launch {
            val documentId = db.textDao().findByExternalKey(normalizeArticleUrl(headline.articleUrl))?.id
            if (selectedHeadline == headline) existingDocumentId = documentId
        }
    }

    fun dismissPreview() {
        headlineLookupJob?.cancel()
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
            val id = insertTextDocument(db.textDao(), bodyStore, TextDocument(title = title, rawText = ""), body)
            onDone(id)
        }
    }

    fun importEpub(
        uri: Uri,
        onDone: (EpubImportResult) -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                onDone(epubImportRepository.import(uri))
            } catch (error: Exception) {
                android.util.Log.w("EpubImport", "EPUB import failed", error)
                onError(error.message.orEmpty())
            }
        }
    }

    fun importFiles(uris: List<Uri>, onDone: (BatchImportResult) -> Unit) {
        viewModelScope.launch {
            onDone(runCatching { batchImportRepository.importAll(uris) }
                .getOrElse { BatchImportResult(0, 0, uris.size, null) })
        }
    }

    fun importTree(uri: Uri, onDone: (BatchImportResult) -> Unit) {
        viewModelScope.launch {
            onDone(runCatching { batchImportRepository.importTree(uri) }
                .getOrElse { BatchImportResult(0, 0, 1, null) })
        }
    }

    /** Topic search across every content source (Vikidia, RFI, France
     * Info) -- the existing "find content" experience Home's search field
     * opens, unrelated to the two-source news dashboard above. */
    fun searchContent(query: String) {
        if (query.isBlank()) return
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
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

    fun refreshTimeSensitiveState() {
        timeRefresh.value = System.currentTimeMillis()
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
            val id = insertTextDocument(
                db.textDao(),
                bodyStore,
                TextDocument(
                    title = article.title,
                    rawText = "",
                    sourceUrl = article.sourceUrl,
                    sourceName = article.sourceName,
                    author = article.author,
                    license = article.license,
                    publishedAt = article.publishedAtMs
                ),
                article.text
            )
            resetContentSearch()
            onOpen(id)
        }
    }
}
