package com.luckyalanzhou.barcodegenerator.ui.component.glass

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput

/** Observes contact without consuming clicks, scrolling, or drag gestures. */
@Stable
internal class GlassPointerTracker {
    var isPressed by mutableStateOf(false)
        private set

    var touchPosition by mutableStateOf(Offset.Unspecified)
        private set

    private var downPosition by mutableStateOf(Offset.Unspecified)

    val dragOffset: Offset
        get() = if (touchPosition.isFiniteVector() && downPosition.isFiniteVector()) {
            touchPosition - downPosition
        } else {
            Offset.Zero
        }

    val pointerModifier: Modifier
        get() = Modifier.pointerInput(this) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                downPosition = down.position
                touchPosition = down.position
                isPressed = true
                try {
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        event.changes.firstOrNull { it.id == down.id }?.let { touchPosition = it.position }
                    } while (event.changes.any { it.id == down.id && it.pressed })
                } finally {
                    isPressed = false
                }
            }
        }
}

/** A contact-local highlight, following the touch without recomposing the surrounding layout. */
internal fun DrawScope.drawGlassTouchHighlight(
    touchPosition: Offset,
    progress: Float,
    strength: Float,
) {
    val amount = progress.takeIf(Float::isFinite)?.coerceIn(0f, 1f) ?: 0f
    if (amount <= 0.001f || !touchPosition.isFiniteVector() || size.minDimension <= 0f) return

    val center = Offset(
        touchPosition.x.coerceIn(0f, size.width),
        touchPosition.y.coerceIn(0f, size.height),
    )
    drawRect(
        brush = Brush.radialGradient(
            0f to Color.White.copy(alpha = strength * amount),
            .48f to Color.White.copy(alpha = strength * amount * .38f),
            1f to Color.Transparent,
            center = center,
            radius = size.minDimension * 1.5f,
        ),
        blendMode = BlendMode.Plus,
    )
}

private fun Offset.isFiniteVector(): Boolean = x.isFinite() && y.isFinite()
