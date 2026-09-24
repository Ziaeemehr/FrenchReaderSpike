package com.ziaee.frenchreader.comprehension

enum class ComprehensionLevel { EASY, MANAGEABLE, HARD }

fun comprehensionLevel(percent: Int): ComprehensionLevel = when {
    percent >= 95 -> ComprehensionLevel.EASY
    percent >= 85 -> ComprehensionLevel.MANAGEABLE
    else -> ComprehensionLevel.HARD
}
