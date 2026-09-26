package com.ziaee.frenchreader.resources

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ziaee.frenchreader.data.AppDatabase
import com.ziaee.frenchreader.data.ResourceLink
import java.net.URI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ResourcesUiState(
    val resources: List<ResourceLink> = emptyList(),
    val query: String = "",
    val isSaving: Boolean = false,
    val error: ResourceSaveError? = null,
    val category: ResourceCategory? = null,
    val countsByCategory: Map<ResourceCategory, Int> = emptyMap(),
    val totalCount: Int = 0,
    val tileView: Boolean = true
)

enum class ResourceSaveError { INVALID_URL, DUPLICATE_OR_DATABASE }

class ResourcesViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = AppDatabase.get(app).resourceDao()
    private val query = MutableStateFlow("")
    private val saving = MutableStateFlow(false)
    private val error = MutableStateFlow<ResourceSaveError?>(null)
    private val category = MutableStateFlow<ResourceCategory?>(null)
    private val prefs = app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val tileView = MutableStateFlow(prefs.getBoolean(KEY_TILE_VIEW, true))

    private data class Controls(
        val query: String, val saving: Boolean, val error: ResourceSaveError?,
        val category: ResourceCategory?, val tileView: Boolean
    )

    private val controls = combine(query, saving, error, category, tileView) { text, busy, problem, selected, tiles ->
        Controls(text, busy, problem, selected, tiles)
    }

    val uiState = combine(dao.observeAll(), controls) { items, c ->
        ResourcesUiState(
            resources = filterResources(items, c.query, c.category),
            query = c.query,
            isSaving = c.saving,
            error = c.error,
            category = c.category,
            countsByCategory = items.groupingBy { ResourceCategory.fromKey(it.category) }.eachCount(),
            totalCount = items.size,
            tileView = c.tileView
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ResourcesUiState())

    init {
        viewModelScope.launch {
            // Built-ins ship with the app. Each is added once (its URL is remembered), so an update
            // brings only new ones and a built-in the user deleted never comes back.
            var seeded = prefs.getStringSet(KEY_SEEDED_URLS, emptySet()).orEmpty()
            // Retired built-ins leave the seeded set too, so the cleanup runs only once.
            val retired = seeded intersect RETIRED_RESOURCE_URLS
            if (retired.isNotEmpty()) {
                dao.deleteByUrls(retired)
                seeded = seeded - retired
                prefs.edit().putStringSet(KEY_SEEDED_URLS, seeded).apply()
            }
            val toSeed = catalogEntriesToSeed(seeded)
            if (toSeed.isNotEmpty()) {
                dao.insertIgnoringExisting(
                    toSeed.map { resource ->
                        ResourceLink(
                            title = resource.title,
                            url = resource.url,
                            createdAtMs = resource.createdAtMs,
                            category = resource.category.key,
                            imageUrl = resource.imageUrl
                        )
                    }
                )
                prefs.edit().putStringSet(KEY_SEEDED_URLS, seeded + toSeed.map { it.url }).apply()
            }
            // Look up a page image once per URL (not on every visit), and never for search links.
            val tried = prefs.getStringSet(KEY_IMAGE_TRIED, emptySet()).orEmpty()
            val pending = dao.getWithoutImages().filter { it.url !in tried && wantsImageLookup(it.url) }
            pending.forEach { resource ->
                val metadata = withContext(Dispatchers.IO) {
                    runCatching { fetchResourceMetadata(resource.url) }.getOrNull()
                }
                metadata?.imageUrl?.let { dao.update(resource.copy(imageUrl = it)) }
            }
            if (pending.isNotEmpty()) {
                prefs.edit().putStringSet(KEY_IMAGE_TRIED, tried + pending.map { it.url }).apply()
            }
        }
    }

    fun setQuery(value: String) { query.value = value }
    fun setCategory(value: ResourceCategory?) { category.value = value }
    fun setTileView(value: Boolean) {
        tileView.value = value
        prefs.edit().putBoolean(KEY_TILE_VIEW, value).apply()
    }
    fun dismissError() { error.value = null }

    fun save(
        existing: ResourceLink?,
        titleInput: String,
        urlInput: String,
        imageInput: String,
        onSaved: () -> Unit,
        category: ResourceCategory = ResourceCategory.OTHER,
        descriptionInput: String = "",
        levelInput: String = ""
    ) {
        val url = normalizeResourceUrl(urlInput)
        if (url == null) {
            error.value = ResourceSaveError.INVALID_URL
            return
        }
        viewModelScope.launch {
            saving.value = true
            error.value = null
            try {
                val metadata = withContext(Dispatchers.IO) {
                    runCatching { fetchResourceMetadata(url) }.getOrDefault(ResourceMetadata(null, null))
                }
                val fallbackTitle = runCatching { URI(url).host.removePrefix("www.") }.getOrDefault(url)
                val resource = ResourceLink(
                    id = existing?.id ?: 0,
                    title = titleInput.trim().ifBlank { metadata.title ?: fallbackTitle },
                    url = url,
                    imageUrl = normalizeResourceUrl(imageInput) ?: metadata.imageUrl ?: existing?.imageUrl,
                    createdAtMs = existing?.createdAtMs ?: System.currentTimeMillis(),
                    category = category.key,
                    // A new link gets the page's own description unless one was typed; built-ins
                    // keep showing their catalog text while the field stays empty.
                    description = descriptionInput.trim().ifBlank {
                        if (existing == null && defaultResourceFor(url) == null) metadata.description.orEmpty() else ""
                    }.ifBlank { null },
                    level = levelInput.trim().ifBlank { null }
                )
                if (existing == null) dao.insert(resource) else dao.update(resource)
                onSaved()
            } catch (_: Exception) {
                error.value = ResourceSaveError.DUPLICATE_OR_DATABASE
            } finally {
                saving.value = false
            }
        }
    }

    fun delete(resource: ResourceLink) {
        viewModelScope.launch { dao.delete(resource) }
    }

    private companion object {
        const val PREFS_NAME = "resources"
        const val KEY_SEEDED_URLS = "seeded_urls"
        const val KEY_IMAGE_TRIED = "image_tried_urls"
        const val KEY_TILE_VIEW = "tile_view"
    }
}
