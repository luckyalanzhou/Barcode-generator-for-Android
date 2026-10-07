package com.luckyalanzhou.barcodegenerator.ui.feature.lanshare

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.createBitmap
import androidx.core.graphics.set
import com.luckyalanzhou.barcodegenerator.domain.LanShareSession
import com.luckyalanzhou.barcodegenerator.ui.dialogs.DialogAction
import com.luckyalanzhou.barcodegenerator.ui.theme.LocalAppColorScheme
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ComposeLanShareQrDialog(
    session: LanShareSession,
    dark: Boolean,
    browserConnected: Boolean,
    primary: Color,
    secondary: Color,
    onDismiss: () -> Unit,
    onCopyAddress: (String) -> Unit,
) {
    val qrSize = 260.dp
    val colors = LocalAppColorScheme.current
    val foreground = colors.barcode.qrForeground.toArgb()
    val background = colors.barcode.qrBackground.toArgb()
    val bitmap = remember(session.shareUrl, dark) {
        createLanShareQrBitmap(session.shareUrl, foreground, background, qrSize.value.toInt())
    }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val closeSheet: () -> Unit = {
        scope.launch {
            sheetState.hide()
            onDismiss()
        }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = colors.surfaces.surface,
        contentColor = primary,
        dragHandle = { BottomSheetDefaults.DragHandle(color = secondary.copy(alpha = 0.45f)) },
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("等待设备扫码", color = primary, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (browserConnected) "设备已连接" else "等待设备连接",
                        color = if (browserConnected) colors.controls.success else secondary,
                        fontSize = 13.sp,
                    )
                }
                TextButton(onClick = closeSheet) {
                    Text("关闭", color = secondary)
                }
            }
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "局域网分享二维码",
                modifier = Modifier.padding(top = 12.dp).size(qrSize),
            )
            Text(
                "同一 Wi-Fi 下扫码即可加入",
                modifier = Modifier.padding(top = 12.dp),
                color = secondary,
                fontSize = 14.sp,
            )
            SelectionContainer(Modifier.fillMaxWidth().padding(top = 14.dp)) {
                Text(
                    session.baseUrl,
                    color = primary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    maxLines = 1,
                )
            }
            Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.End) {
                DialogAction("复制连接地址", dark, { onCopyAddress(session.shareUrl) }, primary = true)
            }
        }
    }
}

private fun createLanShareQrBitmap(value: String, foreground: Int, background: Int, size: Int): Bitmap {
    val matrix = com.google.zxing.MultiFormatWriter().encode(
        value,
        com.google.zxing.BarcodeFormat.QR_CODE,
        size,
        size,
        mapOf(com.google.zxing.EncodeHintType.MARGIN to 1),
    )
    return createBitmap(matrix.width, matrix.height, Bitmap.Config.ARGB_8888).also { image ->
        for (x in 0 until matrix.width) for (y in 0 until matrix.height) {
            image[x, y] = if (matrix[x, y]) foreground else background
        }
    }
}
