package com.privateai.camera.ui.camera

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Camera level indicator with degree number + tilt line.
 * Shows integer degrees. Green when level (within ±1°), white otherwise.
 * Horizontal line rotates with phone tilt — like Google Camera.
 */
@Composable
fun LevelIndicator(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var rollDegrees by remember { mutableFloatStateOf(0f) }

    val alpha = 0.15f
    var filteredRoll by remember { mutableFloatStateOf(0f) }

    DisposableEffect(Unit) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]

                val rawRoll = Math.toDegrees(
                    atan2(x.toDouble(), sqrt((y * y + z * z).toDouble()))
                ).toFloat()

                filteredRoll = filteredRoll + alpha * (rawRoll - filteredRoll)
                rollDegrees = filteredRoll
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        sensorManager.registerListener(listener, accelerometer, SensorManager.SENSOR_DELAY_UI)
        onDispose { sensorManager.unregisterListener(listener) }
    }

    val isLevel = abs(rollDegrees) < 1f
    val color = if (isLevel) Color(0xFF4CAF50) else Color.White
    val degreeInt = rollDegrees.roundToInt()

    Box(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Tilt line
            Canvas(
                modifier = Modifier
                    .width(40.dp)
                    .height(20.dp)
            ) {
                val centerX = size.width / 2
                val centerY = size.height / 2
                val lineLength = size.width * 0.8f

                // Rotate line based on roll
                val angleRad = Math.toRadians(rollDegrees.toDouble().coerceIn(-45.0, 45.0))
                val dx = (lineLength / 2 * kotlin.math.cos(angleRad)).toFloat()
                val dy = (lineLength / 2 * kotlin.math.sin(angleRad)).toFloat()

                // Main tilt line
                drawLine(
                    color = color,
                    start = Offset(centerX - dx, centerY + dy),
                    end = Offset(centerX + dx, centerY - dy),
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round
                )

                // Center dot
                drawCircle(
                    color = color,
                    radius = 3.dp.toPx(),
                    center = Offset(centerX, centerY)
                )
            }

            // Degree number (integer only, hidden at 0°)
            Text(
                text = if (isLevel) "—" else "${degreeInt}°",
                color = color,
                fontSize = 13.sp,
                fontWeight = if (isLevel) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.padding(start = 4.dp)
            )
        }
    }
}
