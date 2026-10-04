package com.luckyalanzhou.barcodegenerator.ui.component

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View

/** Light opening acknowledgement; never use the stronger LONG_PRESS effect for menus. */
internal fun menuOpenHapticType(apiLevel: Int): Int =
    if (apiLevel >= 34) HapticFeedbackConstants.SEGMENT_TICK
    else HapticFeedbackConstants.CLOCK_TICK

internal fun View.performLightMenuOpenHaptic() {
    // Respect system/view haptics settings; no force flags or stronger fallback.
    performHapticFeedback(menuOpenHapticType(Build.VERSION.SDK_INT))
}
