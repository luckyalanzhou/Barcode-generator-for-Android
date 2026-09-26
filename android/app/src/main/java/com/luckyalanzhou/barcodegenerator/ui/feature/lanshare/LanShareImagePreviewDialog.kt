package com.luckyalanzhou.barcodegenerator.ui.feature.lanshare

import android.graphics.Bitmap
import android.os.Build
import android.view.Window
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.window.DialogProperties
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.WindowCompat

@Composable
internal fun LanShareImagePreviewDialog(
    fileName: String,
    bitmap: Bitmap,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        val dialogView = LocalView.current
        SideEffect {
            val dialogWindow = (dialogView.parent as? DialogWindowProvider)?.window
            dialogWindow?.applyImagePreviewSystemBars()
        }
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = fileName,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().padding(start = 8.dp, end = 8.dp, top = 64.dp, bottom = 24.dp),
            )
            Row(
                modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().statusBarsPadding()
                    .padding(start = 16.dp, end = 16.dp, top = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = fileName,
                    modifier = Modifier.weight(1f),
                    color = Color.White,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Surface(
                    onClick = onDismiss,
                    modifier = Modifier.size(42.dp).semantics {
                        contentDescription = "关闭图片预览"
                    },
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.16f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f)),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Canvas(Modifier.size(18.dp)) {
                            val inset = size.minDimension * 0.18f
                            val end = size.minDimension - inset
                            drawLine(
                                color = Color.White,
                                start = Offset(inset, inset),
                                end = Offset(end, end),
                                strokeWidth = 2.dp.toPx(),
                            )
                            drawLine(
                                color = Color.White,
                                start = Offset(end, inset),
                                end = Offset(inset, end),
                                strokeWidth = 2.dp.toPx(),
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun Window.applyImagePreviewSystemBars() {
    WindowCompat.setDecorFitsSystemWindows(this, false)
    WindowInsetsControllerCompat(this, decorView).apply {
        isAppearanceLightStatusBars = false
        isAppearanceLightNavigationBars = false
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        isNavigationBarContrastEnforced = false
    }
}
