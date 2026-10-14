package com.luckyalanzhou.barcodegenerator.ui.feature.lanshare

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.TextButton
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.heightIn
import com.luckyalanzhou.barcodegenerator.ui.component.iosPressFeedback
import com.luckyalanzhou.barcodegenerator.icons.AttachFileIcon
import com.luckyalanzhou.barcodegenerator.ui.theme.LocalAppColorScheme

@Composable
internal fun LanShareUploadingBubble(upload: LanShareUploadingContent, onCancel: () -> Unit) {
    val colors = LocalAppColorScheme.current
    val shape = RoundedCornerShape(18.dp)
    val progress = (upload.progressPercent / 100f).coerceIn(0f, 1f)
    val fill = colors.controls.accent.copy(alpha = .38f)
    val track = colors.surfaces.overlay
    val label = colors.text.primary
    val cancelInteraction = remember { MutableInteractionSource() }

    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 3.dp),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End,
    ) {
        Box(
            Modifier
                .widthIn(min = 220.dp, max = 340.dp)
                .clip(shape)
                .background(track)
                .drawBehind {
                    drawRect(color = fill, size = androidx.compose.ui.geometry.Size(size.width * progress, size.height))
                },
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(AttachFileIcon, contentDescription = "正在上传", tint = label, modifier = Modifier.size(26.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(upload.name, color = label, fontSize = 14.sp, maxLines = 1)
                    Text(
                        "${formatLanShareSize(upload.uploadedBytes.coerceIn(0L, upload.size.coerceAtLeast(0L)))} / ${formatLanShareSize(upload.size)}",
                        color = label.copy(alpha = .75f),
                        fontSize = 12.sp,
                        maxLines = 1,
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text("${upload.progressPercent}%", color = label, fontSize = 13.sp, maxLines = 1)
                TextButton(onClick = onCancel, interactionSource = cancelInteraction,
                    modifier = Modifier.iosPressFeedback(cancelInteraction).widthIn(min = 48.dp).heightIn(min = 48.dp)) {
                Text("取消",
                    color = label.copy(alpha = .82f),
                    fontSize = 12.sp,
                    maxLines = 1,
                )
                }
            }
        }
    }
}
