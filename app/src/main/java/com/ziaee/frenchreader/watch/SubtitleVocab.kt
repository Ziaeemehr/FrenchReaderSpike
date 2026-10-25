package com.ziaee.frenchreader.watch

import com.ziaee.frenchreader.content.SavedVocab
import com.ziaee.frenchreader.content.VocabHighlighter
import com.ziaee.frenchreader.content.VocabMatch
import com.ziaee.frenchreader.data.VocabStatus

/** Use the reader's normalization, phrase precedence and French elision boundaries. */
fun subtitleVocabMatches(text: String, statuses: Map<String, VocabStatus>): List<VocabMatch> =
    VocabHighlighter(statuses.map { SavedVocab(it.key, it.value) }).findMatches(text)
