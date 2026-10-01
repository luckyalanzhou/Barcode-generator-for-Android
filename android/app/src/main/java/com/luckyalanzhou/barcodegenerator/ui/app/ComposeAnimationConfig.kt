package com.luckyalanzhou.barcodegenerator.ui.app

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring

/** Shared time-based motion specs. Compose drives these with its frame clock; ordinary UI motion should not pin a display refresh rate. */
object ComposeAnimationConfig {
    const val pageFadeInDurationMillis = 180
    const val pageFadeOutDurationMillis = 140
    const val tabSelectionEnterDurationMillis = 180
    const val tabItemColorDurationMillis = 100
    const val tabJellyResetDelayMillis = 72L
    const val favoriteRowExpandDurationMillis = 180
    const val favoriteRowCollapseDurationMillis = 160
    const val favoriteRowFadeDurationMillis = 120
    const val favoriteRowRemovalBufferMillis = 48L
    const val progressDurationMillis = 230
    const val indeterminateProgressDurationMillis = 1100

    fun <T> jellySpring(): SpringSpec<T> = spring(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessHigh,
    )

    fun <T> pressSpring(): SpringSpec<T> = spring(
        dampingRatio = 0.68f,
        stiffness = 520f,
    )

    fun <T> toggleSpring(): SpringSpec<T> = spring(
        dampingRatio = 0.72f,
        stiffness = 700f,
    )

    fun <T> settleSpring(): SpringSpec<T> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMedium,
    )
}
