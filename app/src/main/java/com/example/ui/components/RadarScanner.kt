package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.AuroraViolet
import com.example.ui.theme.ElectricCyan
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun RadarScanner(
    modifier: Modifier = Modifier,
    canvasSize: Dp = 260.dp,
    radarColor: Color = ElectricCyan,
    pulseColor: Color = AuroraViolet,
    peerCount: Int = 0
) {
    val transition = rememberInfiniteTransition(label = "RadarTransition")

    // Rotation angle for radar sweeping beam
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "RadarSweep"
    )

    // Wave 1 pulse
    val wave1 by transition.animateFloat(
        initialValue = 0.1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "Wave1"
    )

    // Wave 2 pulse
    val wave2 by transition.animateFloat(
        initialValue = 0.1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2800, delayMillis = 1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "Wave2"
    )

    Box(
        modifier = modifier.size(canvasSize),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val maxRadius = size.minDimension / 2f

            // Concentric grid circles
            val ringCount = 4
            for (i in 1..ringCount) {
                val r = (maxRadius / ringCount) * i
                drawCircle(
                    color = radarColor.copy(alpha = 0.12f),
                    radius = r,
                    center = center,
                    style = Stroke(width = 1.5.dp.toPx())
                )
            }

            // Cross-hair lines
            drawLine(
                color = radarColor.copy(alpha = 0.15f),
                start = Offset(center.x - maxRadius, center.y),
                end = Offset(center.x + maxRadius, center.y),
                strokeWidth = 1.dp.toPx()
            )
            drawLine(
                color = radarColor.copy(alpha = 0.15f),
                start = Offset(center.x, center.y - maxRadius),
                end = Offset(center.x, center.y + maxRadius),
                strokeWidth = 1.dp.toPx()
            )

            // Animated expanding pulse wave 1
            val alpha1 = (1f - wave1).coerceIn(0f, 1f) * 0.4f
            drawCircle(
                color = pulseColor.copy(alpha = alpha1),
                radius = maxRadius * wave1,
                center = center,
                style = Stroke(width = 2.dp.toPx())
            )

            // Animated expanding pulse wave 2
            val alpha2 = (1f - wave2).coerceIn(0f, 1f) * 0.4f
            drawCircle(
                color = radarColor.copy(alpha = alpha2),
                radius = maxRadius * wave2,
                center = center,
                style = Stroke(width = 2.dp.toPx())
            )

            // Sweeping radar beam line
            val rad = Math.toRadians(angle.toDouble())
            val beamEnd = Offset(
                x = center.x + (maxRadius * cos(rad)).toFloat(),
                y = center.y + (maxRadius * sin(rad)).toFloat()
            )

            val sweepGradient = Brush.sweepGradient(
                colors = listOf(
                    radarColor.copy(alpha = 0f),
                    radarColor.copy(alpha = 0.05f),
                    radarColor.copy(alpha = 0.35f)
                ),
                center = center
            )

            // Draw sweep sector arc
            drawArc(
                brush = sweepGradient,
                startAngle = angle - 45f,
                sweepAngle = 45f,
                useCenter = true,
                topLeft = Offset(center.x - maxRadius, center.y - maxRadius),
                size = androidx.compose.ui.geometry.Size(maxRadius * 2f, maxRadius * 2f)
            )

            // Sweeping edge line
            drawLine(
                color = radarColor.copy(alpha = 0.8f),
                start = center,
                end = beamEnd,
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round
            )

            // Central pulsing core beacon
            drawCircle(
                color = radarColor,
                radius = 6.dp.toPx(),
                center = center
            )
            drawCircle(
                color = radarColor.copy(alpha = 0.3f),
                radius = 12.dp.toPx(),
                center = center
            )

            // Draw peer blips if any
            if (peerCount > 0) {
                val blipPositions = listOf(
                    Offset(center.x + maxRadius * 0.45f, center.y - maxRadius * 0.35f),
                    Offset(center.x - maxRadius * 0.55f, center.y + maxRadius * 0.25f),
                    Offset(center.x + maxRadius * 0.25f, center.y + maxRadius * 0.6f)
                )
                for (idx in 0 until minOf(peerCount, blipPositions.size)) {
                    val pos = blipPositions[idx]
                    drawCircle(
                        color = AuroraViolet,
                        radius = 5.dp.toPx(),
                        center = pos
                    )
                    drawCircle(
                        color = AuroraViolet.copy(alpha = 0.4f),
                        radius = 10.dp.toPx(),
                        center = pos
                    )
                }
            }
        }
    }
}
