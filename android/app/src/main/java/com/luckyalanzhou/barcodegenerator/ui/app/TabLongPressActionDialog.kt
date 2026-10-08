package com.luckyalanzhou.barcodegenerator.ui.app

import com.luckyalanzhou.barcodegenerator.icons.DeleteIcon
import com.luckyalanzhou.barcodegenerator.ui.component.menu.TabLongPressAction
import com.luckyalanzhou.barcodegenerator.ui.component.menu.TabLongPressMenuState
import com.luckyalanzhou.barcodegenerator.ui.component.SlideSelectionMenu
import com.luckyalanzhou.barcodegenerator.ui.component.slideMenuItem
import com.luckyalanzhou.barcodegenerator.ui.component.ContextMenuGestureSession
import com.luckyalanzhou.barcodegenerator.ui.component.glass.*
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
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
import com.luckyalanzhou.barcodegenerator.ui.theme.ActionMenuMetrics
import com.luckyalanzhou.barcodegenerator.ui.theme.actionMenuColors
import com.luckyalanzhou.barcodegenerator.ui.theme.actionMenuWidthDp
import kotlin.math.roundToInt

/**
 * Tab 长按操作菜单：按 Tab 锚点显示，手指在菜单项上滑动时更新选中反馈，松开后执行当前项。
 * 点击菜单外部或系统返回键关闭菜单；具体操作由调用方传入，菜单只负责手势、动画和呈现。
 */
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
    val showTitle = menuShowsTitle(state.tabAnchor, state.title)
    val colors = LocalAppColorScheme.current
    val effects = LocalVisualEffectsPolicy.current
    val menuFocus = remember { FocusRequester() }
    val density = LocalDensity.current
    val panelCorner = ActionMenuMetrics.corner
    val sourceCardShape = remember { RoundedCornerShape(16.dp) }
    val menuColors = actionMenuColors(colors, dark, effects.highContrast)
    val separator = menuColors.separator
    var overlayOriginOnScreen by remember { mutableStateOf(Offset.Zero) }
    var overlayCoordinatesReady by remember { mutableStateOf(false) }
    var panelSize by remember { mutableStateOf(IntSize.Zero) }
    val material = menuGlassMaterial(colors.surfaces.panel, panelSize.height / density.density)
    val actionsReady by remember(progress, effects.reduceMotion, interactive) {
        derivedStateOf { interactive && (effects.reduceMotion || progress.value >= .99f) }
    }
    val follow = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    val anchorMotionRangePx = with(density) { 64.dp.toPx() }
    var restingPanelOnScreen by remember { mutableStateOf(Rect.Zero) }
    fun pointerMotion(): Offset = when {
        gesture.selection.selected != null -> Offset.Zero
        gesture.continuation -> menuSourceInteractionMotion(gesture.feedbackPoint,
            gesture.origin, anchorMotionRangePx, restingPanelOnScreen.center.y < gesture.origin.y)
        else -> Offset.Zero // A separate touch on the menu is selection, never panel dragging.
    }
    // 手势状态只在 graphicsLayer 绘制阶段读取；拖动直接跟手，弹簧回弹只发生在松手后。
    fun displayedMotion(): Offset = menuMotionForDrawing(
        if (gesture.feedbackPoint != null) pointerMotion() else null,
        effects.reduceMotion,
    ) { follow.value }
    LaunchedEffect(gesture, effects.reduceMotion, anchorMotionRangePx, state.tabAnchor) {
        snapshotFlow {
            val point = gesture.feedbackPoint
            point to pointerMotion()
        }.collectLatest { (point, target) ->
            if (effects.reduceMotion) follow.snapTo(Offset.Zero)
            else if (point != null) follow.snapTo(target)
            else follow.animateTo(Offset.Zero, spring(
                dampingRatio = if (state.tabAnchor) TabMenuSourceMotion.returnDamping else .86f,
                stiffness = if (state.tabAnchor) TabMenuSourceMotion.returnStiffness else 700f))
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
                    // 外层仅负责透明触控命中，不能用遮罩把原背景额外提亮或压暗。
                    .clickable(onClick = onDismiss)
                    .clearAndSetSemantics { },
            )

            val panelWidth = actionMenuWidthDp(maxWidth.value).dp
            val screenWidthPx = with(density) { maxWidth.toPx() }
            val screenHeightPx = with(density) { maxHeight.toPx() }
            val edgePaddingPx = with(density) { 12.dp.toPx() }
            val gapPx = with(density) { 8.dp.toPx() }
            val statusBarTopPx = WindowInsets.statusBars.getTop(density).toFloat()
            val bottomInsetPx = WindowInsets.navigationBars.getBottom(density).toFloat()
            val focusLiftPx = with(density) { if (effects.reduceMotion) 0f else 10.dp.toPx() }
            val motionMarginPx = with(density) { if (effects.reduceMotion) 0f else 40.dp.toPx() }
            val desiredHeightPx = with(density) { ((if (showTitle) 38 else 0) + actions.size * 40).dp.toPx() } + motionMarginPx
            val rowMenuAnchor = if (state.tabAnchor) menuAnchorBoundsOnScreen else liftedRowMenuAnchor(
                anchorBoundsOnScreen, overlayOriginOnScreen, screenHeightPx, statusBarTopPx,
                bottomInsetPx, gapPx, desiredHeightPx)
            val sourceShiftPx = if (state.tabAnchor) 0f else rowMenuAnchor.top - anchorBoundsOnScreen.top
            LaunchedEffect(gesture, rowMenuAnchor, focusLiftPx) {
                snapshotFlow { progress.value }.collect { value ->
                    // 再次按住时以当前显示中的卡片位置为起点，而非列表中原始行的位置。
                    gesture.sourceBounds = anchorBoundsOnScreen.translate(
                        Offset(0f, (sourceShiftPx - focusLiftPx) * value))
                }
            }
            val menuSpace = contextMenuSpace(rowMenuAnchor, overlayOriginOnScreen,
                with(density) { maxHeight.toPx() }, statusBarTopPx, bottomInsetPx,
                gapPx, focusLiftPx, desiredHeightPx, state.tabAnchor)
            val placement = if (state.tabAnchor) tabMenuPlacement(rowMenuAnchor, overlayOriginOnScreen, panelSize,
                screenWidthPx, statusBarTopPx, edgePaddingPx, gapPx, focusLiftPx, menuSpace.above)
            else rowMenuPlacement(rowMenuAnchor, overlayOriginOnScreen, panelSize,
                screenWidthPx, statusBarTopPx, edgePaddingPx, gapPx, focusLiftPx, menuSpace.above)
            val popupReady = overlayCoordinatesReady && anchorBoundsOnScreen != Rect.Zero && panelSize != IntSize.Zero
            LaunchedEffect(popupReady) {
                if (popupReady) {
                    onMeasured() // Measurement starts the reveal; it must not wait for the reveal.
                    gesture.ready = actionsReady
                }
            }
            LaunchedEffect(popupReady, actionsReady) {
                gesture.ready = popupReady && actionsReady
            }
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
                                (focusCenterY - focusHeightPx / 2f + (sourceShiftPx - focusLiftPx) * progress.value).roundToInt(),
                            )
                        }
                        .size(focusWidth, focusHeight)
                        .graphicsLayer {
                            alpha = progress.value
                            val rawMotion = displayedMotion()
                            val motion = if (state.tabAnchor) tabMenuIconMotion(rawMotion) else rawMotion
                            translationX = (motion.x * (if (state.tabAnchor)
                                TabMenuSourceMotion.horizontalLimitDp else 28f).dp.toPx()).coerceIn(
                                minOf(0f, edgePaddingPx - placement.left),
                                maxOf(0f, screenWidthPx - edgePaddingPx - placement.left - panelSize.width))
                            translationY = (motion.y * (if (state.tabAnchor)
                                TabMenuSourceMotion.verticalLimitDp else 32f).dp.toPx()).coerceIn(
                                minOf(0f, statusBarTopPx - (placement.top - focusLiftPx)),
                                maxOf(0f, screenHeightPx - bottomInsetPx - edgePaddingPx -
                                    (placement.top - focusLiftPx) - panelSize.height))
                        }
                        .then(if (state.tabAnchor) Modifier else Modifier
                            // 只有文件夹/收藏文件长按时显示浮起卡片；Tab 菜单不绘制来源卡片外框。
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
            // 即使菜单较高或系统字体较大，也为受限的跟随位移和膨胀动画预留空间。
            val availableHeightPx = (menuSpace.height - motionMarginPx).coerceAtLeast(with(density) { 48.dp.toPx() })
            LaunchedEffect(placement, panelSize, overlayOriginOnScreen, focusLiftPx) {
                // 使用静止布局尺寸而不是动画中的面板边界，避免测量与位移动画互相反馈。
                restingPanelOnScreen = Rect(placement.left + overlayOriginOnScreen.x,
                    placement.top - focusLiftPx + overlayOriginOnScreen.y,
                    placement.left + overlayOriginOnScreen.x + panelSize.width,
                    placement.top - focusLiftPx + overlayOriginOnScreen.y + panelSize.height)
            }
            Box(
                    modifier = Modifier.width(panelWidth)
                        .heightIn(max = with(density) { availableHeightPx.toDp() })
                        .onSizeChanged { if (panelSize != it) panelSize = it }
                        .offset {
                            IntOffset(
                                placement.left.roundToInt(),
                                (placement.top - focusLiftPx * progress.value - sourceShiftPx * (1f - progress.value)).roundToInt(),
                            )
                        }
                        .graphicsLayer {
                            val reveal = menuGlassReveal(progress.value, state.tabAnchor, effects.reduceMotion)
                            alpha = reveal.alpha
                            shape = MenuRevealContour(placement.pivotX, menuSpace.above, reveal, panelCorner.toPx(),
                                preserveContour = state.tabAnchor)
                            clip = true
                            shadowElevation = 12.dp.toPx() * reveal.shadow
                            ambientShadowColor = Color.Black.copy(alpha = if (dark) .25f else .12f)
                            spotShadowColor = Color.Black.copy(alpha = if (dark) .32f else .18f)
                            val motion = displayedMotion()
                            val dragScale = menuDragScale(motion)
                            scaleX = dragScale
                            scaleY = dragScale
                            // 用稳定且未缩放的边界限制触点范围，避免限制结果随动画变化。
                            translationX = (motion.x * 28.dp.toPx()).coerceIn(
                                minOf(0f, edgePaddingPx - placement.left),
                                maxOf(0f, screenWidthPx - edgePaddingPx - placement.left - panelSize.width))
                            translationY = (motion.y * 32.dp.toPx()).coerceIn(
                                minOf(0f, statusBarTopPx - (placement.top - focusLiftPx)),
                                maxOf(0f, screenHeightPx - bottomInsetPx - edgePaddingPx -
                                    (placement.top - focusLiftPx) - panelSize.height))
                            if (state.tabAnchor) {
                                // 拖动来源只改变面板尺寸，不改变菜单锚定位置。
                                translationX = 0f
                                translationY = 0f
                                transformOrigin = tabMenuDragOrigin(
                                    anchorBoundsOnScreen.center.x - overlayOriginOnScreen.x, screenWidthPx)
                            } else {
                                transformOrigin = TransformOrigin(placement.pivotX, if (menuSpace.above) 1f else 0f)
                            }
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
                        .drawWithContent {
                            drawContent()
                            val reveal = menuGlassReveal(progress.value, state.tabAnchor, effects.reduceMotion)
                            val path = menuRevealPath(menuRevealBounds(size, placement.pivotX, menuSpace.above, reveal,
                                preserveContour = state.tabAnchor), panelCorner.toPx())
                            val edgeWidth = (.45.dp.toPx()).coerceIn(1f, 1.5f)
                            drawPath(path, menuColors.outline, style = Stroke(edgeWidth))
                            if (!effects.highContrast && !state.tabAnchor) {
                                drawPath(
                                    path,
                                    androidx.compose.ui.graphics.Brush.verticalGradient(
                                        listOf(Color.White.copy(alpha = if (dark) .16f else .38f),
                                            Color.Transparent, Color.Black.copy(alpha = if (dark) .15f else .06f)),
                                    ),
                                    style = Stroke(edgeWidth),
                                )
                            }
                        }
                        .pointerInput(Unit) {
                            // 标题区或空白面板上的点按不能穿透到背景并关闭菜单；仍允许从标题区域开始滚动。
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false).consume()
                                waitForUpOrCancellation()?.consume()
                            }
                        },
                ) {
                    if (state.tabAnchor) {
                        // Tab 操作菜单使用稳定材质，不应用实时玻璃着色器。
                        Box(Modifier.matchParentSize().background(colors.surfaces.panel))
                    } else GlassBackdropSurface(
                        modifier = Modifier.matchParentSize(), color = colors.surfaces.panel,
                        opacity = material.opacity, cornerDp = panelCorner.value, blurDp = material.blurDp,
                        refractionDp = { material.refractionDp * menuGlassReveal(progress.value, state.tabAnchor, effects.reduceMotion).thickness },
                        thicknessProgress = { menuGlassReveal(progress.value, state.tabAnchor, effects.reduceMotion).thickness },
                    )
                    key(state) {
                    SlideSelectionMenu(gesture.selection, Modifier.verticalScroll(rememberScrollState())) { selection ->
                    if (showTitle) {
                    Box(
                        modifier = Modifier.fillMaxWidth().heightIn(min = ActionMenuMetrics.titleHeight).padding(horizontal = ActionMenuMetrics.horizontalPadding, vertical = 8.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Text(
                            text = state.title,
                            color = menuColors.title,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                    ActionSeparator(color = separator)
                    }
                    actions.forEachIndexed { index, action ->
                        if (index > 0) ActionSeparator(color = separator)
                        Row(
                            modifier = Modifier.fillMaxWidth().heightIn(min = ActionMenuMetrics.rowHeight)
                                .slideMenuItem(selection, index, actionsReady, onClick = action.onClick)
                                .clickable(enabled = actionsReady, role = Role.Button) { onAction(action.onClick) }
                                .padding(horizontal = ActionMenuMetrics.horizontalPadding, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // 删除等破坏性操作的文字和图标在浅色、深色主题中都固定使用红色。
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
                                modifier = Modifier.padding(start = ActionMenuMetrics.iconGap).size(ActionMenuMetrics.iconSize),
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
        Modifier.fillMaxWidth().height(ActionMenuMetrics.separatorHeight).background(color),
    )
}
