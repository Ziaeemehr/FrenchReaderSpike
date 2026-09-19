package com.ziaee.frenchreader.ui.library

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ziaee.frenchreader.content.ArticleImportRepository
import com.ziaee.frenchreader.content.EpubImportRepository
import com.ziaee.frenchreader.content.EpubImportResult
import com.ziaee.frenchreader.data.AppDatabase
import com.ziaee.frenchreader.data.LibraryFolder
import com.ziaee.frenchreader.data.LibraryTag
import com.ziaee.frenchreader.data.TextDocument
import com.ziaee.frenchreader.data.TextBodyStore
import com.ziaee.frenchreader.data.insertTextDocument
import com.ziaee.frenchreader.data.updateTextDocumentBody
import com.ziaee.frenchreader.images.ArticleImageStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.ziaee.frenchreader.ui.statistics.isTextCompleted

/**
 * Owns Library's local query, sort choice, and deletion. Network state has
 * no effect here -- everything is a local Room query over documents
 * already downloaded (see the design doc's Library section).
 */
class LibraryViewModel(app: Application) : AndroidViewModel(app) {
    private val db = AppDatabase.get(app)
    private val bodyStore = TextBodyStore(app)
    private val epubImportRepository = EpubImportRepository(app, db)

    // Library only ever deletes -- it never imports a headline, so no
    // ContentSource needs to be registered here.
    private val organizerDao = db.libraryOrganizerDao()
    private val importRepository = ArticleImportRepository(
        db.textDao(), emptyList(), ArticleImageStore(app), bodyStore, organizerDao
    )

    private val query = MutableStateFlow("")
    private val sort = MutableStateFlow(LibrarySort.NEWEST)
    private val folderFilter = MutableStateFlow<FolderFilter>(FolderFilter.All)
    private val selectedTagIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedIds = MutableStateFlow<Set<Long>>(emptySet())

    init {
        viewModelScope.launch {
            db.textDao().observeAll().collectLatest { documents ->
                selectedIds.value = prunedSelection(selectedIds.value, documents.mapTo(mutableSetOf()) { it.id })
            }
        }
    }

    private data class LibraryData(
        val documents: List<TextDocument>,
        val completionByTextId: Map<Long, Boolean>,
        val folders: List<LibraryFolder>,
        val tags: List<LibraryTag>,
        val tagIdsByText: Map<Long, Set<Long>>
    )

    private data class DocumentsWithCompletion(
        val documents: List<TextDocument>,
        val completionByTextId: Map<Long, Boolean>
    )

    private val documentsWithCompletion = db.textDao().observeAll().mapLatest { documents ->
        DocumentsWithCompletion(
            documents,
            documents.associate { document ->
                document.id to isTextCompleted(document, bodyStore.read(document))
            }
        )
    }

    private data class FilterSettings(
        val query: String,
        val sort: LibrarySort,
        val folder: FolderFilter,
        val tagIds: Set<Long>
    )

    private val libraryData = combine(
        documentsWithCompletion,
        organizerDao.observeFolders(),
        organizerDao.observeTags(),
        organizerDao.observeAllTagRefs()
    ) { documentData, folders, tags, refs ->
        LibraryData(documentData.documents, documentData.completionByTextId, folders, tags, refs.groupBy { it.textId }.mapValues { entry ->
            entry.value.mapTo(mutableSetOf()) { it.tagId }
        })
    }

    private val filterSettings = combine(query, sort, folderFilter, selectedTagIds) {
            currentQuery, currentSort, currentFolder, currentTags ->
        FilterSettings(currentQuery, currentSort, currentFolder, currentTags)
    }

    val uiState: StateFlow<LibraryUiState> = combine(libraryData, filterSettings, selectedIds) { data, filters, selection ->
        LibraryUiState(
            query = filters.query,
            sort = filters.sort,
            documents = projectLibrary(
                documents = data.documents,
                query = filters.query,
                sort = filters.sort,
                folderFilter = filters.folder,
                selectedTagIds = filters.tagIds,
                tagIdsByText = data.tagIdsByText,
                folders = data.folders
            ),
            folders = data.folders,
            tags = data.tags,
            tagIdsByText = data.tagIdsByText,
            completionByTextId = data.completionByTextId,
            folderFilter = filters.folder,
            selectedTagIds = filters.tagIds,
            selectedIds = selection,
            totalDocumentCount = data.documents.size
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState())

    fun setQuery(value: String) {
        query.value = value
    }

    fun setSort(value: LibrarySort) {
        sort.value = value
    }

    fun setFolderFilter(value: FolderFilter) {
        folderFilter.value = value
    }

    fun toggleTagFilter(id: Long) {
        selectedTagIds.value = selectedTagIds.value.toMutableSet().apply {
            if (!add(id)) remove(id)
        }
    }

    fun toggleSelection(id: Long) {
        selectedIds.value = toggleSelection(selectedIds.value, id)
    }

    fun startSelection(id: Long) {
        selectedIds.value = selectedIds.value + id
    }

    fun selectAll(visibleIds: Set<Long>) {
        selectedIds.value = selectAllVisible(selectedIds.value, visibleIds)
    }

    fun clearSelection() {
        selectedIds.value = emptySet()
    }

    fun deleteSelected() {
        val ids = selectedIds.value.toList()
        viewModelScope.launch {
            ids.forEach { id -> db.textDao().getById(id)?.let { importRepository.deleteWithImage(it) } }
            clearSelection()
        }
    }

    fun moveSelectedToFolder(folderId: Long?) {
        val ids = selectedIds.value.toList()
        viewModelScope.launch {
            if (ids.isNotEmpty()) db.textDao().setFolders(ids, folderId)
            clearSelection()
        }
    }

    fun addTagsToSelected(tagIds: Set<Long>) {
        val ids = selectedIds.value.toList()
        viewModelScope.launch {
            if (ids.isNotEmpty() && tagIds.isNotEmpty()) organizerDao.addTagsToTexts(ids, tagIds.toList())
            clearSelection()
        }
    }

    fun createFolderAndMoveSelected(name: String) {
        val cleanName = name.trim()
        if (cleanName.isBlank()) return
        val ids = selectedIds.value.toList()
        viewModelScope.launch {
            val folderId = organizerDao.findFolderByName(cleanName)?.id
                ?: organizerDao.insertFolder(LibraryFolder(name = cleanName))
            if (ids.isNotEmpty()) db.textDao().setFolders(ids, folderId)
            clearSelection()
        }
    }

    fun createTagAndAddToSelected(name: String, selectedTagIds: Set<Long>) {
        val cleanName = name.trim()
        if (cleanName.isBlank()) return
        val ids = selectedIds.value.toList()
        viewModelScope.launch {
            val existingId = organizerDao.findTagByName(cleanName)?.id
            val insertedId = existingId ?: organizerDao.insertTag(LibraryTag(name = cleanName))
            val tagId = if (insertedId >= 0) insertedId else organizerDao.findTagByName(cleanName)?.id ?: return@launch
            if (ids.isNotEmpty()) organizerDao.addTagsToTexts(ids, (selectedTagIds + tagId).toList())
            clearSelection()
        }
    }

    fun createFolder(name: String, parentId: Long? = null) {
        val cleanName = name.trim()
        if (cleanName.isBlank()) return
        viewModelScope.launch {
            if (organizerDao.findFolderByName(cleanName, parentId) == null) {
                organizerDao.insertFolder(LibraryFolder(name = cleanName, parentId = parentId))
            }
        }
    }

    fun renameFolder(id: Long, name: String) {
        val cleanName = name.trim()
        if (cleanName.isBlank()) return
        viewModelScope.launch {
            val folder = uiState.value.folders.firstOrNull { it.id == id } ?: return@launch
            val existing = organizerDao.findFolderByName(cleanName, folder.parentId)
            if (existing == null || existing.id == id) organizerDao.renameFolder(id, cleanName)
        }
    }

    fun moveFolder(id: Long, newParentId: Long?) {
        viewModelScope.launch {
            val folders = uiState.value.folders
            val folder = folders.firstOrNull { it.id == id } ?: return@launch
            if (!canMoveFolder(folders, id, newParentId)) return@launch
            val duplicate = organizerDao.findFolderByName(folder.name, newParentId)
            if (duplicate == null || duplicate.id == id) organizerDao.moveFolder(id, newParentId)
        }
    }

    fun deleteFolder(id: Long) {
        if (folderFilter.value == FolderFilter.Folder(id)) {
            val parentId = uiState.value.folders.firstOrNull { it.id == id }?.parentId
            folderFilter.value = parentId?.let(FolderFilter::Folder) ?: FolderFilter.All
        }
        viewModelScope.launch { organizerDao.deleteFolder(id) }
    }

    fun createTag(name: String) {
        val cleanName = name.trim()
        if (cleanName.isBlank()) return
        viewModelScope.launch {
            if (organizerDao.findTagByName(cleanName) == null) {
                organizerDao.insertTag(LibraryTag(name = cleanName))
            }
        }
    }

    fun renameTag(id: Long, name: String) {
        val cleanName = name.trim()
        if (cleanName.isBlank()) return
        viewModelScope.launch {
            val existing = organizerDao.findTagByName(cleanName)
            if (existing == null || existing.id == id) organizerDao.renameTag(id, cleanName)
        }
    }

    fun deleteTag(id: Long) {
        selectedTagIds.value = selectedTagIds.value - id
        viewModelScope.launch { organizerDao.deleteTag(id) }
    }

    fun moveToFolder(doc: TextDocument, folderId: Long?) {
        viewModelScope.launch { db.textDao().setFolder(doc.id, folderId) }
    }

    fun setTags(doc: TextDocument, tagIds: Set<Long>) {
        viewModelScope.launch { organizerDao.setTextTags(doc.id, tagIds.toList()) }
    }

    fun createFolderAndMove(doc: TextDocument, name: String, parentId: Long? = null) {
        val cleanName = name.trim()
        if (cleanName.isBlank()) return
        viewModelScope.launch {
            val folderId = organizerDao.findFolderByName(cleanName, parentId)?.id
                ?: organizerDao.insertFolder(LibraryFolder(name = cleanName, parentId = parentId))
            db.textDao().setFolder(doc.id, folderId)
        }
    }

    fun createTagAndAssign(doc: TextDocument, name: String, selectedIds: Set<Long>) {
        val cleanName = name.trim()
        if (cleanName.isBlank()) return
        viewModelScope.launch {
            val existingId = organizerDao.findTagByName(cleanName)?.id
            val insertedId = if (existingId == null) {
                organizerDao.insertTag(LibraryTag(name = cleanName))
            } else {
                existingId
            }
            val tagId = if (insertedId >= 0) insertedId
                else organizerDao.findTagByName(cleanName)?.id ?: return@launch
            organizerDao.setTextTags(doc.id, (selectedIds + tagId).toList())
        }
    }

    fun delete(doc: TextDocument) {
        viewModelScope.launch { importRepository.deleteWithImage(doc) }
    }

    fun updateText(doc: TextDocument, title: String, body: String) {
        viewModelScope.launch {
            if (doc.bodyPath != null) {
                db.textDao().update(doc.copy(title = title.ifBlank { doc.title }))
            } else {
                updateTextDocumentBody(db.textDao(), bodyStore, doc, title, body)
            }
        }
    }

    fun loadBody(doc: TextDocument, onLoaded: (String) -> Unit) {
        viewModelScope.launch { onLoaded(bodyStore.read(doc)) }
    }

    /** Persists a pasted/file-imported/shared text -- Library's Add Text
     * entry point mirrors Home's (see [com.ziaee.frenchreader.ui.home.HomeViewModel.pasteText]). */
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
                val result = epubImportRepository.import(uri)
                onDone(result)
            } catch (error: Exception) {
                android.util.Log.w("EpubImport", "EPUB import failed", error)
                onError(error.message.orEmpty())
            }
        }
    }
}
