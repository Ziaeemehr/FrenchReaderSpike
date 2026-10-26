package com.ziaee.frenchreader.watch

internal fun transcriptSelectionRange(words: List<IntRange>, anchor: Int, target: Int): IntRange =
    words[minOf(anchor, target)].first..words[maxOf(anchor, target)].last
