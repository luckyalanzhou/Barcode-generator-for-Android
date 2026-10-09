package com.luckyalanzhou.barcodegenerator.ui.component.menu

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.luckyalanzhou.barcodegenerator.ui.theme.LocalAppColorScheme
import com.luckyalanzhou.barcodegenerator.ui.theme.LocalVisualEffectsPolicy
import com.luckyalanzhou.barcodegenerator.ui.component.glass.GlassBackdropSurface

/** 菜单唯一材质入口：只绘制背景与边缘，业务回调、定位、手势和整块动画仍由宿主管理。 */
@Composable
internal fun MenuSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(12.dp),
    color: Color = LocalAppColorScheme.current.surfaces.panel,
    content: @Composable BoxScope.() -> Unit,
) {
    val policy = LocalVisualEffectsPolicy.current
    val edge = LocalAppColorScheme.current.text.primary.copy(alpha = if (policy.highContrast) .7f else .12f)
    Box(modifier.clip(shape).drawWithCache {
        val outline = shape.createOutline(size, layoutDirection, this)
        val stroke = Stroke(if (policy.highContrast) 1.dp.toPx() else .5.dp.toPx().coerceAtLeast(1f))
        onDrawWithContent {
            drawContent()
            drawOutline(outline, edge, style = stroke)
        }
    }) {
        Box(Modifier.matchParentSize().background(color))
        // 背景先采样、前景后绘制，文字和分割线永远不进入模糊效果。
        GlassBackdropSurface(Modifier.matchParentSize(), color, opacity = .78f,
            cornerDp = 12f, blurDp = 12f, refractionDp = { 0f },
            drawFallback = false, screenCoordinates = true)
        content()
    }
}
