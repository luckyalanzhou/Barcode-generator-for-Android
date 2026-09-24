package com.luckyalanzhou.barcodegenerator.ui.feature.lanshare

import com.luckyalanzhou.barcodegenerator.domain.isLanShareImageName
import com.luckyalanzhou.barcodegenerator.domain.LanShareFile
import com.luckyalanzhou.barcodegenerator.presentation.lanshare.LanShareUiState
import com.luckyalanzhou.barcodegenerator.icons.AttachFileIcon
import com.luckyalanzhou.barcodegenerator.ui.component.iosPressFeedback
import com.luckyalanzhou.barcodegenerator.ui.theme.LocalAppColorScheme
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

@Composable
internal fun LanShareMessageBubble(
    localFile: (String) -> java.io.File?,
    state: LanShareUiState,
    file: LanShareFile,
    dark: Boolean,
    primary: Color,
    secondary: Color,
    onSaveFile: (LanShareFile) -> Unit,
) {
    val themeColors = LocalAppColorScheme.current
    val downloadInteraction = remember(file.id) { MutableInteractionSource() }
    val mine = file.id in state.ownFileIds
    val previewFile = (localFile(file.id) ?: state.previewFiles[file.id]).takeIf { isLanShareImageName(file.name) }
    val preview = remember(file.id, previewFile?.absolutePath, previewFile?.lastModified()) { previewFile?.let(::decodeLanSharePreview) }
    val bubbleColor = if (mine) themeColors.controls.progress.copy(alpha = .44f) else themeColors.surfaces.overlay
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 3.dp), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
        if (preview != null) {
            Surface(
                modifier = Modifier.widthIn(min = 120.dp, max = 280.dp).clickable { onSaveFile(file) },
                shape = RoundedCornerShape(18.dp), color = bubbleColor, shadowElevation = 0.dp,
            ) {
                Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    val bitmap = preview
                    val scale = minOf(220f / bitmap.width.coerceAtLeast(1), 180f / bitmap.height.coerceAtLeast(1), 1f)
                    Image(
                        bitmap.asImageBitmap(), file.name, contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .width((bitmap.width * scale).coerceAtLeast(80f).roundToInt().dp)
                            .height((bitmap.height * scale).coerceAtLeast(80f).roundToInt().dp),
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        middleEllipsize(file.name),
                        color = if (mine) themeColors.content.sentContent else primary,
                        fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Clip,
                        textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        } else {
            Surface(
                modifier = Modifier.widthIn(min = 220.dp, max = 340.dp),
                shape = RoundedCornerShape(18.dp), color = bubbleColor, shadowElevation = 0.dp,
            ) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(AttachFileIcon, "文件附件", tint = if (mine) themeColors.content.sentContent else themeColors.content.icon, modifier = Modifier.size(26.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(middleEllipsize(file.name), color = if (mine) themeColors.content.sentContent else primary, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Clip, textAlign = TextAlign.Start, modifier = Modifier.fillMaxWidth())
                        Text(formatLanShareSize(file.size), color = if (mine) themeColors.barcode.qrBackground else secondary, fontSize = 12.sp, maxLines = 1, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Start)
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = { onSaveFile(file) }, interactionSource = downloadInteraction,
                        modifier = Modifier.iosPressFeedback(downloadInteraction).height(36.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp), shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (mine) themeColors.content.sentContent.copy(alpha = .18f) else themeColors.controls.button,
                            contentColor = if (mine) themeColors.content.sentContent else themeColors.text.link,
                        ),
                    ) { Text("下载", fontSize = 12.sp, maxLines = 1) }
                }
            }
        }
    }
}

private fun middleEllipsize(value: String, maxChars: Int = 28): String {
    if (value.length <= maxChars) return value
    val visibleChars = (maxChars - 1).coerceAtLeast(2)
    val leading = (visibleChars + 1) / 2
    val trailing = visibleChars - leading
    return value.take(leading) + "…" + value.takeLast(trailing)
}
