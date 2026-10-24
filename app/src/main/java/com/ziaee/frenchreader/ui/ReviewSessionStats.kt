package com.ziaee.frenchreader.ui

import com.ziaee.frenchreader.data.VocabAnswer

internal data class ReviewSessionStats(
    val answerCount: Int = 0,
    val correctCount: Int = 0,
    val movedForward: Int = 0,
    val returnedToBoxOne: Int = 0
)

internal fun ReviewSessionStats.after(answer: VocabAnswer, boxBefore: Int, boxAfter: Int) = copy(
    answerCount = answerCount + 1,
    correctCount = correctCount + if (answer != VocabAnswer.FORGOT) 1 else 0,
    movedForward = movedForward + if (boxAfter > boxBefore) 1 else 0,
    returnedToBoxOne = returnedToBoxOne + if (answer == VocabAnswer.FORGOT && boxBefore > 1) 1 else 0
)

internal fun reviewStudyTimeByDate(
    startMs: Long, endMs: Long, zone: java.time.ZoneId = java.time.ZoneId.systemDefault()
): Map<String, Long> {
    val result = linkedMapOf<String, Long>()
    var cursor = startMs
    while (cursor < endMs) {
        val date = java.time.Instant.ofEpochMilli(cursor).atZone(zone).toLocalDate()
        val nextMidnight = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val end = minOf(endMs, nextMidnight)
        result[date.toString()] = end - cursor
        cursor = end
    }
    return result
}
