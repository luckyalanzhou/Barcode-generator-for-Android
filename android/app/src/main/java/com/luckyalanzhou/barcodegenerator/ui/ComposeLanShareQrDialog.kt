package com.luckyalanzhou.barcodegenerator.ui

import com.luckyalanzhou.barcodegenerator.MainActivity
import com.luckyalanzhou.barcodegenerator.domain.LanShareSession
import com.luckyalanzhou.barcodegenerator.icons.ContentCopyIcon
import android.graphics.Bitmap
import androidx.core.graphics.createBitmap
import androidx.core.graphics.set
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.text.selection.SelectionContainer

@Composable
internal fun ComposeLanShareQrDialog(
    session: LanShareSession,
    dark: Boolean,
    primary: Color,
    secondary: Color,
    onDismiss: () -> Unit,
    onHideQr: () -> Unit,
    onCopyAddress: (String) -> Unit,
) {
    val qrSize = 280.dp
    val dialogWidth = qrSize + 24.dp
    val foreground = LocalBarcodeThemeColors.current.qrForeground.toArgb()
    val background = LocalBarcodeThemeColors.current.qrBackground.toArgb()
    val bitmap = remember(session.baseUrl, dark) { createLanShareQrBitmap(session.baseUrl, foreground, background, qrSize.value.toInt()) }
    Dialog(
        onDismissRequest = {
            onHideQr()
            onDismiss()
        },
        properties = DialogProperties(dismissOnClickOutside = false, usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier.fillMaxSize().clickable {
                onHideQr()
                onDismiss()
            },
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier = Modifier.width(dialogWidth).clickable { },
                shape = RoundedCornerShape(22.dp),
                color = LocalBarcodeThemeColors.current.surface,
                shadowElevation = 1.dp,
            ) {
                Box(Modifier.padding(start = 12.dp, end = 12.dp, top = 14.dp, bottom = 12.dp)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Image(bitmap.asImageBitmap(), "局域网分享二维码", modifier = Modifier.size(qrSize).background(Color(background)), contentScale = ContentScale.FillBounds)
                        Row(Modifier.width(qrSize).height(48.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(session.baseUrl, color = secondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                            IconButton(onClick = { onCopyAddress(session.baseUrl) }, modifier = Modifier.size(48.dp)) { Icon(ContentCopyIcon, "复制局域网传输地址", tint = primary) }
                        }
                    }
                }
            }
        }
    }
}

private fun createLanShareQrBitmap(value: String, foreground: Int, background: Int, size: Int = 280): Bitmap {
    val matrix = com.google.zxing.MultiFormatWriter().encode(value, com.google.zxing.BarcodeFormat.QR_CODE, size, size, mapOf(com.google.zxing.EncodeHintType.MARGIN to 1))
    return createBitmap(matrix.width, matrix.height, Bitmap.Config.ARGB_8888).also { image ->
        for (x in 0 until matrix.width) for (y in 0 until matrix.height) image[x, y] = if (matrix[x, y]) foreground else background
    }
}

/** Beta 测试中心的二维码模拟也复用实际二维码弹窗的 Compose 结构。 */
internal fun MainActivity.showLanShareQrDialogCompose(simulatedSession: LanShareSession? = null) {
    val simulated = simulatedSession != null
    val lanState = lanShareViewModel.uiState.value
    val session = simulatedSession ?: lanState.session
    if ((!lanState.isHost && !simulated) || session == null) {
        toast("请先创建分享房间")
        return
    }
    showComposeDialog(compact = false, metricsLabel = if (simulated) "二维码弹窗" else null) { dismiss ->
        val dark = isDark()
        val colors = LocalBarcodeThemeColors.current
        val primary = colors.primary
        val secondary = colors.secondary
        val foreground = colors.qrForeground.toArgb()
        val qrBackground = colors.qrBackground.toArgb()
        val bitmap = remember(session.baseUrl, dark) { createLanShareQrBitmap(session.baseUrl, foreground, qrBackground) }
        ComposeGlassDialogCard(dark) {
            Image(bitmap = bitmap.asImageBitmap(), contentDescription = "局域网分享二维码", modifier = Modifier.fillMaxWidth().background(Color(qrBackground)), contentScale = ContentScale.FillWidth)
            SelectionContainer {
                Text(session.baseUrl, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), color = secondary, fontSize = 13.sp, textAlign = TextAlign.Center)
            }
            Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.End) {
                DialogAction("关闭", dark, {
                    if (!simulated) lanShareViewModel.setQrVisible(false)
                    dismiss()
                })
            }
        }
    }
}
