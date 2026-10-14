package com.luckyalanzhou.barcodegenerator.ui.component.glass

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/** 结果页圆按钮只保留低高度、低不透明度的接触阴影，不用厚重黑圈表示玻璃边缘。 */
@Immutable
internal data class ResultActionShadow(val elevationDp: Float, val ambientAlpha: Float, val spotAlpha: Float)

internal fun resultActionShadow(background: Color, opaque: Boolean): ResultActionShadow {
    if (opaque) return ResultActionShadow(0f, 0f, 0f)
    val dark = background.luminance() < .35f
    return ResultActionShadow(.6f,
        if (dark) .025f else .04f, if (dark) .04f else .07f)
}
