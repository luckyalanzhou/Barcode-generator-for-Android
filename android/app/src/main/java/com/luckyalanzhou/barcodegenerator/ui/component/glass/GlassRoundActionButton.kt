package com.luckyalanzhou.barcodegenerator.ui.component.glass

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.luckyalanzhou.barcodegenerator.ui.theme.LocalAppColorScheme
import com.luckyalanzhou.barcodegenerator.ui.theme.LocalVisualEffectsPolicy

/** 结果页圆按钮保持静态玻璃，不随按压改变折射、形状、高光或阴影。 */
@Composable
internal fun GlassRoundActionButton(
    contentDescription: String,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    busy: Boolean = false,
    actionWidth: Dp = 64.dp,
    content: @Composable (contentTint: Color) -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    val colors = LocalAppColorScheme.current
    val effects = LocalVisualEffectsPolicy.current
    val density = LocalDensity.current.density
    val renderer = rememberGlassBackdropRenderer(roundAction = true)
    val backdropAvailable = glassBackdropAvailable(effects, renderer)
    val material = resultActionGlassMaterial(colors.surfaces.background).let {
        if (effects.opaqueGlass) it.copy(accentTint = 0f, surfaceOpacity = 1f) else it
    }
    val glassColor = tabGlassFill(colors.surfaces.background, tint, material)
    val contentTint = if (enabled || busy) tint else colors.text.disabled

    Column(
        modifier.width(actionWidth).height(48.dp)
            .clickable(
                enabled = enabled,
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics { this.contentDescription = contentDescription },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.width(48.dp).height(48.dp)
                .graphicsLayer {
                    val shadow = resultActionShadow(colors.surfaces.background, effects.opaqueGlass)
                    shadowElevation = shadow.elevationDp * density
                    ambientShadowColor = Color.Black.copy(alpha = shadow.ambientAlpha)
                    spotShadowColor = Color.Black.copy(alpha = shadow.spotAlpha)
                    shape = CircleShape
                }
                .clip(CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            val circlePx = 48f * density
            val frame = {
                roundActionGlassFrame(
                    width = circlePx,
                    height = circlePx,
                    density = density,
                )
            }
            GlassBackdropSurface(
                modifier = Modifier.matchParentSize(),
                color = glassColor,
                opacity = material.surfaceOpacity,
                cornerDp = 24f,
                blurDp = GlassControlDefaults.RoundActionBlurDp,
                refractionDp = { frame().refractionPx / density },
                capsule = frame,
                drawFallback = true,
                renderer = renderer,
                diagnosticName = contentDescription,
            )
            Canvas(Modifier.matchParentSize()) {
                if (!backdropAvailable || effects.highContrast) {
                    drawGlassControlBevel(
                        Offset.Zero,
                        size,
                        size.minDimension * .5f,
                        material,
                        colors.surfaces.background,
                        contentTint,
                        density,
                        effects.highContrast,
                    )
                }
            }
            // 反馈只作用于前景图标，不改变下方玻璃、折射或阴影；取消手势也会恢复。
            Box(
                Modifier.graphicsLayer {
                    alpha = if (pressed && enabled && !busy) {
                        if (effects.highContrast) .80f else .65f
                    } else 1f
                },
                contentAlignment = Alignment.Center,
            ) {
                content(contentTint)
            }
        }
    }
}
