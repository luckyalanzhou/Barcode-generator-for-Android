package com.luckyalanzhou.barcodegenerator.ui.feature.lanshare

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luckyalanzhou.barcodegenerator.domain.LanShareMessage
import com.luckyalanzhou.barcodegenerator.ui.theme.LocalAppColorScheme

@Composable
internal fun LanShareTextMessageBubble(
    message: LanShareMessage,
    dark: Boolean,
    primary: Color,
    peerColorIndex: Int?,
) {
    val colors = LocalAppColorScheme.current
    val mine = message.sender == "app"
    val bubbleColor = when {
        mine -> colors.controls.progress.copy(alpha = .44f)
        peerColorIndex != null -> lanSharePeerBubbleColor(peerColorIndex, dark)
        else -> colors.surfaces.overlay
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 3.dp),
        horizontalArrangement = if (mine) androidx.compose.foundation.layout.Arrangement.End
        else androidx.compose.foundation.layout.Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            modifier = Modifier.widthIn(max = 340.dp),
            shape = RoundedCornerShape(18.dp),
            color = bubbleColor,
        ) {
            Text(
                text = message.text,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                color = if (mine) colors.content.sentContent else primary,
                fontSize = 15.sp,
                textAlign = TextAlign.Start,
            )
        }
    }
}
