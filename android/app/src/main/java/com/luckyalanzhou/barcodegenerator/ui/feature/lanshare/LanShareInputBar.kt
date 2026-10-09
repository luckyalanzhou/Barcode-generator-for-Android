package com.luckyalanzhou.barcodegenerator.ui.feature.lanshare

import com.luckyalanzhou.barcodegenerator.ui.component.globalButtonChrome
import com.luckyalanzhou.barcodegenerator.ui.dialogs.AnchoredDropdownMenu
import com.luckyalanzhou.barcodegenerator.ui.dialogs.ComposeDropdownDivider
import com.luckyalanzhou.barcodegenerator.ui.theme.LocalAppColorScheme
import com.luckyalanzhou.barcodegenerator.ui.component.iosPressFeedback
import com.luckyalanzhou.barcodegenerator.icons.AddIcon
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Arrangement

/**
 * 固定在分享页底部的输入栏：加号打开拍照/图库/文件选择，发送按钮提交文字或待传文件。
 * 有待上传文件时允许空文字发送；上传状态与网络服务由上层协调。
 */
@Composable
internal fun BoxScope.LanShareInputBar(
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
    val themeColors = LocalAppColorScheme.current
    val attachmentInteraction = remember { MutableInteractionSource() }
    val sendInteraction = remember { MutableInteractionSource() }
    val sendEnabled = canSendLanContent(message, pendingUploadName)
    Surface(
        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding(),
        color = panel,
        shadowElevation = 1.dp,
        shape = RoundedCornerShape(18.dp),
    ) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            // 选择附件后先关闭菜单，再启动对应的系统入口，避免菜单遮挡系统选择界面。
            Box {
                IconButton(onClick = onOpenAttachmentMenu, interactionSource = attachmentInteraction, modifier = Modifier.iosPressFeedback(attachmentInteraction).size(48.dp).globalButtonChrome(RoundedCornerShape(10.dp))) {
                    Icon(AddIcon, "添加附件", tint = accent, modifier = Modifier.size(28.dp))
                }
                AnchoredDropdownMenu(
                    dark = dark, expanded = attachmentMenu, onDismissRequest = onDismissAttachmentMenu,
                    shape = RoundedCornerShape(16.dp),
                    containerColor = themeColors.surfaces.overlay,
                    tonalElevation = 0.dp, shadowElevation = 1.dp, menuWidth = 120.dp,
                ) {
                    DropdownMenuItem(modifier = Modifier.height(40.dp), text = { Text("拍摄图片", color = themeColors.text.primary) }, onClick = { onDismissAttachmentMenu(); onOpenCamera() })
                    ComposeDropdownDivider(dark)
                    DropdownMenuItem(modifier = Modifier.height(40.dp), text = { Text("照片图库", color = themeColors.text.primary) }, onClick = { onDismissAttachmentMenu(); onOpenGallery() })
                    ComposeDropdownDivider(dark)
                    DropdownMenuItem(modifier = Modifier.height(40.dp), text = { Text("选择文件", color = themeColors.text.primary) }, onClick = { onDismissAttachmentMenu(); onOpenFiles() })
                }
            }
            BasicTextField(
                value = message, onValueChange = onMessageChange, singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(color = primary, fontSize = 15.sp),
                cursorBrush = SolidColor(primary),
                modifier = Modifier.weight(1f).height(44.dp).background(inputPanel, RoundedCornerShape(24.dp)).padding(horizontal = 14.dp),
                decorationBox = { field ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
                        if (message.isEmpty()) {
                            Text(pendingUploadName?.let { "已选择：$it" } ?: "输入文字", color = themeColors.text.placeholder, fontSize = 15.sp)
                        }
                        field()
                    }
                },
            )
            Spacer(Modifier.width(8.dp))
            // 空文字且没有待上传文件时禁用发送，避免产生无内容的消息。
            Button(onClick = onSend, enabled = sendEnabled, interactionSource = sendInteraction, modifier = Modifier.iosPressFeedback(sendInteraction).width(64.dp).height(48.dp).globalButtonChrome(RoundedCornerShape(22.dp)), contentPadding = PaddingValues(horizontal = 10.dp), shape = RoundedCornerShape(22.dp), colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = themeColors.content.sentContent,
                disabledContainerColor = themeColors.controls.disabledContainer, disabledContentColor = themeColors.text.disabled)) {
                Text("发送", fontSize = 15.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

internal fun canSendLanContent(message: String, pendingUploadName: String?): Boolean =
    message.isNotBlank() || pendingUploadName != null
