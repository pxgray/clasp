package io.github.pxgray.clasp.ui.scan

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/** Dims everything but a rounded scan window, outlined by gently pulsing corner brackets. */
@Composable
fun ScanOverlay(modifier: Modifier = Modifier) {
    val pulse by rememberInfiniteTransition(label = "scan").animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "corners",
    )
    Canvas(modifier.fillMaxSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
        val window = scanWindow(size)
        val radius = 28.dp.toPx()
        drawRect(Color.Black.copy(alpha = 0.55f))
        drawRoundRect(
            color = Color.Transparent,
            topLeft = window.topLeft,
            size = window.size,
            cornerRadius = CornerRadius(radius),
            blendMode = BlendMode.Clear,
        )
        drawCorners(window, radius, Color.White.copy(alpha = pulse), stroke = 4.dp.toPx(), length = 36.dp.toPx())
    }
}

/** Wide enough for 1D codes and PDF417, tall enough for QR; sits a little above center. */
internal fun scanWindow(canvas: Size): Rect {
    val width = canvas.width * 0.84f
    val height = width * 0.62f
    val left = (canvas.width - width) / 2
    val top = canvas.height * 0.42f - height / 2
    return Rect(left, top, left + width, top + height)
}

private fun DrawScope.drawCorners(window: Rect, radius: Float, color: Color, stroke: Float, length: Float) {
    val path = Path().apply {
        with(window) {
            moveTo(left, top + length); lineTo(left, top + radius); quadraticTo(left, top, left + radius, top); lineTo(left + length, top)
            moveTo(right - length, top); lineTo(right - radius, top); quadraticTo(right, top, right, top + radius); lineTo(right, top + length)
            moveTo(right, bottom - length); lineTo(right, bottom - radius); quadraticTo(right, bottom, right - radius, bottom); lineTo(right - length, bottom)
            moveTo(left + length, bottom); lineTo(left + radius, bottom); quadraticTo(left, bottom, left, bottom - radius); lineTo(left, bottom - length)
        }
    }
    drawPath(path, color, style = Stroke(width = stroke, cap = StrokeCap.Round))
}
