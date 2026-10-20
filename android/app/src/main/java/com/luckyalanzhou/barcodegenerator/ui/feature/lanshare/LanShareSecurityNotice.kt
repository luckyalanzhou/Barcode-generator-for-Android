package com.luckyalanzhou.barcodegenerator.ui.feature.lanshare

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.fillMaxWidth

/** 提醒主机端：分享服务使用未加密且无密码保护的 HTTP，仅应在可信网络中开放。 */
@Composable
internal fun LanShareSecurityNotice(
    secondary: Color,
    modifier: Modifier = Modifier,
) {
    Text(
        text = "HTTP/WebSocket 未加密、无密码保护；能访问此地址的设备可上传或下载文件。请仅在可信 Wi-Fi 下使用。",
        color = secondary,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        textAlign = TextAlign.Center,
        modifier = modifier.fillMaxWidth(),
    )
}
