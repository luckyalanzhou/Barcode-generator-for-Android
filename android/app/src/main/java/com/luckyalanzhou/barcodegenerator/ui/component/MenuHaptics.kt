package com.luckyalanzhou.barcodegenerator.ui.component

import android.annotation.SuppressLint
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View

/** Light opening acknowledgement; never use the stronger LONG_PRESS effect for menus. */
@SuppressLint("InlinedApi") // Runtime caller passes SDK_INT; pure selector tests every supported branch.
internal fun menuOpenHapticType(apiLevel: Int): Int =
    if (apiLevel >= 34) HapticFeedbackConstants.SEGMENT_TICK
    else HapticFeedbackConstants.CLOCK_TICK

/** Frequent selection ticks are intentionally softer than the opening acknowledgement. */
@SuppressLint("InlinedApi") // These inlined constants are chosen only for their supported API level.
internal fun menuSelectionHapticType(apiLevel: Int): Int = when {
    apiLevel >= 34 -> HapticFeedbackConstants.SEGMENT_FREQUENT_TICK
    apiLevel >= 27 -> HapticFeedbackConstants.TEXT_HANDLE_MOVE
    else -> HapticFeedbackConstants.CLOCK_TICK
}

/** Each newly entered row can acknowledge once; rapid jitter must not vibrate continuously. */
internal fun shouldPerformMenuSelectionHaptic(previous: Int?, next: Int?, now: Long, last: Long): Boolean =
    next != null && next != previous && now - last >= 80L

internal fun View.performLightMenuHaptic() {
    performHapticFeedback(menuSelectionHapticType(Build.VERSION.SDK_INT))
}

internal fun View.performLightMenuOpenHaptic() {
    // Respect system/view haptics settings; no force flags or stronger fallback.
    performHapticFeedback(menuOpenHapticType(Build.VERSION.SDK_INT))
}
