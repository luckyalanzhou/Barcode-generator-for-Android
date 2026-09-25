package com.luckyalanzhou.barcodegenerator.ui.feature.lanshare

import com.luckyalanzhou.barcodegenerator.ui.theme.*
import com.luckyalanzhou.barcodegenerator.ui.component.globalButtonChrome
import com.luckyalanzhou.barcodegenerator.ui.component.globalCardSurface
import com.luckyalanzhou.barcodegenerator.ui.component.iosPressFeedback

import com.luckyalanzhou.barcodegenerator.domain.isLanShareImageName

import com.luckyalanzhou.barcodegenerator.presentation.lanshare.LanShareUiState
import com.luckyalanzhou.barcodegenerator.domain.LanShareFile
import com.luckyalanzhou.barcodegenerator.domain.LanShareSession

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.foundation.text.selection.SelectionContainer
import kotlin.math.roundToInt

@Composable
internal fun LanShareContent(
    lanState: LanShareUiState,
    message: String,
    dark: Boolean,
    onMessageChange: (String) -> Unit,
    onRestartHost: () -> LanShareSession,
    onSetQrVisible: (Boolean) -> Unit,
    onSend: (String) -> Unit,
    localFile: (String) -> java.io.File?,
    onOpenCamera: () -> Unit,
    onOpenGallery: () -> Unit,
    onOpenFiles: () -> Unit,
    onSaveFile: (LanShareFile) -> Unit,
    onNotice: (String) -> Unit,
    onCopyAddress: (String) -> Unit,
) {
    val themeColors = LocalAppColorScheme.current
    val primary = themeColors.text.primary
    val secondary = themeColors.text.secondary
    val panel = themeColors.surfaces.surface
    val inputPanel = themeColors.surfaces.inputPanel
    val accent = themeColors.controls.progress
    var qrOpen by remember { mutableStateOf(lanState.qrVisible) }
    var attachmentMenu by remember { mutableStateOf(false) }
    val session = lanState.session

    if (session == null) {
        Text("正在创建分享房间…", color = secondary, modifier = Modifier.fillMaxWidth().padding(32.dp), textAlign = TextAlign.Center)
        return
    }

    val listState = rememberLazyListState()
    val background = themeColors.surfaces.background
    val toggleQr: () -> Unit = {
        if (!qrOpen && lanState.isHost) {
            runCatching { onRestartHost(); qrOpen = true }
                .onFailure { onNotice(it.message ?: "无法刷新分享端口") }
        } else if (qrOpen) {
            qrOpen = false
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
                LanShareHeader(dark, panel, primary, accent, toggleQr)
            }
            item(key = "connection", contentType = "connection") {
                LanShareConnectionStatus(lanState.browserConnected)
            }
            item(key = "security-notice", contentType = "security-notice") {
                LanShareSecurityNotice(Modifier.fillMaxWidth().padding(horizontal = 8.dp))
            }
            items(lanState.files, key = { it.id }, contentType = { "file" }) { file ->
                LanShareMessageBubble(localFile, lanState, file, dark, primary, secondary, onSaveFile)
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
            onHideQr = { onSetQrVisible(false) },
            session = session,
            dark = dark,
            primary = primary,
            secondary = secondary,
            onDismiss = { qrOpen = false },
        )
    }
}
@Composable
private fun LanShareHeader(dark: Boolean, panel: Color, primary: Color, accent: Color, onQrClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        Modifier.fillMaxWidth().height(80.dp).padding(horizontal = 8.dp)
            .globalCardSurface(dark, panel, RoundedCornerShape(18.dp), 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.width(60.dp))
        Text("文件传输", color = primary, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
        // 二维码入口只保留在右侧图标的点击区域，标题卡片本身不承担点击行为。
        IconButton(onClick = onQrClick, interactionSource = interactionSource, modifier = Modifier.iosPressFeedback(interactionSource).width(58.dp).height(48.dp)) {
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
        Text(if (connected) "浏览器已连接" else "等待浏览器连接…", color = statusColor, fontSize = 15.sp)
    }
}
