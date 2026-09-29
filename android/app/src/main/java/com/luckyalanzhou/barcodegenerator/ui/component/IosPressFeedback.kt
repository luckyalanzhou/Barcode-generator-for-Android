package com.luckyalanzhou.barcodegenerator.ui.component

import com.luckyalanzhou.barcodegenerator.ui.app.ComposeAnimationConfig
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

/** iOS 风格触摸反馈：按下轻微变暗并缩小，松开或取消时以弹簧恢复。 */
@Composable
internal fun Modifier.iosPressFeedback(
    interactionSource: InteractionSource,
    pressedScale: Float = 0.96f,
    pressedAlpha: Float = 0.82f,
): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val springSpec = ComposeAnimationConfig.pressSpring<Float>()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = springSpec,
        label = "ios-press-scale",
    )
    val alpha by animateFloatAsState(
        targetValue = if (pressed) pressedAlpha else 1f,
        animationSpec = springSpec,
        label = "ios-press-dim",
    )
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
        this.alpha = alpha
    }
}
