package com.luckyalanzhou.barcodegenerator.ui

import com.luckyalanzhou.barcodegenerator.ui.theme.*

import com.luckyalanzhou.barcodegenerator.domain.FavoriteGroup
import com.luckyalanzhou.barcodegenerator.icons.AttachFileIcon
import com.luckyalanzhou.barcodegenerator.icons.DeleteIcon
import com.luckyalanzhou.barcodegenerator.icons.DriveFileMoveIcon
import com.luckyalanzhou.barcodegenerator.icons.EditIcon
import com.luckyalanzhou.barcodegenerator.icons.VisibilityIcon
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun FavoriteGroupRow(
    row: ComposeFavoriteRow,
    group: FavoriteGroup,
    dark: Boolean,
    secondary: Color,
    fileColor: Color,
    animation: ComposeAnimationConfig,
    hapticView: android.view.View,
    menuExpanded: Boolean,
    onMenuDismiss: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onShowMoveDialog: (FavoriteGroup) -> Unit,
    onShowRenameDialog: (FavoriteGroup) -> Unit,
    onEdit: (FavoriteGroup) -> Unit,
    onConfirm: (String, String, String, () -> Unit) -> Unit,
    onDelete: (FavoriteGroup) -> Unit,
) {
    val interactionSource = remember(group.id) { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) .965f else 1f, animation.settleSpring(), label = "favorite-group-scale")
    val background by animateColorAsState(if (pressed) fileColor.copy(alpha = .16f) else Color.Transparent, animation.settleSpring(), label = "favorite-group-background")

    Box(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().height(44.dp).padding(start = if (row.level <= 1) 20.dp else 38.dp, end = 4.dp)
                .clip(RoundedCornerShape(14.dp)).background(background).graphicsLayer { scaleX = scale; scaleY = scale }
                .combinedClickable(interactionSource, indication = null, onClick = onClick, onLongClick = onLongClick),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(AttachFileIcon, "收藏文件", tint = fileColor, modifier = Modifier.size(21.dp))
            Spacer(Modifier.width(8.dp))
            Text(group.name, color = LocalAppColorScheme.current.text.primary, fontSize = 17.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(SimpleDateFormat("MM-dd HH:mm", Locale.ROOT).format(Date(group.savedAt)), color = LocalAppColorScheme.current.text.placeholder, fontSize = 11.sp, maxLines = 1)
        }
        AnchoredDropdownMenu(
            dark = dark,
            expanded = menuExpanded,
            onDismissRequest = onMenuDismiss,
            shape = RoundedCornerShape(16.dp),
            containerColor = LocalAppColorScheme.current.surfaces.overlay,
            tonalElevation = 0.dp,
            shadowElevation = 1.dp,
            menuWidth = 160.dp,
        ) {
            DropdownMenuItem(modifier = Modifier.height(40.dp), enabled = false, text = { Text("编辑收藏文件", color = LocalAppColorScheme.current.text.placeholder, fontWeight = FontWeight.SemiBold) }, onClick = {})
            ComposeDropdownDivider(dark)
            listOf("查看", "移动", "重命名", "删除").forEachIndexed { index, label ->
                if (index > 0) ComposeDropdownDivider(dark)
                DropdownMenuItem(
                    modifier = Modifier.height(40.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    text = { Text(label) },
                    trailingIcon = {
                        val icon = when (index) {
                            0 -> VisibilityIcon
                            1 -> DriveFileMoveIcon
                            2 -> EditIcon
                            else -> DeleteIcon
                        }
                        Icon(icon, contentDescription = label, tint = if (index == 3) LocalAppColorScheme.current.text.destructive else LocalAppColorScheme.current.text.primary, modifier = Modifier.size(20.dp))
                    },
                    onClick = {
                        onMenuDismiss()
                        when (index) {
                            0 -> onEdit(group)
                            1 -> onShowMoveDialog(group)
                            2 -> onShowRenameDialog(group)
                            else -> onConfirm("删除收藏", "确定删除“${group.name}”吗？", "删除") { onDelete(group) }
                        }
                    },
                )
            }
        }
    }
}
