package com.luckyalanzhou.barcodegenerator.ui.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luckyalanzhou.barcodegenerator.ui.theme.LocalAppColorScheme
import kotlin.math.roundToInt

internal data class TabLongPressAction(
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
)

internal data class TabLongPressMenuState(
    val anchorBoundsOnScreen: Rect,
    val focusIcon: ImageVector,
    val focusLabel: String,
    val focusTint: Color,
    val dark: Boolean,
    val actions: List<TabLongPressAction>,
)

@Composable
internal fun TabLongPressActionOverlay(
    state: TabLongPressMenuState,
    progress: State<Float>,
    interactive: Boolean,
    onMeasured: () -> Unit,
    onDismiss: () -> Unit,
    onAction: (() -> Unit) -> Unit,
) {
    val dark = state.dark
    val anchorBoundsOnScreen = state.anchorBoundsOnScreen
    val focusIcon = state.focusIcon
    val focusLabel = state.focusLabel
    val focusTint = state.focusTint
    val actions = state.actions
    val colors = LocalAppColorScheme.current
    val density = LocalDensity.current
    val panelShape = remember { RoundedCornerShape(24.dp) }
    val separator = colors.borders.divider.copy(alpha = if (dark) .36f else .44f)
    var overlayOriginOnScreen by remember { mutableStateOf(Offset.Zero) }
    var overlayCoordinatesReady by remember { mutableStateOf(false) }
    var panelSize by remember { mutableStateOf(IntSize.Zero) }
    val material = menuGlassMaterial(colors.surfaces.panel, panelSize.height / density.density)

    BoxWithConstraints(
        Modifier.fillMaxSize().onGloballyPositioned { coordinates ->
            val origin = coordinates.localToScreen(Offset.Zero)
            if (overlayOriginOnScreen != origin) overlayOriginOnScreen = origin
            overlayCoordinatesReady = true
        },
    ) {
            Box(
                Modifier.fillMaxSize()
                    // Keep the backdrop frosted/light instead of dimming it like a platform dialog.
                    .background(Color.White.copy(alpha = if (dark) .08f else .16f))
                    .graphicsLayer { alpha = progress.value }
                    .clickable(enabled = interactive, onClick = onDismiss),
            )

            val panelMaxWidth = minOf(maxWidth * .62f, 340.dp)
            val menuWidthPx = panelSize.width.toFloat()
            val menuHeightPx = panelSize.height.toFloat()
            val screenWidthPx = with(density) { maxWidth.toPx() }
            val edgePaddingPx = with(density) { 12.dp.toPx() }
            val gapPx = with(density) { 8.dp.toPx() }
            val anchorCenterX = (anchorBoundsOnScreen.left + anchorBoundsOnScreen.right) / 2f - overlayOriginOnScreen.x
            val desiredLeft = anchorCenterX - menuWidthPx / 2f
            val leftPx = desiredLeft.coerceIn(
                edgePaddingPx,
                (screenWidthPx - menuWidthPx - edgePaddingPx).coerceAtLeast(edgePaddingPx),
            )
            val statusBarTopPx = WindowInsets.statusBars.getTop(density).toFloat()
            val desiredTop = anchorBoundsOnScreen.top - overlayOriginOnScreen.y - menuHeightPx - gapPx
            val focusLiftPx = with(density) { 10.dp.toPx() }
            val topPx = desiredTop.coerceAtLeast(statusBarTopPx + gapPx + focusLiftPx)
            val pivotX = if (menuWidthPx > 0f) {
                ((anchorCenterX - leftPx) / menuWidthPx).coerceIn(0f, 1f)
            } else {
                .5f
            }
            val popupReady = overlayCoordinatesReady && anchorBoundsOnScreen != Rect.Zero && panelSize != IntSize.Zero
            LaunchedEffect(popupReady) { if (popupReady) onMeasured() }
            if (popupReady) {
                val focusWidth = 64.dp
                val focusHeight = 54.dp
                val focusWidthPx = with(density) { focusWidth.toPx() }
                val focusHeightPx = with(density) { focusHeight.toPx() }
                val focusCenterY = (anchorBoundsOnScreen.top + anchorBoundsOnScreen.bottom) / 2f - overlayOriginOnScreen.y
                Box(
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                (anchorCenterX - focusWidthPx / 2f).roundToInt(),
                                (focusCenterY - focusHeightPx / 2f - focusLiftPx * progress.value).roundToInt(),
                            )
                        }
                        .size(focusWidth, focusHeight)
                        .graphicsLayer {
                            alpha = progress.value
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Icon(
                            imageVector = focusIcon,
                            contentDescription = focusLabel,
                            tint = focusTint,
                            modifier = Modifier.size(26.dp),
                        )
                        Text(
                            text = focusLabel,
                            color = focusTint,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                        )
                    }
                }
            }
            val availableHeightPx = (anchorBoundsOnScreen.top - overlayOriginOnScreen.y -
                statusBarTopPx - gapPx * 2f - focusLiftPx).coerceAtLeast(with(density) { 48.dp.toPx() })
            Box(
                    modifier = Modifier.widthIn(min = 140.dp, max = panelMaxWidth)
                        .width(IntrinsicSize.Max)
                        .heightIn(max = with(density) { availableHeightPx.toDp() })
                        .onSizeChanged { if (panelSize != it) panelSize = it }
                        .offset {
                            IntOffset(
                                leftPx.roundToInt(),
                                (topPx - focusLiftPx * progress.value).roundToInt(),
                            )
                        }
                        .graphicsLayer {
                            alpha = progress.value
                            val scale = .97f + .03f * progress.value
                            scaleX = scale
                            scaleY = scale
                            transformOrigin = TransformOrigin(pivotX, 1f)
                        }
                        .shadow(
                            elevation = 18.dp,
                            shape = panelShape,
                            ambientColor = Color.Black.copy(alpha = if (dark) .25f else .12f),
                            spotColor = Color.Black.copy(alpha = if (dark) .32f else .18f),
                        )
                        .clip(panelShape)
                        .border(
                            width = .8.dp,
                            color = Color.White.copy(alpha = if (dark) .18f else .54f),
                            shape = panelShape,
                        ),
                ) {
                    GlassBackdropSurface(
                        modifier = Modifier.matchParentSize(), color = colors.surfaces.panel,
                        opacity = material.opacity, cornerDp = 24f, blurDp = material.blurDp,
                        refractionDp = { material.refractionDp * progress.value },
                    )
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                    Box(
                        modifier = Modifier.fillMaxWidth().heightIn(min = 38.dp).padding(horizontal = 16.dp, vertical = 8.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Text(
                            text = "操作",
                            color = colors.text.placeholder,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                    ActionSeparator(color = separator)
                    actions.forEachIndexed { index, action ->
                        if (index > 0) ActionSeparator(color = separator)
                        Row(
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                .clickable(enabled = interactive) { onAction(action.onClick) }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = action.label,
                                modifier = Modifier.weight(1f),
                                color = colors.text.primary,
                                style = MaterialTheme.typography.labelLarge,
                            )
                            Icon(
                                imageVector = action.icon,
                                contentDescription = null,
                                tint = colors.text.primary,
                                modifier = Modifier.padding(start = 12.dp).size(20.dp),
                            )
                        }
                    }
                    }
                }
    }
}

@Composable
private fun ActionSeparator(color: Color) {
    Box(
        Modifier.fillMaxWidth().height(.7.dp).background(color),
    )
}
