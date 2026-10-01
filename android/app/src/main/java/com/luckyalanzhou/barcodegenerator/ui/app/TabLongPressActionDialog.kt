package com.luckyalanzhou.barcodegenerator.ui.app

import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.view.WindowManager
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.runtime.SideEffect
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.luckyalanzhou.barcodegenerator.ui.theme.LocalAppColorScheme
import kotlin.math.roundToInt

internal data class TabLongPressAction(
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
)

@Composable
internal fun TabLongPressActionDialog(
    dark: Boolean,
    anchorBoundsOnScreen: Rect,
    actions: List<TabLongPressAction>,
    onDismiss: () -> Unit,
) {
    val colors = LocalAppColorScheme.current
    val density = LocalDensity.current
    val dialogView = LocalView.current
    val panelShape = remember { RoundedCornerShape(25.dp) }
    val separator = colors.borders.divider.copy(alpha = if (dark) .36f else .44f)
    var dialogOriginOnScreen by remember { mutableStateOf(Offset.Zero) }
    var dialogCoordinatesReady by remember { mutableStateOf(false) }
    var panelSize by remember { mutableStateOf(IntSize.Zero) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        SideEffect {
            val window = (dialogView.parent as? DialogWindowProvider)?.window
            window?.apply {
                addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                setDimAmount(if (dark) .30f else .18f)
                setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                    attributes = attributes.apply {
                        blurBehindRadius = with(density) { 30.dp.roundToPx() }
                    }
                }
            }
        }

        BoxWithConstraints(
            Modifier.fillMaxSize().onGloballyPositioned { coordinates ->
                val origin = coordinates.localToScreen(Offset.Zero)
                if (dialogOriginOnScreen != origin) dialogOriginOnScreen = origin
                dialogCoordinatesReady = true
            },
        ) {
            Box(
                Modifier.fillMaxSize()
                    .background(Color.Transparent)
                    .clickable(onClick = onDismiss),
            )

            val panelMaxWidth = minOf(maxWidth * .62f, 340.dp)
            val menuWidthPx = panelSize.width.toFloat()
            val menuHeightPx = panelSize.height.toFloat()
            val screenWidthPx = with(density) { maxWidth.toPx() }
            val edgePaddingPx = with(density) { 12.dp.toPx() }
            val gapPx = with(density) { 8.dp.toPx() }
            val anchorCenterX = (anchorBoundsOnScreen.left + anchorBoundsOnScreen.right) / 2f - dialogOriginOnScreen.x
            val desiredLeft = anchorCenterX - menuWidthPx / 2f
            val leftPx = desiredLeft.coerceIn(
                edgePaddingPx,
                (screenWidthPx - menuWidthPx - edgePaddingPx).coerceAtLeast(edgePaddingPx),
            )
            val statusBarTopPx = WindowInsets.statusBars.getTop(density).toFloat()
            val desiredTop = anchorBoundsOnScreen.top - dialogOriginOnScreen.y - menuHeightPx - gapPx
            val topPx = desiredTop.coerceAtLeast(statusBarTopPx + gapPx)
            val pivotX = if (menuWidthPx > 0f) {
                ((anchorCenterX - leftPx) / menuWidthPx).coerceIn(0f, 1f)
            } else {
                .5f
            }
            val popupReady = dialogCoordinatesReady && anchorBoundsOnScreen != Rect.Zero && panelSize != IntSize.Zero
            val popupProgress by animateFloatAsState(
                targetValue = if (popupReady) 1f else 0f,
                animationSpec = tween(190, easing = FastOutSlowInEasing),
                label = "tab-action-menu-entrance",
            )
            Column(
                    modifier = Modifier.widthIn(min = 140.dp, max = panelMaxWidth)
                        .width(IntrinsicSize.Max)
                        .onSizeChanged { if (panelSize != it) panelSize = it }
                        .offset { IntOffset(leftPx.roundToInt(), topPx.roundToInt()) }
                        .graphicsLayer {
                            alpha = popupProgress
                            val scale = .94f + .06f * popupProgress
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
                        .background(
                            Brush.verticalGradient(
                                colors = if (dark) {
                                    listOf(
                                        colors.surfaces.panel.copy(alpha = .94f),
                                        colors.surfaces.panel.copy(alpha = .90f),
                                    )
                                } else {
                                    listOf(
                                        Color.White.copy(alpha = .95f),
                                        colors.surfaces.panel.copy(alpha = .92f),
                                    )
                                },
                            ),
                        )
                        .border(
                            width = .8.dp,
                            color = Color.White.copy(alpha = if (dark) .18f else .54f),
                            shape = panelShape,
                        ),
                ) {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(38.dp).padding(horizontal = 16.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Text(
                            text = "操作",
                            color = colors.text.secondary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                    ActionSeparator(color = separator)
                    actions.forEachIndexed { index, action ->
                        if (index > 0) ActionSeparator(color = separator)
                        Row(
                            modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp)
                                .clickable {
                                    onDismiss()
                                    action.onClick()
                                }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = action.label,
                                color = colors.text.primary,
                                style = MaterialTheme.typography.labelLarge,
                            )
                            Spacer(Modifier.weight(1f))
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
