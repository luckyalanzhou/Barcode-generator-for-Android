package com.luckyalanzhou.barcodegenerator

import android.app.ActivityManager
import android.os.Bundle
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/** Compose 试用页，仅验证新底部导航组件，不接管现有 View 页面。 */
class ComposePreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ComposePreviewScreen() }
    }
}

data class TabItem(
    val label: String,
    val outlineIcon: Painter,
    val filledIcon: Painter
)

@Composable
fun TelegramBottomBar(
    items: List<TabItem>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val supportsBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
        context.getSystemService(ActivityManager::class.java)?.isLowRamDevice != true
    BoxWithConstraints(
        modifier = modifier.fillMaxWidth().height(72.dp).clip(RoundedCornerShape(20.dp))
    ) {
        // Android 12+ 非低内存设备启用轻度模糊；低端机回退为半透明实色，避免掉帧。
        Box(
            modifier = Modifier.fillMaxSize()
                .then(if (supportsBlur) Modifier.blur(18.dp) else Modifier)
                .background(Color.White.copy(alpha = if (supportsBlur) 0.62f else 0.92f))
        )
        val tabWidth = maxWidth / items.size.coerceAtLeast(1)
        val indicatorX = remember { Animatable(4.dp.value) }
        val indicatorScaleX = remember { Animatable(1f) }
        LaunchedEffect(selectedIndex, tabWidth) {
            val target = selectedIndex * tabWidth.value + 4.dp.value
            // 横向速度感：先拉宽，再以低阻尼弹簧回到原尺寸。
            launch {
                indicatorScaleX.snapTo(1f)
                indicatorScaleX.animateTo(1.14f, tween(90))
                indicatorScaleX.animateTo(1f, spring(Spring.DampingRatioLowBouncy, Spring.StiffnessMediumLow))
            }
            indicatorX.animateTo(target, spring(Spring.DampingRatioLowBouncy, Spring.StiffnessMediumLow))
        }
        Box(
            modifier = Modifier.offset { IntOffset((indicatorX.value * density).roundToInt(), 8.dp.roundToPx()) }
                .size(width = (tabWidth - 8.dp).coerceAtLeast(1.dp), height = 56.dp)
                .graphicsLayer {
                    scaleX = indicatorScaleX.value
                    transformOrigin = TransformOrigin.Center
                }
                .clip(RoundedCornerShape(16.dp)).background(Color(0xffe8f1ff))
        )
        Row(modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp)) {
            items.forEachIndexed { index, item ->
                TelegramTabItem(
                    item = item,
                    selected = index == selectedIndex,
                    onClick = {
                        if (index != selectedIndex) {
                            // 触觉在指示器弹性动画启动前触发，与拉伸瞬间同步。
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onSelected(index)
                        }
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun TelegramTabItem(item: TabItem, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val iconScale by animateFloatAsState(
        targetValue = if (selected) 1.12f else 1f,
        animationSpec = spring(Spring.DampingRatioLowBouncy, Spring.StiffnessMediumLow),
        label = "tabIconScale"
    )
    val tint by animateColorAsState(
        targetValue = if (selected) Color(0xff007aff) else Color(0xff667085),
        animationSpec = tween(180),
        label = "tabTint"
    )
    Column(
        modifier = modifier.clickable(onClick = onClick).padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        AnimatedContent(
            targetState = selected,
            label = "tabIconStyle"
        ) { active ->
            Icon(
                painter = if (active) item.filledIcon else item.outlineIcon,
                contentDescription = item.label,
                tint = tint,
                modifier = Modifier.size(24.dp).scale(iconScale)
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(item.label, fontSize = 12.sp, color = tint)
    }
}

@Composable
private fun ComposePreviewScreen() {
    var selected by remember { mutableIntStateOf(0) }
    val context = LocalContext.current
    val lowRam = context.getSystemService(ActivityManager::class.java)?.isLowRamDevice == true
    val tabs = listOf(
        TabItem("生成", painterResource(R.drawable.ic_tab_barcode), painterResource(R.drawable.ic_tab_barcode_selected)),
        TabItem("历史", painterResource(R.drawable.ic_tab_history), painterResource(R.drawable.ic_tab_history_selected)),
        TabItem("收藏", painterResource(R.drawable.ic_tab_favorite), painterResource(R.drawable.ic_tab_favorite_selected)),
        TabItem("设置", painterResource(R.drawable.ic_tab_settings), painterResource(R.drawable.ic_tab_settings_selected))
    )
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = Color(0xfff2f2f7)) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                Text("Compose 试用版", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(8.dp))
                Text(if (lowRam) "低端机模式：已关闭模糊" else "Jetpack Compose Bottom Tab", color = Color(0xff667085), fontSize = 14.sp)
                Spacer(Modifier.height(180.dp))
                TelegramBottomBar(items = tabs, selectedIndex = selected, onSelected = { selected = it })
            }
        }
    }
}
