package com.luckyalanzhou.barcodegenerator.ui.component

import android.annotation.SuppressLint
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View

/** 菜单打开时提供轻微确认；不使用更强的 LONG_PRESS 反馈。 */
@SuppressLint("InlinedApi") // Runtime caller passes SDK_INT; pure selector tests every supported branch.
internal fun menuOpenHapticType(apiLevel: Int): Int =
    if (apiLevel >= 34) HapticFeedbackConstants.SEGMENT_TICK
    else HapticFeedbackConstants.CLOCK_TICK

/** 频繁触发的选项反馈刻意设置得比菜单打开反馈更轻。 */
@SuppressLint("InlinedApi") // These inlined constants are chosen only for their supported API level.
internal fun menuSelectionHapticType(apiLevel: Int): Int = when {
    apiLevel >= 34 -> HapticFeedbackConstants.SEGMENT_FREQUENT_TICK
    apiLevel >= 27 -> HapticFeedbackConstants.TEXT_HANDLE_MOVE
    else -> HapticFeedbackConstants.CLOCK_TICK
}

/** 每次进入新选项行最多反馈一次；快速抖动不能导致连续震动。 */
internal fun shouldPerformMenuSelectionHaptic(previous: Int?, next: Int?, now: Long, last: Long): Boolean =
    next != null && next != previous && now - last >= 80L

internal fun View.performLightMenuHaptic() {
    performHapticFeedback(menuSelectionHapticType(Build.VERSION.SDK_INT))
}

internal fun View.performLightMenuOpenHaptic() {
// 遵循系统和 View 的触感设置；不强制开启，也不回退到更强的反馈。
    performHapticFeedback(menuOpenHapticType(Build.VERSION.SDK_INT))
}
