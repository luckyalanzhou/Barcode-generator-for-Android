package com.luckyalanzhou.barcodegenerator.ui.animation

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring

/** 全局共用的时间动画参数。动画由 Compose 的帧时钟驱动，普通界面动效不应锁定显示刷新率。 */
object ComposeAnimationConfig {
    const val pageFadeInDurationMillis = 180
    const val pageFadeOutDurationMillis = 140
    const val tabSelectionEnterDurationMillis = 180
    const val tabItemColorDurationMillis = 100
    const val tabJellyResetDelayMillis = 72L
    const val favoriteRowExpandDurationMillis = 180
    const val favoriteRowCollapseDurationMillis = 160
    const val favoriteRowFadeDurationMillis = 120
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
