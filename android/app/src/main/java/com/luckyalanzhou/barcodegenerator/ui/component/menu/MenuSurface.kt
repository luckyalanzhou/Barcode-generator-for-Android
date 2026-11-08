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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.CompositingStrategy
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
import com.luckyalanzhou.barcodegenerator.ui.component.glass.rememberScreenGlassBackdrop
import com.luckyalanzhou.barcodegenerator.ui.component.glass.LocalLiquidGlassBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.snap
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput

/** 操作菜单和上下文菜单共享 Kyant 材质；跨窗口背景按屏幕坐标采样。 */
@Composable
internal fun MenuSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(12.dp),
    color: Color = LocalAppColorScheme.current.surfaces.panel,
    dynamicBackdrop: Boolean = true,
    screenSpaceBackdrop: Boolean = false,
    backdropRenderer: BackdropRenderer? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val policy = LocalVisualEffectsPolicy.current
    val density = LocalDensity.current
    val backdrop = if (screenSpaceBackdrop) rememberScreenGlassBackdrop() else LocalLiquidGlassBackdrop.current
    val useKyant = android.os.Build.VERSION.SDK_INT >= 33 && backdrop != null && dynamicBackdrop && !policy.opaqueGlass
    var pressed by remember { mutableStateOf(false) }
    val press by animateFloatAsState(if (pressed) 1f else 0f,
        if (policy.reduceMotion) snap() else spring(.7f, 400f), label = "menu-glass-contact")
    val layoutDirection = LocalLayoutDirection.current
    var heightDp by remember { mutableFloatStateOf(80f) }
    val spec = menuMaterialSpec(color, LocalAppColorScheme.current.text.primary, heightDp, policy.highContrast)
    val renderer = if (dynamicBackdrop && !useKyant) {
        backdropRenderer ?: rememberGlassBackdropRenderer(menuMaterial = true)
    } else null
    // 轮廓取实际 shape，防止 16dp 下拉菜单却用 12dp shader 蒙版。
    var measuredSize by remember { mutableStateOf(IntSize.Zero) }
    val measuredOutline = shape.createOutline(Size(measuredSize.width.toFloat(), measuredSize.height.toFloat()), layoutDirection, density)
    val cornerDp = ((measuredOutline as? androidx.compose.ui.graphics.Outline.Rounded)?.roundRect?.topLeftCornerRadius?.x ?: 0f) / density.density
    Box(modifier.graphicsLayer {
        compositingStrategy = CompositingStrategy.Offscreen
    }.then(if (useKyant) Modifier.drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        effects = {
            vibrancy()
            blur(8.dp.toPx())
            lens(24.dp.toPx(), (16f + 8f * press).dp.toPx(), chromaticAberration = true)
        },
        highlight = { Highlight.Default.copy(alpha = .45f + .5f * press) },
        shadow = { Shadow(alpha = .15f + .15f * press) },
        onDrawSurface = { drawRect(color.copy(alpha = .42f + .06f * press)) },
    ) else Modifier).clip(shape).pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            pressed = true
            try {
                do { val event = awaitPointerEvent(PointerEventPass.Initial) }
                while (event.changes.any { it.pressed })
            } finally { pressed = false }
        }
    }.onSizeChanged {
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
        if (!useKyant) Box(Modifier.matchParentSize().background(color))
        if (dynamicBackdrop && !useKyant) {
            // 仅下拉菜单采样背景；长按上下文菜单不创建渲染器，也不执行模糊或折射。
            GlassBackdropSurface(Modifier.matchParentSize(), color, opacity = spec.opacity,
                cornerDp = cornerDp, blurDp = spec.blurDp, refractionDp = { 0f },
                drawFallback = false, screenCoordinates = true, renderer = renderer)
        }
        content()
    }
}
