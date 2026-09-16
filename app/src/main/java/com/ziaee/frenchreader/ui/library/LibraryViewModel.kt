package com.ziaee.frenchreader.ui.library

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ziaee.frenchreader.content.ArticleImportRepository
import com.ziaee.frenchreader.data.AppDatabase
import com.ziaee.frenchreader.data.TextDocument
import com.ziaee.frenchreader.images.ArticleImageStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Owns Library's local query, sort choice, and deletion. Network state has
 * no effect here -- everything is a local Room query over documents
 * already downloaded (see the design doc's Library section).
 */
class LibraryViewModel(app: Application) : AndroidViewModel(app) {
    private val db = AppDatabase.get(app)

    // Library only ever deletes -- it never imports a headline, so no
    // ContentSource needs to be registered here.
    private val importRepository = ArticleImportRepository(db.textDao(), emptyList(), ArticleImageStore(app))

    private val query = MutableStateFlow("")
    private val sort = MutableStateFlow(LibrarySort.NEWEST)

    val uiState: StateFlow<LibraryUiState> = combine(
        db.textDao().observeAll(),
        query,
        sort
    ) { documents, currentQuery, currentSort ->
        LibraryUiState(
            query = currentQuery,
            sort = currentSort,
            documents = projectLibrary(documents, currentQuery, currentSort)
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState())

    fun setQuery(value: String) {
        query.value = value
    }

    fun setSort(value: LibrarySort) {
        sort.value = value
    }

    fun delete(doc: TextDocument) {
        viewModelScope.launch { importRepository.deleteWithImage(doc) }
    }

    /** Persists a pasted/file-imported/shared text -- Library's Add Text
     * entry point mirrors Home's (see [com.ziaee.frenchreader.ui.home.HomeViewModel.pasteText]). */
    fun pasteText(title: String, body: String, onDone: (Long) -> Unit) {
        viewModelScope.launch {
            val id = db.textDao().insert(TextDocument(title = title, rawText = body))
            onDone(id)
        }
    }
}
