package com.ziaee.frenchreader.shadowing

import com.ziaee.frenchreader.data.ShadowAttempt
import kotlin.math.roundToInt

data class WeakSentence(val ref: SentenceRef, val accuracyPercent: Int)

data class ShadowTextSummary(
    val sentences: Int,
    val accuracyPercent: Int,
    val averagePaceRatio: Float?,
    val goodPacePercent: Int?,
    val weakest: List<WeakSentence>
)

private const val MAX_WEAKEST = 3

/** End-of-session summary for one text: the latest attempt per sentence counts. */
fun shadowTextSummary(attempts: List<ShadowAttempt>): ShadowTextSummary? {
    if (attempts.isEmpty()) return null
    val latest = attempts
        .groupBy { SentenceRef(it.chunkIndex, it.sentenceIndex) }
        .mapValues { (_, list) -> list.maxWith(compareBy({ it.timestampMs }, { it.id })) }
    val words = latest.values.sumOf { it.total }
    val accuracy = if (words == 0) 0 else (latest.values.sumOf { it.matched } * 100f / words).roundToInt()
    val paces = latest.values.mapNotNull { it.paceRatio }
    val weakest = latest
        .filter { (_, a) -> a.total > 0 && a.matched < a.total }
        .map { (ref, a) -> WeakSentence(ref, (a.matched * 100f / a.total).roundToInt()) }
        .sortedWith(compareBy({ it.accuracyPercent }, { it.ref.chunkIndex }, { it.ref.sentenceIndex }))
        .take(MAX_WEAKEST)
    return ShadowTextSummary(
        sentences = latest.size,
        accuracyPercent = accuracy,
        averagePaceRatio = if (paces.isEmpty()) null else paces.average().toFloat(),
        goodPacePercent = if (paces.isEmpty()) null
            else (paces.count { PaceAnalyzer.rate(it) == PaceRating.GOOD } * 100f / paces.size).roundToInt(),
        weakest = weakest
    )
}
