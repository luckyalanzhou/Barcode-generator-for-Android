package com.luckyalanzhou.barcodegenerator.ui.component.menu

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/** 菜单只调整材质厚度与可读性，不启用控件的动态透镜、色散或流光。 */
@Immutable
internal data class MenuMaterialSpec(val opacity: Float, val blurDp: Float,
    val outline: Color, val highlight: Color)

internal fun menuMaterialSpec(background: Color, foreground: Color, heightDp: Float,
    highContrast: Boolean): MenuMaterialSpec {
    val dark = background.luminance() < .35f
    val height = if (heightDp.isFinite()) heightDp else 80f
    val thickness = ((height - 80f) / 160f).coerceIn(0f, 1f)
    return MenuMaterialSpec(
        opacity = if (highContrast) 1f else (if (dark) .78f else .70f) + .03f * thickness,
        blurDp = if (highContrast) 0f else 12f + 2f * thickness,
        outline = foreground.copy(alpha = if (highContrast) .70f else if (dark) .18f else .10f),
        highlight = Color.White.copy(alpha = if (highContrast) 0f else if (dark) .28f else .62f),
    )
}
