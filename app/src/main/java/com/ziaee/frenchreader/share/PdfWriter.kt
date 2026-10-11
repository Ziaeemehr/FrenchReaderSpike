package com.ziaee.frenchreader.share

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.SpannableString
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import android.text.style.StyleSpan
import androidx.core.content.res.ResourcesCompat
import com.ziaee.frenchreader.R
import java.io.OutputStream

// A4 in PostScript points.
private const val PAGE_WIDTH = 595
private const val PAGE_HEIGHT = 842
private const val MARGIN = 44f
private const val FOOTER_HEIGHT = 46f
private const val CELL_PADDING = 5f

/**
 * Writes [blocks] as an A4 PDF. Every page ends with [signature] (the app name and its links) and
 * the page number. Persian paragraphs are right-aligned and French ones left-aligned (see
 * [PdfText.isRtl]); a table with Persian headers or row labels has its first column on the right.
 */
fun writePdf(context: Context, blocks: List<PdfBlock>, signature: List<String>, output: OutputStream) {
    val regular = ResourcesCompat.getFont(context, R.font.vazirmatn_regular) ?: Typeface.DEFAULT
    val medium = ResourcesCompat.getFont(context, R.font.vazirmatn_medium) ?: Typeface.DEFAULT_BOLD
    fun paint(size: Float, face: Typeface = regular, color: Int = Color.BLACK) =
        TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = size; typeface = face; this.color = color }
    val body = paint(11f)
    val quote = paint(11f, color = Color.DKGRAY)
    val cell = paint(9.5f)
    val headerCell = paint(9.5f, medium)
    val footer = paint(8f, color = Color.GRAY)
    val line = Paint().apply { color = Color.LTGRAY; strokeWidth = 0.7f; style = Paint.Style.STROKE }
    val contentWidth = PAGE_WIDTH - 2 * MARGIN
    val bottom = PAGE_HEIGHT - MARGIN - FOOTER_HEIGHT

    fun layout(text: PdfText, paint: TextPaint, width: Float): StaticLayout {
        // As in the app, an arrow in a right-to-left line points the way the line reads.
        val spannable = SpannableString(if (text.isRtl) text.text.replace('→', '←') else text.text)
        text.bold.forEach {
            if (it.first >= 0 && it.last < text.text.length && it.first <= it.last) {
                spannable.setSpan(StyleSpan(Typeface.BOLD), it.first, it.last + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
        return StaticLayout.Builder.obtain(spannable, 0, spannable.length, paint, width.toInt().coerceAtLeast(1))
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setTextDirection(if (text.isRtl) TextDirectionHeuristics.RTL else TextDirectionHeuristics.LTR)
            .setLineSpacing(0f, 1.25f)
            .build()
    }

    val document = PdfDocument()
    var pageNumber = 0
    var page: PdfDocument.Page? = null
    var y = MARGIN

    fun finishPage() {
        val current = page ?: return
        val canvas = current.canvas
        val top = PAGE_HEIGHT - MARGIN - FOOTER_HEIGHT + 10f
        canvas.drawLine(MARGIN, top, PAGE_WIDTH - MARGIN, top, line)
        var textY = top + 12f
        // The signature is Latin text and links: always left-to-right, whatever the content is.
        signature.forEach { canvas.drawText(it, MARGIN, textY, footer); textY += 10f }
        val number = pageNumber.toString()
        canvas.drawText(number, PAGE_WIDTH - MARGIN - footer.measureText(number), top + 12f, footer)
        document.finishPage(current)
        page = null
    }
    fun canvas(): Canvas {
        page?.let { return it.canvas }
        pageNumber++
        y = MARGIN
        return document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
            .also { page = it }.canvas
    }
    /** Draws a layout at the left margin + [indent], breaking between lines when a page is full. */
    fun drawFlowing(layout: StaticLayout, indent: Float = 0f) {
        var first = 0
        while (first < layout.lineCount) {
            var canvas = canvas()
            if (y + layout.getLineBottom(first) - layout.getLineTop(first) > bottom) {
                finishPage()
                canvas = canvas()
            }
            var last = first
            while (last + 1 < layout.lineCount &&
                y + layout.getLineBottom(last + 1) - layout.getLineTop(first) <= bottom) last++
            val top = layout.getLineTop(first)
            val height = layout.getLineBottom(last) - top
            canvas.save()
            canvas.translate(MARGIN + indent, y - top)
            canvas.clipRect(0f, top.toFloat(), layout.width.toFloat(), (top + height).toFloat())
            layout.draw(canvas)
            canvas.restore()
            y += height
            first = last + 1
        }
    }

    try {
        blocks.forEach { block ->
            when (block) {
                is PdfBlock.Heading -> {
                    val size = when (block.level) { 1 -> 18f; 2 -> 13.5f; else -> 11.5f }
                    val heading = layout(PdfText(block.text), paint(size, medium), contentWidth)
                    y += if (page == null) 0f else if (block.level == 1) 6f else 10f
                    // A heading never sits alone at the foot of a page.
                    if (page != null && y + heading.height + 30f > bottom) finishPage()
                    drawFlowing(heading)
                    y += 6f
                }
                is PdfBlock.Paragraph -> {
                    val indent = if (block.quote) 10f else 0f
                    drawFlowing(layout(block.text, if (block.quote) quote else body, contentWidth - 2 * indent), indent)
                    y += 7f
                }
                is PdfBlock.Table -> {
                    val columns = block.header.size.coerceAtLeast(1)
                    val columnWidth = contentWidth / columns
                    val mirrored = (block.header + block.rows.mapNotNull { it.firstOrNull() }).any { it.isRtl }
                    (listOf(block.header) + block.rows).forEachIndexed { index, row ->
                        val layouts = row.take(columns).map {
                            layout(it, if (index == 0) headerCell else cell, columnWidth - 2 * CELL_PADDING)
                        }
                        val height = (layouts.maxOfOrNull { it.height } ?: 0) + 2 * CELL_PADDING
                        var canvas = canvas()
                        if (y + height > bottom && y > MARGIN) { finishPage(); canvas = canvas() }
                        layouts.forEachIndexed { column, cellLayout ->
                            val left = MARGIN + (if (mirrored) columns - 1 - column else column) * columnWidth
                            canvas.drawRect(left, y, left + columnWidth, y + height, line)
                            canvas.save()
                            canvas.translate(left + CELL_PADDING, y + CELL_PADDING)
                            cellLayout.draw(canvas)
                            canvas.restore()
                        }
                        y += height
                    }
                    y += 9f
                }
            }
        }
        if (page == null) canvas()
        finishPage()
        document.writeTo(output)
    } finally {
        document.close()
    }
}
