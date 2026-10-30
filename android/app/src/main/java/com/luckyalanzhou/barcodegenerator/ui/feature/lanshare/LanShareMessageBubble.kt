package com.luckyalanzhou.barcodegenerator.ui.feature.lanshare

import com.luckyalanzhou.barcodegenerator.ui.component.glass.resultActionEdgeChrome
import com.luckyalanzhou.barcodegenerator.domain.isLanShareImage
import com.luckyalanzhou.barcodegenerator.domain.LanShareFile
import com.luckyalanzhou.barcodegenerator.icons.AttachFileIcon
import com.luckyalanzhou.barcodegenerator.ui.component.iosPressFeedback
import com.luckyalanzhou.barcodegenerator.ui.theme.LocalAppColorScheme
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 已完成文件的聊天气泡：图片区域点击打开全屏预览，图片文件名或普通附件的“下载”按钮保存原文件。
 * 气泡左右位置由当前设备是否为发送方决定，其他加入设备按稳定颜色索引区分。
 */
@Composable
internal fun LanShareMessageBubble(
    ownFileIds: Set<String>,
    file: LanShareFile,
    previewReady: Boolean,
    loadPreview: suspend (LanShareFile) -> Bitmap?,
    dark: Boolean,
    primary: Color,
    secondary: Color,
    onSaveFile: (LanShareFile) -> Unit,
    isDownloading: Boolean,
    peerColorIndex: Int?,
    onPreviewImage: (Bitmap) -> Unit,
) {
    val themeColors = LocalAppColorScheme.current
    val downloadInteraction = remember(file.id) { MutableInteractionSource() }
    val mine = file.id in ownFileIds
    val previewState = produceState<Bitmap?>(null, file.id, file.modifiedAt, previewReady) {
        if (isLanShareImage(file.name, file.mimeType)) value = loadPreview(file)
    }
    val preview = previewState.value
    val previewSize = remember(preview?.width, preview?.height) {
        preview?.let { fitLanSharePreviewSize(it.width, it.height) }
    }
    val bubbleColor = when {
        mine -> themeColors.controls.accent.copy(alpha = .44f)
        peerColorIndex != null -> lanSharePeerBubbleColor(peerColorIndex, dark)
        else -> themeColors.surfaces.overlay
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 3.dp), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
        if (preview != null && previewSize != null) {
            Surface(
                modifier = Modifier.width((maxOf(previewSize.widthDp, 48) + 16).dp),
                shape = RoundedCornerShape(18.dp), color = bubbleColor, shadowElevation = 0.dp,
            ) {
                Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    val bitmap = preview
                    Box(
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                            .clickable(onClickLabel = "预览图片", role = Role.Button) { onPreviewImage(bitmap) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(
                            bitmap.asImageBitmap(), file.name, contentScale = ContentScale.Fit,
                            modifier = Modifier.width(previewSize.widthDp.dp).height(previewSize.heightDp.dp),
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Box(
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                            .clickable(enabled = !isDownloading, role = Role.Button) { onSaveFile(file) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            middleEllipsize(file.name),
                            color = if (mine) themeColors.content.sentContent else primary,
                            fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Clip,
                            textAlign = TextAlign.Center,
                            textDecoration = TextDecoration.Underline,
                        )
                    }
                    if (isDownloading) {
                        Row(
                            modifier = Modifier.padding(top = 6.dp).heightIn(min = 24.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            androidx.compose.material3.CircularProgressIndicator(
                                modifier = Modifier.size(12.dp),
                                color = if (mine) themeColors.content.sentContent else themeColors.controls.accent,
                                strokeWidth = 1.5.dp,
                            )
                            Text("正在下载…", color = if (mine) themeColors.content.sentContent else secondary, fontSize = 12.sp)
                        }
                    }
                    Text(
                        formatLanShareSize(file.size),
                        color = if (mine) themeColors.content.sentContent.copy(alpha = .78f) else secondary,
                        fontSize = 12.sp,
                        maxLines = 1,
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
                    val downloadShape = RoundedCornerShape(12.dp)
                    Box(
                        modifier = Modifier
                            .iosPressFeedback(downloadInteraction)
                            .heightIn(min = 48.dp)
                            .widthIn(min = ButtonDefaults.MinWidth)
                            .clip(downloadShape)
                            .clickable(
                                enabled = !isDownloading,
                                onClick = { onSaveFile(file) },
                                interactionSource = downloadInteraction,
                                role = Role.Button,
                            )
                            .resultActionEdgeChrome(
                                shape = downloadShape,
                                rimBackground = bubbleColor,
                                edgeHighlight = false,
                            )
                            .padding(horizontal = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (isDownloading) {
                            androidx.compose.material3.CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                color = if (mine) themeColors.content.sentContent else themeColors.controls.accent,
                                strokeWidth = 1.5.dp,
                            )
                            Spacer(Modifier.width(5.dp))
                            Text("下载中", color = if (mine) themeColors.content.sentContent else themeColors.text.link, fontSize = 12.sp, maxLines = 1)
                        } else Text(
                            "下载",
                            color = if (mine) themeColors.content.sentContent else themeColors.text.link,
                            fontSize = 12.sp,
                            maxLines = 1,
                        )
                    }
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
