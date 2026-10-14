package com.luckyalanzhou.barcodegenerator.ui.component.glass

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.luckyalanzhou.barcodegenerator.ui.animation.ComposeAnimationConfig
import com.luckyalanzhou.barcodegenerator.ui.theme.LocalAppColorScheme
import com.luckyalanzhou.barcodegenerator.ui.theme.LocalVisualEffectsPolicy

/** Shared glass surface and press optics for circular toolbar actions. */
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
    var pressPosition by remember(interaction) { mutableStateOf<Offset?>(null) }
    LaunchedEffect(interaction) {
        interaction.interactions.collect { event ->
            if (event is PressInteraction.Press) pressPosition = event.pressPosition
        }
    }

    val colors = LocalAppColorScheme.current
    val effects = LocalVisualEffectsPolicy.current
    val density = LocalDensity.current.density
    val renderer = rememberGlassBackdropRenderer()
    val backdropAvailable = glassBackdropAvailable(effects, renderer)
    val material = resultActionGlassMaterial(colors.surfaces.background).let {
        if (effects.opaqueGlass) it.copy(accentTint = 0f, surfaceOpacity = 1f) else it
    }
    val glassColor = tabGlassFill(colors.surfaces.background, tint, material)
    val opticalActivity by animateFloatAsState(
        targetValue = if (pressed && !effects.reduceMotion && !effects.opaqueGlass) 1f else 0f,
        animationSpec = if (effects.reduceMotion) androidx.compose.animation.core.tween(0)
            else ComposeAnimationConfig.pressSpring(),
        label = "round-action-glass-interaction",
    )
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
                    val lift = opticalActivity
                    scaleX = 1f + .035f * lift
                    scaleY = 1f + .035f * lift
                    translationY = -1.5f * density * lift
                    shadowElevation = (1f + 2f * lift) * density
                    shape = CircleShape
                }
                .clip(CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            val circlePx = 48f * density
            val actionWidthPx = actionWidth.value * density
            val touchPoint = pressPosition?.let {
                Offset(
                    x = (it.x - (actionWidthPx - circlePx) * .5f).coerceIn(0f, circlePx),
                    y = it.y.coerceIn(0f, circlePx),
                )
            }
            val frame = {
                roundActionGlassFrame(
                    width = circlePx,
                    height = circlePx,
                    density = density,
                    activity = opticalActivity,
                    touch = touchPoint,
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
            )
            Canvas(Modifier.matchParentSize()) {
                val activity = opticalActivity
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
                if (!backdropAvailable && activity > .01f) {
                    val center = touchPoint ?: Offset(size.width * .5f, size.height * .5f)
                    val radius = size.minDimension * .68f
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(Color.White.copy(alpha = .13f * activity), Color.Transparent),
                            center = center,
                            radius = radius,
                        ),
                        radius = radius,
                        center = center,
                    )
                }
            }
            content(contentTint)
        }
    }
}
