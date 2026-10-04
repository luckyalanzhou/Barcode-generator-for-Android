package com.luckyalanzhou.barcodegenerator.ui.app

import com.luckyalanzhou.barcodegenerator.icons.DeleteIcon
import com.luckyalanzhou.barcodegenerator.ui.component.SlideSelectionMenu
import com.luckyalanzhou.barcodegenerator.ui.component.slideMenuItem
import com.luckyalanzhou.barcodegenerator.ui.component.ContextMenuGestureSession
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.key
import kotlinx.coroutines.flow.collectLatest

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.navigationBars
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.dismiss
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luckyalanzhou.barcodegenerator.ui.theme.LocalAppColorScheme
import com.luckyalanzhou.barcodegenerator.ui.theme.LocalVisualEffectsPolicy
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
    val restoreFocus: () -> Unit = {},
    val title: String = "操作",
    val tabAnchor: Boolean = true,
    val menuAnchorBoundsOnScreen: Rect = anchorBoundsOnScreen,
)

@Composable
internal fun TabLongPressActionOverlay(
    state: TabLongPressMenuState,
    progress: State<Float>,
    interactive: Boolean,
    gesture: ContextMenuGestureSession,
    onMeasured: () -> Unit,
    onDismiss: () -> Unit,
    onAction: (() -> Unit) -> Unit,
) {
    val dark = state.dark
    val anchorBoundsOnScreen = state.anchorBoundsOnScreen
    val menuAnchorBoundsOnScreen = state.menuAnchorBoundsOnScreen
    val focusIcon = state.focusIcon
    val focusLabel = state.focusLabel
    val focusTint = state.focusTint
    val actions = state.actions
    val colors = LocalAppColorScheme.current
    val effects = LocalVisualEffectsPolicy.current
    val menuFocus = remember { FocusRequester() }
    val density = LocalDensity.current
    val panelShape = remember { RoundedCornerShape(24.dp) }
    val sourceCardShape = remember { RoundedCornerShape(16.dp) }
    val separator = colors.borders.divider.copy(alpha = if (dark) .36f else .44f)
    var overlayOriginOnScreen by remember { mutableStateOf(Offset.Zero) }
    var overlayCoordinatesReady by remember { mutableStateOf(false) }
    var panelSize by remember { mutableStateOf(IntSize.Zero) }
    val material = menuGlassMaterial(colors.surfaces.panel, panelSize.height / density.density)
    val follow = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    val anchorMotionRangePx = with(density) { 64.dp.toPx() }
    var restingPanelOnScreen by remember { mutableStateOf(Rect.Zero) }
    fun pointerMotion(): Offset = when {
        gesture.selection.selected != null -> Offset.Zero
        gesture.continuation -> menuSourceInteractionMotion(gesture.feedbackPoint,
            gesture.origin, anchorMotionRangePx, restingPanelOnScreen.center.y < gesture.origin.y)
        else -> Offset.Zero // A separate touch on the menu is selection, never panel dragging.
    }
    // Read only from graphicsLayer: active input is direct, springs are release-only.
    fun displayedMotion(): Offset = menuMotionForDrawing(
        if (gesture.feedbackPoint != null) pointerMotion() else null,
        effects.reduceMotion,
    ) { follow.value }
    LaunchedEffect(gesture, effects.reduceMotion, anchorMotionRangePx) {
        snapshotFlow {
            val point = gesture.feedbackPoint
            point to pointerMotion()
        }.collectLatest { (point, target) ->
            if (effects.reduceMotion) follow.snapTo(Offset.Zero)
            else if (point != null) follow.snapTo(target)
            else follow.animateTo(Offset.Zero, spring(dampingRatio = .86f, stiffness = 700f))
        }
    }

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
                    .background(Color.White.copy(alpha = if (effects.opaqueGlass) 0f else if (dark) .08f else .16f))
                    .graphicsLayer { alpha = progress.value }
                    .clickable(onClick = onDismiss)
                    .clearAndSetSemantics { },
            )

            val panelMaxWidth = minOf(maxWidth * .62f, 340.dp, (maxWidth - 24.dp).coerceAtLeast(1.dp))
            val screenWidthPx = with(density) { maxWidth.toPx() }
            val screenHeightPx = with(density) { maxHeight.toPx() }
            val edgePaddingPx = with(density) { 12.dp.toPx() }
            val gapPx = with(density) { 8.dp.toPx() }
            val statusBarTopPx = WindowInsets.statusBars.getTop(density).toFloat()
            val bottomInsetPx = WindowInsets.navigationBars.getBottom(density).toFloat()
            val focusLiftPx = with(density) { if (effects.reduceMotion) 0f else 10.dp.toPx() }
            val motionMarginPx = with(density) { if (effects.reduceMotion) 0f else 40.dp.toPx() }
            val desiredHeightPx = with(density) { (38 + actions.size * 40).dp.toPx() } + motionMarginPx
            val menuSpace = contextMenuSpace(menuAnchorBoundsOnScreen, overlayOriginOnScreen,
                with(density) { maxHeight.toPx() }, statusBarTopPx, bottomInsetPx,
                gapPx, focusLiftPx, desiredHeightPx, state.tabAnchor)
            val placement = tabMenuPlacement(menuAnchorBoundsOnScreen, overlayOriginOnScreen, panelSize,
                screenWidthPx, statusBarTopPx, edgePaddingPx, gapPx, focusLiftPx, menuSpace.above)
            val popupReady = overlayCoordinatesReady && anchorBoundsOnScreen != Rect.Zero && panelSize != IntSize.Zero
            LaunchedEffect(popupReady) { if (popupReady) onMeasured() }
            LaunchedEffect(popupReady, interactive) { if (popupReady && interactive) menuFocus.requestFocus() }
            if (popupReady) {
                val focusWidth = if (state.tabAnchor) 64.dp else with(density) { anchorBoundsOnScreen.width.toDp() }
                val focusHeight = if (state.tabAnchor) 54.dp else with(density) { anchorBoundsOnScreen.height.toDp() }
                val focusWidthPx = with(density) { focusWidth.toPx() }
                val focusHeightPx = with(density) { focusHeight.toPx() }
                val focusCenterY = (anchorBoundsOnScreen.top + anchorBoundsOnScreen.bottom) / 2f - overlayOriginOnScreen.y
                Box(
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                (anchorBoundsOnScreen.center.x - overlayOriginOnScreen.x - focusWidthPx / 2f).roundToInt(),
                                (focusCenterY - focusHeightPx / 2f - focusLiftPx * progress.value).roundToInt(),
                            )
                        }
                        .size(focusWidth, focusHeight)
                        .graphicsLayer {
                            alpha = progress.value
                            val motion = displayedMotion()
                            translationX = (motion.x * 28.dp.toPx()).coerceIn(
                                minOf(0f, edgePaddingPx - placement.left),
                                maxOf(0f, screenWidthPx - edgePaddingPx - placement.left - panelSize.width))
                            translationY = (motion.y * 32.dp.toPx()).coerceIn(
                                minOf(0f, statusBarTopPx - (placement.top - focusLiftPx)),
                                maxOf(0f, screenHeightPx - bottomInsetPx - edgePaddingPx -
                                    (placement.top - focusLiftPx) - panelSize.height))
                        }
                        .then(if (state.tabAnchor) Modifier else Modifier
                            // Only folder/file sources get a lifted card. Tabs remain unframed.
                            .shadow(12.dp, sourceCardShape, clip = false,
                                ambientColor = Color.Black.copy(alpha = if (dark) .24f else .10f),
                                spotColor = Color.Black.copy(alpha = if (dark) .30f else .16f))
                            .background(colors.surfaces.card, sourceCardShape)
                            .border(if (effects.highContrast) 1.dp else .7.dp,
                                if (effects.highContrast) colors.text.primary
                                else colors.borders.card.copy(alpha = if (dark) .55f else .65f),
                                sourceCardShape)
                            .clip(sourceCardShape))
                        .clearAndSetSemantics { },
                    contentAlignment = Alignment.Center,
                ) {
                    if (state.tabAnchor) {
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
                    } else {
                        Row(Modifier.fillMaxSize().padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Icon(focusIcon, null, tint = focusTint, modifier = Modifier.size(24.dp))
                            Text(focusLabel, color = colors.text.primary, fontSize = 17.sp,
                                fontWeight = FontWeight.Medium, maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            }
            // Leave room for the bounded follow/swell even on tall, large-font menus.
            val availableHeightPx = (menuSpace.height - motionMarginPx).coerceAtLeast(with(density) { 48.dp.toPx() })
            LaunchedEffect(placement, panelSize, overlayOriginOnScreen, focusLiftPx) {
                // Use resting geometry, not the animated panel's bounds, to avoid feedback loops.
                restingPanelOnScreen = Rect(placement.left + overlayOriginOnScreen.x,
                    placement.top - focusLiftPx + overlayOriginOnScreen.y,
                    placement.left + overlayOriginOnScreen.x + panelSize.width,
                    placement.top - focusLiftPx + overlayOriginOnScreen.y + panelSize.height)
            }
            Box(
                    modifier = Modifier.widthIn(min = minOf(200.dp, panelMaxWidth), max = panelMaxWidth)
                        .width(IntrinsicSize.Max)
                        .heightIn(max = with(density) { availableHeightPx.toDp() })
                        .onSizeChanged { if (panelSize != it) panelSize = it }
                        .offset {
                            IntOffset(
                                placement.left.roundToInt(),
                                (placement.top - focusLiftPx * progress.value).roundToInt(),
                            )
                        }
                        .graphicsLayer {
                            alpha = progress.value
                            val scale = if (effects.reduceMotion) 1f else .97f + .03f * progress.value
                            val motion = displayedMotion()
                            val dragScale = menuDragScale(motion)
                            scaleX = scale * dragScale
                            scaleY = scale * dragScale
                            // Stable, unscaled bounds make edge limiting independent of animation.
                            translationX = (motion.x * 28.dp.toPx()).coerceIn(
                                minOf(0f, edgePaddingPx - placement.left),
                                maxOf(0f, screenWidthPx - edgePaddingPx - placement.left - panelSize.width))
                            translationY = (motion.y * 32.dp.toPx()).coerceIn(
                                minOf(0f, statusBarTopPx - (placement.top - focusLiftPx)),
                                maxOf(0f, screenHeightPx - bottomInsetPx - edgePaddingPx -
                                    (placement.top - focusLiftPx) - panelSize.height))
                            transformOrigin = TransformOrigin(placement.pivotX, if (menuSpace.above) 1f else 0f)
                        }
                        .focusRequester(menuFocus)
                        .focusable()
                        .onPreviewKeyEvent {
                            if (it.key == Key.Escape && it.type == KeyEventType.KeyUp) { onDismiss(); true } else false
                        }
                        .semantics {
                            paneTitle = "$focusLabel 操作菜单"
                            isTraversalGroup = true
                            dismiss { onDismiss(); true }
                        }
                        .shadow(
                            elevation = 18.dp,
                            shape = panelShape,
                            ambientColor = Color.Black.copy(alpha = if (dark) .25f else .12f),
                            spotColor = Color.Black.copy(alpha = if (dark) .32f else .18f),
                        )
                        .clip(panelShape)
                        .pointerInput(Unit) {
                            // Header/empty panel taps must not fall through to the backdrop.
                            // Consume taps in Main; scrolling from the header remains available.
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false).consume()
                                waitForUpOrCancellation()?.consume()
                            }
                        }
                        .border(
                            width = .8.dp,
                            color = if (effects.highContrast) colors.text.primary else Color.White.copy(alpha = if (dark) .18f else .54f),
                            shape = panelShape,
                        ),
                ) {
                    GlassBackdropSurface(
                        modifier = Modifier.matchParentSize(), color = colors.surfaces.panel,
                        opacity = material.opacity, cornerDp = 24f, blurDp = material.blurDp,
                        refractionDp = { material.refractionDp * progress.value },
                    )
                    key(state) {
                    SlideSelectionMenu(gesture.selection, Modifier.verticalScroll(rememberScrollState())) { selection ->
                    Box(
                        modifier = Modifier.fillMaxWidth().heightIn(min = 38.dp).padding(horizontal = 16.dp, vertical = 8.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Text(
                            text = state.title,
                            color = if (effects.highContrast) colors.text.primary else colors.text.placeholder,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                    ActionSeparator(color = separator)
                    actions.forEachIndexed { index, action ->
                        if (index > 0) ActionSeparator(color = separator)
                        Row(
                            modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp)
                                .slideMenuItem(selection, index, interactive, onClick = action.onClick)
                                .clickable(enabled = interactive, role = Role.Button) { onAction(action.onClick) }
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // Destructive menu labels and icons share the same fixed red in both themes.
                            val actionColor = if (action.icon == DeleteIcon) colors.content.deleteIcon else colors.text.primary
                            Text(
                                text = action.label,
                                modifier = Modifier.weight(1f),
                                color = actionColor,
                                style = MaterialTheme.typography.labelLarge,
                            )
                            Icon(
                                imageVector = action.icon,
                                contentDescription = null,
                                tint = actionColor,
                                modifier = Modifier.padding(start = 12.dp).size(20.dp),
                            )
                        }
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
