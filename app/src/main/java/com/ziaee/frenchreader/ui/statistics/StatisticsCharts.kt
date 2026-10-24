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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import java.time.DayOfWeek
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt

internal fun niceAxisMax(max: Float): Float {
    if (max <= 0f) return 1f
    val magnitude = 10.0.pow(floor(log10(max.toDouble())))
    return listOf(1.0, 2.0, 2.5, 5.0, 10.0)
        .map { (it * magnitude).toFloat() }
        .first { it >= max }
}

internal fun chartAxisMax(dataMax: Float, maxValue: Float?, integerTicks: Boolean): Float {
    if (maxValue != null && maxValue > 0f) return maxValue
    val nice = niceAxisMax(dataMax)
    return if (integerTicks) {
        val max = if (dataMax <= 5f) dataMax.coerceAtLeast(1f) else nice
        (ceil(max / 2f) * 2f).coerceAtLeast(2f)
    } else nice
}

@Composable
private fun ChartFrame(
    labels: List<String>,
    max: Float,
    formatValue: ((Float) -> String)?,
    height: Dp,
    inset: Dp,
    modifier: Modifier,
    plot: @Composable (Modifier) -> Unit
) {
    Row(modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp)) {
        if (formatValue != null) {
            Layout(
                content = {
                    listOf(max, max / 2f, 0f).forEach { value ->
                        Text(
                            formatValue(value),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                },
                modifier = Modifier.height(height).padding(end = 8.dp)
            ) { measurables, constraints ->
                val ticks = measurables.map { it.measure(Constraints()) }
                val width = ticks.maxOf { it.width }
                val h = constraints.maxHeight
                val edge = inset.toPx()
                // Endpoint labels extend half their height beyond the plot so their centers
                // match the gridlines, including the point-radius inset on line charts.
                layout(width, h) {
                    ticks.forEachIndexed { index, tick ->
                        val center = edge + (h - 2 * edge) * index / 2f
                        tick.placeRelative(width - tick.width, (center - tick.height / 2f).roundToInt())
                    }
                }
            }
        }
        Column(Modifier.weight(1f)) {
            plot(Modifier.fillMaxWidth().height(height))
            Box(Modifier.padding(top = if (formatValue != null) 8.dp else 0.dp)) {
                LabelRow(labels)
            }
        }
    }
}

private fun DrawScope.axisGrid(color: Color, inset: Float = 0f) {
    repeat(3) { tick ->
        val y = inset + (size.height - 2 * inset) * tick / 2f
        drawLine(color, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
    }
}

@Composable
private fun LabelRow(labels: List<String>) {
    val visibleLabels = labels.mapIndexedNotNull { index, label ->
        label.takeIf { it.isNotEmpty() }?.let { index to it }
    }
    Layout(
        content = {
            visibleLabels.forEach { (_, label) ->
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        modifier = Modifier.fillMaxWidth()
    ) { measurables, constraints ->
        val width = if (constraints.maxWidth == Constraints.Infinity) {
            constraints.minWidth
        } else {
            constraints.maxWidth
        }
        if (labels.isEmpty() || visibleLabels.isEmpty()) {
            return@Layout layout(width, 0) {}
        }
        val centers = visibleLabels.map { (index, _) ->
            width * (index + 0.5f) / labels.size
        }
        val bounds = centers.indices.map { index ->
            val start = if (index == 0) 0f else (centers[index - 1] + centers[index]) / 2f
            val end = if (index == centers.lastIndex) width.toFloat()
            else (centers[index] + centers[index + 1]) / 2f
            start.roundToInt() to end.roundToInt()
        }
        val placeables = measurables.mapIndexed { index, measurable ->
            val (start, end) = bounds[index]
            measurable.measure(
                Constraints(
                    minWidth = 0,
                    maxWidth = (end - start).coerceAtLeast(0),
                    minHeight = 0,
                    maxHeight = constraints.maxHeight
                )
            )
        }
        val height = placeables.maxOfOrNull { it.height } ?: 0
        layout(width, height.coerceIn(constraints.minHeight, constraints.maxHeight)) {
            placeables.forEachIndexed { index, placeable ->
                val (start, end) = bounds[index]
                val centered = (centers[index] - placeable.width / 2f).roundToInt()
                val x = centered.coerceIn(start, (end - placeable.width).coerceAtLeast(start))
                placeable.placeRelative(x, 0)
            }
        }
    }
}

@Composable
fun BarChart(
    values: List<Float>,
    labels: List<String>,
    color: Color,
    modifier: Modifier = Modifier,
    maxValue: Float? = null,
    formatValue: ((Float) -> String)? = null,
    integerTicks: Boolean = false
) {
    val max = chartAxisMax(values.maxOrNull() ?: 0f, maxValue, integerTicks)
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    ChartFrame(labels, max, formatValue, 100.dp, 0.dp, modifier) { plotModifier ->
        Canvas(modifier = plotModifier) {
            if (formatValue != null) axisGrid(gridColor)
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
    }
}

@Composable
fun StackedBarChart(
    valuesByBar: List<List<Float>>,
    labels: List<String>,
    colors: List<Color>,
    modifier: Modifier = Modifier,
    maxValue: Float? = null,
    formatValue: ((Float) -> String)? = null,
    integerTicks: Boolean = false
) {
    val max = chartAxisMax(valuesByBar.maxOfOrNull { it.sum() } ?: 0f, maxValue, integerTicks)
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    ChartFrame(labels, max, formatValue, 140.dp, 0.dp, modifier) { plotModifier ->
        Canvas(modifier = plotModifier) {
            if (formatValue != null) axisGrid(gridColor)
            if (valuesByBar.isEmpty()) return@Canvas
            val slot = size.width / valuesByBar.size
            val width = slot * .65f
            valuesByBar.forEachIndexed { index, segments ->
                val x0 = index * slot + (slot - width) / 2
                val x = if (layoutDirection == LayoutDirection.Rtl) {
                    size.width - x0 - width
                } else {
                    x0
                }
                var bottom = size.height
                segments.forEachIndexed { segment, value ->
                    val height = size.height * (value / max).coerceIn(0f, 1f)
                    drawRect(
                        color = colors.getOrElse(segment) { Color.Gray },
                        topLeft = Offset(x, bottom - height),
                        size = Size(width, height)
                    )
                    bottom -= height
                }
            }
        }
    }
}

@Composable
fun LineChart(
    values: List<Float>,
    labels: List<String>,
    color: Color,
    modifier: Modifier = Modifier,
    maxValue: Float? = null,
    formatValue: ((Float) -> String)? = null,
    integerTicks: Boolean = false
) {
    val max = chartAxisMax(values.maxOrNull() ?: 0f, maxValue, integerTicks)
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    ChartFrame(labels, max, formatValue, 100.dp, 4.dp, modifier) { plotModifier ->
        Canvas(modifier = plotModifier) {
            if (formatValue != null) axisGrid(gridColor, 4.dp.toPx())
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
    }
}

@Composable
fun MultiLineChart(
    valuesBySeries: List<List<Float>>,
    labels: List<String>,
    colors: List<Color>,
    modifier: Modifier = Modifier,
    maxValue: Float? = null,
    formatValue: ((Float) -> String)? = null,
    integerTicks: Boolean = false
) {
    val max = chartAxisMax(valuesBySeries.maxOfOrNull { it.maxOrNull() ?: 0f } ?: 0f, maxValue, integerTicks)
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    ChartFrame(labels, max, formatValue, 140.dp, 4.dp, modifier) { plotModifier ->
        Canvas(modifier = plotModifier) {
            if (formatValue != null) axisGrid(gridColor, 4.dp.toPx())
            val count = valuesBySeries.maxOfOrNull { it.size } ?: 0
            if (count == 0) return@Canvas
            val slot = size.width / count
            valuesBySeries.forEachIndexed { seriesIndex, values ->
                val points = values.mapIndexed { index, value ->
                    val x0 = index * slot + slot / 2f
                    val x = if (layoutDirection == LayoutDirection.Rtl) size.width - x0 else x0
                    val y = 4.dp.toPx() + (size.height - 8.dp.toPx()) *
                        (1f - (value / max).coerceIn(0f, 1f))
                    Offset(x, y)
                }
                points.zipWithNext().forEach { (start, end) ->
                    drawLine(
                        color = colors.getOrElse(seriesIndex) { Color.Gray },
                        start = start,
                        end = end,
                        strokeWidth = 2.dp.toPx()
                    )
                }
                if (points.size == 1) {
                    drawCircle(
                        color = colors.getOrElse(seriesIndex) { Color.Gray },
                        radius = 4.dp.toPx(),
                        center = points.single()
                    )
                }
            }
        }
    }
}

@Composable
fun HeatmapGrid(
    days: List<HeatmapDay>,
    modifier: Modifier = Modifier,
    firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY
) {
    val maxCount = maxOf(days.maxOfOrNull { it.count } ?: 0, 1)
    val primary = MaterialTheme.colorScheme.primary
    val empty = MaterialTheme.colorScheme.surfaceVariant
    val offset = days.firstOrNull()?.date?.dayOfWeek?.let {
        (it.value - firstDayOfWeek.value + 7) % 7
    } ?: 0
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
