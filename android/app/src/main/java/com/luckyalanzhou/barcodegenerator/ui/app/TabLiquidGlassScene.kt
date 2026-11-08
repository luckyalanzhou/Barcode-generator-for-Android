package com.luckyalanzhou.barcodegenerator.ui.app

import android.animation.ValueAnimator
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.Capsule
import android.os.Build
import com.luckyalanzhou.barcodegenerator.ui.component.glass.*
import com.luckyalanzhou.barcodegenerator.ui.theme.LocalVisualEffectsPolicy
import kotlin.math.abs
import kotlin.math.sign

/** 绘制 Tab 玻璃背景与动态胶囊；前景内容保持原生绘制以确保手势期间稳定可见。 */
@Composable
internal fun TabLiquidGlassScene(
    motion: TabGlassMotionState,
    tabCount: Int,
    accent: Color,
    background: Color,
    visible: Boolean,
    foregroundBackdropContent: @Composable (() -> Float) -> Unit,
    content: @Composable BoxScope.(() -> TabGlassFrame) -> Unit,
) {
    var sceneSize by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current.density
    val policy = LocalVisualEffectsPolicy.current
    val kyantBackdrop = LocalLiquidGlassBackdrop.current
    val tabsBackdrop = rememberLayerBackdrop()
    val renderer = rememberGlassBackdropRenderer()
    val backdropAvailable = Build.VERSION.SDK_INT >= 33 && kyantBackdrop != null && !policy.opaqueGlass
    val material = remember(background, sceneSize.height, density) {
        tabGlassMaterial(background, sceneSize.height / density)
    }
    val resolvedMaterial = if (policy.opaqueGlass) material.copy(surfaceOpacity = 1f, accentTint = 0f) else material
    val containerColor = if (background.luminance() > .5f) {
        Color(0xFFFAFAFA).copy(alpha = .4f)
    } else {
        Color(0xFF121212).copy(alpha = .4f)
    }
    val active = !policy.reduceMotion && visible && motion.pressed
    val activity = animateFloatAsState(
        targetValue = if (active) 1f else 0f,
        animationSpec = if (policy.reduceMotion) tween(0) else spring(dampingRatio = 1f, stiffness = 1000f),
        label = "tab-glass-optical-strength",
    )
    val pressScaleX = animateFloatAsState(
        targetValue = if (motion.pressed && !policy.reduceMotion) 70f / 56f else 1f,
        animationSpec = if (policy.reduceMotion) tween(0) else spring(dampingRatio = .6f, stiffness = 250f),
        label = "tab-glass-press-scale-x",
    )
    val pressScaleY = animateFloatAsState(
        targetValue = if (motion.pressed && !policy.reduceMotion) 70f / 56f else 1f,
        animationSpec = if (policy.reduceMotion) tween(0) else spring(dampingRatio = .7f, stiffness = 250f),
        label = "tab-glass-press-scale-y",
    )
    val direction = animateFloatAsState(
        targetValue = motion.direction,
        animationSpec = tween(if (policy.reduceMotion) 0 else 90),
        label = "tab-glass-light-direction",
    )
    val frameProvider = {
        val animationsEnabled = !policy.reduceMotion && ValueAnimator.areAnimatorsEnabled()
        val frame = tabGlassFrame(
            sceneSize.width.toFloat(), sceneSize.height.toFloat(), density, tabCount,
            motion.progress, if (animationsEnabled) activity.value else 0f,
            if (animationsEnabled) motion.impact.value else 0f, direction.value,
            motion.touchX.takeIf { it.isFinite() }, motion.touchY.takeIf { it.isFinite() },
            motion.contactSpread.value,
            resolvedMaterial.refractionDp,
            velocityTabsPerSecond = motion.velocity,
            pressScaleX = if (animationsEnabled) pressScaleX.value else 1f,
            pressScaleY = if (animationsEnabled) pressScaleY.value else 1f,
            edgePull = motion.edgePull,
        )
        frame
    }
    val panelTranslationPx = {
        val fraction = (motion.panelOffsetPx / sceneSize.width.coerceAtLeast(1)).coerceIn(-1f, 1f)
        if (policy.reduceMotion) 0f else 4f * density * sign(fraction) * EaseOut.transform(abs(fraction))
    }
    // Draw the real row once, then let the selected glass capsule sample its tinted backdrop copy.
    Box(Modifier.fillMaxSize().onSizeChanged { sceneSize = it }) {
        if (backdropAvailable && visible) {
            Box(
                Modifier.fillMaxWidth().height(64.dp).align(Alignment.Center).graphicsLayer {
                    translationX = panelTranslationPx()
                }.drawBackdrop(
                    backdrop = kyantBackdrop,
                    shape = { Capsule() },
                    effects = {
                        vibrancy()
                        blur(8f * density)
                        lens(24f * density, 24f * density)
                    },
                    layerBlock = {
                        val scale = 1f + 16f * density / size.width.coerceAtLeast(1f) * frameProvider().motion
                        scaleX = scale
                        scaleY = scale
                    },
                    onDrawSurface = { drawRect(containerColor) },
            ),
            )

            Box(Modifier.fillMaxSize().graphicsLayer { translationX = panelTranslationPx() }) {
                content(frameProvider)
            }

            Box(
                Modifier.fillMaxWidth().height(56.dp).align(Alignment.Center)
                    .clearAndSetSemantics { }
                    .alpha(0f)
                    .layerBackdrop(tabsBackdrop)
                    .graphicsLayer { translationX = panelTranslationPx() }
                    .drawBackdrop(
                        backdrop = kyantBackdrop,
                        shape = { Capsule() },
                        effects = {
                            val progress = frameProvider().motion.coerceIn(0f, 1f)
                            vibrancy()
                            blur(8f * density)
                            lens(24f * density * progress, 24f * density * progress)
                        },
                        highlight = { Highlight.Default.copy(alpha = frameProvider().motion.coerceIn(0f, 1f)) },
                        onDrawSurface = { drawRect(containerColor) },
                    )
                    .padding(horizontal = 4.dp)
                    .graphicsLayer { colorFilter = ColorFilter.tint(accent) },
            ) {
                foregroundBackdropContent { 1f + .2f * frameProvider().motion }
            }

            Box(
                Modifier.align(Alignment.CenterStart).padding(horizontal = 4.dp).graphicsLayer {
                    val frame = frameProvider()
                    val contentCellWidth = (frame.width - 8f * density) / tabCount
                    translationX = frame.centerX - 4f * density - contentCellWidth / 2f + panelTranslationPx()
                }.drawBackdrop(
                    backdrop = rememberCombinedBackdrop(kyantBackdrop, tabsBackdrop),
                    shape = { Capsule() },
                    effects = {
                        val frame = frameProvider()
                        val movement = frame.motion.coerceIn(0f, 1f)
                        lens(10f * density * movement, 14f * density * movement, chromaticAberration = true)
                    },
                    layerBlock = {
                        val frame = frameProvider()
                        scaleX = frame.halfWidth / (size.width / 2f).coerceAtLeast(1f)
                        scaleY = frame.halfHeight / (size.height / 2f).coerceAtLeast(1f)
                    },
                    highlight = {
                        Highlight.Default.copy(alpha = frameProvider().motion.coerceIn(0f, 1f))
                    },
                    shadow = { Shadow(alpha = frameProvider().motion.coerceIn(0f, 1f)) },
                    innerShadow = {
                        val motionAmount = frameProvider().motion.coerceIn(0f, 1f)
                        InnerShadow(radius = 8f.dp * motionAmount, alpha = motionAmount)
                    },
                    onDrawSurface = {
                        val motionAmount = frameProvider().motion.coerceIn(0f, 1f)
                        val surfaceTint = if (background.luminance() < .35f) Color.White else Color.Black
                        drawRect(surfaceTint.copy(alpha = .04f * (1f - motionAmount)))
                        drawRect(Color.Black.copy(alpha = .03f * motionAmount))
                    },
                ).height(56.dp).fillMaxWidth(1f / tabCount),
            )
        } else if (visible && !policy.opaqueGlass) {
            Box(Modifier.fillMaxWidth().height(64.dp).align(Alignment.Center).drawBehind {
                val radius = androidx.compose.ui.geometry.CornerRadius(size.height / 2f)
                drawRoundRect(containerColor, cornerRadius = radius)
                drawRoundRect(
                    Color.White.copy(alpha = if (background.luminance() < .35f) .18f else .58f),
                    cornerRadius = radius,
                    style = Stroke(width = 1.2f * density),
                )
            })
            GlassBackdropSurface(
                modifier = Modifier.fillMaxWidth().height(64.dp).align(Alignment.Center), color = containerColor,
                opacity = .4f, cornerDp = 32f,
                blurDp = 8f, refractionDp = { 24f }, drawFallback = false,
                renderer = renderer,
            )
        }
        if (visible && !backdropAvailable) {
            TabGlassSurface(frameProvider, resolvedMaterial,
                accent, background, policy.highContrast, drawMotionHighlight = true)
        }
        if (!backdropAvailable || !visible) {
            Box(Modifier.fillMaxSize().graphicsLayer { translationX = panelTranslationPx() }) {
                content(frameProvider)
            }
        }
    }
}
