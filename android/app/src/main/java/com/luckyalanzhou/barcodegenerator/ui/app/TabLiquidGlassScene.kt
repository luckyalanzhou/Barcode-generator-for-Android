package com.luckyalanzhou.barcodegenerator.ui.app

import android.animation.ValueAnimator
import android.os.Build
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import com.luckyalanzhou.barcodegenerator.ui.theme.LocalVisualEffectsPolicy

/** A single output layer. Only drawing reads position/contact; child semantics remain unchanged. */
@Composable
internal fun TabLiquidGlassScene(
    motion: TabGlassMotionState,
    tabCount: Int,
    accent: Color,
    background: Color,
    visible: Boolean,
    content: @Composable BoxScope.() -> Unit,
) {
    var sceneSize by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current.density
    val policy = LocalVisualEffectsPolicy.current
    val renderer = rememberGlassBackdropRenderer()
    val backdropAvailable = glassBackdropAvailable(policy, renderer)
    val foregroundFactory = remember { lazy {
        if (Build.VERSION.SDK_INT >= 33) TabForegroundLensRenderer.createOrNull() else null
    } }
    val foregroundLayer = rememberGraphicsLayer()
    val atlasLayer = rememberGraphicsLayer()
    val backdropSource = LocalGlassBackdrop.current
    var foregroundOrigin by remember { mutableStateOf(Offset.Zero) }
    val material = remember(background, sceneSize.height, density) {
        tabGlassMaterial(background, sceneSize.height / density)
    }
    val resolvedMaterial = if (policy.opaqueGlass) material.copy(surfaceOpacity = 1f, whiteLift = 0f, accentTint = 0f) else material
    val active = !policy.reduceMotion && visible && (motion.dragging || motion.settling || motion.pressed)
    val activity = animateFloatAsState(
        targetValue = if (active) 1f else 0f,
        animationSpec = tween(if (policy.reduceMotion) 0 else if (active) 80 else 180, easing = FastOutSlowInEasing),
        label = "tab-glass-optical-strength",
    )
    val direction = animateFloatAsState(
        targetValue = motion.direction,
        animationSpec = tween(if (policy.reduceMotion) 0 else 90),
        label = "tab-glass-light-direction",
    )
    val frameProvider = {
        val animationsEnabled = !policy.reduceMotion && ValueAnimator.areAnimatorsEnabled()
        tabGlassFrame(
            sceneSize.width.toFloat(), sceneSize.height.toFloat(), density, tabCount,
            motion.progress, if (animationsEnabled) activity.value else 0f,
            if (animationsEnabled) motion.impact.value else 0f, direction.value,
            motion.touchX.takeIf { it.isFinite() }, motion.touchY.takeIf { it.isFinite() },
            motion.contactSpread.value,
            resolvedMaterial.refractionDp,
            velocityTabsPerSecond = motion.velocity,
        )
    }
    // Background owns the material; this separate foreground layer only displaces moving pixels.
    Box(Modifier.fillMaxSize().onSizeChanged { sceneSize = it }) {
        if (visible && !policy.opaqueGlass) {
            GlassBackdropSurface(
                modifier = Modifier.fillMaxSize(), color = background,
                opacity = material.surfaceOpacity, cornerDp = sceneSize.height / density / 2f,
                blurDp = 1.5f, refractionDp = { frameProvider().refractionPx / density }, capsule = frameProvider, drawFallback = false,
                renderer = renderer,
            )
        }
        if (visible) {
            TabGlassSurface(frameProvider, resolvedMaterial.copy(surfaceOpacity =
                if (backdropAvailable) 0f else resolvedMaterial.surfaceOpacity.coerceAtLeast(.82f)),
                accent, background, policy.highContrast)
        }
        Box(Modifier.fillMaxSize().onGloballyPositioned {
            foregroundOrigin = it.localToWindow(Offset.Zero)
        }.drawWithContent {
            val frame = frameProvider()
            val displacement = if (visible && !policy.reduceMotion && !policy.opaqueGlass &&
                ValueAnimator.areAnimatorsEnabled()) tabForegroundDisplacement(frame, motion.velocity) else 0f
            val region = tabForegroundRegion(frame, IntSize(size.width.toInt(), size.height.toInt()))
            val atlasSize = region?.let { tabForegroundAtlasSize(it.size) }
            val foregroundRenderer = if (Build.VERSION.SDK_INT >= 33 && backdropAvailable &&
                backdropSource?.ready == true && displacement > .001f && atlasSize != null)
                foregroundFactory.value else null
            if (Build.VERSION.SDK_INT >= 33 && foregroundRenderer != null && backdropSource != null && atlasSize != null) {
                val localFrame = region.localFrame(frame)
                // Outside the integer-aligned crop, draw the original pixels once, without a shader.
                clipRect(region.left.toFloat(), 0f, region.right.toFloat(), frame.height, ClipOp.Difference) {
                    this@drawWithContent.drawContent()
                }
                traceGlassDraw("TabGlass.ForegroundRecord") {
                    foregroundLayer.record { this@drawWithContent.drawContent() }
                }
                traceGlassDraw("TabGlass.AtlasRecord") {
                    atlasLayer.record(size = atlasSize) {
                        clipRect(0f, 0f, localFrame.width, localFrame.height) {
                            drawRect(background)
                            val offset = backdropSource.origin - foregroundOrigin
                            translate(offset.x - region.left, offset.y) { drawLayer(backdropSource.layer) }
                        }
                        clipRect(localFrame.width, 0f, localFrame.width * 2f, localFrame.height) {
                            translate(localFrame.width - region.left, 0f) { drawLayer(foregroundLayer) }
                        }
                    }
                }
                atlasLayer.renderEffect = foregroundRenderer.effect(localFrame, displacement, background, material.surfaceOpacity)
                traceGlassDraw("TabGlass.AtlasDraw") {
                    translate(region.left.toFloat(), 0f) { drawLayer(atlasLayer) }
                }
            } else {
                atlasLayer.renderEffect = null
                drawContent()
            }
        }, content = content)
    }
}
