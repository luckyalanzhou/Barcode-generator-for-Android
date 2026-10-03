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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
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
    val renderer = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) TabGlassRenderer.createOrNull() else null
    }
    var sceneSize by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current.density
    val policy = LocalVisualEffectsPolicy.current
    val material = remember(background, sceneSize.height, density) {
        tabGlassMaterial(background, sceneSize.height / density)
    }
    val resolvedMaterial = if (policy.opaqueGlass) material.copy(surfaceOpacity = 1f, whiteLift = 0f, accentTint = 0f) else material
    val useGpu = !policy.opaqueGlass && renderer != null && LocalView.current.isHardwareAccelerated &&
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    val backdropReady = LocalGlassBackdrop.current?.ready == true
    val foregroundMaterial = if (backdropReady) resolvedMaterial.copy(surfaceOpacity = 0f) else resolvedMaterial
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
        )
    }
    val effectModifier = if (useGpu) {
        Modifier.graphicsLayer {
            renderEffect = if (visible && sceneSize.width > 0 && sceneSize.height > 0) {
                renderer.effect(frameProvider(), background, accent, foregroundMaterial)
            } else null
        }
    } else Modifier
    // The page beneath remains visible; the shader paints material only inside the selected capsule.
    Box(Modifier.fillMaxSize().onSizeChanged { sceneSize = it }) {
        if (useGpu && visible) {
            GlassBackdropSurface(
                modifier = Modifier.fillMaxSize(), color = background,
                opacity = material.surfaceOpacity, cornerDp = sceneSize.height / density / 2f,
                blurDp = 1.5f, refractionDp = { frameProvider().refractionPx / density }, capsule = frameProvider, drawFallback = false,
            )
        }
        Box(Modifier.fillMaxSize().then(effectModifier)) {
            if (!useGpu && visible) {
                TabGlassSurface(frameProvider, resolvedMaterial.copy(surfaceOpacity = resolvedMaterial.surfaceOpacity.coerceAtLeast(.82f)), accent, background, policy.highContrast)
            }
            content()
        }
    }
}
