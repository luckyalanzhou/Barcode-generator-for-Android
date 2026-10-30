package com.luckyalanzhou.barcodegenerator.ui.component

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
import com.luckyalanzhou.barcodegenerator.ui.theme.LocalAppColorScheme
import com.luckyalanzhou.barcodegenerator.ui.theme.LocalAppDimensions
import com.luckyalanzhou.barcodegenerator.ui.theme.LocalVisualEffectsPolicy
import com.luckyalanzhou.barcodegenerator.ui.component.glass.drawResultActionRim

/**
 * 普通按钮外框只画轮廓，不创建阴影/离屏图层、不填充内部、不负责文字或业务。
 * 透明按钮不能复用卡片的原生阴影；真正的液态玻璃按钮继续由 glass 包独立渲染。
 */
@Composable
internal fun Modifier.globalButtonChrome(
    shape: RoundedCornerShape = RoundedCornerShape(LocalAppDimensions.current.buttonCornerRadius),
    borderColor: Color? = null,
): Modifier {
    val colors = LocalAppColorScheme.current
    val highContrast = LocalVisualEffectsPolicy.current.highContrast
    val density = LocalDensity.current.density
    val edge = if (highContrast) colors.text.primary else borderColor ?: colors.text.primary
    return drawWithCache {
        val stroke = Stroke(if (highContrast) 1.dp.toPx().coerceAtLeast(1f) else (.55.dp.toPx()).coerceIn(1f, 1.5f))
        // 整条描边置于原尺寸内，避免父容器裁掉半条线；不改变测量或点击区域。
        val inset = stroke.width / 2f + .5f
        val outline = shape.createOutline(Size((size.width - 2f * inset).coerceAtLeast(0f),
            (size.height - 2f * inset).coerceAtLeast(0f)), layoutDirection, this)
        onDrawWithContent {
            drawContent()
            if (borderColor == null) {
                // 普通有框按钮复用结果按钮的同一条静态边缘高光，不增加填充或离屏层。
                drawResultActionRim(
                    shape = shape,
                    layoutDirection = layoutDirection,
                    background = colors.surfaces.background,
                    accent = colors.controls.accent,
                    density = density,
                    highContrast = highContrast,
                    bounds = size,
                )
            } else {
                translate(inset, inset) { drawOutline(outline, edge, style = stroke) }
            }
        }
    }
}
