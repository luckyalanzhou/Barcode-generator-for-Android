package com.luckyalanzhou.barcodegenerator.ui.app

import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.luckyalanzhou.barcodegenerator.ui.theme.LocalAppColorScheme
import com.luckyalanzhou.barcodegenerator.ui.theme.LocalAppDimensions

internal data class TabLongPressAction(
    val label: String,
    val icon: ImageVector,
    val destructive: Boolean = false,
    val onClick: () -> Unit,
)

@Composable
internal fun TabLongPressActionDialog(
    dark: Boolean,
    actions: List<TabLongPressAction>,
    onDismiss: () -> Unit,
) {
    val colors = LocalAppColorScheme.current
    val dimensions = LocalAppDimensions.current
    val density = LocalDensity.current
    val dialogView = LocalView.current
    val panelShape = remember { RoundedCornerShape(25.dp) }
    val separator = colors.borders.divider.copy(alpha = if (dark) .36f else .44f)

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

        BoxWithConstraints(Modifier.fillMaxSize()) {
            Box(
                Modifier.fillMaxSize()
                    .background(Color.Transparent)
                    .clickable(onClick = onDismiss),
            )

            val panelWidth = minOf(maxWidth * .62f, 340.dp)
            AnimatedVisibility(
                visible = true,
                modifier = Modifier.align(Alignment.BottomStart)
                    .navigationBarsPadding()
                    .padding(
                        start = dimensions.pageHorizontalPadding,
                        bottom = dimensions.pageBottomPadding + dimensions.bottomTabBarHeight + 12.dp,
                    ),
                enter = fadeIn(tween(150)) + scaleIn(
                    animationSpec = tween(190, easing = FastOutSlowInEasing),
                    initialScale = .94f,
                    transformOrigin = TransformOrigin(.18f, 1f),
                ),
            ) {
                Column(
                    modifier = Modifier.width(panelWidth)
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
                        modifier = Modifier.fillMaxWidth().height(38.dp).padding(horizontal = 18.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Text(
                            text = "操作",
                            color = colors.text.secondary.copy(alpha = .78f),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                    ActionSeparator(color = separator)
                    actions.forEachIndexed { index, action ->
                        if (index > 0) ActionSeparator(color = separator)
                        Row(
                            modifier = Modifier.fillMaxWidth().height(54.dp)
                                .clickable(onClick = action.onClick)
                                .padding(horizontal = 18.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = action.label,
                                color = if (action.destructive) colors.text.destructive else colors.text.primary,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Normal,
                            )
                            Spacer(Modifier.weight(1f))
                            Icon(
                                imageVector = action.icon,
                                contentDescription = null,
                                tint = if (action.destructive) colors.text.destructive else colors.text.primary,
                                modifier = Modifier.padding(start = 18.dp),
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
        Modifier.fillMaxWidth().height(.7.dp).padding(horizontal = 18.dp)
            .background(color),
    )
}
