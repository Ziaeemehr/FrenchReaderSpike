package com.ziaee.frenchreader.exams

import com.ziaee.frenchreader.data.ExamAttempt
import java.text.Normalizer
import java.util.Locale

data class ExamQuestionProgress(val attempts: Int, val bestScore: Double?, val lastScore: Double?, val lastAtMs: Long)

fun examProgress(attempts: List<ExamAttempt>): Map<String, ExamQuestionProgress> =
    attempts.sortedWith(compareBy<ExamAttempt> { it.createdAtMs }.thenBy { it.id }).groupBy { it.questionId }.mapValues { (_, rows) ->
        ExamQuestionProgress(rows.size, rows.mapNotNull { it.score }.maxOrNull(), rows.last().score, rows.last().createdAtMs)
    }

enum class ExamQuestionSort { FREQUENT, RECENT, WEAKEST }
enum class ExamQuestionStatus { ALL, TODO, DONE }

private fun searchable(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFD)
    .replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT)

fun filterExamQuestions(items: List<ExamQuestionItem>, progress: Map<String, ExamQuestionProgress>, query: String,
                        status: ExamQuestionStatus, sort: ExamQuestionSort): List<ExamQuestionItem> {
    val needle = searchable(query.trim())
    val filtered = items.filter { item ->
        val done = (progress[item.id]?.attempts ?: 0) > 0
        (status == ExamQuestionStatus.ALL || (status == ExamQuestionStatus.DONE) == done) &&
            (needle.isBlank() || item.texts.any { needle in searchable(it.fr) || needle in searchable(it.fa) })
    }
    return when (sort) {
        ExamQuestionSort.FREQUENT -> filtered
        ExamQuestionSort.RECENT -> filtered.sortedWith(compareByDescending<ExamQuestionItem> { it.lastSeen }.thenByDescending { it.count })
        ExamQuestionSort.WEAKEST -> filtered.sortedWith(compareBy<ExamQuestionItem> {
            val p = progress[it.id]
            when { p == null || p.attempts == 0 -> 2; p.lastScore == null -> 1; else -> 0 }
        }.thenBy { progress[it.id]?.lastScore ?: 0.0 })
    }
}

fun frenchWordCount(text: String): Int = text.split(Regex("\\s+")).count { token -> token.any { it.isLetterOrDigit() } }

data class ExamScoreResult(val value: Double?, val valid: Boolean)
fun parseScore(text: String): ExamScoreResult {
    if (text.isBlank()) return ExamScoreResult(null, true)
    val normalized = text.trim().map { char ->
        when { char == ',' || char == '٫' || char == '،' -> '.'; char.isDigit() -> Character.digit(char, 10).digitToChar(); else -> char }
    }.joinToString("")
    val value = normalized.takeIf { it.matches(Regex("[0-9]+(?:\\.[0-9]+)?")) }?.toDoubleOrNull()
    return ExamScoreResult(value?.takeIf { it.isFinite() && it in 0.0..20.0 }, value != null && value.isFinite() && value in 0.0..20.0)
}
fun formatScore(value: Double): String = java.math.BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()
