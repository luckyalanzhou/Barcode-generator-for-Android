package com.luckyalanzhou.barcodegenerator.ui.component

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import com.luckyalanzhou.barcodegenerator.ui.theme.LocalAppColorScheme
import com.luckyalanzhou.barcodegenerator.ui.theme.LocalAppDimensions
import com.luckyalanzhou.barcodegenerator.ui.theme.LocalVisualEffectsPolicy

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
    val dark = colors.surfaces.background.luminance() < .5f
    val edge = if (highContrast) colors.text.primary else borderColor ?: colors.borders.button
    return drawWithCache {
        val outline = shape.createOutline(size, layoutDirection, this)
        val stroke = Stroke((if (highContrast) 1.dp else .7.dp).toPx().coerceAtLeast(1f))
        val bevel = Brush.verticalGradient(listOf(
            Color.White.copy(alpha = if (dark) .32f else .9f),
            Color.Transparent, Color.Transparent), endY = size.height)
        onDrawWithContent {
            drawContent()
            drawOutline(outline, edge, style = stroke)
            if (!highContrast) drawOutline(outline, bevel, style = Stroke(1f))
        }
    }
}
