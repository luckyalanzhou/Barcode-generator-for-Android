package com.luckyalanzhou.barcodegenerator.ui.component

import com.luckyalanzhou.barcodegenerator.ui.theme.*

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance

/** 全局卡片基座：统一圆角、边缘分离度和极轻阴影，页面保留自己的表面颜色。 */
@Composable
internal fun Modifier.globalCardSurface(
    dark: Boolean,
    color: Color,
    shape: RoundedCornerShape = RoundedCornerShape(LocalAppDimensions.current.cardCornerRadius),
    elevation: Dp = 3.dp,
): Modifier = this
    .shadow(minOf(elevation, 1.dp), shape, clip = true)
    .clip(shape)
    .background(color)
    .border(
        if (dark) 0.7.dp else 1.dp,
        LocalAppColorScheme.current.borders.card,
        shape,
    )

/** Static history/settings groups: separate by fill, without changing other card variants. */
@Composable
internal fun Modifier.groupedContentSurface(
    dark: Boolean,
    color: Color,
    shape: RoundedCornerShape,
): Modifier {
    val outline = LocalAppColorScheme.current.borders.card
    return this.clip(shape).background(color).then(
        if (dark) Modifier else Modifier.border(0.5.dp, outline.copy(alpha = outline.alpha * .4f), shape),
    )
}

/** 静态玻璃式按钮外框：中性轮廓保证可见性，顶部倒角亮边表达厚度，不增加内部填充。 */
@Composable
internal fun Modifier.globalButtonChrome(
    shape: RoundedCornerShape = RoundedCornerShape(LocalAppDimensions.current.buttonCornerRadius),
    elevation: Dp = 1.5.dp,
    borderColor: Color? = null,
): Modifier {
    val themeColors = LocalAppColorScheme.current
    val effects = LocalVisualEffectsPolicy.current
    val dark = themeColors.surfaces.background.luminance() < .5f
    val outlineColor = if (effects.highContrast) themeColors.text.primary
        else borderColor ?: themeColors.borders.button
    return this
        .shadow(minOf(elevation, 0.5.dp), shape, clip = false)
        .drawWithCache {
            val outline = shape.createOutline(size, layoutDirection, this)
            val stroke = Stroke((if (effects.highContrast) 1.dp else .7.dp).toPx().coerceAtLeast(1f))
            val bevel = Brush.verticalGradient(listOf(
                Color.White.copy(alpha = if (dark) .32f else .9f),
                Color.Transparent, Color.Transparent), endY = size.height)
            onDrawWithContent {
                drawContent()
                drawOutline(outline, outlineColor, style = stroke)
                if (!effects.highContrast) drawOutline(outline, bevel, style = Stroke(1f))
            }
        }
}
