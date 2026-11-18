package com.luckyalanzhou.barcodegenerator.ui.component.glass

import android.os.Build
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.snap
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.luckyalanzhou.barcodegenerator.icons.KeyboardArrowRightIcon
import com.luckyalanzhou.barcodegenerator.ui.theme.LocalAppColorScheme
import com.luckyalanzhou.barcodegenerator.ui.theme.LocalVisualEffectsPolicy
import kotlinx.coroutines.delay

/** Secondary settings pages use a compact round glass control, separate from popup menu surfaces. */
@Composable
internal fun GlassBackButton(
    onClick: () -> Unit,
    contentDescription: String,
    backdropCaptureKey: String,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppColorScheme.current
    val effects = LocalVisualEffectsPolicy.current
    val density = LocalDensity.current.density
    val interaction = remember { MutableInteractionSource() }
    val touch = remember { GlassPointerTracker() }
    val pressed by interaction.collectIsPressedAsState()
    val press by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = if (effects.reduceMotion) snap() else spring(dampingRatio = .72f, stiffness = 520f),
        label = "settings-glass-back-press",
    )
    // A screenshot backdrop keeps this control outside the page's LayerBackdrop recording tree,
    // avoiding a RenderNode cycle. Wait for the secondary page's fade-in before capture so the
    // snapshot does not preserve the previous route under the glass.
    var backdropReady by remember(backdropCaptureKey) { mutableStateOf(false) }
    LaunchedEffect(backdropCaptureKey, effects.reduceMotion) {
        if (!effects.reduceMotion) delay(250)
        backdropReady = true
    }
    val kyantBackdrop = if (Build.VERSION.SDK_INT >= 33 && !effects.opaqueGlass) {
        if (backdropReady) {
            rememberScreenGlassBackdrop(captureKey = backdropCaptureKey, waitForNextFrame = true)
        } else null
    } else null
    val lightSurface = colors.surfaces.background.luminance() > .5f

    Box(
        modifier = modifier.size(56.dp)
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.matchParentSize()
                .then(touch.pointerModifier)
                .graphicsLayer {
                    shape = CircleShape
                    clip = true
                }
                .then(if (kyantBackdrop != null) Modifier.drawBackdrop(
                    backdrop = kyantBackdrop,
                    shape = { CircleShape },
                    effects = {
                        vibrancy()
                        blur(1.5f * density)
                        val progress = press.coerceIn(0f, 1f)
                        lens((12f + 3f * progress) * density, (20f + 5f * progress) * density)
                    },
                    layerBlock = {
                        val scale = 1f + .035f * press.coerceIn(0f, 1f)
                        scaleX = scale
                        scaleY = scale
                    },
                    highlight = {
                        val progress = press.coerceIn(0f, 1f)
                        Highlight.Default.copy(alpha = .38f + .30f * progress)
                    },
                    shadow = {
                        val progress = press.coerceIn(0f, 1f)
                        Shadow(alpha = (if (lightSurface) .22f else .12f) + .20f * progress)
                    },
                    innerShadow = {
                        val progress = press.coerceIn(0f, 1f)
                        InnerShadow(
                            radius = (2f + 2f * progress).dp,
                            alpha = (if (lightSurface) .06f else .10f) + .10f * progress,
                        )
                    },
                    onDrawSurface = {
                        val progress = press.coerceIn(0f, 1f)
                        val opacity = if (lightSurface) .54f else .18f
                        val alpha = (glassOpacityForStrength(opacity, effects.glassStrengthPercent) + .08f * progress)
                            .coerceIn(0f, 1f)
                        drawCircle((if (lightSurface) Color.White else Color(0xFF18191D)).copy(alpha = alpha))
                        drawGlassTouchHighlight(touch.touchPosition, progress, if (lightSurface) .14f else .19f)
                    },
                ) else Modifier.drawBehind {
                    val base = if (effects.opaqueGlass) 1f else glassOpacityForStrength(
                        if (lightSurface) .54f else .18f,
                        effects.glassStrengthPercent,
                    )
                    drawCircle(colors.surfaces.panel.copy(alpha = base))
                })
                .drawWithCache {
                    val outerStroke = Stroke(1.dp.toPx())
                    val innerStroke = Stroke(.5.dp.toPx())
                    onDrawWithContent {
                        drawContent()
                        drawCircle(
                            color = Color.White.copy(alpha = if (lightSurface) .82f else .18f),
                            style = outerStroke,
                        )
                        drawCircle(
                            color = Color.Black.copy(alpha = if (lightSurface) .08f else .22f),
                            radius = size.minDimension / 2f - 1.dp.toPx(),
                            style = innerStroke,
                        )
                    }
                },
        )
        Icon(
            imageVector = KeyboardArrowRightIcon,
            contentDescription = null,
            tint = colors.text.primary,
            modifier = Modifier.size(40.dp).graphicsLayer { scaleX = -1f },
        )
    }
}
