package com.ziaee.frenchreader.exams

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ziaee.frenchreader.data.AppDatabase
import com.ziaee.frenchreader.data.ExamAttempt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ExamPracticeState(
    val loading: Boolean = true, val questions: ExamQuestionSet? = null, val attempts: List<ExamAttempt> = emptyList(),
    val loadFailed: Boolean = false, val saveFailed: Boolean = false
)

/** The parsed question file, kept for the life of the process: every subject screen needs it and parsing ~900 subjects is not free. */
internal object ExamQuestionCache {
    @Volatile private var cached: Pair<String, ExamQuestionSet>? = null
    fun get(assets: android.content.res.AssetManager, file: String): ExamQuestionSet =
        cached?.takeIf { it.first == file }?.second ?: loadExamQuestions(assets, file).also { cached = file to it }
}

class ExamPracticeViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = AppDatabase.get(app).examAttemptDao()
    val state = MutableStateFlow(ExamPracticeState())
    private var examId: String? = null

    fun load(id: String) {
        if (examId == id) return
        examId = id
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching {
                loadExams(getApplication<Application>().assets).firstOrNull { it.id == id }?.questionsFile?.let {
                    ExamQuestionCache.get(getApplication<Application>().assets, it)
                }
            } }
            dao.observeForExam(id).collect { state.value = state.value.copy(loading = false, questions = result.getOrNull(), attempts = it, loadFailed = result.isFailure) }
        }
    }

    fun save(attempt: ExamAttempt, onFinished: (Boolean) -> Unit) = viewModelScope.launch {
        runCatching { if (attempt.id == 0L) dao.insert(attempt) else dao.update(attempt) }
            .onSuccess { state.value = state.value.copy(saveFailed = false); onFinished(true) }
            .onFailure { state.value = state.value.copy(saveFailed = true); onFinished(false) }
    }
    fun delete(id: Long) = viewModelScope.launch {
        runCatching { dao.deleteById(id) }.onFailure { state.value = state.value.copy(saveFailed = true) }
    }
}
