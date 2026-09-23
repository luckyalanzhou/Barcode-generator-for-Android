package com.luckyalanzhou.barcodegenerator.ui.feature.lanshare

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
import androidx.compose.foundation.layout.height

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
    Surface(
        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding(),
        color = panel,
        shadowElevation = 1.dp,
        shape = RoundedCornerShape(18.dp),
    ) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box {
                IconButton(onClick = onOpenAttachmentMenu, interactionSource = attachmentInteraction, modifier = Modifier.iosPressFeedback(attachmentInteraction).size(48.dp)) {
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
            Button(onClick = onSend, interactionSource = sendInteraction, modifier = Modifier.iosPressFeedback(sendInteraction).width(64.dp).height(44.dp), contentPadding = PaddingValues(horizontal = 10.dp), shape = RoundedCornerShape(22.dp), colors = ButtonDefaults.buttonColors(containerColor = accent)) {
                Text("发送", color = themeColors.content.sentContent, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}
