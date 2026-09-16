package com.ziaee.frenchreader.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ziaee.frenchreader.R
import com.ziaee.frenchreader.data.HeadlineEntity
import com.ziaee.frenchreader.ui.shared.AddTextHost
import com.ziaee.frenchreader.ui.shared.ContentSearchUiState
import com.ziaee.frenchreader.ui.shared.FindArticleSheet
import com.ziaee.frenchreader.ui.shared.rememberAddTextUiState
import com.ziaee.frenchreader.ui.shared.rememberFilePickerLauncher

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
    onOpenSettings: () -> Unit
) {
    val vm: HomeViewModel = viewModel()
    val state by vm.uiState.collectAsState()

    val addTextState = rememberAddTextUiState()
    var showFindArticleSheet by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val openFilePicker = rememberFilePickerLauncher(addTextState)
    val genericErrorMessage = stringResource(R.string.error_generic)
    val untitledFallback = stringResource(R.string.text_untitled)

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
        onOpenSettings = onOpenSettings,
        onOpenLibrary = onOpenLibrary,
        onAddTextClick = { addTextState.openBlank() },
        onSelectHeadline = { vm.selectHeadline(it) },
        onRetryNews = { vm.refresh(force = true) },
        onPullRefresh = { vm.refresh(force = true) },
        onOpenText = onOpenText,
        onDownloadOrOpen = { vm.importSelected { id -> onOpenText(id) } },
        onDismissPreview = { vm.dismissPreview() }
    )

    AddTextHost(addTextState) { title, body ->
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
    onOpenSettings: () -> Unit,
    onOpenLibrary: () -> Unit,
    onAddTextClick: () -> Unit,
    onSelectHeadline: (HeadlineEntity) -> Unit,
    onRetryNews: () -> Unit,
    onPullRefresh: () -> Unit,
    onOpenText: (Long) -> Unit,
    onDownloadOrOpen: () -> Unit,
    onDismissPreview: () -> Unit
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_home)) },
                actions = {
                    IconButton(onClick = onSearchClick) {
                        Icon(Icons.Default.Search, contentDescription = stringResource(R.string.content_search_title))
                    }
                    IconButton(onClick = onFilePickerClick) {
                        Icon(Icons.Default.FileOpen, contentDescription = stringResource(R.string.accessibility_import_file))
                    }
                    IconButton(onClick = onOpenVocab) {
                        Icon(Icons.Default.MenuBook, contentDescription = stringResource(R.string.accessibility_vocabulary))
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.accessibility_settings))
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = true,
                    onClick = {},
                    icon = { Icon(Icons.Default.Home, contentDescription = null) },
                    label = { Text(stringResource(R.string.nav_home)) }
                )
                NavigationBarItem(
                    selected = false,
                    onClick = onOpenLibrary,
                    icon = { Icon(Icons.Default.LibraryBooks, contentDescription = null) },
                    label = { Text(stringResource(R.string.nav_library)) }
                )
                NavigationBarItem(
                    selected = false,
                    onClick = onAddTextClick,
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    label = { Text(stringResource(R.string.action_add_text)) }
                )
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).nestedScroll(pullToRefreshState.nestedScrollConnection)) {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                item {
                    SearchEntryPoint(onClick = onSearchClick)
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
                state.continueReading?.let { doc ->
                    item {
                        ContinueReadingCard(doc = doc, onClick = { onOpenText(doc.id) })
                    }
                }
                item {
                    RecentTextsSection(
                        documents = state.recentTexts,
                        onOpen = { onOpenText(it.id) },
                        onSeeAll = onOpenLibrary
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

@Composable
private fun SearchEntryPoint(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(12.dp))
        Text(stringResource(R.string.content_search_hint), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
