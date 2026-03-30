package com.privateai.camera.ui.camera

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.privateai.camera.bridge.Detection

@Composable
fun DetectionOverlay(
    detections: List<Detection>,
    selectedDetection: Detection? = null,
    isFrontCamera: Boolean = false,
    onDetectionTapped: ((Detection, Offset) -> Unit)? = null,
    onBackgroundTapped: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(detections, isFrontCamera) {
                detectTapGestures { tapOffset ->
                    val canvasWidth = size.width.toFloat()
                    val canvasHeight = size.height.toFloat()

                    val hit = detections.firstOrNull { det ->
                        val left = if (isFrontCamera) (1f - det.x2) * canvasWidth else det.x1 * canvasWidth
                        val right = if (isFrontCamera) (1f - det.x1) * canvasWidth else det.x2 * canvasWidth
                        val top = det.y1 * canvasHeight
                        val bottom = det.y2 * canvasHeight
                        tapOffset.x in left..right && tapOffset.y in top..bottom
                    }

                    if (hit != null) {
                        onDetectionTapped?.invoke(hit, tapOffset)
                    } else {
                        onBackgroundTapped?.invoke()
                    }
                }
            }
    ) {
        val canvasWidth = size.width
        val canvasHeight = size.height

        for (detection in detections) {
            // Mirror x-coordinates for front camera (preview is mirrored by PreviewView)
            val left = if (isFrontCamera) (1f - detection.x2) * canvasWidth else detection.x1 * canvasWidth
            val right = if (isFrontCamera) (1f - detection.x1) * canvasWidth else detection.x2 * canvasWidth
            val top = detection.y1 * canvasHeight
            val bottom = detection.y2 * canvasHeight
            val boxWidth = right - left
            val boxHeight = bottom - top

            val isSelected = selectedDetection != null &&
                selectedDetection.classId == detection.classId &&
                iouMatch(selectedDetection, detection) > 0.3f

            val color = getColorForClass(detection.classId)
            val strokeWidth = if (isSelected) 6f else 3f

            // Draw bounding box
            drawRect(
                color = color,
                topLeft = Offset(left, top),
                size = Size(boxWidth, boxHeight),
                style = Stroke(width = strokeWidth)
            )

            // Draw semi-transparent fill for selected detection
            if (isSelected) {
                drawRect(
                    color = color.copy(alpha = 0.15f),
                    topLeft = Offset(left, top),
                    size = Size(boxWidth, boxHeight)
                )
            }

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

private fun iouMatch(a: Detection, b: Detection): Float {
    val interX1 = maxOf(a.x1, b.x1)
    val interY1 = maxOf(a.y1, b.y1)
    val interX2 = minOf(a.x2, b.x2)
    val interY2 = minOf(a.y2, b.y2)
    val interArea = maxOf(0f, interX2 - interX1) * maxOf(0f, interY2 - interY1)
    val areaA = (a.x2 - a.x1) * (a.y2 - a.y1)
    val areaB = (b.x2 - b.x1) * (b.y2 - b.y1)
    val unionArea = areaA + areaB - interArea
    return if (unionArea > 0f) interArea / unionArea else 0f
}

private fun getColorForClass(classId: Int): Color {
    val colors = listOf(
        Color(0xFFFF6B6B),
        Color(0xFF4ECDC4),
        Color(0xFFFFE66D),
        Color(0xFF95E1D3),
        Color(0xFFF38181),
        Color(0xFF6C5CE7),
        Color(0xFFFD79A8),
        Color(0xFF00B894),
        Color(0xFFE17055),
        Color(0xFF0984E3),
    )
    return colors[classId % colors.size]
}
