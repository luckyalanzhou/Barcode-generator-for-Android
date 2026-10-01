package com.luckyalanzhou.barcodegenerator.ui.app

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import com.luckyalanzhou.barcodegenerator.ui.theme.*

import com.luckyalanzhou.barcodegenerator.icons.BarcodeIcon
import com.luckyalanzhou.barcodegenerator.icons.CloudDownloadIcon
import com.luckyalanzhou.barcodegenerator.icons.CloudUploadIcon
import com.luckyalanzhou.barcodegenerator.icons.DeleteIcon
import com.luckyalanzhou.barcodegenerator.icons.FavoriteFilledIcon
import com.luckyalanzhou.barcodegenerator.icons.FavoriteIcon
import com.luckyalanzhou.barcodegenerator.icons.HistoryFilledIcon
import com.luckyalanzhou.barcodegenerator.icons.HistoryIcon
import com.luckyalanzhou.barcodegenerator.icons.SettingsFilledIcon
import com.luckyalanzhou.barcodegenerator.icons.SettingsIcon
import com.luckyalanzhou.barcodegenerator.icons.UpgradeIcon

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private data class ComposeTabSpec(
    val label: String,
    val description: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector = icon,
)

@Composable
@OptIn(ExperimentalFoundationApi::class)
internal fun BarcodeComposeBottomTabBar(
    selectedIndex: Int,
    dark: Boolean,
    onTabSelected: (index: Int, fromSwipe: Boolean) -> Unit,
    onHistoryClear: () -> Unit,
    onFavoritesImport: () -> Unit,
    onFavoritesExport: () -> Unit,
    onCheckForUpdates: () -> Unit,
    onLongPressActionMenuRequested: (TabLongPressMenuState) -> Unit,
    showSelectionIndicator: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val tabs = remember {
        listOf(
            ComposeTabSpec("\u751f\u6210", "\u751f\u6210\u6761\u7801", BarcodeIcon),
            // 历史、收藏和设置选中时使用各自的填充图标。
            ComposeTabSpec("\u5386\u53f2", "\u5386\u53f2\u8bb0\u5f55", HistoryIcon, HistoryFilledIcon),
            ComposeTabSpec("\u6536\u85cf", "\u6536\u85cf\u5939", FavoriteIcon, FavoriteFilledIcon),
            ComposeTabSpec("\u8bbe\u7f6e", "\u8bbe\u7f6e", SettingsIcon, SettingsFilledIcon)
        )
    }
    val themeColors = LocalAppColorScheme.current
    val selectedColor = themeColors.controls.accent
    val unselectedColor = themeColors.navigation.tabUnselected
    val indicatorShape = remember { RoundedCornerShape(50) }
    val hapticView = LocalView.current
    var lastTabHapticAt by remember { mutableLongStateOf(0L) }
    var dragProgress by remember { mutableFloatStateOf(selectedIndex.toFloat()) }
    var dragging by remember { mutableStateOf(false) }
    var lastTarget by remember { mutableIntStateOf(selectedIndex) }
    var glassImpact by remember { mutableFloatStateOf(0f) }
    var glassImpactGeneration by remember { mutableIntStateOf(0) }
    var dragDirection by remember { mutableFloatStateOf(1f) }
    var tapPulseTab by remember { mutableIntStateOf(-1) }
    var tapPulseGeneration by remember { mutableIntStateOf(0) }
    val tabBoundsOnScreen = remember { mutableStateListOf(Rect.Zero, Rect.Zero, Rect.Zero, Rect.Zero) }
    val tapScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val selectedProgress by animateFloatAsState(
        targetValue = selectedIndex.toFloat(),
        animationSpec = spring(dampingRatio = 0.84f, stiffness = Spring.StiffnessMediumLow),
        label = "tab-indicator-position",
    )
    val glassImpactProgress by animateFloatAsState(
        targetValue = glassImpact,
        animationSpec = spring(dampingRatio = 0.58f, stiffness = 720f),
        label = "liquid-glass-tab-impact",
    )

    fun pulseGlass(amount: Float) {
        glassImpact = amount.coerceIn(0f, 0.14f)
        glassImpactGeneration += 1
        val generation = glassImpactGeneration
        tapScope.launch {
            delay(68)
            if (glassImpactGeneration == generation) glassImpact = 0f
        }
    }

    fun performTabSwitchHaptic() {
        val now = android.os.SystemClock.uptimeMillis()
        if (now - lastTabHapticAt < TAB_SWITCH_HAPTIC_MIN_INTERVAL_MS) return
        lastTabHapticAt = now
        hapticView.performSubtleTabHaptic()
    }

    fun handleTabClick(index: Int) {
        if (index != selectedIndex) performTabSwitchHaptic()
        if (index != selectedIndex) dragDirection = if (index > selectedIndex) 1f else -1f
        pulseGlass(if (index == selectedIndex) .035f else .085f)
        onTabSelected(index, false)
        tapPulseTab = index
        tapPulseGeneration += 1
        val generation = tapPulseGeneration
        tapScope.launch {
            delay(ComposeAnimationConfig.tabJellyResetDelayMillis)
            if (tapPulseGeneration == generation) tapPulseTab = -1
        }
    }

    BoxWithConstraints(
        modifier = modifier.fillMaxSize()
            .background(themeColors.surfaces.panel)
            .padding(horizontal = 4.dp, vertical = 5.dp)
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { position ->
                        dragging = true
                        pulseGlass(.035f)
                        dragProgress = selectedProgress
                        val tabWidth = size.width.toFloat() / tabs.size
                        dragProgress = (position.x / tabWidth - .5f)
                            .coerceIn(0f, (tabs.size - 1).toFloat())
                        lastTarget = dragProgress.roundToInt().coerceIn(tabs.indices)
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        if (kotlin.math.abs(dragAmount) > .1f) dragDirection = if (dragAmount > 0f) 1f else -1f
                        val tabWidth = size.width.toFloat() / tabs.size
                        dragProgress = (dragProgress + dragAmount / tabWidth)
                            .coerceIn(0f, (tabs.size - 1).toFloat())
                        val target = dragProgress.roundToInt().coerceIn(tabs.indices)
                        if (target != lastTarget) {
                            lastTarget = target
                            pulseGlass(.105f)
                            performTabSwitchHaptic()
                            onTabSelected(target, true)
                        }
                    },
                    onDragEnd = {
                        dragging = false
                        pulseGlass(.07f)
                        onTabSelected(lastTarget, true)
                    },
                    onDragCancel = {
                        dragging = false
                        pulseGlass(.04f)
                    },
                )
            }
    ) {
        val tabWidth = maxWidth / tabs.size
        val indicatorProgress = if (dragging) dragProgress else selectedProgress
        val indicatorOffset = tabWidth * indicatorProgress
        if (showSelectionIndicator) {
            Box(
                // Keep the liquid-glass treatment scoped to the selected capsule, not the full rail.
                modifier = Modifier.offset(x = indicatorOffset).width(tabWidth).fillMaxHeight()
                    .align(Alignment.CenterStart)
                    .graphicsLayer {
                        scaleX = 1f + glassImpactProgress + if (dragging) .025f else 0f
                        scaleY = 1f - glassImpactProgress * .26f
                        transformOrigin = TransformOrigin(
                            pivotFractionX = if (dragDirection > 0f) 0f else 1f,
                            pivotFractionY = .5f,
                        )
                    }
                    .shadow(
                        elevation = if (dark) .8.dp else 2.5.dp,
                        shape = indicatorShape,
                        clip = false,
                        ambientColor = Color.Black.copy(alpha = if (dark) .10f else .06f),
                        spotColor = Color.Black.copy(alpha = if (dark) .13f else .10f),
                    )
                    .drawBehind {
                        val outline = .8.dp.toPx()
                        val corner = CornerRadius(size.height / 2f)
                        val fill = if (dark) {
                            listOf(
                                Color.White.copy(alpha = .19f),
                                Color.White.copy(alpha = .09f),
                                Color.Black.copy(alpha = .22f),
                            )
                        } else {
                            listOf(
                                Color.White.copy(alpha = .94f),
                                selectedColor.copy(alpha = .16f),
                                Color.White.copy(alpha = .62f),
                            )
                        }
                        val rim = if (dark) {
                            listOf(Color.White.copy(alpha = .32f), Color.White.copy(alpha = .10f))
                        } else {
                            listOf(Color.White.copy(alpha = .92f), selectedColor.copy(alpha = .28f))
                        }
                        drawRoundRect(
                            brush = Brush.verticalGradient(fill),
                            cornerRadius = corner,
                        )
                        drawRoundRect(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = if (dark) .24f else .56f),
                                    Color.White.copy(alpha = if (dark) .07f else .14f),
                                    Color.Transparent,
                                ),
                                center = Offset(size.width * if (dragDirection > 0f) .26f else .74f, size.height * .12f),
                                radius = size.height * 1.15f,
                            ),
                            cornerRadius = corner,
                        )
                        drawRoundRect(
                            brush = Brush.verticalGradient(rim),
                            topLeft = Offset(outline / 2f, outline / 2f),
                            size = androidx.compose.ui.geometry.Size(size.width - outline, size.height - outline),
                            cornerRadius = CornerRadius((size.height - outline) / 2f),
                            style = Stroke(width = outline),
                        )
                    }
            )
        }
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tabs.forEachIndexed { index, tab ->
                val selected = selectedIndex == index
                val itemColor by animateColorAsState(
                    targetValue = if (selected) selectedColor else unselectedColor,
                    animationSpec = tween(ComposeAnimationConfig.tabItemColorDurationMillis),
                    label = "tab-item-color-$index",
                )
                val tapScale = animateFloatAsState(
                    targetValue = if (tapPulseTab == index) .92f else 1f,
                    animationSpec = ComposeAnimationConfig.pressSpring(),
                    label = "tab-icon-tap-response-$index",
                )
                val tabClickModifier = if (index in 1..3) {
                    Modifier.combinedClickable(
                        onClick = { handleTabClick(index) },
                        onLongClick = {
                            hapticView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                            val actions = when (index) {
                                1 -> listOf(
                                    TabLongPressAction(
                                        label = "清空历史记录",
                                        icon = DeleteIcon,
                                        onClick = onHistoryClear,
                                    ),
                                )
                                2 -> listOf(
                                    TabLongPressAction(
                                        label = "导入收藏",
                                        icon = CloudDownloadIcon,
                                        onClick = onFavoritesImport,
                                    ),
                                    TabLongPressAction(
                                        label = "导出收藏",
                                        icon = CloudUploadIcon,
                                        onClick = onFavoritesExport,
                                    ),
                                )
                                else -> listOf(
                                    TabLongPressAction(
                                        label = "检查更新",
                                        icon = UpgradeIcon,
                                        onClick = onCheckForUpdates,
                                    ),
                                )
                            }
                            onLongPressActionMenuRequested(
                                TabLongPressMenuState(
                                    anchorBoundsOnScreen = tabBoundsOnScreen[index],
                                    focusIcon = if (selected) tab.selectedIcon else tab.icon,
                                    focusLabel = tab.label,
                                    isSelected = selected,
                                    focusTint = if (selected) selectedColor else unselectedColor,
                                    dark = dark,
                                    actions = actions,
                                ),
                            )
                        },
                    )
                } else {
                    Modifier.clickable(
                        onClick = { handleTabClick(index) },
                    )
                }
                Box(
                    modifier = Modifier.weight(1f).fillMaxHeight()
                        .onGloballyPositioned { coordinates ->
                            val topLeft = coordinates.localToScreen(Offset.Zero)
                            val bounds = Rect(
                                left = topLeft.x,
                                top = topLeft.y,
                                right = topLeft.x + coordinates.size.width,
                                bottom = topLeft.y + coordinates.size.height,
                            )
                            if (tabBoundsOnScreen[index] != bounds) tabBoundsOnScreen[index] = bounds
                        }
                        .then(tabClickModifier)
                        .padding(vertical = 3.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Icon(
                            imageVector = if (selected) tab.selectedIcon else tab.icon,
                            contentDescription = tab.description,
                            tint = itemColor,
                            modifier = Modifier.size(26.dp).graphicsLayer {
                                val squash = 1f - tapScale.value
                                scaleX = 1f + squash * .34f
                                scaleY = tapScale.value
                                translationY = squash * 12.dp.toPx()
                            },
                        )
                        Text(tab.label, color = itemColor, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                    }
                }
            }
        }
    }
}

private const val TAB_SWITCH_HAPTIC_MIN_INTERVAL_MS = 120L

/** Prefer Android's intentionally soft frequent-choice tick, with compatible older-API fallbacks. */
private fun View.performSubtleTabHaptic() {
    val feedback = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE ->
            HapticFeedbackConstants.SEGMENT_FREQUENT_TICK
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1 ->
            HapticFeedbackConstants.TEXT_HANDLE_MOVE
        else -> HapticFeedbackConstants.CLOCK_TICK
    }
    performHapticFeedback(feedback)
}

internal fun Modifier.tabLiquidGlassSurface(
    dark: Boolean,
    accent: Color,
): Modifier = shadow(
    elevation = if (dark) .8.dp else 2.5.dp,
    shape = RoundedCornerShape(50),
    clip = false,
    ambientColor = Color.Black.copy(alpha = if (dark) .10f else .06f),
    spotColor = Color.Black.copy(alpha = if (dark) .13f else .10f),
).drawBehind {
    val outline = .8.dp.toPx()
    val corner = CornerRadius(size.height / 2f)
    val fill = if (dark) {
        listOf(Color.White.copy(alpha = .19f), Color.White.copy(alpha = .09f), Color.Black.copy(alpha = .22f))
    } else {
        listOf(Color.White.copy(alpha = .94f), accent.copy(alpha = .16f), Color.White.copy(alpha = .62f))
    }
    val rim = if (dark) {
        listOf(Color.White.copy(alpha = .32f), Color.White.copy(alpha = .10f))
    } else {
        listOf(Color.White.copy(alpha = .92f), accent.copy(alpha = .28f))
    }
    drawRoundRect(brush = Brush.verticalGradient(fill), cornerRadius = corner)
    drawRoundRect(
        brush = Brush.radialGradient(
            colors = listOf(
                Color.White.copy(alpha = if (dark) .24f else .56f),
                Color.White.copy(alpha = if (dark) .07f else .14f),
                Color.Transparent,
            ),
            center = Offset(size.width * .26f, size.height * .12f),
            radius = size.height * 1.15f,
        ),
        cornerRadius = corner,
    )
    drawRoundRect(
        brush = Brush.verticalGradient(rim),
        topLeft = Offset(outline / 2f, outline / 2f),
        size = androidx.compose.ui.geometry.Size(size.width - outline, size.height - outline),
        cornerRadius = CornerRadius((size.height - outline) / 2f),
        style = Stroke(width = outline),
    )
}
