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
