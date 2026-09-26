package com.luckyalanzhou.barcodegenerator.ui.app

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import kotlin.math.roundToInt

/** Shared frame-rate-aware animation parameters for the Compose UI. */
data class ComposeAnimationConfig(val refreshRateHz: Int) {
    private fun frames(frameCount: Int): Int =
        (frameCount * 1000f / refreshRateHz).roundToInt().coerceAtLeast(1)

    val pageEnterDurationMillis: Int get() = frames(16)
    val pageExitDurationMillis: Int get() = frames(12)
    val pageFadeInDurationMillis: Int get() = frames(11)
    val pageFadeOutDurationMillis: Int get() = frames(8)
    val progressDurationMillis: Int get() = frames(14)

    private val stiffnessScale: Float
        get() = 1f + ((refreshRateHz - 60) / 105f).coerceIn(0f, 1f) * 0.12f

    fun <T> bouncySpring(): SpringSpec<T> = spring(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessMediumLow * stiffnessScale,
    )

    fun <T> settleSpring(): SpringSpec<T> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMedium * stiffnessScale,
    )

    companion object {
        private fun bucket(rate: Float): Int = when {
            rate >= 157f -> 165
            rate >= 137f -> 144
            rate >= 112f -> 120
            rate >= 75f -> 90
            else -> 60
        }

        fun from(rate: Float): ComposeAnimationConfig = ComposeAnimationConfig(bucket(rate))
    }
}

@Composable
fun rememberComposeAnimationConfig(): ComposeAnimationConfig {
    val context = LocalContext.current
    val view = LocalView.current
    var refreshRate by remember(view) {
        mutableFloatStateOf(view.display?.refreshRate?.takeIf { it > 0f } ?: 60f)
    }

    DisposableEffect(context, view) {
        val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
        val displayId = view.display?.displayId ?: -1
        val listener = object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(id: Int) = Unit
            override fun onDisplayRemoved(id: Int) = Unit
            override fun onDisplayChanged(id: Int) {
                if (id == displayId) {
                    view.display?.refreshRate?.takeIf { it > 0f }?.let { refreshRate = it }
                }
            }
        }
        displayManager?.registerDisplayListener(listener, Handler(Looper.getMainLooper()))
        onDispose { displayManager?.unregisterDisplayListener(listener) }
    }

    return remember(refreshRate) { ComposeAnimationConfig.from(refreshRate) }
}
