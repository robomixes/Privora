package com.privateai.camera.ui.camera

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.privateai.camera.bridge.Detection

@Composable
fun DetectionOverlay(
    detections: List<Detection>,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()

    Canvas(modifier = modifier.fillMaxSize()) {
        val canvasWidth = size.width
        val canvasHeight = size.height

        for (detection in detections) {
            val left = detection.x1 * canvasWidth
            val top = detection.y1 * canvasHeight
            val right = detection.x2 * canvasWidth
            val bottom = detection.y2 * canvasHeight
            val boxWidth = right - left
            val boxHeight = bottom - top

            val color = getColorForClass(detection.classId)

            // Draw bounding box
            drawRect(
                color = color,
                topLeft = Offset(left, top),
                size = Size(boxWidth, boxHeight),
                style = Stroke(width = 3f)
            )

            // Draw label background
            val label = "${detection.className} ${(detection.confidence * 100).toInt()}%"
            val textResult = textMeasurer.measure(
                text = label,
                style = TextStyle(fontSize = 14.sp, color = Color.White)
            )
            val labelHeight = textResult.size.height.toFloat()
            val labelWidth = textResult.size.width.toFloat()

            drawRect(
                color = color,
                topLeft = Offset(left, top - labelHeight - 4),
                size = Size(labelWidth + 8, labelHeight + 4)
            )

            // Draw label text
            drawText(
                textMeasurer = textMeasurer,
                text = label,
                topLeft = Offset(left + 4, top - labelHeight - 2),
                style = TextStyle(fontSize = 14.sp, color = Color.White)
            )
        }
    }
}

private fun getColorForClass(classId: Int): Color {
    val colors = listOf(
        Color(0xFFFF6B6B),  // red
        Color(0xFF4ECDC4),  // teal
        Color(0xFFFFE66D),  // yellow
        Color(0xFF95E1D3),  // mint
        Color(0xFFF38181),  // salmon
        Color(0xFF6C5CE7),  // purple
        Color(0xFFFD79A8),  // pink
        Color(0xFF00B894),  // green
        Color(0xFFE17055),  // orange
        Color(0xFF0984E3),  // blue
    )
    return colors[classId % colors.size]
}
