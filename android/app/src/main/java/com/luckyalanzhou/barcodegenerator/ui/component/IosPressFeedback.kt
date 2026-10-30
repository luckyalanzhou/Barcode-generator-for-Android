package com.luckyalanzhou.barcodegenerator.ui.component

import com.luckyalanzhou.barcodegenerator.ui.animation.ComposeAnimationConfig
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import com.luckyalanzhou.barcodegenerator.ui.theme.LocalVisualEffectsPolicy

/** iOS 风格触摸反馈：按下轻微变暗并缩小，松开或取消时以弹簧恢复。 */
@Composable
internal fun Modifier.iosPressFeedback(
    interactionSource: InteractionSource,
    pressedScale: Float = 0.96f,
    pressedAlpha: Float = 0.82f,
): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val reduceMotion = LocalVisualEffectsPolicy.current.reduceMotion
    val pressAnimationSpec = if (reduceMotion) snap<Float>() else ComposeAnimationConfig.pressSpring()
    val scale = animateFloatAsState(
        targetValue = if (pressed && !reduceMotion) pressedScale else 1f,
        animationSpec = pressAnimationSpec,
        label = "ios-press-scale",
    )
    val alpha = animateFloatAsState(
        targetValue = if (pressed) pressedAlpha else 1f,
        animationSpec = pressAnimationSpec,
        label = "ios-press-dim",
    )
    return graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
        this.alpha = alpha.value
    }
}
