package com.luckyalanzhou.barcodegenerator.ui.app

import android.animation.ValueAnimator
import android.os.Build
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
    val material = remember(background, sceneSize.height, density) {
        tabGlassMaterial(background, sceneSize.height / density)
    }
    val useGpu = renderer != null && LocalView.current.isHardwareAccelerated &&
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    val active = visible && (motion.dragging || motion.settling || motion.pressed)
    val activity = animateFloatAsState(
        targetValue = if (active) 1f else 0f,
        animationSpec = tween(if (active) 80 else 180, easing = FastOutSlowInEasing),
        label = "tab-glass-optical-strength",
    )
    val direction = animateFloatAsState(
        targetValue = motion.direction,
        animationSpec = tween(90),
        label = "tab-glass-light-direction",
    )
    val frameProvider = {
        val animationsEnabled = ValueAnimator.areAnimatorsEnabled()
        tabGlassFrame(
            sceneSize.width.toFloat(), sceneSize.height.toFloat(), density, tabCount,
            motion.progress, if (animationsEnabled) activity.value else 0f,
            if (animationsEnabled) motion.impact.value else 0f, direction.value,
            motion.touchX.takeIf { it.isFinite() }, motion.touchY.takeIf { it.isFinite() },
            motion.contactSpread.value,
            material.refractionDp,
        )
    }
    val effectModifier = if (useGpu && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Modifier.graphicsLayer {
            renderEffect = if (visible && sceneSize.width > 0 && sceneSize.height > 0) {
                renderer.effect(frameProvider(), background, accent, material)
            } else null
        }
    } else Modifier
    Box(Modifier.fillMaxSize().onSizeChanged { sceneSize = it }.then(effectModifier).background(background)) {
        if (!useGpu && visible) {
            TabGlassSurface(frameProvider, material, accent, background)
        }
        content()
    }
}
