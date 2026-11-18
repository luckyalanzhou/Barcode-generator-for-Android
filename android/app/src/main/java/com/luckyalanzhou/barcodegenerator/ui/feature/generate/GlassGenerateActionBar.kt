package com.luckyalanzhou.barcodegenerator.ui.feature.generate

import androidx.compose.ui.res.stringResource
import com.luckyalanzhou.barcodegenerator.R
import android.os.Build
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.backdrops.rememberCanvasBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow
import com.luckyalanzhou.barcodegenerator.ui.component.glass.GlassPointerTracker
import com.luckyalanzhou.barcodegenerator.ui.component.glass.drawGlassTouchHighlight
import com.luckyalanzhou.barcodegenerator.ui.component.glass.glassOpacityForStrength
import com.luckyalanzhou.barcodegenerator.icons.AddIcon
import com.luckyalanzhou.barcodegenerator.icons.ContentPasteIcon
import com.luckyalanzhou.barcodegenerator.icons.PhotoCameraIcon
import com.luckyalanzhou.barcodegenerator.ui.theme.LocalAppColorScheme
import com.luckyalanzhou.barcodegenerator.ui.theme.LocalVisualEffectsPolicy

@Composable
internal fun GlassGenerateActionBar(
    onAddRow: () -> Unit,
    onBatchPaste: () -> Unit,
    onCaptureText: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val gap = 8.dp
    val colors = LocalAppColorScheme.current
    val background = colors.surfaces.background
    // These buttons sit in the same page subtree recorded by the app shell. Sampling that
    // ancestor from inside its recording creates a RenderNode cycle; this region has only
    // the page background behind it, so use a coordinate-independent canvas backdrop.
    val backdrop = rememberCanvasBackdrop { drawRect(background) }
    val light = background.luminance() > .5f
    val pasteTint = if (light) Color(0xFF08796C) else Color(0xFF59D7C3)
    val cameraTint = if (light) Color(0xFF7444AE) else Color(0xFFC7A4FF)
    val labels = listOf(
        stringResource(R.string.add_row),
        stringResource(R.string.batch_paste),
        stringResource(R.string.ocr_take_photo),
    )
    val labelStyle = LocalTextStyle.current.copy(fontSize = 15.sp)
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    BoxWithConstraints(
        modifier.fillMaxWidth().testTag("generate-actions").pointerInput(Unit) {
            awaitEachGesture {
                do {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    if (event.changes.count { it.pressed } > 1) {
                        event.changes.forEach { it.consume() }
                    }
                } while (event.changes.any { it.pressed })
            }
        },
    ) {
        val buttonWidth = (maxWidth - gap * 2) / 3
        val availableInlineTextWidth = with(density) { (buttonWidth - 36.dp).toPx() }
        val hasLabelThatWouldWrapInline = remember(
            labels,
            labelStyle,
            textMeasurer,
            density,
            layoutDirection,
            availableInlineTextWidth,
        ) {
            labels.any { label ->
                textMeasurer.measure(
                    text = AnnotatedString(label),
                    style = labelStyle,
                    softWrap = false,
                    maxLines = 1,
                    layoutDirection = layoutDirection,
                    density = density,
                ).size.width > availableInlineTextWidth
            }
        }
        val stacked = hasLabelThatWouldWrapInline ||
            buttonWidth < (36 + 60 * density.fontScale).dp
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(gap)) {
            GenerateActionButton(labels[0], labelStyle, AddIcon, colors.controls.accent, stacked, backdrop, onAddRow, Modifier.weight(1f))
            GenerateActionButton(labels[1], labelStyle, ContentPasteIcon, pasteTint, stacked, backdrop, onBatchPaste, Modifier.weight(1f))
            GenerateActionButton(labels[2], labelStyle, PhotoCameraIcon, cameraTint, stacked, backdrop, onCaptureText, Modifier.weight(1f))
        }
    }
}

@Composable
private fun GenerateActionButton(
    label: String,
    labelStyle: androidx.compose.ui.text.TextStyle,
    icon: ImageVector,
    accent: Color,
    stacked: Boolean,
    backdrop: com.kyant.backdrop.Backdrop?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val touch = remember { GlassPointerTracker() }
    val pressed by interaction.collectIsPressedAsState()
    val policy = LocalVisualEffectsPolicy.current
    val colors = LocalAppColorScheme.current
    val density = LocalDensity.current.density
    val shape = RoundedCornerShape(50)
    val light = colors.surfaces.background.luminance() > .5f
    val fill = if (light) Color.White else Color(0xFF242426)
    val progress by animateFloatAsState(
        if (pressed) 1f else 0f,
        if (policy.reduceMotion) snap() else spring(dampingRatio = .9f, stiffness = 650f),
        label = "generate-action-press",
    )
    val iconTint = if (policy.highContrast) colors.text.primary else accent
    val pressedTint = { lerp(fill, accent, .18f * progress.coerceIn(0f, 1f)) }
    val surface = if (Build.VERSION.SDK_INT >= 33 && backdrop != null && !policy.opaqueGlass) {
        Modifier.drawBackdrop(
            backdrop = backdrop,
            shape = { shape },
            effects = {
                val strength = progress.coerceIn(0f, 1f)
                vibrancy()
                blur(6f * density)
                lens(10f * density, (5f + 4f * strength) * density)
            },
            layerBlock = {
                val scale = if (policy.reduceMotion) 1f else 1f - .035f * progress.coerceIn(0f, 1f)
                scaleX = scale
                scaleY = scale
            },
            highlight = { Highlight.Default.copy(alpha = .24f + .24f * progress.coerceIn(0f, 1f)) },
            shadow = { Shadow(alpha = if (light) .06f else .12f) },
            onDrawSurface = {
                val baseOpacity = glassOpacityForStrength(
                    if (light) .34f else .47f, policy.glassStrengthPercent)
                drawRect(pressedTint().copy(alpha = (baseOpacity + .10f * progress.coerceIn(0f, 1f)).coerceAtMost(1f)))
                drawGlassTouchHighlight(touch.touchPosition, progress, if (light) .14f else .18f)
            },
        )
    } else {
        Modifier.graphicsLayer {
            val scale = if (policy.reduceMotion) 1f else 1f - .035f * progress.coerceIn(0f, 1f)
            scaleX = scale
            scaleY = scale
        }.background((if (policy.highContrast) fill else pressedTint()).copy(alpha = if (policy.opaqueGlass) 1f else
            (glassOpacityForStrength(.78f, policy.glassStrengthPercent) + .16f * progress.coerceIn(0f, 1f)).coerceAtMost(1f)), shape)
    }
    Box(
        modifier.fillMaxHeight().heightIn(min = 48.dp)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.matchParentSize().then(surface).border(
                if (policy.highContrast) 1.5.dp else 1.dp,
                iconTint.copy(alpha = if (policy.highContrast) 1f else if (light) .30f else .36f),
                shape,
            ).then(touch.pointerModifier),
        )
        val content: @Composable () -> Unit = {
            Icon(icon, null, Modifier.size(20.dp), tint = iconTint)
            Text(label, modifier = Modifier, color = colors.text.primary, style = labelStyle, textAlign = TextAlign.Center)
        }
        if (stacked) {
            Column(Modifier.padding(horizontal = 6.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp)) { content() }
        } else {
            Row(Modifier.padding(horizontal = 6.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)) { content() }
        }
    }
}
