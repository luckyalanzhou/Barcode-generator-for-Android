package com.luckyalanzhou.barcodegenerator.ui.component.menu

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.geometry.Size
import com.luckyalanzhou.barcodegenerator.ui.theme.LocalAppColorScheme
import com.luckyalanzhou.barcodegenerator.ui.theme.LocalVisualEffectsPolicy
import com.luckyalanzhou.barcodegenerator.ui.component.glass.GlassBackdropSurface
import com.luckyalanzhou.barcodegenerator.ui.component.glass.BackdropRenderer
import com.luckyalanzhou.barcodegenerator.ui.component.glass.rememberGlassBackdropRenderer

/** 菜单材质入口：长按上下文菜单默认使用静态表面；下拉菜单可显式启用背景采样。 */
@Composable
internal fun MenuSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(12.dp),
    color: Color = LocalAppColorScheme.current.surfaces.panel,
    dynamicBackdrop: Boolean = false,
    backdropRenderer: BackdropRenderer? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val policy = LocalVisualEffectsPolicy.current
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    var heightDp by remember { mutableFloatStateOf(80f) }
    val spec = menuMaterialSpec(color, LocalAppColorScheme.current.text.primary, heightDp, policy.highContrast)
    val renderer = if (dynamicBackdrop) {
        backdropRenderer ?: rememberGlassBackdropRenderer(menuMaterial = true)
    } else null
    // 轮廓取实际 shape，防止 16dp 下拉菜单却用 12dp shader 蒙版。
    var measuredSize by remember { mutableStateOf(IntSize.Zero) }
    val measuredOutline = shape.createOutline(Size(measuredSize.width.toFloat(), measuredSize.height.toFloat()), layoutDirection, density)
    val cornerDp = ((measuredOutline as? androidx.compose.ui.graphics.Outline.Rounded)?.roundRect?.topLeftCornerRadius?.x ?: 0f) / density.density
    Box(modifier.clip(shape).onSizeChanged {
        heightDp = with(density) { it.height.toDp().value }
        measuredSize = it
    }.drawWithCache {
        val outline = shape.createOutline(size, layoutDirection, this)
        val stroke = Stroke(if (policy.highContrast) 1.dp.toPx() else .5.dp.toPx().coerceAtLeast(1f))
        val highlight = Brush.verticalGradient(
            0f to spec.highlight,
            .42f to spec.highlight.copy(alpha = spec.highlight.alpha * .42f),
            1f to Color.Transparent,
            endY = size.height * .58f,
        )
        onDrawWithContent {
            drawContent()
            drawOutline(outline, spec.outline, style = stroke)
            if (!policy.highContrast) drawOutline(outline, highlight, style = Stroke(1f))
        }
    }) {
        Box(Modifier.matchParentSize().background(color))
        if (dynamicBackdrop) {
            // 仅下拉菜单采样背景；长按上下文菜单不创建渲染器，也不执行模糊或折射。
            GlassBackdropSurface(Modifier.matchParentSize(), color, opacity = spec.opacity,
                cornerDp = cornerDp, blurDp = spec.blurDp, refractionDp = { 0f },
                drawFallback = false, screenCoordinates = true, renderer = renderer)
        }
        content()
    }
}
