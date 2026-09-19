package com.ziaee.frenchreader.ui.home

import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ziaee.frenchreader.R
import com.ziaee.frenchreader.data.HeadlineEntity
import com.ziaee.frenchreader.ui.components.EditorialBottomBar
import com.ziaee.frenchreader.ui.components.EditorialDestination
import com.ziaee.frenchreader.ui.components.EditorialSearchEntry
import com.ziaee.frenchreader.ui.components.EditorialTopAppBar
import com.ziaee.frenchreader.ui.shared.AddTextHost
import com.ziaee.frenchreader.ui.shared.ContentSearchUiState
import com.ziaee.frenchreader.ui.shared.FindArticleSheet
import com.ziaee.frenchreader.ui.shared.rememberAddTextUiState
import com.ziaee.frenchreader.ui.shared.rememberFilePickerLauncher
import com.ziaee.frenchreader.ui.theme.FrenchReaderDesign

/** Lets instrumented tests scroll Home's content list to a node that's
 * below the initially-composed viewport (LazyColumn only composes visible
 * items) -- see [HomeScreenTest]. */
internal const val HOME_LAZY_COLUMN_TEST_TAG = "home_lazy_column"

/** Fabulang's graded-reader catalog -- opened in the phone's own browser
 * (see the design doc's decision to link out rather than scrape/import:
 * Fabulang's stories aren't openly licensed like Wikisource/Vikidia's). */
/**
 * The app's landing screen: today's cached news, the most recently active
 * document, and a handful of recently added texts -- see the design doc's
 * Home Information Architecture. Opening this screen or a headline preview
 * never downloads a full article body. Sources live state from
 * [HomeViewModel] and delegates rendering to the stateless [HomeContent],
 * which a UI test can drive with a static [HomeUiState] instead.
 */
@Composable
fun HomeScreen(
    onOpenText: (Long) -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenVocab: () -> Unit,
    onOpenStatistics: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenResources: () -> Unit,
    onStartReview: () -> Unit
) {
    val vm: HomeViewModel = viewModel()
    val state by vm.uiState.collectAsState()

    val addTextState = rememberAddTextUiState()
    val context = LocalContext.current
    var showFindArticleSheet by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val genericErrorMessage = stringResource(R.string.error_generic)
    val untitledFallback = stringResource(R.string.text_untitled)
    val epubImportStarted = stringResource(R.string.epub_import_started)
    val epubImportDone = stringResource(R.string.epub_import_done)
    val epubImportAlready = stringResource(R.string.epub_import_already)
    val epubImportFailed = stringResource(R.string.epub_import_failed)
    val importEpub: (Uri) -> Unit = { uri ->
        Toast.makeText(context, epubImportStarted, Toast.LENGTH_SHORT).show()
        vm.importEpub(
            uri,
            onDone = { result ->
                val message = if (result.importedCount == 0) {
                    epubImportAlready.format(result.bookTitle)
                } else {
                    epubImportDone.format(result.importedCount, result.bookTitle)
                }
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                result.firstTextId?.let(onOpenText)
            },
            onError = { Toast.makeText(context, epubImportFailed, Toast.LENGTH_LONG).show() }
        )
    }
    val openFilePicker = rememberFilePickerLauncher(addTextState, onEpub = importEpub)

    LaunchedEffect(vm.contentSearchState) {
        if (vm.contentSearchState is ContentSearchUiState.Error) {
            snackbarHostState.showSnackbar(genericErrorMessage)
            vm.dismissContentSearchError()
        }
    }
    LaunchedEffect(vm.contentImportError) {
        if (vm.contentImportError != null) {
            snackbarHostState.showSnackbar(genericErrorMessage)
            vm.dismissContentImportError()
        }
    }

    HomeContent(
        state = state,
        selectedHeadline = vm.selectedHeadline,
        isPreviewImporting = vm.selectedHeadline?.let { state.importingKey == it.articleUrl } ?: false,
        previewHasError = vm.previewImportError,
        isPreviewAlreadyDownloaded = vm.existingDocumentId != null,
        snackbarHostState = snackbarHostState,
        onSearchClick = { showFindArticleSheet = true },
        onFilePickerClick = openFilePicker,
        onOpenVocab = onOpenVocab,
        onOpenStatistics = onOpenStatistics,
        onOpenSettings = onOpenSettings,
        onOpenLibrary = onOpenLibrary,
        onAddTextClick = { addTextState.openBlank() },
        onOpenGradedReaders = onOpenResources,
        onSelectHeadline = { vm.selectHeadline(it) },
        onRetryNews = { vm.refresh(force = true) },
        onPullRefresh = { vm.refresh(force = true) },
        onOpenText = onOpenText,
        onDownloadOrOpen = { vm.importSelected { id -> onOpenText(id) } },
        onDismissPreview = { vm.dismissPreview() },
        onStartReview = onStartReview
    )

    AddTextHost(addTextState, onEpub = importEpub) { title, body ->
        vm.pasteText(title.ifBlank { untitledFallback }, body) { id -> onOpenText(id) }
    }

    if (showFindArticleSheet) {
        FindArticleSheet(
            searchState = vm.contentSearchState,
            importingRef = vm.contentImportingRef,
            onSearch = { vm.searchContent(it) },
            onSelect = { result -> vm.importTopicResult(result) { id -> onOpenText(id) } },
            onDismiss = { showFindArticleSheet = false; vm.resetContentSearch() }
        )
    }
}

/** Stateless Home layout: everything HomeScreen renders once its ViewModel
 * state is resolved, hoisted out so a UI test can drive it with a static
 * [HomeUiState] instead of a real database-backed ViewModel. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeContent(
    state: HomeUiState,
    selectedHeadline: HeadlineEntity?,
    isPreviewImporting: Boolean,
    previewHasError: Boolean,
    isPreviewAlreadyDownloaded: Boolean,
    snackbarHostState: SnackbarHostState,
    onSearchClick: () -> Unit,
    onFilePickerClick: () -> Unit,
    onOpenVocab: () -> Unit,
    onOpenStatistics: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenLibrary: () -> Unit,
    onAddTextClick: () -> Unit,
    onOpenGradedReaders: () -> Unit,
    onSelectHeadline: (HeadlineEntity) -> Unit,
    onRetryNews: () -> Unit,
    onPullRefresh: () -> Unit,
    onOpenText: (Long) -> Unit,
    onDownloadOrOpen: () -> Unit,
    onDismissPreview: () -> Unit,
    onStartReview: () -> Unit
) {
    val pullToRefreshState = rememberPullToRefreshState()
    if (pullToRefreshState.isRefreshing) {
        LaunchedEffect(true) {
            onPullRefresh()
        }
    }
    LaunchedEffect(state.isRefreshing) {
        if (!state.isRefreshing) pullToRefreshState.endRefresh()
    }
    var moreMenuExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            EditorialTopAppBar(
                title = stringResource(R.string.home_brand_title),
                actions = {
                    IconButton(onClick = onSearchClick) {
                        Icon(Icons.Default.Search, contentDescription = stringResource(R.string.content_search_title))
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.accessibility_settings))
                    }
                    IconButton(onClick = { moreMenuExpanded = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.home_more_actions))
                    }
                    DropdownMenu(expanded = moreMenuExpanded, onDismissRequest = { moreMenuExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.home_action_import_file)) },
                            leadingIcon = { Icon(Icons.Default.FileOpen, contentDescription = null) },
                            onClick = { moreMenuExpanded = false; onFilePickerClick() }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.home_action_vocabulary)) },
                            leadingIcon = { Icon(Icons.Default.MenuBook, contentDescription = null) },
                            onClick = { moreMenuExpanded = false; onOpenVocab() }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.home_action_statistics)) },
                            leadingIcon = { Icon(Icons.Default.BarChart, contentDescription = null) },
                            onClick = { moreMenuExpanded = false; onOpenStatistics() }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.home_action_graded_readers)) },
                            leadingIcon = { Icon(Icons.Default.AutoStories, contentDescription = null) },
                            onClick = { moreMenuExpanded = false; onOpenGradedReaders() }
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            EditorialBottomBar(
                selectedDestination = EditorialDestination.HOME,
                onHome = {},
                onLibrary = onOpenLibrary,
                onAddText = onAddTextClick,
                onResources = onOpenGradedReaders
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).nestedScroll(pullToRefreshState.nestedScrollConnection)) {
            LazyColumn(modifier = Modifier.fillMaxSize().testTag(HOME_LAZY_COLUMN_TEST_TAG)) {
                item {
                    EditorialSearchEntry(
                        text = stringResource(R.string.home_search_hint),
                        onClick = onSearchClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = FrenchReaderDesign.spacing.small, vertical = FrenchReaderDesign.spacing.half)
                    )
                }
                item {
                    LearningSummaryStrip(
                        streakDays = state.streakDays,
                        learnedWordCount = state.learnedWordCount,
                        savedWordCount = state.savedWordCount,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = FrenchReaderDesign.spacing.small, vertical = FrenchReaderDesign.spacing.half)
                    )
                }
                item {
                    TodayNewsSection(
                        headlines = state.headlines,
                        hasPartialError = state.hasPartialError,
                        isEmptyError = state.isEmptyError,
                        onSelect = onSelectHeadline,
                        onRetry = onRetryNews
                    )
                }
                item {
                    DailyReviewCard(
                        dueCount = state.dueReviewCount,
                        onStart = onStartReview,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = FrenchReaderDesign.spacing.small, vertical = FrenchReaderDesign.spacing.half)
                    )
                }
                state.continueReading?.let { doc ->
                    item {
                        ContinueReadingCard(
                            doc = doc,
                            body = state.bodyByTextId[doc.id].orEmpty(),
                            onClick = { onOpenText(doc.id) }
                        )
                    }
                }
                item {
                    RecentTextsSection(
                        documents = state.recentTexts,
                        bodyByTextId = state.bodyByTextId,
                        onOpen = { onOpenText(it.id) },
                        onSeeAll = onOpenLibrary,
                        onAddText = onAddTextClick
                    )
                }
            }
            PullToRefreshContainer(state = pullToRefreshState, modifier = Modifier.align(Alignment.TopCenter))
        }
    }

    selectedHeadline?.let { headline ->
        NewsPreviewSheet(
            headline = headline,
            isImporting = isPreviewImporting,
            hasError = previewHasError,
            isAlreadyDownloaded = isPreviewAlreadyDownloaded,
            onDownloadOrOpen = onDownloadOrOpen,
            onDismiss = onDismissPreview
        )
    }
}
