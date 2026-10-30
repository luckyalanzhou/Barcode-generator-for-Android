package com.luckyalanzhou.barcodegenerator.ui.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import com.luckyalanzhou.barcodegenerator.ui.animation.ComposeAnimationConfig
import com.luckyalanzhou.barcodegenerator.ui.theme.LocalVisualEffectsPolicy
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filter

internal const val UnframedPressScale = .84f
internal const val UnframedPressAlpha = .58f

/** 快速点按也可见；只延续视觉反馈，不延迟点击回调。 */
internal fun unframedReleaseDelayMillis(startNanos: Long, nowNanos: Long): Long =
    (80L - ((nowNanos - startNanos) / 1_000_000L).coerceAtLeast(0L)).coerceIn(0L, 80L)

/** 无框小图标专用：快速轻压、短暂可见、弹性恢复；取消不保留反馈，不画框或底色。 */
@Composable
internal fun Modifier.unframedActionPressFeedback(interactionSource: InteractionSource): Modifier {
    var pressed by remember(interactionSource) { mutableStateOf(false) }
    val reduced = LocalVisualEffectsPolicy.current.reduceMotion
    LaunchedEffect(interactionSource) {
        var started = 0L
        interactionSource.interactions.filter { it is PressInteraction }.collectLatest { interaction ->
            when (interaction) {
                is PressInteraction.Press -> { started = System.nanoTime(); pressed = true }
                is PressInteraction.Release -> {
                    delay(unframedReleaseDelayMillis(started, System.nanoTime()))
                    pressed = false
                }
                is PressInteraction.Cancel -> pressed = false
            }
        }
    }
    val scale = animateFloatAsState(
        if (pressed && !reduced) UnframedPressScale else 1f,
        animationSpec = if (pressed || reduced) tween(if (reduced) 0 else 45)
            else ComposeAnimationConfig.pressSpring(), label = "unframed-press-scale",
    )
    val alpha = animateFloatAsState(
        if (pressed) UnframedPressAlpha else 1f,
        animationSpec = tween(if (reduced) 0 else if (pressed) 45 else 100),
        label = "unframed-press-alpha",
    )
    return graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
        this.alpha = alpha.value
    }
}
