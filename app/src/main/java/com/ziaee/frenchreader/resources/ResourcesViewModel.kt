package com.ziaee.frenchreader.resources

import android.app.Application
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
    val category: ResourceCategory? = null
)

enum class ResourceSaveError { INVALID_URL, DUPLICATE_OR_DATABASE }

class ResourcesViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = AppDatabase.get(app).resourceDao()
    private val query = MutableStateFlow("")
    private val saving = MutableStateFlow(false)
    private val error = MutableStateFlow<ResourceSaveError?>(null)
    private val category = MutableStateFlow<ResourceCategory?>(null)

    val uiState = combine(dao.observeAll(), query, saving, error, category) { items, text, busy, problem, selected ->
        ResourcesUiState(filterResources(items, text, selected), text, busy, problem, selected)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ResourcesUiState())

    init {
        viewModelScope.launch {
            if (dao.count() == 0) {
                DEFAULT_RESOURCES.forEach { resource ->
                    dao.insert(
                        ResourceLink(
                            title = resource.title,
                            url = resource.url,
                            createdAtMs = resource.createdAtMs,
                            category = resource.category.key
                        )
                    )
                }
            }
            dao.getWithoutImages().forEach { resource ->
                val metadata = withContext(Dispatchers.IO) {
                    runCatching { fetchResourceMetadata(resource.url) }.getOrNull()
                }
                metadata?.imageUrl?.let { dao.update(resource.copy(imageUrl = it)) }
            }
        }
    }

    fun setQuery(value: String) { query.value = value }
    fun setCategory(value: ResourceCategory?) { category.value = value }
    fun dismissError() { error.value = null }

    fun save(
        existing: ResourceLink?,
        titleInput: String,
        urlInput: String,
        imageInput: String,
        onSaved: () -> Unit,
        category: ResourceCategory = ResourceCategory.OTHER
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
                    category = category.key
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
}
