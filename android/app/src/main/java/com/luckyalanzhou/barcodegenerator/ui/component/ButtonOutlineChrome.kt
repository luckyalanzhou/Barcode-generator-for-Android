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
    val dark = colors.surfaces.background.luminance() < .35f
    // 与静态工具按钮同一轮廓语言；普通按钮不叠加倒角高光或渐变。
    val edge = if (highContrast) colors.text.primary else borderColor ?: staticButtonOutlineColor(dark)
    return drawWithCache {
        val stroke = Stroke(if (highContrast) 1.dp.toPx().coerceAtLeast(1f) else (.55.dp.toPx()).coerceIn(1f, 1.5f))
        // 整条描边置于原尺寸内，避免父容器裁掉半条线；不改变测量或点击区域。
        val inset = stroke.width / 2f + .5f
        val outline = shape.createOutline(Size((size.width - 2f * inset).coerceAtLeast(0f),
            (size.height - 2f * inset).coerceAtLeast(0f)), layoutDirection, this)
        onDrawWithContent {
            drawContent()
            translate(inset, inset) { drawOutline(outline, edge, style = stroke) }
        }
    }
}

internal fun staticButtonOutlineColor(dark: Boolean): Color =
    if (dark) Color.White.copy(alpha = .22f) else Color.Black.copy(alpha = .16f)
