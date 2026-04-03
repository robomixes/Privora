package com.privateai.camera.ui.insights

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp

/**
 * Simple bar chart. Each bar = (label, value, color).
 */
@Composable
fun BarChart(
    data: List<Triple<String, Float, Color>>,
    modifier: Modifier = Modifier.fillMaxWidth().height(150.dp)
) {
    if (data.isEmpty()) return
    val maxVal = data.maxOf { it.second }.coerceAtLeast(1f)

    Canvas(modifier) {
        val barWidth = size.width / (data.size * 2f)
        val bottomPadding = 30f

        data.forEachIndexed { i, (label, value, color) ->
            val x = (i * 2 + 0.5f) * barWidth
            val barHeight = (value / maxVal) * (size.height - bottomPadding - 20f)
            val y = size.height - bottomPadding - barHeight

            drawRect(color, Offset(x, y), Size(barWidth, barHeight))

            // Label
            drawIntoCanvas { canvas ->
                canvas.nativeCanvas.drawText(
                    label, x + barWidth / 2, size.height - 5f,
                    android.graphics.Paint().apply {
                        this.color = android.graphics.Color.GRAY; textSize = 24f; textAlign = android.graphics.Paint.Align.CENTER
                    }
                )
            }
        }
    }
}

/**
 * Simple pie chart. Each slice = (label, value, color).
 */
@Composable
fun PieChart(
    data: List<Triple<String, Float, Color>>,
    modifier: Modifier = Modifier.height(160.dp)
) {
    if (data.isEmpty()) return
    val total = data.sumOf { it.second.toDouble() }.toFloat().coerceAtLeast(1f)

    Canvas(modifier) {
        val diameter = minOf(size.width, size.height) - 20f
        val radius = diameter / 2
        val cx = size.width / 2
        val cy = size.height / 2

        var startAngle = -90f
        data.forEach { (_, value, color) ->
            val sweep = (value / total) * 360f
            drawArc(color, startAngle, sweep, useCenter = true, topLeft = Offset(cx - radius, cy - radius), size = Size(diameter, diameter))
            startAngle += sweep
        }
    }
}

/**
 * Simple line chart. Points = list of (x label, y value).
 */
@Composable
fun LineChart(
    data: List<Pair<String, Float>>,
    color: Color = Color(0xFF4CAF50),
    modifier: Modifier = Modifier.fillMaxWidth().height(150.dp)
) {
    if (data.size < 2) return
    val maxVal = data.maxOf { it.second }.coerceAtLeast(1f)
    val minVal = data.minOf { it.second }
    val range = (maxVal - minVal).coerceAtLeast(1f)

    Canvas(modifier) {
        val padding = 20f
        val w = size.width - padding * 2
        val h = size.height - padding * 2

        val points = data.mapIndexed { i, (_, value) ->
            val x = padding + (i.toFloat() / (data.size - 1)) * w
            val y = padding + h - ((value - minVal) / range) * h
            Offset(x, y)
        }

        // Draw lines
        for (i in 0 until points.size - 1) {
            drawLine(color, points[i], points[i + 1], strokeWidth = 4f, cap = StrokeCap.Round)
        }

        // Draw dots
        points.forEach { pt ->
            drawCircle(color, 6f, pt)
        }
    }
}
