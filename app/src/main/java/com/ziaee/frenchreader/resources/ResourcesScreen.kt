package com.ziaee.frenchreader.resources

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.ziaee.frenchreader.R
import com.ziaee.frenchreader.data.ResourceLink
import com.ziaee.frenchreader.ui.components.EditorialBottomBar
import com.ziaee.frenchreader.ui.components.EditorialDestination
import com.ziaee.frenchreader.ui.components.EditorialTopAppBar
import com.ziaee.frenchreader.ui.theme.FrenchReaderDesign
import java.net.URI

@Composable
fun ResourcesScreen(
    onOpenHome: () -> Unit,
    onOpenLibrary: () -> Unit,
    onAddText: () -> Unit,
    onWords: () -> Unit = {}
) {
    val vm: ResourcesViewModel = viewModel()
    val state by vm.uiState.collectAsState()
    ResourcesContent(
        state = state,
        onQueryChange = vm::setQuery,
        onCategoryChange = vm::setCategory,
        onSave = vm::save,
        onDelete = vm::delete,
        onDismissError = vm::dismissError,
        onOpenHome = onOpenHome,
        onOpenLibrary = onOpenLibrary,
        onAddText = onAddText,
        onWords = onWords
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResourcesContent(
    state: ResourcesUiState,
    onQueryChange: (String) -> Unit,
    onCategoryChange: (ResourceCategory?) -> Unit,
    onSave: (ResourceLink?, String, String, String, () -> Unit, ResourceCategory) -> Unit,
    onDelete: (ResourceLink) -> Unit,
    onDismissError: () -> Unit,
    onOpenHome: () -> Unit,
    onOpenLibrary: () -> Unit,
    onAddText: () -> Unit,
    onWords: () -> Unit = {}
) {
    val context = LocalContext.current
    var editing by remember { mutableStateOf<ResourceLink?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<ResourceLink?>(null) }

    Scaffold(
        topBar = {
            EditorialTopAppBar(
                title = stringResource(R.string.resources_title),
                actions = {
                    Button(onClick = { editing = null; showEditor = true }) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Text(stringResource(R.string.resources_add))
                    }
                }
            )
        },
        bottomBar = {
            EditorialBottomBar(
                selectedDestination = EditorialDestination.RESOURCES,
                onHome = onOpenHome,
                onLibrary = onOpenLibrary,
                onAddText = onAddText,
                onWords = onWords,
                onResources = {}
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = state.query,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = FrenchReaderDesign.spacing.small, vertical = FrenchReaderDesign.spacing.xSmall),
                placeholder = { Text(stringResource(R.string.resources_search_hint)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                shape = MaterialTheme.shapes.large
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = FrenchReaderDesign.spacing.small),
                horizontalArrangement = Arrangement.spacedBy(FrenchReaderDesign.spacing.xSmall)
            ) {
                FilterChip(
                    selected = state.category == null,
                    onClick = { onCategoryChange(null) },
                    label = { Text(stringResource(R.string.resources_category_all)) }
                )
                ResourceCategory.entries.forEach { category ->
                    FilterChip(
                        selected = state.category == category,
                        onClick = { onCategoryChange(category) },
                        label = { Text(stringResource(category.labelRes)) }
                    )
                }
            }
            if (state.resources.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(FrenchReaderDesign.spacing.large),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Default.Bookmark, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(
                        if (state.query.isBlank() && state.category == null) stringResource(R.string.resources_empty)
                        else stringResource(R.string.resources_no_results),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(top = FrenchReaderDesign.spacing.xSmall)
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(FrenchReaderDesign.spacing.small),
                    horizontalArrangement = Arrangement.spacedBy(FrenchReaderDesign.spacing.xSmall),
                    verticalArrangement = Arrangement.spacedBy(FrenchReaderDesign.spacing.xSmall)
                ) {
                    items(state.resources, key = { it.id }) { resource ->
                        ResourceCard(
                            resource = resource,
                            onOpen = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(resource.url))) },
                            onEdit = { editing = resource; showEditor = true },
                            onDelete = { deleting = resource }
                        )
                    }
                }
            }
        }
    }

    if (showEditor) {
        ResourceEditorDialog(
            resource = editing,
            isSaving = state.isSaving,
            error = state.error,
            onDismissError = onDismissError,
            onDismiss = { if (!state.isSaving) showEditor = false },
            onSave = { title, url, image, category ->
                onSave(editing, title, url, image, { showEditor = false }, category)
            }
        )
    }

    deleting?.let { resource ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.resources_delete_title)) },
            text = { Text(stringResource(R.string.resources_delete_message, resource.title)) },
            confirmButton = {
                TextButton(onClick = { onDelete(resource); deleting = null }) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }
}

@Composable
private fun ResourceCard(
    resource: ResourceLink,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen)) {
        Column {
            if (resource.imageUrl != null) {
                AsyncImage(
                    model = resource.imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().height(104.dp)
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(104.dp)
                        .background(MaterialTheme.colorScheme.secondaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Bookmark, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                }
            }
            Box(modifier = Modifier.fillMaxWidth().padding(start = 12.dp, top = 8.dp, bottom = 10.dp)) {
                Column(modifier = Modifier.padding(end = 42.dp)) {
                    Text(
                        stringResource(ResourceCategory.fromKey(resource.category).labelRes),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(resource.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        runCatching { URI(resource.url).host.removePrefix("www.") }.getOrDefault(resource.url),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Box(modifier = Modifier.align(Alignment.TopEnd)) {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.resources_more_actions, resource.title))
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.resources_edit)) },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                            onClick = { menuExpanded = false; onEdit() }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_delete)) },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                            onClick = { menuExpanded = false; onDelete() }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ResourceEditorDialog(
    resource: ResourceLink?,
    isSaving: Boolean,
    error: ResourceSaveError?,
    onDismissError: () -> Unit,
    onDismiss: () -> Unit,
    onSave: (String, String, String, ResourceCategory) -> Unit
) {
    var title by remember(resource?.id) { mutableStateOf(resource?.title.orEmpty()) }
    var url by remember(resource?.id) { mutableStateOf(resource?.url.orEmpty()) }
    var image by remember(resource?.id) { mutableStateOf(resource?.imageUrl.orEmpty()) }
    var category by remember(resource?.id) {
        mutableStateOf(resource?.let { ResourceCategory.fromKey(it.category) } ?: ResourceCategory.OTHER)
    }
    LaunchedEffect(url, title, image, category) { if (error != null) onDismissError() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (resource == null) R.string.resources_add else R.string.resources_edit)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text(stringResource(R.string.resources_url)) },
                    singleLine = true
                )
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.resources_name_optional)) },
                    singleLine = true
                )
                OutlinedTextField(
                    value = image,
                    onValueChange = { image = it },
                    label = { Text(stringResource(R.string.resources_image_optional)) },
                    singleLine = true
                )
                Text(
                    stringResource(R.string.resources_category),
                    style = MaterialTheme.typography.labelLarge
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ResourceCategory.entries.forEach { option ->
                        FilterChip(
                            selected = category == option,
                            onClick = { category = option },
                            label = { Text(stringResource(option.labelRes)) }
                        )
                    }
                }
                if (error != null) {
                    Text(
                        stringResource(
                            if (error == ResourceSaveError.INVALID_URL) R.string.resources_invalid_url
                            else R.string.resources_save_error
                        ),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(enabled = !isSaving, onClick = { onSave(title, url, image, category) }) {
                if (isSaving) CircularProgressIndicator(modifier = Modifier.height(20.dp))
                else Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(enabled = !isSaving, onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}
