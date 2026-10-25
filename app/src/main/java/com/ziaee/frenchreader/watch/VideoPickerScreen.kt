package com.ziaee.frenchreader.watch

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.ziaee.frenchreader.R
import com.ziaee.frenchreader.ui.components.EditorialTopAppBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoPickerScreen(onBack: () -> Unit, onWatch: (String, String, String) -> Unit) {
    val vm: VideoPickerViewModel = viewModel()
    val state by vm.uiState.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    var level by rememberSaveable { mutableStateOf<String?>(null) }
    var levelMenu by remember { mutableStateOf(false) }
    var linkDialog by remember { mutableStateOf(false) }
    val language = LocalConfiguration.current.locales[0].language
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(vm, lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) vm.refreshRecent() }
        lifecycle.addObserver(observer)
        vm.refreshRecent()
        onDispose { lifecycle.removeObserver(observer) }
    }
    val selected = videoChannels.firstOrNull { channelId(it.url) == state.selectedChannel }
    BackHandler(selected != null) { vm.closeChannel() }
    val search = normalizeVideoSearch(query.trim())
    val channels = searchVideoChannels(videoChannels, query, level, state.feeds)
    if (linkDialog) WatchLinkDialog(onDismiss = { linkDialog = false }, onOpen = {
        linkDialog = false
        onWatch(it, "", "")
    })
    Scaffold(topBar = {
        EditorialTopAppBar(title = selected?.title ?: stringResource(R.string.videos_title),
            navigationIcon = {
                IconButton(onClick = { if (selected != null) vm.closeChannel() else onBack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.watch_back))
                }
            })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(query, { query = it }, modifier = Modifier.weight(1f), singleLine = true,
                    label = { Text(stringResource(R.string.videos_search)) })
                IconButton(onClick = { linkDialog = true }) {
                    Icon(Icons.Default.Link, stringResource(R.string.videos_paste_link))
                }
            }
            Box(Modifier.padding(horizontal = 12.dp)) {
                TextButton(onClick = { levelMenu = true }) {
                    Text(stringResource(R.string.videos_level, level ?: stringResource(R.string.videos_all)))
                }
                DropdownMenu(expanded = levelMenu, onDismissRequest = { levelMenu = false }) {
                    listOf(null, "A1–A2", "B1", "B2", "C1+").forEach { option ->
                        DropdownMenuItem(text = { Text(option ?: stringResource(R.string.videos_all)) }, onClick = {
                            level = option; levelMenu = false
                        })
                    }
                }
            }
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (selected == null) {
                    val recents = state.recent.filter { recent ->
                        val channel = videoChannels.firstOrNull { it.title == recent.channel }
                        videoLevelOverlaps(channel?.level, level) && (search.isBlank() ||
                            normalizeVideoSearch("${recent.title} ${recent.channel}").contains(search))
                    }
                    if (recents.isNotEmpty()) {
                        item { Text(stringResource(R.string.videos_continue), style = MaterialTheme.typography.titleMedium) }
                        items(recents, key = { "recent:${it.videoId}" }) { recent ->
                            RecentVideoRow(recent, onWatch = { onWatch(recent.videoId, recent.title, recent.channel) }, onRemove = { vm.removeRecent(recent.videoId) })
                        }
                    }
                    item { Text(stringResource(R.string.videos_channels), style = MaterialTheme.typography.titleMedium) }
                    items(channels, key = { it.url }) { channel ->
                        Surface(onClick = { vm.openChannel(channelId(channel.url)) }, shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.surfaceContainerLow) {
                            Column(Modifier.fillMaxWidth().padding(10.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(channel.title, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.titleSmall)
                                    channel.level?.let { value ->
                                        Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.small) {
                                            Text(value, Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                                Text(channel.description[language] ?: channel.description["en"].orEmpty(), maxLines = 1,
                                    overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    if (channels.isEmpty()) item { Text(stringResource(R.string.videos_no_results)) }
                } else {
                    if (state.loading) item { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
                    if (state.error) item {
                        Column {
                            Text(stringResource(R.string.videos_feed_error), color = MaterialTheme.colorScheme.error)
                            TextButton(onClick = { vm.openChannel(channelId(selected.url), force = true) }) { Text(stringResource(R.string.videos_retry)) }
                        }
                    }
                    val videos = if (videoLevelOverlaps(selected.level, level))
                        searchChannelVideos(state.feeds[channelId(selected.url)].orEmpty(), query) else emptyList()
                    items(videos, key = { it.videoId }) { video ->
                        VideoRow(video.thumbnail, video.title, video.published.take(10), onClick = {
                            onWatch(video.videoId, video.title, selected.title)
                        })
                    }
                    if (!state.loading && !state.error && videos.isEmpty()) item { Text(stringResource(R.string.videos_no_results)) }
                }
            }
        }
    }
}

@Composable
private fun RecentVideoRow(video: RecentVideo, onWatch: () -> Unit, onRemove: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().clickable(onClick = onWatch), verticalAlignment = Alignment.CenterVertically) {
        val seconds = video.positionMs / 1000
        VideoRow("https://i.ytimg.com/vi/${video.videoId}/mqdefault.jpg", video.title.ifBlank { video.videoId },
            stringResource(R.string.videos_progress, seconds / 60, seconds % 60) +
                video.channel.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty(),
            modifier = Modifier.weight(1f), onClick = onWatch)
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, stringResource(R.string.videos_options)) }
            DropdownMenu(menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text(stringResource(R.string.videos_remove)) }, onClick = { menu = false; onRemove() })
            }
        }
    }
}

@Composable
private fun VideoRow(thumbnail: String, title: String, support: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        AsyncImage(model = thumbnail, contentDescription = null, contentScale = ContentScale.Crop,
            modifier = Modifier.width(112.dp).aspectRatio(16f / 9f))
        Column(Modifier.weight(1f)) {
            Text(title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
            Text(support, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
