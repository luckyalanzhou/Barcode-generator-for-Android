package com.luckyalanzhou.barcodegenerator.ui.app

import android.animation.ValueAnimator
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import com.luckyalanzhou.barcodegenerator.ui.component.glass.*
import com.luckyalanzhou.barcodegenerator.ui.theme.LocalVisualEffectsPolicy

/** 绘制 Tab 玻璃背景与动态胶囊；前景内容保持原生绘制以确保手势期间稳定可见。 */
@Composable
internal fun TabLiquidGlassScene(
    motion: TabGlassMotionState,
    tabCount: Int,
    accent: Color,
    background: Color,
    visible: Boolean,
    content: @Composable BoxScope.(() -> TabGlassFrame) -> Unit,
) {
    var sceneSize by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current.density
    val policy = LocalVisualEffectsPolicy.current
    val renderer = rememberGlassBackdropRenderer()
    val backdropAvailable = glassBackdropAvailable(policy, renderer)
    val material = remember(background, sceneSize.height, density) {
        tabGlassMaterial(background, sceneSize.height / density)
    }
    val resolvedMaterial = if (policy.opaqueGlass) material.copy(surfaceOpacity = 1f, accentTint = 0f) else material
    val materialColor = tabGlassFill(background, accent, resolvedMaterial)
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
    // 背景层负责玻璃材质；图标和文字不进入可失效的 GPU 图集替换路径。
    Box(Modifier.fillMaxSize().onSizeChanged { sceneSize = it }) {
        if (visible && !policy.opaqueGlass) {
            GlassBackdropSurface(
                modifier = Modifier.fillMaxSize(), color = materialColor,
                opacity = material.surfaceOpacity, cornerDp = sceneSize.height / density / 2f,
                blurDp = GlassControlDefaults.TabBlurDp, refractionDp = { tabBackdropRefractionDp(frameProvider()) }, capsule = frameProvider, drawFallback = false,
                renderer = renderer,
            )
        }
        if (visible) {
            TabGlassSurface(frameProvider, resolvedMaterial.copy(surfaceOpacity =
                if (backdropAvailable) 0f else resolvedMaterial.surfaceOpacity),
                accent, background, policy.highContrast, drawMotionHighlight = !backdropAvailable)
        }
        // 图标和文字始终直接绘制，避免手势期间由全栏 GPU 图集替换前景而整栏闪空。
        // 动态折射、边缘高光和选中态颜色仍由背景玻璃层与胶囊裁切层负责。
        Box(Modifier.fillMaxSize()) { content(frameProvider) }
    }
}
