package com.ziaee.frenchreader.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ziaee.frenchreader.text.BlockType
import com.ziaee.frenchreader.text.isRtlText
import com.ziaee.frenchreader.ui.theme.FrenchReaderDesign
import kotlinx.coroutines.yield
import kotlin.math.ceil

internal fun ChunkState.readingDisplayText(): String = text

/** Incrementally measures real Compose line breaks, publishing usable pages as it progresses. */
@Composable
internal fun rememberReadingPages(
    chunks: List<ChunkState>,
    widthPx: Int,
    pageHeightPx: Int,
    fontScale: Float,
    showTranslations: Boolean,
    density: Density,
    geometryThemeKey: Any?
): State<List<ReadingPage>> {
    val textMeasurer = rememberTextMeasurer(cacheSize = 32)
    val mediumSpacing = FrenchReaderDesign.spacing.medium
    val largeSpacing = FrenchReaderDesign.spacing.large
    val headingFontFamily = FrenchReaderDesign.editorialTypography.articleHeadline.fontFamily
    val snapshot = chunks.toList()
    val measurementKey = snapshot.map {
        listOf(it.readingDisplayText(), it.translation, it.translationStatus, it.block.type, it.block.headerLevel)
    }
    return produceState(
        initialValue = emptyList(),
        measurementKey,
        widthPx,
        pageHeightPx,
        fontScale,
        showTranslations,
        density.density,
        density.fontScale,
        geometryThemeKey,
        mediumSpacing,
        largeSpacing,
        headingFontFamily
    ) {
        if (widthPx <= 0 || pageHeightPx <= 0) {
            value = emptyList()
            return@produceState
        }
        val measured = ArrayList<MeasuredReadingChunk>(snapshot.size)
        val horizontalInset = with(density) { mediumSpacing.roundToPx() * 2 }
        val contentWidth = (widthPx - horizontalInset).coerceAtLeast(1)
        val bodyLinePx = with(density) { (31f * fontScale).sp.roundToPx() }

        snapshot.forEachIndexed { index, chunk ->
            val block = chunk.block
            if (block.type == BlockType.IMAGE || block.type == BlockType.TABLE) {
                measured += MeasuredReadingChunk.atomic(index, chunk.text.length, pageHeightPx)
            } else {
                val listInset = if (block.type == BlockType.LIST_ITEM) with(density) { 30.dp.roundToPx() } else 0
                val maxWidth = (contentWidth - listInset).coerceAtLeast(1)
                val fontSize = when (block.type) {
                    BlockType.HEADER -> headerFontSizeForPage(block.headerLevel, fontScale)
                    else -> (19f * fontScale).sp
                }
                val lineHeight = when (block.type) {
                    BlockType.HEADER -> headerLineHeightForPage(block.headerLevel, fontScale)
                    BlockType.LIST_ITEM -> (29f * fontScale).sp
                    else -> (31f * fontScale).sp
                }
                val displayText = chunk.readingDisplayText()
                val layout = textMeasurer.measure(
                    text = displayText,
                    style = TextStyle(
                        fontSize = fontSize,
                        lineHeight = lineHeight,
                        letterSpacing = 0.1.sp,
                        fontWeight = if (block.type == BlockType.HEADER) FontWeight.SemiBold else null,
                        fontFamily = if (block.type == BlockType.HEADER) {
                            headingFontFamily
                        } else null,
                        textAlign = if (block.type == BlockType.PARAGRAPH) TextAlign.Justify else TextAlign.Start,
                        hyphens = Hyphens.None
                    ),
                    constraints = Constraints(maxWidth = maxWidth),
                    layoutDirection = if (isRtlText(displayText)) LayoutDirection.Rtl else LayoutDirection.Ltr
                )
                val topExtra = if (block.type == BlockType.HEADER) with(density) {
                    (if (block.headerLevel == 1) 12.dp else 4.dp).roundToPx()
                } else 0
                val lines = (0 until layout.lineCount).map { line ->
                    MeasuredReadingLine(
                        startOffset = layout.getLineStart(line),
                        endOffset = layout.getLineEnd(line, visibleEnd = false),
                        heightPx = ceil(layout.getLineBottom(line) - layout.getLineTop(line)).toInt() +
                            if (line == 0) topExtra else 0
                    )
                }
                val translationHeight = if (showTranslations) {
                    val translated = when (chunk.translationStatus) {
                        ChunkStatus.READY -> chunk.translation
                        ChunkStatus.LOADING, ChunkStatus.ERROR -> "…"
                        ChunkStatus.PENDING -> null
                    }
                    translated?.let {
                        val translatedLayout = textMeasurer.measure(
                            text = it,
                            style = TextStyle(
                                fontSize = (14f * fontScale).sp,
                                lineHeight = (21f * fontScale).sp,
                                fontStyle = FontStyle.Italic
                            ),
                            constraints = Constraints(maxWidth = contentWidth),
                            layoutDirection = if (isRtlText(it)) LayoutDirection.Rtl else LayoutDirection.Ltr
                        )
                        translatedLayout.size.height + with(density) { 6.dp.roundToPx() }
                    } ?: with(density) { 6.dp.roundToPx() }
                } else 0
                val spacing = with(density) {
                    when {
                        block.type == BlockType.LIST_ITEM && snapshot.getOrNull(index + 1)?.block?.type == BlockType.LIST_ITEM -> 6.dp
                        block.type == BlockType.HEADER -> largeSpacing
                        else -> mediumSpacing
                    }.roundToPx()
                }
                val decorationHeight = with(density) {
                    var total = 0
                    if (block.type == BlockType.HEADER && block.headerLevel == 1) total += 14.dp.roundToPx()
                    if (chunk.status == ChunkStatus.LOADING || chunk.status == ChunkStatus.ERROR) {
                        total += 22.dp.roundToPx()
                    }
                    total
                }
                measured += MeasuredReadingChunk(
                    chunkIndex = index,
                    textLength = chunk.readingDisplayText().length,
                    lines = lines,
                    spacingAfterPx = spacing,
                    trailingHeightPx = translationHeight + decorationHeight,
                    showTranslation = showTranslations,
                    keepWithNextPx = if (block.type == BlockType.HEADER) bodyLinePx else 0
                )
            }
            if (index % 12 == 11) {
                value = paginateMeasuredChunks(measured, pageHeightPx)
                yield()
            }
        }
        value = paginateMeasuredChunks(measured, pageHeightPx)
    }
}

private fun headerFontSizeForPage(level: Int, scale: Float) = (when (level) {
    1 -> 30f
    2 -> 25f
    3 -> 22f
    else -> 20f
} * scale).sp

private fun headerLineHeightForPage(level: Int, scale: Float) = (when (level) {
    1 -> 38f
    2 -> 33f
    3 -> 30f
    else -> 28f
} * scale).sp
