package com.luckyalanzhou.barcodegenerator.ui.feature.lanshare

import com.luckyalanzhou.barcodegenerator.ui.app.*
import com.luckyalanzhou.barcodegenerator.ui.dialogs.*

import com.luckyalanzhou.barcodegenerator.ui.theme.*

import com.luckyalanzhou.barcodegenerator.MainActivity
import com.luckyalanzhou.barcodegenerator.domain.LanShareSession
import com.luckyalanzhou.barcodegenerator.icons.ContentCopyIcon
import android.graphics.Bitmap
import androidx.core.graphics.createBitmap
import androidx.core.graphics.set
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
    val foreground = LocalAppColorScheme.current.barcode.qrForeground.toArgb()
    val background = LocalAppColorScheme.current.barcode.qrBackground.toArgb()
    val bitmap = remember(session.shareUrl, dark) { createLanShareQrBitmap(session.shareUrl, foreground, background, qrSize.value.toInt()) }
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
                color = LocalAppColorScheme.current.surfaces.surface,
                shadowElevation = 1.dp,
            ) {
                Box(Modifier.padding(start = 12.dp, end = 12.dp, top = 14.dp, bottom = 12.dp)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Image(bitmap.asImageBitmap(), "局域网分享二维码", modifier = Modifier.size(qrSize).background(Color(background)), contentScale = ContentScale.FillBounds)
                        Row(Modifier.width(qrSize).height(48.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(session.shareUrl, color = secondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                            IconButton(onClick = { onCopyAddress(session.shareUrl) }, modifier = Modifier.size(48.dp)) { Icon(ContentCopyIcon, "复制完整分享链接", tint = primary) }
                        }
                        Text("手动连接：打开 ${session.baseUrl} 并输入访问码", color = secondary, fontSize = 12.sp, textAlign = TextAlign.Center)
                        SelectionContainer {
                            Text(session.accessToken, color = primary, fontSize = 15.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp))
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

/** 二维码弹窗统一复用实际的 Compose 结构。 */
internal fun MainActivity.showLanShareQrDialogCompose() {
    val lanState = lanShareViewModel.uiState.value
    val session = lanState.session
    if (!lanState.isHost || session == null) {
        toast("请先创建分享房间")
        return
    }
    showComposeDialog(compact = false) { dismiss ->
        val dark = isDark()
        val colors = LocalAppColorScheme.current
        val primary = colors.text.primary
        val secondary = colors.text.secondary
        val foreground = colors.barcode.qrForeground.toArgb()
        val qrBackground = colors.barcode.qrBackground.toArgb()
        val bitmap = remember(session.shareUrl, dark) { createLanShareQrBitmap(session.shareUrl, foreground, qrBackground) }
        ComposeGlassDialogCard(dark) {
            Image(bitmap = bitmap.asImageBitmap(), contentDescription = "局域网分享二维码", modifier = Modifier.fillMaxWidth().background(Color(qrBackground)), contentScale = ContentScale.FillWidth)
            SelectionContainer {
                Text(session.shareUrl, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), color = secondary, fontSize = 13.sp, textAlign = TextAlign.Center)
            }
            Text("手动连接：打开 ${session.baseUrl} 并输入访问码", modifier = Modifier.fillMaxWidth().padding(top = 8.dp), color = secondary, fontSize = 12.sp, textAlign = TextAlign.Center)
            SelectionContainer {
                Text(session.accessToken, modifier = Modifier.fillMaxWidth().padding(top = 4.dp), color = primary, fontSize = 15.sp, textAlign = TextAlign.Center)
            }
            Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.End) {
                DialogAction("复制完整链接", dark, { composeAppShellActions().copyLanShareAddress(session.shareUrl) })
                DialogAction("关闭", dark, {
                    lanShareViewModel.setQrVisible(false)
                    dismiss()
                })
            }
        }
    }
}
