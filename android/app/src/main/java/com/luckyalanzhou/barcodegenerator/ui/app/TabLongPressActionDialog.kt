package com.luckyalanzhou.barcodegenerator.ui.app

import com.luckyalanzhou.barcodegenerator.ui.animation.contextMenuSourceScale
import com.luckyalanzhou.barcodegenerator.ui.animation.contextMenuFocusScale

import com.luckyalanzhou.barcodegenerator.icons.DeleteIcon
import com.luckyalanzhou.barcodegenerator.ui.component.menu.TabLongPressAction
import com.luckyalanzhou.barcodegenerator.ui.component.menu.TabLongPressMenuState
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
    sourceProgress: State<Float>,
    panelProgress: State<Float>,
    opacity: State<Float>,
    actionExit: State<Float>,
    actionClosing: Boolean,
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
    val sourceCardShape = remember { RoundedCornerShape(14.dp) }
    val menuColors = actionMenuColors(colors, dark, effects.highContrast)
    val separator = menuColors.separator
    var overlayOriginOnScreen by remember { mutableStateOf(Offset.Zero) }
    var overlayCoordinatesReady by remember { mutableStateOf(false) }
    var panelSize by remember { mutableStateOf(IntSize.Zero) }
    val actionsReady by remember(progress, panelProgress, opacity, effects.reduceMotion, interactive) {
        derivedStateOf { interactive && opacity.value >= .99f &&
            (effects.reduceMotion || (progress.value >= .95f && panelProgress.value >= .99f)) }
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
                    // 弱黑色遮罩只分离焦点层，深色模式不混入白色，关闭时连续恢复原背景。
                    .drawWithContent {
                        drawContent()
                        drawRect(Color.Black.copy(alpha = menuBackdropDimAlpha(progress.value, dark)))
                    }
                    .clickable(interactionSource = remember { MutableInteractionSource() },
                        indication = null, onClick = onDismiss)
                    .clearAndSetSemantics { },
            )

            val panelWidth = actionMenuWidthDp(maxWidth.value).dp
            val screenWidthPx = with(density) { maxWidth.toPx() }
            val screenHeightPx = with(density) { maxHeight.toPx() }
            val edgePaddingPx = with(density) { 12.dp.toPx() }
            val gapPx = with(density) { 8.dp.toPx() }
            val statusBarTopPx = WindowInsets.statusBars.getTop(density).toFloat()
            val bottomInsetPx = WindowInsets.navigationBars.getBottom(density).toFloat()
            val focusLiftPx = with(density) { if (effects.reduceMotion) 0f else 12.dp.toPx() }
            val motionMarginPx = with(density) { if (effects.reduceMotion) 0f else 40.dp.toPx() }
            val desiredHeightPx = with(density) { ((if (showTitle) 38 else 0) + actions.size * 40).dp.toPx() } + motionMarginPx
            val rowMenuAnchor = if (state.tabAnchor) menuAnchorBoundsOnScreen else liftedRowMenuAnchor(
                anchorBoundsOnScreen, overlayOriginOnScreen, screenHeightPx, statusBarTopPx,
                bottomInsetPx, gapPx, desiredHeightPx)
            val sourceShiftPx = if (state.tabAnchor) 0f else rowMenuAnchor.top - anchorBoundsOnScreen.top
            LaunchedEffect(gesture, rowMenuAnchor, focusLiftPx, actionClosing, effects.reduceMotion) {
                snapshotFlow { sourceProgress.value }.collect { value ->
                    // 再次按住时以当前显示中的卡片位置为起点，而非列表中原始行的位置。
                    gesture.sourceBounds = anchorBoundsOnScreen.translate(
                        Offset(0f, (sourceShiftPx - focusLiftPx) *
                            (if (actionClosing || effects.reduceMotion) 1f else value.coerceIn(0f, 1f))))
                }
            }
            val menuSpace = contextMenuSpace(rowMenuAnchor, overlayOriginOnScreen,
                with(density) { maxHeight.toPx() }, statusBarTopPx, bottomInsetPx,
                gapPx, focusLiftPx, desiredHeightPx, state.tabAnchor)
            val placement = if (state.tabAnchor) tabMenuPlacement(rowMenuAnchor, overlayOriginOnScreen, panelSize,
                screenWidthPx, statusBarTopPx, edgePaddingPx, gapPx, focusLiftPx, menuSpace.above)
            else rowMenuPlacement(rowMenuAnchor.copy(left = menuAnchorBoundsOnScreen.left), overlayOriginOnScreen, panelSize,
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
                // 预览只轻微聚焦，尺寸随实际目标适配；不再套用统一的大幅放大比例。
                val focusScale = if (effects.reduceMotion) 1f else contextMenuFocusScale(
                    if (state.tabAnchor) with(density) { 64.dp.toPx() } else anchorBoundsOnScreen.width,
                    anchorBoundsOnScreen.height, with(density) { 2.dp.toPx() })
                val focusWidth = if (state.tabAnchor) 64.dp else with(density) {
                    minOf(anchorBoundsOnScreen.width,
                        (screenWidthPx - edgePaddingPx * 2f) / focusScale).toDp()
                }
                val focusHeight = if (state.tabAnchor) 54.dp else with(density) { anchorBoundsOnScreen.height.toDp() }
                val focusWidthPx = with(density) { focusWidth.toPx() }
                val focusHeightPx = with(density) { focusHeight.toPx() }
                val focusCenterY = (anchorBoundsOnScreen.top + anchorBoundsOnScreen.bottom) / 2f - overlayOriginOnScreen.y
                Box(
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                (anchorBoundsOnScreen.center.x - overlayOriginOnScreen.x - focusWidthPx / 2f).roundToInt(),
                                (focusCenterY - focusHeightPx / 2f).roundToInt(),
                            )
                        }
                        .size(focusWidth, focusHeight)
                        .graphicsLayer {
                            val exit = actionExit.value
                            alpha = if (actionClosing) 1f - exit else if (effects.reduceMotion)
                                opacity.value else if (interactive) 1f else (progress.value / .15f).coerceIn(0f, 1f)
                            val pop = contextMenuSourceScale(sourceProgress.value,
                                if (actionClosing) exit else 0f, effects.reduceMotion, focusScale)
                            scaleX = pop
                            scaleY = pop
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
                                    (placement.top - focusLiftPx) - panelSize.height)) +
                                (sourceShiftPx - focusLiftPx) * (if (actionClosing || effects.reduceMotion) 1f
                                    else sourceProgress.value.coerceIn(0f, 1.06f))
                            if (!state.tabAnchor) {
                                shape = sourceCardShape
                                shadowElevation = 12.dp.toPx() * progress.value.coerceIn(0f, 1f)
                                ambientShadowColor = Color.Black.copy(alpha = if (dark) .24f else .10f)
                                spotShadowColor = Color.Black.copy(alpha = if (dark) .30f else .16f)
                            }
                        }
                        .then(if (state.tabAnchor) Modifier else Modifier
                            // 只有文件夹/收藏文件长按时显示浮起卡片；Tab 菜单不绘制来源卡片外框。
                            .drawWithContent {
                                val p = progress.value.coerceIn(0f, 1f)
                                drawRoundRect(colors.surfaces.card.copy(alpha = p),
                                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(14.dp.toPx()))
                                drawContent()
                                if (effects.highContrast) drawRoundRect(colors.text.primary.copy(alpha = p),
                                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(14.dp.toPx()),
                                    style = Stroke(1.dp.toPx()))
                            }
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
                        state.sourceContent?.invoke()
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
                                placement.top.roundToInt(),
                            )
                        }
                        .graphicsLayer {
                            val reveal = menuGlassReveal(if (actionClosing) 1f else panelProgress.value, effects.reduceMotion)
                            alpha = opacity.value
                            // 全部选项作为完整面板同时出现，不再依次裁切标题和菜单行。
                            shape = RoundedCornerShape(panelCorner)
                            clip = true
                            shadowElevation = 12.dp.toPx() * reveal.shadow
                            ambientShadowColor = Color.Black.copy(alpha = if (dark) .25f else .12f)
                            spotShadowColor = Color.Black.copy(alpha = if (dark) .32f else .18f)
                            val motion = displayedMotion()
                            val dragScale = menuDragScale(motion)
                            val presentationScale = if (effects.reduceMotion) 1f else if (actionClosing) 1f - .03f * actionExit.value
                                else reveal.scale
                            scaleX = dragScale * presentationScale
                            scaleY = dragScale * presentationScale
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
                            }
                            // 从真实来源位置生长，不再硬编码左右两侧菜单的角点。
                            transformOrigin = TransformOrigin(placement.pivotX, if (menuSpace.above) 1f else 0f)
                            val source = if (actionClosing || effects.reduceMotion) 1f else sourceProgress.value.coerceIn(0f, 1.06f)
                            translationY += -focusLiftPx * source - sourceShiftPx * (1f - source)
                            if (!effects.reduceMotion && !actionClosing) {
                                // 从靠近来源的一侧短距离展开，方向随上下锚定改变。
                                val remaining = 1f - panelProgress.value.coerceIn(0f, 1f)
                                translationX += (if (state.tabAnchor && placement.anchorCenterX >= screenWidthPx / 2f)
                                    10f else -10f).dp.toPx() * remaining
                                translationY += (if (menuSpace.above) 14f else -14f).dp.toPx() *
                                    (1f - panelProgress.value.coerceIn(0f, 1.08f))
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
                            val path = androidx.compose.ui.graphics.Path().apply {
                                addRoundRect(androidx.compose.ui.geometry.RoundRect(
                                    Rect(0f, 0f, size.width, size.height),
                                    androidx.compose.ui.geometry.CornerRadius(panelCorner.toPx())))
                            }
                            val edgeWidth = (.45.dp.toPx()).coerceIn(1f, 1.5f)
                            drawPath(path, menuColors.outline, style = Stroke(edgeWidth))
                        }
                        .pointerInput(Unit) {
                            // 标题区或空白面板上的点按不能穿透到背景并关闭菜单；仍允许从标题区域开始滚动。
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false).consume()
                                waitForUpOrCancellation()?.consume()
                            }
                        },
                ) {
                    // Tab、文件夹和文件菜单都使用静态面板；背景聚焦模糊与弹出动画独立保留。
                    Box(Modifier.matchParentSize().background(colors.surfaces.panel))
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
                                .clickable(enabled = actionsReady, role = Role.Button) {
                                    selection.confirmed = index
                                    onAction(action.onClick)
                                }
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
