package com.ziaee.frenchreader.ui

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.referentialEqualityPolicy
import androidx.compose.runtime.setValue
import com.ziaee.frenchreader.data.VocabEntry

/** Identity-equality holder: one per card presentation, so AnimatedContent never merges two equal entries.
 *  [entry] is observable so an in-place edit updates the shown card without re-triggering the slide animation. */
internal class CardSlot(entry: VocabEntry) {
    var entry by mutableStateOf(entry)
}

/** Reveal tracking is identity-based, so equal copies must still invalidate Compose state. */
internal fun newRevealedEntryState(): MutableState<VocabEntry?> =
    mutableStateOf(null, referentialEqualityPolicy())
