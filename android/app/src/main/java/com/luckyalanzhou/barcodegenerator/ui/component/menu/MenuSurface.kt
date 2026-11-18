package com.luckyalanzhou.barcodegenerator.ui.component.menu

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.geometry.Size
import com.luckyalanzhou.barcodegenerator.ui.theme.LocalAppColorScheme
import com.luckyalanzhou.barcodegenerator.ui.theme.LocalVisualEffectsPolicy
import com.luckyalanzhou.barcodegenerator.ui.theme.MenuMetrics
import com.luckyalanzhou.barcodegenerator.ui.theme.actionMenuShadowColors
import com.luckyalanzhou.barcodegenerator.ui.component.glass.GlassBackdropSurface
import com.luckyalanzhou.barcodegenerator.ui.component.glass.BackdropRenderer
import com.luckyalanzhou.barcodegenerator.ui.component.glass.rememberGlassBackdropRenderer
import com.luckyalanzhou.barcodegenerator.ui.component.glass.rememberScreenGlassBackdrop
import com.luckyalanzhou.barcodegenerator.ui.component.glass.LocalLiquidGlassBackdrop
import com.luckyalanzhou.barcodegenerator.ui.component.glass.GlassPointerTracker
import com.luckyalanzhou.barcodegenerator.ui.component.glass.drawGlassTouchHighlight
import com.luckyalanzhou.barcodegenerator.ui.component.glass.glassOpacityForStrength
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.snap
import kotlin.math.tanh

/** 操作菜单和上下文菜单共享 Kyant 材质；跨窗口背景按屏幕坐标采样。 */
@Composable
internal fun MenuSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(MenuMetrics.surfaceCornerRadius),
    color: Color = LocalAppColorScheme.current.surfaces.panel,
    dynamicBackdrop: Boolean = true,
    screenSpaceBackdrop: Boolean = false,
    clearerGlass: Boolean = false,
    obscuresBackdropContent: Boolean = false,
    shadowElevation: Dp = 0.dp,
    backdropRenderer: BackdropRenderer? = null,
    screenBackdropCaptureKey: Any? = Unit,
    waitForScreenBackdropFrame: Boolean = false,
    externalGlassMotion: State<Offset>? = null,
    backdropWindow: android.view.Window? = null,
    trackPointer: Boolean = true,
    drawBottomOutline: Boolean = true,
    drawKyantHighlight: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    val policy = LocalVisualEffectsPolicy.current
    val density = LocalDensity.current
    val screenBackdrop = if (screenSpaceBackdrop && dynamicBackdrop && !policy.opaqueGlass &&
        android.os.Build.VERSION.SDK_INT >= 33) rememberScreenGlassBackdrop(
            softenBackdropColor = clearerGlass,
            captureKey = screenBackdropCaptureKey,
            waitForNextFrame = waitForScreenBackdropFrame,
            sourceWindow = backdropWindow,
        ) else null
    val backdrop = if (screenSpaceBackdrop) screenBackdrop else LocalLiquidGlassBackdrop.current
    var screenBounds by remember { mutableStateOf(Rect.Zero) }
    val useKyant = android.os.Build.VERSION.SDK_INT >= 33 && backdrop != null && dynamicBackdrop && !policy.opaqueGlass
    val touch = remember { GlassPointerTracker() }
    val press by animateFloatAsState(if (trackPointer && touch.isPressed) 1f else 0f,
        if (policy.reduceMotion) snap() else spring(.7f, 400f), label = "menu-glass-contact")
    val currentGlassMotion = { externalGlassMotion?.value ?: Offset.Zero }
    val glassProgress = { maxOf(press, currentGlassMotion().getDistance().coerceIn(0f, 1f)) }
    val layoutDirection = LocalLayoutDirection.current
    var heightDp by remember { mutableFloatStateOf(80f) }
    val spec = menuMaterialSpec(color, LocalAppColorScheme.current.text.primary, heightDp,
        policy.highContrast, policy.glassStrengthPercent)
    val surfaceOpacity = if (obscuresBackdropContent) 1f else spec.opacity
    val thickness = ((heightDp - 80f) / 240f).coerceIn(0f, 1f)
    val surfaceLuminance = color.luminance()
    val shadowColors = actionMenuShadowColors(surfaceLuminance < .35f)
    val backgroundLuminance = remember(screenBackdrop, screenBounds) {
        androidx.compose.runtime.derivedStateOf { screenBackdrop?.averageLuminance(screenBounds) }
    }.value
    val contrastTint by animateFloatAsState(
        kotlin.math.abs((backgroundLuminance ?: surfaceLuminance) - surfaceLuminance) * .35f,
        if (policy.reduceMotion) snap() else spring(1f, 400f),
        label = "menu-background-contrast",
    )
    val renderer = if (dynamicBackdrop && !policy.opaqueGlass && !useKyant) {
        backdropRenderer ?: rememberGlassBackdropRenderer(menuMaterial = true)
    } else null
    // 轮廓取实际 shape，防止 16dp 下拉菜单却用 12dp shader 蒙版。
    var measuredSize by remember { mutableStateOf(IntSize.Zero) }
    val measuredOutline = shape.createOutline(Size(measuredSize.width.toFloat(), measuredSize.height.toFloat()), layoutDirection, density)
    val cornerDp = ((measuredOutline as? androidx.compose.ui.graphics.Outline.Rounded)?.roundRect?.topLeftCornerRadius?.x ?: 0f) / density.density
    val surfaceShape = shape
    Box(modifier.then(if (shadowElevation > 0.dp) Modifier.graphicsLayer {
        this.shadowElevation = shadowElevation.toPx()
        this.shape = surfaceShape
        clip = true
        ambientShadowColor = shadowColors.ambient
        spotShadowColor = shadowColors.spot
    } else Modifier).then(if (screenBackdrop != null) Modifier.onGloballyPositioned { coordinates ->
        val windowOrigin = coordinates.localToScreen(Offset.Zero) - coordinates.localToWindow(Offset.Zero)
        if (windowOrigin.x.isFinite() && windowOrigin.y.isFinite()) {
            screenBounds = coordinates.boundsInWindow().translate(windowOrigin)
        }
    } else Modifier).graphicsLayer {
        compositingStrategy = CompositingStrategy.Offscreen
    }.then(if (useKyant) Modifier.drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        effects = {
            vibrancy()
            blur((if (clearerGlass) 2f else 3f + 2f * thickness).dp.toPx())
            val strength = glassProgress()
            val rim = ((if (clearerGlass) 16f else 12f) + 6f * strength).coerceAtMost(cornerDp)
            if (rim > 0f) lens(rim.dp.toPx(), (10f + 10f * strength).dp.toPx())
        },
        layerBlock = {
            val strength = glassProgress()
            val motion = currentGlassMotion()
            val drag = if (touch.isPressed) touch.dragOffset else Offset.Zero
            val maxOffset = size.minDimension.coerceAtLeast(1f)
            translationX = maxOffset * tanh(.05f * drag.x / maxOffset) + motion.x * 2.dp.toPx()
            translationY = maxOffset * tanh(.05f * drag.y / maxOffset) + motion.y * 2.dp.toPx()
            val scale = 1f + .02f * strength
            scaleX = scale
            scaleY = scale
        },
        highlight = if (drawKyantHighlight) {
            { Highlight.Default.copy(alpha = .36f + .28f * glassProgress()) }
        } else null,
        shadow = { Shadow(alpha = .16f + .10f * glassProgress()) },
        onDrawSurface = {
            val strength = glassProgress()
            val tint = if (obscuresBackdropContent) surfaceOpacity
                else if (clearerGlass) .34f + .12f * thickness else .36f + .16f * thickness
            val edgeTint = if (obscuresBackdropContent) 1f else glassOpacityForStrength(
                (tint + contrastTint).coerceIn(0f, 1f), policy.glassStrengthPercent)
            if (clearerGlass && !obscuresBackdropContent) {
                val rimFraction = (16.dp.toPx() / size.height).coerceIn(.01f, .45f)
                val edge = color.copy(alpha = edgeTint)
                val center = color.copy(alpha = glassOpacityForStrength(
                    (tint + contrastTint + .30f).coerceAtMost(.92f), policy.glassStrengthPercent))
                drawRect(Brush.verticalGradient(
                    0f to edge,
                    rimFraction to center,
                    1f - rimFraction to center,
                    1f to edge,
                ))
            } else {
                drawRect(color.copy(alpha = edgeTint))
            }
            val motion = currentGlassMotion()
            val localTouch = if (touch.isPressed || (strength > .001f && motion == Offset.Zero)) {
                touch.touchPosition
            } else if (strength > .001f) {
                Offset(size.width * (.5f + .35f * motion.x), size.height * (.5f + .35f * motion.y))
            } else {
                touch.touchPosition
            }
            drawGlassTouchHighlight(localTouch, strength, if (surfaceLuminance > .5f) .14f else .18f)
        },
    ) else Modifier).clip(shape).then(if (trackPointer) touch.pointerModifier else Modifier).onSizeChanged {
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
            if (drawBottomOutline) {
                drawOutline(outline, spec.outline, style = stroke)
                if (!policy.highContrast && !policy.opaqueGlass) drawOutline(outline, highlight, style = Stroke(1f))
            } else {
                clipRect(bottom = (size.height - stroke.width * 2f).coerceAtLeast(0f)) {
                    drawOutline(outline, spec.outline, style = stroke)
                    if (!policy.highContrast && !policy.opaqueGlass) {
                        drawOutline(outline, highlight, style = Stroke(1f))
                    }
                }
            }
        }
    }) {
        if (!useKyant) Box(Modifier.matchParentSize().background(color))
        if (dynamicBackdrop && !useKyant) {
            GlassBackdropSurface(Modifier.matchParentSize(), color, opacity = surfaceOpacity,
                cornerDp = cornerDp, blurDp = spec.blurDp, refractionDp = { 0f }, strengthPercent = 50,
                drawFallback = false, screenCoordinates = true, renderer = renderer)
        }
        content()
    }
}
