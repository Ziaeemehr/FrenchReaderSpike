package com.ziaee.frenchreader.watch

/** Offset is relative to leading content padding; tall sentences start at the viewport top. */
internal fun transcriptCenterOffset(viewportHeight: Int, itemHeight: Int, beforePadding: Int = 0): Int =
    beforePadding - ((viewportHeight.coerceAtLeast(0) - itemHeight.coerceAtLeast(0)).coerceAtLeast(0) / 2)

/** Symmetric padding lets even the first and last short items reach the centre. */
internal fun transcriptEdgePadding(viewportHeight: Int, inset: Int): Int =
    (viewportHeight.coerceAtLeast(0) / 2 - inset.coerceAtLeast(0)).coerceAtLeast(0)

/** Leave an unfaded line and the inter-item gap on both sides of the current item. */
internal fun transcriptItemHeight(viewportHeight: Int, lineHeight: Int, fadeHeight: Int, spacing: Int): Int =
    (viewportHeight - 2 * (lineHeight + fadeHeight + spacing)).coerceAtLeast(1)

/** Follow a cue inside a tall sentence, with the first/last cue clamped to the content bounds. */
internal fun transcriptActiveOffset(rangeTop: Int, rangeBottom: Int, viewportHeight: Int, contentHeight: Int): Int =
    ((rangeTop + rangeBottom - viewportHeight) / 2).coerceIn(0, (contentHeight - viewportHeight).coerceAtLeast(0))
