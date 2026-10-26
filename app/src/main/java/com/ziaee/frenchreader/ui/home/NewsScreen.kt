package com.ziaee.frenchreader.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ziaee.frenchreader.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewsScreen(onBack: () -> Unit, onOpenText: (Long) -> Unit) {
    val vm: HomeViewModel = viewModel()
    val state by vm.uiState.collectAsState()
    val refresh = rememberPullToRefreshState()
    if (refresh.isRefreshing) LaunchedEffect(Unit) { vm.refresh(force = true) }
    LaunchedEffect(state.isRefreshing) { if (!state.isRefreshing) refresh.endRefresh() }
    Scaffold(topBar = {
        TopAppBar(title = { Text(stringResource(R.string.home_section_today_news)) }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.accessibility_back)) }
        })
    }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).nestedScroll(refresh.nestedScrollConnection)) {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
                item { NewsStatus(state.hasPartialError, state.isEmptyError, { vm.refresh(force = true) }) }
                items(state.headlines.sortedByDescending { it.publishedAtMs ?: 0L }, key = { it.sourceId + it.externalId }) { headline ->
                    NewsHeadlineRow(headline, onClick = { vm.selectHeadline(headline) })
                }
            }
            PullToRefreshContainer(refresh, Modifier.align(Alignment.TopCenter))
        }
    }
    vm.selectedHeadline?.let { headline ->
        NewsPreviewSheet(
            headline = headline,
            isImporting = state.importingKey == headline.articleUrl,
            hasError = vm.previewImportError,
            isAlreadyDownloaded = vm.existingDocumentId != null,
            onDownloadOrOpen = { vm.importSelected(onOpenText) },
            onDismiss = vm::dismissPreview
        )
    }
}
