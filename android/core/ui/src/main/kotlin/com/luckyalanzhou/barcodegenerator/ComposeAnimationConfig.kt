package com.luckyalanzhou.barcodegenerator

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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import kotlin.math.roundToInt

/**
 * Compose 全局动画参数。
 *
 * 动画时长以“目标帧数”计算，而不是固定毫秒数：设备刷新率越高，同一段动效
 * 会拥有更多中间帧；弹簧刚度也会略微提升，避免高刷设备上的回弹显得拖沓。
 */
data class ComposeAnimationConfig(val refreshRateHz: Int) {
    private fun frames(frameCount: Int): Int =
        (frameCount * 1000f / refreshRateHz).roundToInt().coerceAtLeast(1)

    // 页面切换：约 16 帧进入、12 帧退出，保持连贯但不拖慢操作。
    val pageEnterDurationMillis: Int get() = frames(16)
    val pageExitDurationMillis: Int get() = frames(12)
    val pageFadeInDurationMillis: Int get() = frames(11)
    val pageFadeOutDurationMillis: Int get() = frames(8)

    // 图标旋转和下载进度使用同一套帧基准，避免不同刷新率下节奏不一致。
    val iconRotationDurationMillis: Int get() = frames(13)
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

/**
 * 监听当前窗口 Display 的刷新率变化。系统切换省电/高刷模式时会触发
 * onDisplayChanged，配置随即重组，不需要重启页面或应用。
 */
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

