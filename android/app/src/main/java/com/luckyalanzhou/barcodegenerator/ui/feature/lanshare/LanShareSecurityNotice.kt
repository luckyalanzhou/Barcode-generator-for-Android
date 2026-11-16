package com.luckyalanzhou.barcodegenerator.ui.feature.lanshare

import androidx.compose.ui.res.stringResource
import com.luckyalanzhou.barcodegenerator.R
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.fillMaxWidth

/** 提醒主机端：令牌限制加入权限，但 HTTP 数据本身未加密，仍应只在可信网络使用。 */
@Composable
internal fun LanShareSecurityNotice(
    secondary: Color,
    modifier: Modifier = Modifier,
) {
    Text(
        text = stringResource(R.string.lan_share_security),
        color = secondary,
        fontSize = 14.sp,
        lineHeight = 19.sp,
        fontWeight = FontWeight.Medium,
        textAlign = TextAlign.Start,
        modifier = modifier.fillMaxWidth(),
    )
}
