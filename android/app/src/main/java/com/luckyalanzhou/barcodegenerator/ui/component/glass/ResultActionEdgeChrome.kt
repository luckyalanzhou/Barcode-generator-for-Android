package com.luckyalanzhou.barcodegenerator.ui.component.glass

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.platform.LocalDensity
import com.luckyalanzhou.barcodegenerator.ui.theme.LocalAppColorScheme
import com.luckyalanzhou.barcodegenerator.ui.theme.LocalAppDimensions
import com.luckyalanzhou.barcodegenerator.ui.theme.LocalVisualEffectsPolicy

/** 在已有控件范围内复用结果页按钮边缘，不改变尺寸、填充或交互。 */
@Composable
internal fun Modifier.resultActionEdgeChrome(
    shape: Shape = RoundedCornerShape(LocalAppDimensions.current.buttonCornerRadius),
    rimBackground: Color? = null,
    rimAccent: Color? = null,
    showInnerHighlight: Boolean = true,
): Modifier {
    val colors = LocalAppColorScheme.current
    val effects = LocalVisualEffectsPolicy.current
    val density = LocalDensity.current.density
    val background = rimBackground ?: colors.surfaces.background
    val accent = rimAccent ?: colors.controls.accent
    return drawWithCache {
        onDrawWithContent {
            drawContent()
            drawResultActionRim(
                shape = shape,
                layoutDirection = layoutDirection,
                background = background,
                accent = accent,
                density = density,
                highContrast = effects.highContrast,
                showInnerHighlight = showInnerHighlight,
                bounds = size,
            )
        }
    }
}
