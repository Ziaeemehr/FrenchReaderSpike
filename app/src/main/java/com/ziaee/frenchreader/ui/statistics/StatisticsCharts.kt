package com.ziaee.frenchreader.ui.statistics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

@Composable
private fun LabelRow(labels: List<String>) {
    Row(modifier = Modifier.fillMaxWidth()) {
        labels.forEach { label ->
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Visible,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun BarChart(values: List<Float>, labels: List<String>, color: Color, modifier: Modifier = Modifier, maxValue: Float? = null) {
    val max = maxValue ?: maxOf(values.maxOrNull() ?: 0f, 1f)
    Column(modifier = modifier.fillMaxWidth().padding(top = 8.dp)) {
        Canvas(modifier = Modifier.fillMaxWidth().height(100.dp)) {
            if (values.isEmpty()) return@Canvas
            val slot = size.width / values.size
            val barWidth = slot * 0.6f
            values.forEachIndexed { i, v ->
                val h = size.height * (v / max).coerceIn(0f, 1f)
                // DrawScope does not auto-mirror in RTL; flip x so bars line up with the (mirrored) label Row.
                val x0 = i * slot + (slot - barWidth) / 2f
                val x = if (layoutDirection == LayoutDirection.Rtl) size.width - x0 - barWidth else x0
                drawRect(color, Offset(x, size.height - h), Size(barWidth, h))
            }
        }
        LabelRow(labels)
    }
}

@Composable
fun LineChart(values: List<Float>, labels: List<String>, color: Color, modifier: Modifier = Modifier, maxValue: Float? = null) {
    val max = maxValue ?: maxOf(values.maxOrNull() ?: 0f, 1f)
    Column(modifier = modifier.fillMaxWidth().padding(top = 8.dp)) {
        Canvas(modifier = Modifier.fillMaxWidth().height(100.dp)) {
            if (values.isEmpty()) return@Canvas
            val slot = size.width / values.size
            val r = 4.dp.toPx()
            val points = values.mapIndexed { i, v ->
                val x0 = i * slot + slot / 2f
                // DrawScope does not auto-mirror in RTL; flip x so points line up with the (mirrored) label Row.
                val x = if (layoutDirection == LayoutDirection.Rtl) size.width - x0 else x0
                val y = r + (size.height - 2 * r) * (1f - (v / max).coerceIn(0f, 1f))
                Offset(x, y)
            }
            for (i in 0 until points.size - 1) {
                drawLine(color, points[i], points[i + 1], strokeWidth = 2.dp.toPx())
            }
            points.forEach { drawCircle(color, radius = r, center = it) }
        }
        LabelRow(labels)
    }
}

@Composable
fun HeatmapGrid(days: List<HeatmapDay>, modifier: Modifier = Modifier) {
    val maxCount = maxOf(days.maxOfOrNull { it.count } ?: 0, 1)
    val primary = MaterialTheme.colorScheme.primary
    val empty = MaterialTheme.colorScheme.surfaceVariant
    val offset = days.firstOrNull()?.date?.dayOfWeek?.let { it.value - 1 } ?: 0
    val cells: List<HeatmapDay?> = List(offset) { null } + days
    Row(
        modifier = modifier.padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        cells.chunked(7).forEach { week ->
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                repeat(7) { row ->
                    val day = week.getOrNull(row)
                    val bg = when {
                        day == null -> Color.Transparent
                        day.count == 0 -> empty
                        else -> primary.copy(alpha = (0.2f + 0.8f * day.count / maxCount).coerceIn(0f, 1f))
                    }
                    Box(modifier = Modifier.size(14.dp).background(bg))
                }
            }
        }
    }
}
