package com.luckyalanzhou.barcodegenerator

import com.luckyalanzhou.barcodegenerator.icons.AttachFileIcon
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
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.layout.fillMaxSize
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
    val primary = if (dark) Color.White else Color(0xff182230)
    val secondary = if (dark) Color(0xffc5cedb) else Color(0xff667085)
    val panel = if (dark) Color(0xff1c1c1e) else Color.White
    val inputPanel = if (dark) Color(0xff2c2c2e) else Color(0xfff0f2f5)
    val accent = Color(0xff0a84ff)
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

    Column(Modifier.fillMaxSize().background(if (dark) Color.Black else Color(0xfff4f6fb))) {
        Row(
            Modifier.fillMaxWidth().height(80.dp).globalCardSurface(dark, panel, RoundedCornerShape(18.dp), 3.dp).clickable {
                if (!qrOpen && lanState.isHost) {
                    runCatching {
                        viewModel.restartHostSession()
                        qrOpen = true
                    }.onFailure { onNotice(it.message ?: "无法刷新分享端口") }
                } else if (qrOpen) {
                    qrOpen = false
                    viewModel.setQrVisible(false)
                }
            }.padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(Modifier.width(64.dp))
            Text("文件传输", color = primary, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
            IconButton(onClick = {
                if (!qrOpen && lanState.isHost) {
                    runCatching {
                        viewModel.restartHostSession()
                        qrOpen = true
                    }.onFailure { onNotice(it.message ?: "无法刷新分享端口") }
                } else if (qrOpen) {
                    qrOpen = false
                    viewModel.setQrVisible(false)
                }
            }, modifier = Modifier.size(60.dp)) {
                Icon(QrCode2Icon, "显示二维码", tint = if (dark) Color(0xff8fc1ff) else accent, modifier = Modifier.size(32.dp))
            }
        }

        Box(Modifier.fillMaxWidth().weight(1f)) {
            Column(
                Modifier.fillMaxSize().verticalScroll(androidx.compose.foundation.rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val connected = lanState.browserConnected
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Text(if (connected) "●" else "○", color = if (connected) Color(0xff22c55e) else secondary, fontSize = 15.sp)
                    Spacer(Modifier.width(5.dp))
                    Text(if (connected) "浏览器已连接" else "等待浏览器连接…", color = if (connected) Color(0xff22c55e) else secondary, fontSize = 15.sp)
                }

                lanState.files.forEach { file ->
                    ComposeLanShareBubble(viewModel, lanState, file, dark, primary, secondary, onSaveFile)
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().globalCardSurface(dark, panel, RoundedCornerShape(16.dp), 3.dp).padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box {
                IconButton(onClick = { attachmentMenu = true }, modifier = Modifier.size(48.dp)) {
                Icon(AttachFileIcon, "选择附件", tint = if (dark) Color.White else Color(0xff344054), modifier = Modifier.size(28.dp))
                }
                AnchoredDropdownMenu(
                    dark = dark,
                    expanded = attachmentMenu,
                    onDismissRequest = { attachmentMenu = false },
                    shape = RoundedCornerShape(16.dp),
                    containerColor = if (dark) Color(0xff252a33).copy(alpha = .98f) else Color.White.copy(alpha = .94f),
                    tonalElevation = 0.dp,
                    shadowElevation = 3.dp,
                    menuWidth = 168.dp,
                ) {
                    DropdownMenuItem(text = { Text("拍摄图片") }, onClick = { attachmentMenu = false; onOpenCamera() })
                    ComposeDropdownDivider(dark)
                    DropdownMenuItem(text = { Text("照片图库") }, onClick = { attachmentMenu = false; onOpenGallery() })
                    ComposeDropdownDivider(dark)
                    DropdownMenuItem(text = { Text("选择文件") }, onClick = { attachmentMenu = false; onOpenFiles() })
                }
            }
            BasicTextField(
                value = message,
                onValueChange = { message = it },
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(color = primary, fontSize = 15.sp),
                cursorBrush = SolidColor(primary),
                modifier = Modifier.weight(1f).height(44.dp).background(inputPanel, RoundedCornerShape(24.dp)).padding(horizontal = 14.dp, vertical = 12.dp),
                decorationBox = { field -> Box { if (message.isEmpty()) Text(lanState.pendingUploadName?.let { "已选择：$it" } ?: "输入文字", color = secondary, fontSize = 15.sp); field() } }
            )
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = {
                    if (lanState.pendingUploadUri != null) {
                        viewModel.takePendingUpload()?.let { (uri, temporaryFile) ->
                            lanState.session?.let { session -> viewModel.uploadFile(session, uri, temporaryFile) }
                        }
                    } else message.takeIf { it.isNotBlank() }?.let { text ->
                        lanState.session?.let { session -> viewModel.uploadText(session, text) }
                    }
                },
                modifier = Modifier.size(48.dp),
                contentPadding = PaddingValues(0.dp),
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(containerColor = accent)
            ) { Icon(IosShareIcon, "发送文字或上传附件", tint = Color.White, modifier = Modifier.size(24.dp)) }
        }
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
private fun ComposeLanShareBubble(viewModel: LanShareViewModel, state: LanShareUiState, file: LanShareFile, dark: Boolean, primary: Color, secondary: Color, onSaveFile: (LanShareFile) -> Unit) {
    val mine = file.id in state.ownFileIds
    val previewFile = (viewModel.localFile(file.id) ?: state.previewFiles[file.id]).takeIf { isLanShareImageName(file.name) }
    val preview = remember(file.id, previewFile?.absolutePath, previewFile?.lastModified()) { previewFile?.let(::decodeLanSharePreview) }
    val bubbleColor = if (mine) (if (dark) Color(0xff0a84ff).copy(alpha = .48f) else Color(0xff0a84ff).copy(alpha = .40f)) else if (dark) Color(0xff2c2c2e).copy(alpha = .62f) else Color.White.copy(alpha = .82f)
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 3.dp), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
        Surface(modifier = Modifier.width(260.dp).clickable { onSaveFile(file) }, shape = RoundedCornerShape(18.dp), color = bubbleColor, shadowElevation = 0.dp) {
            Column(Modifier.padding(if (preview == null) 12.dp else 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                preview?.let { bitmap ->
                    val scale = minOf(220f / bitmap.width.coerceAtLeast(1), 180f / bitmap.height.coerceAtLeast(1), 1f)
                    Image(bitmap.asImageBitmap(), file.name, contentScale = ContentScale.Crop, modifier = Modifier.width((bitmap.width * scale).coerceAtLeast(80f).roundToInt().dp).height((bitmap.height * scale).coerceAtLeast(80f).roundToInt().dp))
                    Spacer(Modifier.height(6.dp))
                } ?: Icon(AttachFileIcon, "文件附件", tint = if (mine) Color.White else if (dark) Color(0xffd0d6e4) else Color(0xff52627a), modifier = Modifier.size(26.dp))
                Text(file.name, color = if (mine) Color.White else primary, fontSize = 14.sp, maxLines = 4, overflow = TextOverflow.Clip, textAlign = TextAlign.Center)
                Text(formatLanShareSize(file.size), color = if (mine) Color(0xffdbeafe) else secondary, fontSize = 12.sp)
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
    val foreground = if (dark) 0xff111318.toInt() else AndroidColor.BLACK
    val background = if (dark) 0xfff1f3f6.toInt() else AndroidColor.WHITE
    val bitmap = remember(session.baseUrl, dark) { createLanShareQrBitmap(session.baseUrl, foreground, background) }
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
                modifier = Modifier.clickable { },
                shape = RoundedCornerShape(22.dp),
                color = if (dark) Color(0xff1c1c1e) else Color.White,
                shadowElevation = 4.dp,
            ) {
                Box(Modifier.padding(top = 14.dp, bottom = 10.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(bitmap.asImageBitmap(), "局域网分享二维码", modifier = Modifier.size(240.dp).background(Color(background)), contentScale = ContentScale.FillBounds)
                    Row(Modifier.width(240.dp).height(42.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(session.baseUrl, color = secondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                        IconButton(onClick = {
                            onCopyAddress(session.baseUrl)
                        }, modifier = Modifier.size(42.dp)) { Icon(ContentCopyIcon, "复制局域网传输地址", tint = primary) }
                    }
                }
            }
            }
        }
    }
}

private fun createLanShareQrBitmap(value: String, foreground: Int, background: Int): Bitmap {
    val matrix = com.google.zxing.MultiFormatWriter().encode(value, com.google.zxing.BarcodeFormat.QR_CODE, 240, 240, mapOf(com.google.zxing.EncodeHintType.MARGIN to 1))
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
        val primary = if (dark) Color(0xfff2f4f8) else Color(0xff182230)
        val secondary = if (dark) Color(0xffaeb9c9) else Color(0xff667085)
        val foreground = if (dark) AndroidColor.rgb(17, 19, 24) else AndroidColor.BLACK
        val qrBackground = if (dark) AndroidColor.rgb(241, 243, 246) else AndroidColor.WHITE
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
