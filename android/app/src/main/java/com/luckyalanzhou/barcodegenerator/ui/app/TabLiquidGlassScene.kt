package com.luckyalanzhou.barcodegenerator.ui.app

import android.os.Build
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
import androidx.compose.ui.unit.IntSize
import kotlin.math.abs

/** A single output layer for the tab scene. Navigation and accessibility stay on the original children. */
@Composable
internal fun TabLiquidGlassScene(
    progress: Float,
    selectedIndex: Int,
    inDrag: Boolean,
    impact: Float,
    direction: Float,
    tabCount: Int,
    dark: Boolean,
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
    val effectModifier = if (renderer != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Modifier.onSizeChanged { sceneSize = it }.graphicsLayer {
            renderEffect = if (visible && sceneSize.width > 0 && sceneSize.height > 0) {
                val moving = inDrag || abs(progress - selectedIndex) > .01f
                renderer.effect(
                    tabGlassFrame(sceneSize.width.toFloat(), sceneSize.height.toFloat(), density, tabCount,
                        progress, if (moving) 1f else 0f, impact, direction),
                    background, accent, dark,
                )
            } else null
        }
    } else Modifier
    Box(Modifier.fillMaxSize().then(effectModifier).background(background)) {
        if (renderer == null && visible) {
            TabGlassSurface(progress, selectedIndex, inDrag, impact, direction, tabCount, dark, accent)
        }
        content()
    }
}
