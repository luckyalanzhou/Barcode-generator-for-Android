package com.luckyalanzhou.barcodegenerator.ui.dialogs

import android.os.Build
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.luckyalanzhou.barcodegenerator.ui.theme.LocalVisualEffectsPolicy
import com.luckyalanzhou.barcodegenerator.ui.theme.VisualEffectsPolicy

internal enum class PopupKind(val classicCorner: Dp, val glassCorner: Dp) {
    Card(18.dp, 30.dp),
    Options(14.dp, 22.dp),
    Sheet(20.dp, 30.dp),
    Preview(12.dp, 28.dp),
    Action(8.dp, 24.dp),
}

internal data class PopupAppearance(val glass: Boolean, val corner: Dp) {
    val shape get() = RoundedCornerShape(corner)
    val topShape get() = RoundedCornerShape(topStart = corner, topEnd = corner)
}

internal fun popupAppearance(kind: PopupKind, policy: VisualEffectsPolicy, sdk: Int): PopupAppearance {
    val glass = sdk >= 33 && policy.liquidGlassEnabled && !policy.opaqueGlass && !policy.highContrast
    return PopupAppearance(glass, if (glass) kind.glassCorner else kind.classicCorner)
}

@Composable
internal fun popupAppearance(kind: PopupKind): PopupAppearance =
    popupAppearance(kind, LocalVisualEffectsPolicy.current, Build.VERSION.SDK_INT)
