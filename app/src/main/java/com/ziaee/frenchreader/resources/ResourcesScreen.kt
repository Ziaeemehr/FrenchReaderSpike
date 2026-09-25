package com.ziaee.frenchreader.resources

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
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

/** Save callback: existing, title, url, image, category, description, level; then onSaved. */
typealias ResourceSave = (ResourceLink?, String, String, String, ResourceCategory, String, String, () -> Unit) -> Unit

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
        onTileViewChange = vm::setTileView,
        onSave = { existing, title, url, image, category, description, level, onSaved ->
            vm.save(existing, title, url, image, onSaved, category, description, level)
        },
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
    onTileViewChange: (Boolean) -> Unit,
    onSave: ResourceSave,
    onDelete: (ResourceLink) -> Unit,
    onDismissError: () -> Unit,
    onOpenHome: () -> Unit,
    onOpenLibrary: () -> Unit,
    onAddText: () -> Unit,
    onWords: () -> Unit = {}
) {
    val context = LocalContext.current
    val language = LocalConfiguration.current.locales[0].language
    var editing by remember { mutableStateOf<ResourceLink?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<ResourceLink?>(null) }
    val open: (ResourceLink) -> Unit = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it.url))) }
    val edit: (ResourceLink) -> Unit = { editing = it; showEditor = true }
    // Group under category headings when showing everything, unsearched.
    val grouped = state.category == null && state.query.isBlank()
    val sections = remember(state.resources, grouped) {
        if (grouped) state.resources.groupBy { ResourceCategory.fromKey(it.category) }.toSortedMap(compareBy { it.ordinal })
        else sortedMapOf<ResourceCategory, List<ResourceLink>>()
    }

    Scaffold(
        topBar = {
            EditorialTopAppBar(
                title = stringResource(R.string.resources_title),
                actions = {
                    IconButton(onClick = { onTileViewChange(!state.tileView) }, modifier = Modifier.testTag("resourcesViewToggle")) {
                        Icon(
                            if (state.tileView) Icons.Default.ViewList else Icons.Default.Apps,
                            contentDescription = stringResource(
                                if (state.tileView) R.string.resources_view_list else R.string.resources_view_tiles
                            )
                        )
                    }
                    IconButton(onClick = { editing = null; showEditor = true }) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.resources_add))
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
            CategoryFilter(
                selected = state.category,
                counts = state.countsByCategory,
                total = state.totalCount,
                onSelect = onCategoryChange,
                modifier = Modifier.padding(horizontal = FrenchReaderDesign.spacing.small)
            )
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
            } else if (state.tileView) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize().testTag("resourcesGrid"),
                    contentPadding = PaddingValues(FrenchReaderDesign.spacing.small),
                    horizontalArrangement = Arrangement.spacedBy(FrenchReaderDesign.spacing.xSmall),
                    verticalArrangement = Arrangement.spacedBy(FrenchReaderDesign.spacing.xSmall)
                ) {
                    if (grouped) {
                        sections.forEach { (category, items) ->
                            item(key = "h_${category.key}", span = { GridItemSpan(maxLineSpan) }) {
                                SectionHeader(category, items.size)
                            }
                            items(items, key = { it.id }) { resource ->
                                ResourceTile(resource, language, { open(resource) }, { edit(resource) }, { deleting = resource })
                            }
                        }
                    } else {
                        items(state.resources, key = { it.id }) { resource ->
                            ResourceTile(resource, language, { open(resource) }, { edit(resource) }, { deleting = resource })
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().testTag("resourcesList"),
                    contentPadding = PaddingValues(vertical = FrenchReaderDesign.spacing.xSmall)
                ) {
                    if (grouped) {
                        sections.forEach { (category, items) ->
                            item(key = "h_${category.key}") {
                                SectionHeader(category, items.size, Modifier.padding(horizontal = FrenchReaderDesign.spacing.small))
                            }
                            items(items, key = { it.id }) { resource ->
                                ResourceRow(resource, language, { open(resource) }, { edit(resource) }, { deleting = resource })
                            }
                        }
                    } else {
                        items(state.resources, key = { it.id }) { resource ->
                            ResourceRow(resource, language, { open(resource) }, { edit(resource) }, { deleting = resource })
                        }
                    }
                }
            }
        }
    }

    if (showEditor) {
        ResourceEditorDialog(
            resource = editing,
            language = language,
            isSaving = state.isSaving,
            error = state.error,
            onDismissError = onDismissError,
            onDismiss = { if (!state.isSaving) showEditor = false },
            onSave = { title, url, image, category, description, level ->
                onSave(editing, title, url, image, category, description, level) { showEditor = false }
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

/** Dropdown replacing the old chip row: "All" plus each category that has resources, with counts. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryFilter(
    selected: ResourceCategory?,
    counts: Map<ResourceCategory, Int>,
    total: Int,
    onSelect: (ResourceCategory?) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val label = selected?.let { "${stringResource(it.labelRes)} (${counts[it] ?: 0})" }
        ?: "${stringResource(R.string.resources_category_all)} ($total)"
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = label,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(stringResource(R.string.resources_category)) },
            leadingIcon = { Icon(selected?.icon ?: Icons.Default.Bookmark, contentDescription = null) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth().testTag("resourcesCategoryFilter")
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("${stringResource(R.string.resources_category_all)} ($total)") },
                leadingIcon = { Icon(Icons.Default.Bookmark, contentDescription = null) },
                onClick = { onSelect(null); expanded = false }
            )
            ResourceCategory.entries.filter { (counts[it] ?: 0) > 0 || it == selected }.forEach { category ->
                DropdownMenuItem(
                    text = { Text("${stringResource(category.labelRes)} (${counts[category] ?: 0})") },
                    leadingIcon = { Icon(category.icon, contentDescription = null) },
                    onClick = { onSelect(category); expanded = false }
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(category: ResourceCategory, count: Int, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().padding(top = FrenchReaderDesign.spacing.small, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(category.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(stringResource(category.labelRes), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        Text("$count", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Where a link goes: the site's host, or "Web search" / "YouTube search" for search links. */
@Composable
private fun hostOf(url: String): String {
    val uri = runCatching { URI(url) }.getOrNull()
    val host = uri?.host?.removePrefix("www.") ?: return url
    return when {
        host.startsWith("google.") && uri.path == "/search" -> stringResource(R.string.resources_web_search)
        host == "youtube.com" && uri.path == "/results" -> stringResource(R.string.resources_youtube_search)
        host == "t.me" -> "Telegram · @" + uri.path.trim('/')
        else -> host
    }
}

@Composable
private fun LevelBadge(level: String) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(6.dp)) {
        Text(
            level,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

/** The page's image, or the category icon on a tinted background. */
@Composable
private fun ResourceImage(resource: ResourceLink, modifier: Modifier) {
    val category = ResourceCategory.fromKey(resource.category)
    Box(modifier.background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) {
        Icon(category.icon, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
        if (resource.imageUrl != null) {
            // Book covers are shown whole, as a small cover; site images fill the frame.
            val cover = category == ResourceCategory.TEXTBOOKS
            AsyncImage(
                model = resource.imageUrl,
                contentDescription = null,
                contentScale = if (cover) ContentScale.Fit else ContentScale.Crop,
                modifier = if (cover) Modifier.fillMaxSize().padding(6.dp) else Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun ResourceMenu(resource: ResourceLink, onEdit: () -> Unit, onDelete: () -> Unit) {
    var menuExpanded by remember { mutableStateOf(false) }
    Box {
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

@Composable
private fun ResourceTile(
    resource: ResourceLink,
    language: String,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val description = resourceDescription(resource, language)
    val level = resourceLevel(resource)
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen)) {
        Column {
            ResourceImage(resource, Modifier.fillMaxWidth().aspectRatio(16f / 9f))
            Box(modifier = Modifier.fillMaxWidth().padding(start = 12.dp, top = 8.dp, bottom = 10.dp)) {
                Column(modifier = Modifier.padding(end = 40.dp)) {
                    Text(resource.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (level != null) {
                        Spacer(Modifier.height(4.dp))
                        LevelBadge(level)
                    }
                }
                Box(modifier = Modifier.align(Alignment.TopEnd)) { ResourceMenu(resource, onEdit, onDelete) }
            }
            if (description != null) {
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            }
            Text(
                hostOf(resource.url),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 10.dp)
            )
        }
    }
}

@Composable
private fun ResourceRow(
    resource: ResourceLink,
    language: String,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val description = resourceDescription(resource, language)
    val level = resourceLevel(resource)
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpen)
                .padding(start = FrenchReaderDesign.spacing.small, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.Top
        ) {
            ResourceImage(resource, Modifier.size(64.dp).clip(RoundedCornerShape(10.dp)))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(resource.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (description != null) {
                    Text(
                        description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (level != null) {
                        LevelBadge(level)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(
                        hostOf(resource.url),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            ResourceMenu(resource, onEdit, onDelete)
        }
        HorizontalDivider(Modifier.padding(start = FrenchReaderDesign.spacing.small + 76.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ResourceEditorDialog(
    resource: ResourceLink?,
    language: String,
    isSaving: Boolean,
    error: ResourceSaveError?,
    onDismissError: () -> Unit,
    onDismiss: () -> Unit,
    onSave: (String, String, String, ResourceCategory, String, String) -> Unit
) {
    var title by remember(resource?.id) { mutableStateOf(resource?.title.orEmpty()) }
    var url by remember(resource?.id) { mutableStateOf(resource?.url.orEmpty()) }
    var image by remember(resource?.id) { mutableStateOf(resource?.imageUrl.orEmpty()) }
    var description by remember(resource?.id) { mutableStateOf(resource?.description.orEmpty()) }
    var level by remember(resource?.id) { mutableStateOf(resource?.level.orEmpty()) }
    var category by remember(resource?.id) {
        mutableStateOf(resource?.let { ResourceCategory.fromKey(it.category) } ?: ResourceCategory.OTHER)
    }
    var categoryMenu by remember { mutableStateOf(false) }
    // Built-ins show their catalog text until the user writes their own.
    val builtIn = resource?.let { defaultResourceFor(it.url) }
    LaunchedEffect(url, title, image, category) { if (error != null) onDismissError() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (resource == null) R.string.resources_add else R.string.resources_edit)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                ExposedDropdownMenuBox(expanded = categoryMenu, onExpandedChange = { categoryMenu = it }) {
                    OutlinedTextField(
                        value = stringResource(category.labelRes),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.resources_category)) },
                        leadingIcon = { Icon(category.icon, contentDescription = null) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryMenu) },
                        modifier = Modifier.menuAnchor()
                    )
                    ExposedDropdownMenu(expanded = categoryMenu, onDismissRequest = { categoryMenu = false }) {
                        ResourceCategory.entries.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(stringResource(option.labelRes)) },
                                leadingIcon = { Icon(option.icon, contentDescription = null) },
                                onClick = { category = option; categoryMenu = false }
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = level,
                    onValueChange = { level = it },
                    label = { Text(stringResource(R.string.resources_level_optional)) },
                    placeholder = { Text(builtIn?.level ?: "A2–B1") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(R.string.resources_description_optional)) },
                    placeholder = {
                        builtIn?.description?.let { Text(it[language] ?: it["en"].orEmpty(), maxLines = 2) }
                            ?: Text(stringResource(R.string.resources_description_hint))
                    },
                    minLines = 2,
                    maxLines = 4
                )
                OutlinedTextField(
                    value = image,
                    onValueChange = { image = it },
                    label = { Text(stringResource(R.string.resources_image_optional)) },
                    singleLine = true
                )
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
            TextButton(enabled = !isSaving, onClick = { onSave(title, url, image, category, description, level) }) {
                if (isSaving) CircularProgressIndicator(modifier = Modifier.height(20.dp))
                else Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(enabled = !isSaving, onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}
