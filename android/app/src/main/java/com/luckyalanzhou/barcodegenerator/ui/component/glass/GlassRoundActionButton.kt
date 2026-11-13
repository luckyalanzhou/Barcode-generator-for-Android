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
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.snap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import android.os.Build
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.luckyalanzhou.barcodegenerator.ui.theme.LocalAppColorScheme
import com.luckyalanzhou.barcodegenerator.ui.theme.LocalVisualEffectsPolicy

/** 结果页圆按钮用清晰的静态材质，并在按压时加强透镜、高光与阴影。 */
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
    val backdrop = LocalLiquidGlassBackdrop.current
    val useKyantGlass = Build.VERSION.SDK_INT >= 33 && backdrop != null && !effects.opaqueGlass
    val pressProgress by animateFloatAsState(
        targetValue = if (pressed && enabled && !busy) 1f else 0f,
        animationSpec = if (effects.reduceMotion) snap() else spring(dampingRatio = .72f, stiffness = 520f),
        label = "result-action-glass-press",
    )
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
            .semantics {
                this.contentDescription = contentDescription
                if (busy) this.stateDescription = "正在准备"
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.width(48.dp).height(48.dp)
                .graphicsLayer {
                    val shadow = resultActionShadow(colors.surfaces.background, effects.opaqueGlass)
                    shadowElevation = if (useKyantGlass) 0f else shadow.elevationDp * density
                    ambientShadowColor = Color.Black.copy(alpha = shadow.ambientAlpha)
                    spotShadowColor = Color.Black.copy(alpha = shadow.spotAlpha)
                    shape = CircleShape
                }
                .then(if (useKyantGlass) Modifier.drawBackdrop(
                    backdrop = backdrop,
                    shape = { CircleShape },
                    effects = {
                        vibrancy()
                        val strength = pressProgress.coerceIn(0f, 1f)
                        // Keep passing barcode edges visible, with refraction concentrated near the rim.
                        blur(1.5f * density)
                        lens(
                            (11f + 4f * strength) * density,
                            (18f + 6f * strength) * density,
                            chromaticAberration = true,
                        )
                    },
                    layerBlock = {
                        val scale = 1f + (4f / 48f) * pressProgress.coerceIn(0f, 1f)
                        scaleX = scale
                        scaleY = scale
                    },
                    highlight = {
                        val progress = pressProgress.coerceIn(0f, 1f)
                        Highlight.Default.copy(alpha = .48f + .40f * progress)
                    },
                    shadow = {
                        val progress = pressProgress.coerceIn(0f, 1f)
                        Shadow(alpha = .18f + .24f * progress)
                    },
                    innerShadow = {
                        val progress = pressProgress.coerceIn(0f, 1f)
                        InnerShadow(radius = (2f + 2f * progress).dp, alpha = .12f + .13f * progress)
                    },
                    onDrawSurface = {
                        val lightSurface = colors.surfaces.background.luminance() > .5f
                        val press = pressProgress.coerceIn(0f, 1f)
                        drawCircle((if (lightSurface) Color.White else Color(0xFF121212))
                            .copy(alpha = .18f + .16f * press))
                    },
                ) else Modifier.clip(CircleShape)),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.matchParentSize()) {
                // Kyant 路径由 drawBackdrop 绘制材质；兼容路径使用本地静态底色。
                if (!useKyantGlass) drawCircle(glassColor.copy(alpha = if (effects.opaqueGlass) 1f else .88f))
                // Keep the original circular control outline over Kyant's sampled glass surface.
                drawResultActionRim(
                    colors.surfaces.background,
                    contentTint,
                    density,
                    effects.highContrast,
                )
            }
            // 反馈只作用于前景图标，不改变下方玻璃外观或阴影；取消手势也会恢复。
            Box(
                Modifier.graphicsLayer {
                    val scale = 1f - .06f * pressProgress
                    scaleX = scale
                    scaleY = scale
                },
                contentAlignment = Alignment.Center,
            ) {
                content(contentTint)
            }
        }
    }
}
