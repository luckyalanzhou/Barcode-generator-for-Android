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
import com.luckyalanzhou.barcodegenerator.ui.component.glass.rememberGlassBackdropRenderer

/** 菜单唯一材质入口：只绘制背景与边缘，业务回调、定位、手势和整块动画仍由宿主管理。 */
@Composable
internal fun MenuSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(12.dp),
    color: Color = LocalAppColorScheme.current.surfaces.panel,
    content: @Composable BoxScope.() -> Unit,
) {
    val policy = LocalVisualEffectsPolicy.current
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    var heightDp by remember { mutableFloatStateOf(80f) }
    val spec = menuMaterialSpec(color, LocalAppColorScheme.current.text.primary, heightDp, policy.highContrast)
    val renderer = rememberGlassBackdropRenderer(menuMaterial = true)
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
        val highlight = Brush.verticalGradient(listOf(spec.highlight, Color.Transparent), endY = size.height * .45f)
        onDrawWithContent {
            drawContent()
            drawOutline(outline, spec.outline, style = stroke)
            if (!policy.highContrast) drawOutline(outline, highlight, style = Stroke(1f))
        }
    }) {
        Box(Modifier.matchParentSize().background(color))
        // 背景先采样、前景后绘制，文字和分割线永远不进入模糊效果。
        GlassBackdropSurface(Modifier.matchParentSize(), color, opacity = spec.opacity,
            cornerDp = cornerDp, blurDp = spec.blurDp, refractionDp = { 0f },
            drawFallback = false, screenCoordinates = true, renderer = renderer)
        content()
    }
}
