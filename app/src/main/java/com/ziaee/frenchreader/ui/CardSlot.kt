package com.ziaee.frenchreader.ui

import com.ziaee.frenchreader.data.VocabEntry

/** Identity-equality holder: one per card presentation, so AnimatedContent never merges two equal entries. */
internal class CardSlot(val entry: VocabEntry)
