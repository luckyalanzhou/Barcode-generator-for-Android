package com.luckyalanzhou.barcodegenerator.ui.feature.lanshare

import com.luckyalanzhou.barcodegenerator.ui.theme.*
import com.luckyalanzhou.barcodegenerator.ui.component.globalButtonChrome
import com.luckyalanzhou.barcodegenerator.ui.component.iosPressFeedback

import com.luckyalanzhou.barcodegenerator.presentation.lanshare.LanShareUiState
import com.luckyalanzhou.barcodegenerator.presentation.lanshare.LanShareUploadingFile
import com.luckyalanzhou.barcodegenerator.domain.LanShareFile
import com.luckyalanzhou.barcodegenerator.domain.LanShareMessage

import com.luckyalanzhou.barcodegenerator.icons.AddIcon
import com.luckyalanzhou.barcodegenerator.icons.AttachFileIcon
import com.luckyalanzhou.barcodegenerator.icons.CircleFilledIcon
import com.luckyalanzhou.barcodegenerator.icons.CircleIcon
import com.luckyalanzhou.barcodegenerator.icons.ContentCopyIcon
import com.luckyalanzhou.barcodegenerator.icons.QrCode2Icon

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.foundation.text.BasicTextField

private sealed interface LanShareTimelineEntry {
    val key: String
    val timestamp: Long

    data class FileEntry(val file: LanShareFile) : LanShareTimelineEntry {
        override val key: String get() = "file:${file.id}"
        override val timestamp: Long get() = file.modifiedAt
    }

    data class MessageEntry(val message: LanShareMessage) : LanShareTimelineEntry {
        override val key: String get() = "message:${message.id}"
        override val timestamp: Long get() = message.createdAt
    }

    data class UploadEntry(val upload: LanShareUploadingFile) : LanShareTimelineEntry {
        override val key: String get() = "upload:${upload.id}"
        override val timestamp: Long get() = upload.startedAt
    }
}

@Composable
internal fun LanShareContent(
    lanState: LanShareUiState,
    message: String,
    dark: Boolean,
    onMessageChange: (String) -> Unit,
    onSetQrVisible: (Boolean) -> Unit,
    onSend: (String) -> Unit,
    onCancelUpload: (String) -> Unit,
    onLoadImagePreview: suspend (LanShareFile) -> Bitmap?,
    onLoadFullImagePreview: suspend (LanShareFile) -> Bitmap?,
    onOpenCamera: () -> Unit,
    onOpenGallery: () -> Unit,
    onOpenFiles: () -> Unit,
    onSaveFile: (LanShareFile) -> Unit,
    onCopyAddress: (String) -> Unit,
) {
    val themeColors = LocalAppColorScheme.current
    val primary = themeColors.text.primary
    val secondary = themeColors.text.secondary
    val panel = themeColors.surfaces.surface
    val inputPanel = themeColors.surfaces.inputPanel
    val accent = themeColors.controls.accent
    val qrOpen = lanState.qrVisible
    var imagePreview by remember { mutableStateOf<Pair<String, Bitmap>?>(null) }
    var imagePreviewFile by remember { mutableStateOf<LanShareFile?>(null) }
    LaunchedEffect(imagePreviewFile?.id, imagePreviewFile?.modifiedAt) {
        imagePreviewFile?.let { file ->
            onLoadFullImagePreview(file)?.let { imagePreview = file.name to it }
        }
    }
    var attachmentMenu by remember { mutableStateOf(false) }
    val session = lanState.session

    if (session == null) {
        Text("正在创建分享房间…", color = secondary, modifier = Modifier.fillMaxWidth().padding(32.dp), textAlign = TextAlign.Center)
        return
    }

    val listState = rememberLazyListState()
    val peerColorIndices = remember(lanState.files, lanState.messages, lanState.ownFileIds) {
        lanSharePeerColorIndices(lanState.files, lanState.ownFileIds, lanState.messages)
    }
    val timeline = remember(lanState.files, lanState.messages, lanState.uploadingFiles) {
        buildList {
            lanState.files.forEach { add(LanShareTimelineEntry.FileEntry(it)) }
            lanState.messages.forEach { add(LanShareTimelineEntry.MessageEntry(it)) }
            lanState.uploadingFiles.forEach { add(LanShareTimelineEntry.UploadEntry(it)) }
        }.sortedWith(compareBy<LanShareTimelineEntry> { it.timestamp }.thenBy { it.key })
    }
    val background = themeColors.surfaces.background
    val toggleQr: () -> Unit = {
        if (!qrOpen && lanState.isHost) {
            onSetQrVisible(true)
        } else if (qrOpen) {
            onSetQrVisible(false)
        }
    }

    Box(Modifier.fillMaxSize().background(background)) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 8.dp, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "header", contentType = "header") {
                LanShareHeader(primary, accent, toggleQr)
            }
            item(key = "connection", contentType = "connection") {
                LanShareConnectionStatus(lanState.browserConnected)
            }
            items(timeline, key = { it.key }, contentType = {
                when (it) {
                    is LanShareTimelineEntry.FileEntry, is LanShareTimelineEntry.UploadEntry -> "file"
                    is LanShareTimelineEntry.MessageEntry -> "message"
                }
            }) { entry ->
                when (entry) {
                    is LanShareTimelineEntry.FileEntry -> {
                        val file = entry.file
                        LanShareMessageBubble(
                            state = lanState,
                            file = file,
                            previewReady = file.id in lanState.previewFileIds,
                            loadPreview = onLoadImagePreview,
                            dark = dark,
                            primary = primary,
                            secondary = secondary,
                            onSaveFile = onSaveFile,
                            peerColorIndex = peerColorIndices[file.sender],
                            onPreviewImage = { bitmap ->
                                imagePreview = file.name to bitmap
                                imagePreviewFile = file
                            },
                        )
                    }
                    is LanShareTimelineEntry.UploadEntry -> {
                        LanShareUploadingBubble(entry.upload) { onCancelUpload(entry.upload.id) }
                    }
                    is LanShareTimelineEntry.MessageEntry -> {
                        val chatMessage = entry.message
                        LanShareTextMessageBubble(
                            message = chatMessage,
                            dark = dark,
                            primary = primary,
                            peerColorIndex = peerColorIndices[chatMessage.sender],
                        )
                    }
                }
            }
        }

        LanShareInputBar(
            dark = dark,
            panel = panel,
            inputPanel = inputPanel,
            primary = primary,
            secondary = secondary,
            accent = accent,
            pendingUploadName = lanState.pendingUploadName,
            message = message,
            onMessageChange = onMessageChange,
            onSend = { onSend(message) },
            onOpenAttachmentMenu = { attachmentMenu = true },
            attachmentMenu = attachmentMenu,
            onDismissAttachmentMenu = { attachmentMenu = false },
            onOpenCamera = onOpenCamera,
            onOpenGallery = onOpenGallery,
            onOpenFiles = onOpenFiles,
        )
    }

    if (qrOpen) {
        ComposeLanShareQrDialog(
            onCopyAddress = onCopyAddress,
            session = session,
            dark = dark,
            browserConnected = lanState.browserConnected,
            primary = primary,
            secondary = secondary,
            onDismiss = { onSetQrVisible(false) },
        )
    }

    imagePreview?.let { (fileName, bitmap) ->
        LanShareImagePreviewDialog(
            fileName = fileName,
            bitmap = bitmap,
            onDismiss = { imagePreview = null; imagePreviewFile = null },
        )
    }
}
@Composable
private fun LanShareHeader(primary: Color, accent: Color, onQrClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.width(48.dp))
        Text("文件传输", color = primary, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
        // 二维码入口保留独立的触控区域，标题区域不承担点击行为。
        IconButton(onClick = onQrClick, interactionSource = interactionSource, modifier = Modifier.iosPressFeedback(interactionSource).size(48.dp)) {
            Icon(
                imageVector = QrCode2Icon,
                contentDescription = "显示二维码",
                tint = accent,
                modifier = Modifier.size(30.dp),
            )
        }
    }
}
@Composable
private fun LanShareConnectionStatus(connected: Boolean) {
    val colors = LocalAppColorScheme.current
    val statusColor = if (connected) colors.controls.success else colors.text.placeholder
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = if (connected) CircleFilledIcon else CircleIcon,
            contentDescription = if (connected) "已连接" else "等待连接",
            tint = statusColor,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(5.dp))
        Text(if (connected) "设备已连接" else "等待设备连接", color = statusColor, fontSize = 15.sp)
    }
}
