package com.luckyalanzhou.barcodegenerator.ui

import com.luckyalanzhou.barcodegenerator.LanShareEvent
import com.luckyalanzhou.barcodegenerator.LanShareUiState
import com.luckyalanzhou.barcodegenerator.LanShareViewModel
import com.luckyalanzhou.barcodegenerator.MainActivity
import com.luckyalanzhou.barcodegenerator.data.network.protocol.LanShareFile
import com.luckyalanzhou.barcodegenerator.data.network.protocol.LanShareSession

import com.luckyalanzhou.barcodegenerator.icons.AttachFileIcon
import com.luckyalanzhou.barcodegenerator.icons.CircleFilledIcon
import com.luckyalanzhou.barcodegenerator.icons.CircleIcon
import com.luckyalanzhou.barcodegenerator.icons.ContentCopyIcon
import com.luckyalanzhou.barcodegenerator.icons.IosShareIcon
import com.luckyalanzhou.barcodegenerator.icons.QrCode2Icon

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import androidx.compose.foundation.text.selection.SelectionContainer
import kotlin.math.roundToInt

@Composable
internal fun ComposeLanSharePage(
    viewModel: LanShareViewModel,
    dark: Boolean,
    onOpenCamera: () -> Unit,
    onOpenGallery: () -> Unit,
    onOpenFiles: () -> Unit,
    onSaveFile: (LanShareFile) -> Unit,
    onNotice: (String) -> Unit,
    onCopyAddress: (String) -> Unit,
) {
    val lanState by viewModel.uiState.collectAsStateWithLifecycle()
    val themeColors = LocalBarcodeThemeColors.current
    val primary = themeColors.primary
    val secondary = themeColors.secondary
    val panel = themeColors.surface
    val inputPanel = themeColors.inputPanel
    val accent = themeColors.progress
    var qrOpen by remember { mutableStateOf(lanState.qrVisible) }
    var attachmentMenu by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    val session = lanState.session

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is LanShareEvent.Error -> onNotice(event.message)
                is LanShareEvent.Notice -> {
                    if (event.clearInput) message = ""
                    onNotice(event.message)
                }
            }
        }
    }

    if (session == null) {
        Text("正在创建分享房间…", color = secondary, modifier = Modifier.fillMaxWidth().padding(32.dp), textAlign = TextAlign.Center)
        return
    }

    val listState = rememberLazyListState()
    val background = themeColors.background
    val toggleQr: () -> Unit = {
        if (!qrOpen && lanState.isHost) {
            runCatching { viewModel.restartHostSession(); qrOpen = true }
                .onFailure { onNotice(it.message ?: "无法刷新分享端口") }
        } else if (qrOpen) {
            qrOpen = false
            viewModel.setQrVisible(false)
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
                LanShareConnectionStatus(lanState.browserConnected, secondary)
            }
            items(lanState.files, key = { it.id }, contentType = { "file" }) { file ->
                ComposeLanShareBubble(viewModel, lanState, file, dark, primary, secondary, onSaveFile)
            }
        }

        LanShareComposer(
            dark = dark,
            panel = panel,
            inputPanel = inputPanel,
            primary = primary,
            secondary = secondary,
            accent = accent,
            pendingUploadName = lanState.pendingUploadName,
            message = message,
            onMessageChange = { message = it },
            onSend = {
                if (lanState.pendingUploadUri != null) {
                    viewModel.takePendingUpload()?.let { (uri, temporaryFile) ->
                        lanState.session?.let { session -> viewModel.uploadFile(session, uri, temporaryFile) }
                    }
                } else message.takeIf { it.isNotBlank() }?.let { text ->
                    lanState.session?.let { session -> viewModel.uploadText(session, text) }
                }
            },
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
            onHideQr = { viewModel.setQrVisible(false) },
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
    Row(
        Modifier.fillMaxWidth().height(80.dp).padding(horizontal = 8.dp)
            .globalCardSurface(dark, panel, RoundedCornerShape(18.dp), 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.width(60.dp))
        Text("文件传输", color = primary, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
        // 二维码入口只保留在右侧图标的点击区域，标题卡片本身不承担点击行为。
        IconButton(onClick = onQrClick, modifier = Modifier.width(58.dp).height(48.dp)) {
            Icon(
                imageVector = QrCode2Icon,
                contentDescription = "显示二维码",
                tint = LocalBarcodeThemeColors.current.link,
                modifier = Modifier.size(30.dp),
            )
        }
    }
}

@Composable
private fun LanShareConnectionStatus(connected: Boolean, secondary: Color) {
    val statusColor = if (connected) LocalBarcodeThemeColors.current.success else secondary
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = if (connected) CircleFilledIcon else CircleIcon,
            contentDescription = if (connected) "已连接" else "等待连接",
            tint = statusColor,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(5.dp))
        Text(if (connected) "浏览器已连接" else "等待浏览器连接…", color = statusColor, fontSize = 15.sp)
    }
}

@Composable
private fun BoxScope.LanShareComposer(
    dark: Boolean,
    panel: Color,
    inputPanel: Color,
    primary: Color,
    secondary: Color,
    accent: Color,
    pendingUploadName: String?,
    message: String,
    onMessageChange: (String) -> Unit,
    onSend: () -> Unit,
    onOpenAttachmentMenu: () -> Unit,
    attachmentMenu: Boolean,
    onDismissAttachmentMenu: () -> Unit,
    onOpenCamera: () -> Unit,
    onOpenGallery: () -> Unit,
    onOpenFiles: () -> Unit,
) {
    val themeColors = LocalBarcodeThemeColors.current
    Surface(
        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding(),
        color = panel,
        shadowElevation = 1.dp,
        shape = RoundedCornerShape(18.dp),
    ) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box {
                IconButton(onClick = onOpenAttachmentMenu, modifier = Modifier.size(48.dp)) {
                    Icon(AttachFileIcon, "选择附件", tint = themeColors.icon, modifier = Modifier.size(28.dp))
                }
                AnchoredDropdownMenu(
                    dark = dark, expanded = attachmentMenu, onDismissRequest = onDismissAttachmentMenu,
                    shape = RoundedCornerShape(16.dp),
                    containerColor = themeColors.surfaceOverlay,
                    tonalElevation = 0.dp, shadowElevation = 1.dp, menuWidth = 120.dp,
                ) {
                    DropdownMenuItem(modifier = Modifier.height(40.dp), text = { Text("拍摄图片") }, onClick = { onDismissAttachmentMenu(); onOpenCamera() })
                    ComposeDropdownDivider(dark)
                    DropdownMenuItem(modifier = Modifier.height(40.dp), text = { Text("照片图库") }, onClick = { onDismissAttachmentMenu(); onOpenGallery() })
                    ComposeDropdownDivider(dark)
                    DropdownMenuItem(modifier = Modifier.height(40.dp), text = { Text("选择文件") }, onClick = { onDismissAttachmentMenu(); onOpenFiles() })
                }
            }
            BasicTextField(
                value = message, onValueChange = onMessageChange, singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(color = primary, fontSize = 15.sp),
                cursorBrush = SolidColor(primary),
                modifier = Modifier.weight(1f).height(44.dp).background(inputPanel, RoundedCornerShape(24.dp)).padding(horizontal = 14.dp, vertical = 12.dp),
                decorationBox = { field ->
                    Box { if (message.isEmpty()) Text(pendingUploadName?.let { "已选择：$it" } ?: "输入文字", color = secondary, fontSize = 15.sp); field() }
                },
            )
            Spacer(Modifier.width(8.dp))
            Button(onClick = onSend, modifier = Modifier.size(48.dp), contentPadding = PaddingValues(0.dp), shape = RoundedCornerShape(24.dp), colors = ButtonDefaults.buttonColors(containerColor = accent)) {
                Icon(IosShareIcon, "发送文字或上传附件", tint = themeColors.sentContent, modifier = Modifier.size(24.dp))
            }
        }
    }
}

@Composable
private fun ComposeLanShareBubble(viewModel: LanShareViewModel, state: LanShareUiState, file: LanShareFile, dark: Boolean, primary: Color, secondary: Color, onSaveFile: (LanShareFile) -> Unit) {
    val themeColors = LocalBarcodeThemeColors.current
    val mine = file.id in state.ownFileIds
    val previewFile = (viewModel.localFile(file.id) ?: state.previewFiles[file.id]).takeIf { isLanShareImageName(file.name) }
    val preview = remember(file.id, previewFile?.absolutePath, previewFile?.lastModified()) { previewFile?.let(::decodeLanSharePreview) }
    val bubbleColor = if (mine) themeColors.progress.copy(alpha = .44f) else themeColors.surfaceOverlay
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 3.dp), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
        Surface(modifier = Modifier.width(260.dp).clickable { onSaveFile(file) }, shape = RoundedCornerShape(18.dp), color = bubbleColor, shadowElevation = 0.dp) {
            Column(Modifier.padding(if (preview == null) 12.dp else 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                preview?.let { bitmap ->
                    val scale = minOf(220f / bitmap.width.coerceAtLeast(1), 180f / bitmap.height.coerceAtLeast(1), 1f)
                    Image(bitmap.asImageBitmap(), file.name, contentScale = ContentScale.Crop, modifier = Modifier.width((bitmap.width * scale).coerceAtLeast(80f).roundToInt().dp).height((bitmap.height * scale).coerceAtLeast(80f).roundToInt().dp))
                    Spacer(Modifier.height(6.dp))
                } ?: Icon(AttachFileIcon, "文件附件", tint = if (mine) themeColors.sentContent else themeColors.icon, modifier = Modifier.size(26.dp))
                Text(file.name, color = if (mine) themeColors.sentContent else primary, fontSize = 14.sp, maxLines = 4, overflow = TextOverflow.Clip, textAlign = TextAlign.Center)
                Text(formatLanShareSize(file.size), color = if (mine) themeColors.qrBackground else secondary, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun ComposeLanShareQrDialog(
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
    val foreground = LocalBarcodeThemeColors.current.qrForeground.toArgb()
    val background = LocalBarcodeThemeColors.current.qrBackground.toArgb()
    val bitmap = remember(session.baseUrl, dark) { createLanShareQrBitmap(session.baseUrl, foreground, background, qrSize.value.toInt()) }
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
                color = LocalBarcodeThemeColors.current.surface,
                shadowElevation = 1.dp,
            ) {
                Box(Modifier.padding(start = 12.dp, end = 12.dp, top = 14.dp, bottom = 12.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(bitmap.asImageBitmap(), "局域网分享二维码", modifier = Modifier.size(qrSize).background(Color(background)), contentScale = ContentScale.FillBounds)
                    Row(Modifier.width(qrSize).height(48.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(session.baseUrl, color = secondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                        IconButton(onClick = {
                            onCopyAddress(session.baseUrl)
                        }, modifier = Modifier.size(48.dp)) { Icon(ContentCopyIcon, "复制局域网传输地址", tint = primary) }
                    }
                }
            }
            }
        }
    }
}

private fun createLanShareQrBitmap(value: String, foreground: Int, background: Int, size: Int = 280): Bitmap {
    val matrix = com.google.zxing.MultiFormatWriter().encode(value, com.google.zxing.BarcodeFormat.QR_CODE, size, size, mapOf(com.google.zxing.EncodeHintType.MARGIN to 1))
    return Bitmap.createBitmap(matrix.width, matrix.height, Bitmap.Config.ARGB_8888).also { image ->
        for (x in 0 until matrix.width) for (y in 0 until matrix.height) image.setPixel(x, y, if (matrix[x, y]) foreground else background)
    }
}

/** Beta 测试中心的二维码模拟也复用实际二维码弹窗的 Compose 结构。 */
internal fun MainActivity.showLanShareQrDialogCompose(simulatedSession: LanShareSession? = null) {
    val simulated = simulatedSession != null
    val lanState = lanShareViewModel.uiState.value
    val session = simulatedSession ?: lanState.session
    if ((!lanState.isHost && !simulated) || session == null) {
        toast("请先创建分享房间")
        return
    }
    showComposeDialog(compact = false, metricsLabel = if (simulated) "二维码弹窗" else null) { dismiss ->
        val dark = isDark()
        val colors = LocalBarcodeThemeColors.current
        val primary = colors.primary
        val secondary = colors.secondary
        val foreground = colors.qrForeground.toArgb()
        val qrBackground = colors.qrBackground.toArgb()
        val bitmap = remember(session.baseUrl, dark) {
            createLanShareQrBitmap(session.baseUrl, foreground, qrBackground)
        }
        ComposeGlassDialogCard(dark) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "局域网分享二维码",
                modifier = Modifier.fillMaxWidth().background(Color(qrBackground)),
                contentScale = ContentScale.FillWidth,
            )
            SelectionContainer {
                Text(
                    session.baseUrl,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    color = secondary,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                )
            }
            Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.End) {
                DialogAction("关闭", dark, {
                    if (!simulated) lanShareViewModel.setQrVisible(false)
                    dismiss()
                })
            }
        }
    }
}
