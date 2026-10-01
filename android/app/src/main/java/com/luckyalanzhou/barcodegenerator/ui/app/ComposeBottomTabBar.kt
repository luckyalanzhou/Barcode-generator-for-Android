package com.luckyalanzhou.barcodegenerator.ui.app

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import com.luckyalanzhou.barcodegenerator.ui.theme.*

import com.luckyalanzhou.barcodegenerator.icons.BarcodeIcon
import com.luckyalanzhou.barcodegenerator.icons.FavoriteFilledIcon
import com.luckyalanzhou.barcodegenerator.icons.FavoriteIcon
import com.luckyalanzhou.barcodegenerator.icons.HistoryFilledIcon
import com.luckyalanzhou.barcodegenerator.icons.HistoryIcon
import com.luckyalanzhou.barcodegenerator.icons.SettingsFilledIcon
import com.luckyalanzhou.barcodegenerator.icons.SettingsIcon

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeSourceSelection
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.glass.GlassStyle
import dev.chrisbanes.haze.glass.GlassTransformPivot
import dev.chrisbanes.haze.glass.GlassTransformTarget
import dev.chrisbanes.haze.glass.hazeGlass

private data class ComposeTabSpec(
    val label: String,
    val description: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector = icon,
)

@Composable
@OptIn(ExperimentalHazeApi::class)
internal fun BarcodeComposeBottomTabBar(selectedIndex: Int, dark: Boolean, backdrop: HazeState, onTabSelected: (index: Int, fromSwipe: Boolean) -> Unit, modifier: Modifier = Modifier) {
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
    val hapticView = LocalView.current
    var dragProgress by remember { mutableFloatStateOf(selectedIndex.toFloat()) }
    var dragging by remember { mutableStateOf(false) }
    var lastTarget by remember { mutableIntStateOf(selectedIndex) }
    var dragDirection by remember { mutableFloatStateOf(1f) }
    var dragTouchX by remember { mutableFloatStateOf(0f) }
    var dragStretch by remember { mutableFloatStateOf(0f) }
    var releaseStretch by remember { mutableFloatStateOf(0f) }
    var settlingDrag by remember { mutableStateOf(false) }
    var settleGeneration by remember { mutableIntStateOf(0) }
    var tapPulseTab by remember { mutableIntStateOf(-1) }
    var tapPulseGeneration by remember { mutableIntStateOf(0) }
    val tapScope = rememberCoroutineScope()
    val glassInteractionSource = remember { MutableInteractionSource() }
    val glassPressed by glassInteractionSource.collectIsPressedAsState()
    val density = LocalDensity.current
    // Capture the tab artwork separately so the moving indicator can refract
    // icons and labels without feeding its own glass output back into the source.
    val tabArtworkBackdrop = remember { HazeState() }
    val tabArtworkInput = remember(tabArtworkBackdrop) {
        HazeInput.Sources(
            state = tabArtworkBackdrop,
            selection = HazeSourceSelection.All,
        )
    }
    val velocityTracker = remember { VelocityTracker() }
    val selectedProgress by animateFloatAsState(
        targetValue = selectedIndex.toFloat(),
        animationSpec = spring(dampingRatio = 0.78f, stiffness = Spring.StiffnessMediumLow),
        label = "liquid-glass-tab-position",
    )
    val indicatorStretch by animateFloatAsState(
        targetValue = 1f + when {
            dragging -> dragStretch
            settlingDrag -> releaseStretch
            else -> 0f
        },
        animationSpec = spring(dampingRatio = 0.58f, stiffness = 680f),
        label = "liquid-glass-tab-stretch",
    )
    val dragLightAlpha by animateFloatAsState(
        targetValue = when {
            dragging -> 0.62f
            settlingDrag -> 0.30f
            else -> 0f
        },
        animationSpec = tween(durationMillis = if (dragging) 70 else 260),
        label = "liquid-glass-drag-light",
    )
    val pressCompression by animateFloatAsState(
        targetValue = if (glassPressed) 0.055f else 0f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 620f),
        label = "liquid-glass-tab-press",
    )

    fun settleIndicator(stretch: Float) {
        dragging = false
        releaseStretch = stretch.coerceIn(0.02f, 0.10f)
        settlingDrag = true
        settleGeneration += 1
        val generation = settleGeneration
        tapScope.launch {
            delay(72)
            if (settleGeneration == generation) {
                releaseStretch = 0f
                delay(260)
                if (settleGeneration == generation) settlingDrag = false
            }
        }
    }

    val themeGlassStyle = remember(dark, themeColors.surfaces.surface) {
        GlassStyle.regular.then {
            tint(themeColors.surfaces.surface.copy(alpha = if (dark) 0.10f else 0.06f))
            shape(RoundedCornerShape(50))
            pressed {
                lightingIntensity(0.9f)
                refractionMultiplier(1.04f)
                whitePointDelta(0.025f)
            }
            interactionLightRadiusFraction(0.72f)
            interactionPositionAnimationSpec(
                spring(dampingRatio = 1f, stiffness = Spring.StiffnessMedium),
            )
        }
    }
    val indicatorGlassStyle = remember(dark, themeColors.navigation.tabHighlight, themeColors.surfaces.surface) {
        GlassStyle.clear.then {
            backgroundColor(themeColors.surfaces.surface.copy(alpha = if (dark) 0.16f else 0.12f))
            tint(themeColors.navigation.tabHighlight.copy(alpha = if (dark) 0.10f else 0.08f))
            optics(
                refractionStrength = 0.86f,
                refractionHeightFraction = 0.34f,
                depth = 0.58f,
            )
            shape(RoundedCornerShape(50))
            specularIntensity(0.62f)
        }
    }

    BoxWithConstraints(
        modifier = modifier.fillMaxSize().padding(4.dp)
            .hazeGlass(
                input = HazeInput.Sources(backdrop),
                style = themeGlassStyle,
                interactionSource = glassInteractionSource,
                interactionTransformTarget = GlassTransformTarget.MaterialOnly,
                interactionTransformPivot = GlassTransformPivot.Pointer,
            )
            .padding(horizontal = 6.dp, vertical = 6.dp)
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { position ->
                        settleGeneration += 1
                        settlingDrag = false
                        releaseStretch = 0f
                        dragStretch = 0.025f
                        dragTouchX = position.x
                        dragDirection = 1f
                        velocityTracker.resetTracking()
                        dragging = true
                        dragProgress = selectedProgress
                        val tabWidth = (size.width - 12.dp.toPx()) / tabs.size
                        val step = tabWidth + 4.dp.toPx()
                        dragProgress = ((position.x - tabWidth / 2f) / step)
                            .coerceIn(0f, (tabs.size - 1).toFloat())
                        lastTarget = dragProgress.roundToInt().coerceIn(tabs.indices)
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        velocityTracker.addPosition(change.uptimeMillis, change.position)
                        dragTouchX = change.position.x
                        if (kotlin.math.abs(dragAmount) > 0.1f) {
                            dragDirection = if (dragAmount > 0f) 1f else -1f
                        }
                        val speedFraction = velocityTracker.calculateVelocity().x.let { kotlin.math.abs(it) / 3800f }
                        dragStretch = (0.025f + speedFraction * 0.12f).coerceAtMost(0.14f)

                        val tabWidth = (size.width - 12.dp.toPx()) / tabs.size
                        val step = tabWidth + 4.dp.toPx()
                        dragProgress = (dragProgress + dragAmount / step)
                            .coerceIn(0f, (tabs.size - 1).toFloat())
                        val target = dragProgress.roundToInt().coerceIn(tabs.indices)
                        if (target != lastTarget) {
                            lastTarget = target
                            hapticView.performSubtleTabHaptic()
                            onTabSelected(target, true)
                        }
                    },
                    onDragEnd = {
                        settleIndicator((dragStretch * 0.42f).coerceIn(0.025f, 0.065f))
                        onTabSelected(lastTarget, true)
                    },
                    onDragCancel = {
                        settleIndicator((dragStretch * 0.36f).coerceIn(0.02f, 0.055f))
                    },
                )
            }
    ) {
        val tabWidth = (maxWidth - 12.dp) / tabs.size
        val indicatorProgress = if (dragging) dragProgress else selectedProgress
        val indicatorOffset = (tabWidth + 4.dp) * indicatorProgress
        Box(
            // Keep the selected capsule inset; there is no enclosing capsule border.
            modifier = Modifier.offset(x = indicatorOffset).width(tabWidth).fillMaxSize()
                // 以导航栏左侧为水平基准，避免 Center 先居中后再叠加偏移导致错位。
                .align(Alignment.CenterStart)
                // This effect is above the tab row and samples that row as its input,
                // so the moving lens bends the actual icon/text pixels beneath it.
                .zIndex(1f)
                .graphicsLayer {
                    val stretchAmount = (indicatorStretch - 1f).coerceAtLeast(0f)
                    scaleX = indicatorStretch * (1f - pressCompression * 0.24f)
                    scaleY = (1f - pressCompression) * (1f - stretchAmount * 0.30f)
                    transformOrigin = TransformOrigin(
                        pivotFractionX = if (dragDirection > 0f) 0f else 1f,
                        pivotFractionY = 0.5f,
                    )
                }
                .shadow(
                    elevation = 3.dp,
                    shape = RoundedCornerShape(50),
                    clip = false,
                    ambientColor = selectedColor.copy(alpha = if (dark) .16f else .10f),
                    spotColor = androidx.compose.ui.graphics.Color.Black.copy(alpha = if (dark) .16f else .08f),
                )
                .drawBehind {
                    val outline = .7.dp.toPx()
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = if (dark) .22f else .56f),
                                themeColors.navigation.tabHighlight.copy(alpha = if (dark) .32f else .38f),
                                selectedColor.copy(alpha = if (dark) .16f else .10f),
                            ),
                        ),
                        cornerRadius = CornerRadius(size.height / 2f),
                    )
                    if (dragLightAlpha > 0f) {
                        val touchCenter = Offset(
                            x = (dragTouchX - indicatorOffset.toPx()).coerceIn(0f, size.width),
                            y = size.height * 0.5f,
                        )
                        drawRoundRect(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = dragLightAlpha),
                                    selectedColor.copy(alpha = dragLightAlpha * 0.42f),
                                    Color.Transparent,
                                ),
                                center = touchCenter,
                                radius = size.height * 1.18f,
                            ),
                            cornerRadius = CornerRadius(size.height / 2f),
                        )
                    }
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                themeColors.navigation.tabRimTop.copy(alpha = if (dark) .58f else .78f),
                                themeColors.navigation.tabRimBottom.copy(alpha = if (dark) .20f else .24f),
                            ),
                        ),
                        topLeft = androidx.compose.ui.geometry.Offset(outline / 2f, outline / 2f),
                        size = Size(size.width - outline, size.height - outline),
                        cornerRadius = CornerRadius((size.height - outline) / 2f),
                        style = Stroke(width = outline),
                    )
                }
                .hazeGlass(
                    input = tabArtworkInput,
                    style = indicatorGlassStyle,
                )
        )
        Row(
            modifier = Modifier.fillMaxSize().hazeSource(tabArtworkBackdrop, zIndex = 0f),
            horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically
        ) {
            tabs.forEachIndexed { index, tab ->
                val selected = selectedIndex == index
                val itemCenter = (tabWidth + 4.dp) * index + tabWidth / 2
                val glassLeft = indicatorOffset
                val glassRight = indicatorOffset + tabWidth
                val contentHalfWidth = 16.dp
                val contentLeft = itemCenter - contentHalfWidth
                val contentRight = itemCenter + contentHalfWidth
                val glassTouchesContent = dragging && glassRight >= contentLeft && glassLeft <= contentRight
                val itemColor by animateColorAsState(
                    targetValue = if (if (dragging) glassTouchesContent else selected) selectedColor else unselectedColor,
                    animationSpec = tween(ComposeAnimationConfig.tabItemColorDurationMillis),
                    label = "tab-item-color-$index",
                )
                val itemScale = animateFloatAsState(
                    targetValue = if (glassTouchesContent) 1.08f else 1f,
                    animationSpec = tween(ComposeAnimationConfig.tabItemScaleDurationMillis),
                    label = "tab-item-scale-$index",
                )
                val tapScale = animateFloatAsState(
                    targetValue = if (tapPulseTab == index) .92f else 1f,
                    animationSpec = ComposeAnimationConfig.pressSpring(),
                    label = "tab-tap-glass-response-$index",
                )
                Box(
                    modifier = Modifier.weight(1f).fillMaxHeight().graphicsLayer {
                        scaleX = itemScale.value
                        scaleY = itemScale.value
                    }
                        // clickable emits a real PressInteraction consumed by
                        // hazeGlass, so the material lights from the touch point.
                        .clickable(
                            interactionSource = glassInteractionSource,
                            indication = null,
                        ) {
                            hapticView.performSubtleTabHaptic()
                            val travel = index - selectedIndex
                            if (travel != 0) dragDirection = if (travel > 0) 1f else -1f
                            dragTouchX = with(density) { itemCenter.toPx() }
                            val clickStretch = if (travel == 0) 0.025f
                            else (0.045f + kotlin.math.abs(travel) * 0.014f).coerceAtMost(0.09f)
                            settleIndicator(clickStretch)
                            onTabSelected(index, false)
                            tapPulseTab = index
                            tapPulseGeneration += 1
                            val generation = tapPulseGeneration
                            tapScope.launch {
                                delay(ComposeAnimationConfig.tabJellyResetDelayMillis)
                                if (tapPulseGeneration == generation) tapPulseTab = -1
                            }
                        }.padding(vertical = 3.dp), contentAlignment = Alignment.Center
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
