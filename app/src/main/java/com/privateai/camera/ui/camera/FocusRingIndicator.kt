// SPDX-FileCopyrightText: 2026 Anas
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.privateai.camera.ui.camera

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

@Composable
fun FocusRingIndicator(
    center: Offset,
    onAnimationComplete: () -> Unit
) {
    val radius = remember { Animatable(80f) }
    val alpha = remember { Animatable(1f) }

    LaunchedEffect(center) {
        radius.snapTo(80f)
        alpha.snapTo(1f)
        radius.animateTo(40f, animationSpec = tween(300))
        alpha.animateTo(0f, animationSpec = tween(500))
        onAnimationComplete()
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        drawCircle(
            color = Color.White.copy(alpha = alpha.value),
            radius = radius.value,
            center = center,
            style = Stroke(width = 2.dp.toPx())
        )
    }
}
