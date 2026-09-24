package com.ziaee.frenchreader.ui

import android.app.Application
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ziaee.frenchreader.R
import com.ziaee.frenchreader.content.DatasetDeckPack
import com.ziaee.frenchreader.content.DatasetInstallResult
import com.ziaee.frenchreader.content.DatasetManifest
import com.ziaee.frenchreader.content.DatasetPackStatus
import com.ziaee.frenchreader.content.DatasetRepository
import com.ziaee.frenchreader.content.DatasetStatus
import com.ziaee.frenchreader.content.DatasetStoryPack
import com.ziaee.frenchreader.data.AppDatabase
import kotlinx.coroutines.launch

class DatasetViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = DatasetRepository(app, AppDatabase.get(app))

    var manifest by mutableStateOf<DatasetManifest?>(null)
        private set
    var status by mutableStateOf<DatasetStatus?>(null)
        private set
    var busy by mutableStateOf<String?>(null)
        private set
    var result by mutableStateOf<DatasetInstallResult?>(null)
        private set
    var failed by mutableStateOf(false)
        private set

    init {
        refresh()
    }

    private fun refresh() = viewModelScope.launch {
        manifest = repository.loadManifest()
        status = repository.status(manifest)
    }

    fun addDeck(pack: DatasetDeckPack) = run(pack.id) {
        repository.installDeck(pack)
    }

    fun addStories(pack: DatasetStoryPack) = run(pack.id) {
        repository.installStories(pack)
    }

    fun addAll() = run(ALL_PACKS_ID) {
        repository.installAll(requireNotNull(manifest))
    }

    private fun run(id: String, block: suspend () -> DatasetInstallResult) {
        if (busy != null) return
        busy = id
        viewModelScope.launch {
            try {
                result = block()
                status = repository.status(requireNotNull(manifest))
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.e("DatasetViewModel", "Dataset install failed", error)
                failed = true
            } finally {
                busy = null
            }
        }
    }

    fun consumeResult() {
        result = null
        failed = false
    }

    private companion object {
        const val ALL_PACKS_ID = "all"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatasetScreen(onBack: () -> Unit) {
    val viewModel: DatasetViewModel = viewModel()
    val snackbarHostState = remember { SnackbarHostState() }
    val resultMessage = stringResource(R.string.dataset_added_result)
    val errorMessage = stringResource(R.string.error_generic)
    LaunchedEffect(viewModel.failed) {
        if (viewModel.failed) {
            snackbarHostState.showSnackbar(errorMessage)
            viewModel.consumeResult()
        }
    }
    LaunchedEffect(viewModel.result) {
        viewModel.result?.let { result ->
            snackbarHostState.showSnackbar(resultMessage.format(result.added, result.skipped))
            viewModel.consumeResult()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.dataset_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            stringResource(R.string.accessibility_back)
                        )
                    }
                },
                actions = {
                    TextButton(
                        onClick = viewModel::addAll,
                        enabled = viewModel.busy == null
                    ) {
                        Text(stringResource(R.string.dataset_add_all))
                    }
                }
            )
        }
    ) { padding ->
        val manifest = viewModel.manifest
        if (manifest == null) {
            Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Text(
                        stringResource(R.string.dataset_subtitle),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                item {
                    Text(
                        stringResource(R.string.dataset_decks),
                        style = MaterialTheme.typography.titleLarge
                    )
                }
                items(manifest.decks, key = { it.id }) { pack ->
                    DatasetItem(
                        name = pack.name,
                        level = pack.level,
                        count = stringResource(R.string.dataset_cards_count, pack.count),
                        status = viewModel.status?.decks?.get(pack.id),
                        busy = viewModel.busy == pack.id,
                        enabled = viewModel.busy == null,
                        onAdd = { viewModel.addDeck(pack) }
                    )
                }
                item {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.dataset_stories),
                        style = MaterialTheme.typography.titleLarge
                    )
                }
                items(manifest.stories, key = { it.id }) { pack ->
                    DatasetItem(
                        name = pack.name,
                        level = pack.level,
                        count = stringResource(R.string.dataset_stories_count, pack.count),
                        status = viewModel.status?.stories?.get(pack.id),
                        busy = viewModel.busy == pack.id,
                        enabled = viewModel.busy == null,
                        onAdd = { viewModel.addStories(pack) }
                    )
                }
            }
        }
    }
}

@Composable
private fun DatasetItem(
    name: String,
    level: String,
    count: String,
    status: DatasetPackStatus?,
    busy: Boolean,
    enabled: Boolean,
    onAdd: () -> Unit
) {
    Card {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleMedium)
                Text("$level · $count", style = MaterialTheme.typography.bodySmall)
            }
            if (busy) {
                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
            } else {
                Button(onClick = onAdd, enabled = enabled && status?.complete != true) {
                    val label = when {
                        status?.complete == true -> R.string.dataset_added
                        (status?.installed ?: 0) > 0 -> R.string.dataset_add_missing
                        else -> R.string.dataset_add
                    }
                    Text(stringResource(label))
                }
            }
        }
    }
}
