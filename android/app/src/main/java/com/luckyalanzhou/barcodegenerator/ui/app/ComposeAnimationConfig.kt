package com.luckyalanzhou.barcodegenerator.ui.app

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring

/** Shared, refresh-rate-independent motion tokens for the Compose UI. */
object ComposeAnimationConfig {
    const val pageFadeInDurationMillis = 180
    const val pageFadeOutDurationMillis = 140
    const val tabSwipeEnterDurationMillis = 200
    const val tabSwipeExitDurationMillis = 160
    const val tabSwipeFadeDurationMillis = 180
    const val tabSelectionEnterDurationMillis = 180
    const val tabSelectionExitDurationMillis = 120
    const val progressDurationMillis = 230

    fun <T> bouncySpring(): SpringSpec<T> = spring(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )

    fun <T> settleSpring(): SpringSpec<T> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMedium,
    )
}
