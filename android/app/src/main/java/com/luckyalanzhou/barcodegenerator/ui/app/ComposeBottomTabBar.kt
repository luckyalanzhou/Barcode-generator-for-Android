package com.luckyalanzhou.barcodegenerator.ui.app

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
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

private data class ComposeTabSpec(
    val label: String,
    val description: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector = icon,
)

@Composable
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
    var dragProgress by remember { mutableFloatStateOf(selectedIndex.toFloat()) }
    var dragging by remember { mutableStateOf(false) }
    var lastTarget by remember { mutableIntStateOf(selectedIndex) }
    var tapPulseTab by remember { mutableIntStateOf(-1) }
    var tapPulseGeneration by remember { mutableIntStateOf(0) }
    val tapScope = rememberCoroutineScope()

    LaunchedEffect(selectedIndex, dragging) {
        if (!dragging) {
            dragProgress = selectedIndex.toFloat()
        }
    }

    BoxWithConstraints(
        modifier = modifier.fillMaxSize().padding(4.dp)
            .hazeEffect(backdrop) {
                backgroundColor = themeColors.surfaces.background
                blurRadius = 20.dp
                noiseFactor = 0f
                tints = listOf(HazeTint(themeColors.surfaces.surface.copy(alpha = if (dark) .18f else .10f)))
            }
            .padding(horizontal = 6.dp, vertical = 6.dp)
            .pointerInput(Unit) {
            detectHorizontalDragGestures(
                onDragStart = { position ->
                    dragging = true
                    val tabWidth = (size.width - 12.dp.toPx()) / tabs.size
                    val step = tabWidth + 4.dp.toPx()
                    dragProgress = ((position.x - tabWidth / 2f) / step)
                        .coerceIn(0f, (tabs.size - 1).toFloat())
                    lastTarget = dragProgress.roundToInt().coerceIn(tabs.indices)
                },
                onHorizontalDrag = { change, dragAmount ->
                    change.consume()
                    val tabWidth = (size.width - 12.dp.toPx()) / tabs.size
                    val step = tabWidth + 4.dp.toPx()
                    dragProgress = (dragProgress + dragAmount / step)
                        .coerceIn(0f, (tabs.size - 1).toFloat())
                    val target = dragProgress.roundToInt().coerceIn(tabs.indices)
                    if (target != lastTarget) {
                        lastTarget = target
                        onTabSelected(target, true)
                    }
                },
                onDragEnd = {
                    dragging = false
                    onTabSelected(lastTarget, true)
                },
                onDragCancel = { dragging = false }
            )
        }
    ) {
        val tabWidth = (maxWidth - 12.dp) / tabs.size
        val indicatorOffset = (tabWidth + 4.dp) * dragProgress
        Box(
            // Keep the selected capsule inset; there is no enclosing capsule border.
            modifier = Modifier.offset(x = indicatorOffset).width(tabWidth).fillMaxSize()
                // 以导航栏左侧为水平基准，避免 Center 先居中后再叠加偏移导致错位。
                .align(Alignment.CenterStart)
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
                                themeColors.navigation.tabHighlight.copy(alpha = if (dark) .42f else .86f),
                                themeColors.surfaces.surface.copy(alpha = if (dark) .24f else .42f),
                                selectedColor.copy(alpha = if (dark) .20f else .12f),
                            ),
                        ),
                        cornerRadius = CornerRadius(size.height / 2f),
                    )
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
        )
        Row(
            modifier = Modifier.fillMaxSize(),
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
                    targetValue = if (glassTouchesContent) 1.12f else 1f,
                    animationSpec = tween(ComposeAnimationConfig.tabItemScaleDurationMillis),
                    label = "tab-item-scale-$index",
                )
                val tapScale = animateFloatAsState(
                    targetValue = if (tapPulseTab == index) .78f else 1f,
                    animationSpec = ComposeAnimationConfig.jellySpring(),
                    label = "tab-tap-jelly-scale-$index",
                )
                Box(
                    modifier = Modifier.weight(1f).fillMaxHeight().graphicsLayer {
                        scaleX = itemScale.value
                        scaleY = itemScale.value
                    }
                        .pointerInput(index) {
                            detectTapGestures {
                                onTabSelected(index, false)
                                tapPulseTab = index
                                tapPulseGeneration += 1
                                val generation = tapPulseGeneration
                                tapScope.launch {
                                    delay(ComposeAnimationConfig.tabJellyResetDelayMillis)
                                    if (tapPulseGeneration == generation) tapPulseTab = -1
                                }
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
