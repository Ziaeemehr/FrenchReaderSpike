package com.ziaee.frenchreader.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.ziaee.frenchreader.R
import com.ziaee.frenchreader.data.VocabStatus
import kotlin.math.ceil

@Composable
fun VocabStatus.color(): Color = color(isSystemInDarkTheme())

fun VocabStatus.color(dark: Boolean): Color {
    return when (this) {
        VocabStatus.NEW -> if (dark) Color(0xFFC4B5FD) else Color(0xFF6D28D9)
        VocabStatus.LEARNING -> if (dark) Color(0xFFFBBF24) else Color(0xFFB45309)
        VocabStatus.REVIEWING -> if (dark) Color(0xFF67E8F9) else Color(0xFF087F8C)
        VocabStatus.LEARNED -> if (dark) Color(0xFF86EFAC) else Color(0xFF15803D)
    }
}

@Composable
fun VocabStatus.containerColor(): Color = containerColor(isSystemInDarkTheme())

fun VocabStatus.containerColor(dark: Boolean): Color {
    return when (this) {
        VocabStatus.NEW -> if (dark) Color(0xFF2E2547) else Color(0xFFF3EEFF)
        VocabStatus.LEARNING -> if (dark) Color(0xFF3D2E18) else Color(0xFFFFF3DC)
        VocabStatus.REVIEWING -> if (dark) Color(0xFF17353A) else Color(0xFFE5F7F8)
        VocabStatus.LEARNED -> if (dark) Color(0xFF193824) else Color(0xFFE8F7EC)
    }
}

@StringRes
fun VocabStatus.labelRes(): Int = when (this) {
    VocabStatus.NEW -> R.string.vocab_status_new
    VocabStatus.LEARNING -> R.string.vocab_status_learning
    VocabStatus.REVIEWING -> R.string.vocab_status_reviewing
    VocabStatus.LEARNED -> R.string.vocab_status_learned
}

@Composable
fun leitnerBoxColor(box: Int): Color {
    val dark = isSystemInDarkTheme()
    return when (box.coerceIn(1, 5)) {
        1 -> if (dark) Color(0xFFFF8A65) else Color(0xFFD84315)
        2 -> if (dark) Color(0xFFFFCA5C) else Color(0xFFCA8A04)
        3 -> if (dark) Color(0xFFBEDE72) else Color(0xFF65A30D)
        4 -> if (dark) Color(0xFF5EEAD4) else Color(0xFF0F8A7B)
        else -> if (dark) Color(0xFF86EFAC) else Color(0xFF15803D)
    }
}

sealed interface DueBucket {
    data object Learned : DueBucket
    data object Today : DueBucket
    data class InDays(val days: Long) : DueBucket
}

fun dueBucket(nextReviewAtMs: Long, nowMs: Long, learned: Boolean = false): DueBucket {
    if (learned) return DueBucket.Learned
    if (nextReviewAtMs <= nowMs) return DueBucket.Today
    val days = ceil((nextReviewAtMs - nowMs).toDouble() / 86_400_000.0).toLong()
    return DueBucket.InDays(days.coerceAtLeast(1))
}
