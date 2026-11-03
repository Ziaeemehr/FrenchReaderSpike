package com.ziaee.frenchreader.ui.pdf

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ziaee.frenchreader.content.pdf.PdfFileStore
import com.ziaee.frenchreader.content.pdf.PdfPageOcr
import com.ziaee.frenchreader.content.pdf.PdfPageRenderer
import com.ziaee.frenchreader.content.pdf.PdfWordLayout
import com.ziaee.frenchreader.translate.TranslationRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PdfReaderViewModel(app: Application) : AndroidViewModel(app) {
    private val preferences = app.getSharedPreferences("pdf_reader", Context.MODE_PRIVATE)
    private val translations = TranslationRepository(app)
    private val _renderer = MutableStateFlow<PdfPageRenderer?>(null)
    val renderer = _renderer.asStateFlow()
    private val _failed = MutableStateFlow(false)
    val failed = _failed.asStateFlow()
    var layout: PdfWordLayout? = null
        private set
    private var loadedPath: String? = null

    fun load(path: String) {
        if (loadedPath == path) return
        loadedPath = path
        viewModelScope.launch {
            val old = _renderer.value
            _renderer.value = null
            old?.close()
            _failed.value = false
            var pending: PdfPageRenderer? = null
            try {
                val file = withContext(Dispatchers.IO) {
                    PdfFileStore.resolve(getApplication(), path) ?: error("Missing PDF")
                }
                val opened = withContext(Dispatchers.IO) { PdfPageRenderer(file) }
                pending = opened
                val pageOcr = PdfPageOcr(getApplication(), opened, file)
                layout = PdfWordLayout(getApplication(), file) { page ->
                    // A failed page OCR leaves the "use Select area" hint instead of a page error.
                    try { pageOcr.words(page) } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled } catch (_: Exception) { emptyList() }
                }
                _renderer.value = pending
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _failed.value = true
            } finally {
                if (_renderer.value !== pending) withContext(NonCancellable) { pending?.close() }
            }
        }
    }

    private val bodyUpdateMutex = kotlinx.coroutines.sync.Mutex()

    suspend fun addRecognizedText(textId: Long, recognized: String) {
        bodyUpdateMutex.lock()
        try {
            val app = getApplication<Application>()
            val dao = com.ziaee.frenchreader.data.AppDatabase.get(app).textDao()
            val doc = dao.getById(textId) ?: error("Document no longer exists")
            val store = com.ziaee.frenchreader.data.TextBodyStore(app)
            // Always read the latest row and body, including file-backed documents.
            val body = store.read(doc)
            com.ziaee.frenchreader.data.updateTextDocumentBody(
                dao, store, doc, doc.title,
                com.ziaee.frenchreader.content.pdf.appendPdfOcrText(body, recognized)
            )
        } finally {
            bodyUpdateMutex.unlock()
        }
    }

    fun lastPage(textId: Long): Int = preferences.getInt("page_$textId", 0).coerceAtLeast(0)
    fun savePage(textId: Long, page: Int, pageCount: Int) {
        preferences.edit().putInt("page_$textId", page).putInt("pageCount_$textId", pageCount).apply()
    }

    suspend fun translate(text: String, before: String?, after: String?, targetLang: String): String? =
        translations.getOrTranslateInContext(listOf(text), before, targetLang, after).let { results ->
            check(results.containsKey(text)) { "Translation unavailable" }
            results[text]
        }

    override fun onCleared() {
        val current = _renderer.value
        // close must complete even though the ViewModel scope has just been cancelled.
        viewModelScope.launch(NonCancellable + Dispatchers.IO) { current?.close() }
    }
}
