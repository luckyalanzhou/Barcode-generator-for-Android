package com.luckyalanzhou.barcodegenerator.ui.feature.favorites

import com.luckyalanzhou.barcodegenerator.ui.dialogs.AnchoredDropdownMenu
import com.luckyalanzhou.barcodegenerator.ui.dialogs.ComposeDropdownDivider
import com.luckyalanzhou.barcodegenerator.ui.app.ComposeAnimationConfig

import com.luckyalanzhou.barcodegenerator.ui.theme.*

import com.luckyalanzhou.barcodegenerator.icons.CreateNewFolderIcon
import com.luckyalanzhou.barcodegenerator.icons.DeleteIcon
import com.luckyalanzhou.barcodegenerator.icons.EditIcon
import com.luckyalanzhou.barcodegenerator.icons.FolderIcon
import com.luckyalanzhou.barcodegenerator.icons.KeyboardArrowDownIcon
import com.luckyalanzhou.barcodegenerator.icons.KeyboardArrowRightIcon
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun FavoriteFolderRow(
    row: ComposeFavoriteRow,
    dark: Boolean,
    secondary: Color,
    folderColor: Color,
    animation: ComposeAnimationConfig,
    menuExpanded: Boolean,
    onMenuDismiss: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onShowSubfolderEditor: (String) -> Unit,
    onShowFolderEditor: (String, (String) -> Unit) -> Unit,
    onConfirm: (String, String, String, () -> Unit) -> Unit,
    onRenameFolder: (String, String) -> Unit,
    onDeleteFolder: (String) -> Unit,
) {
    val interactionSource = remember(row.path) { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) .965f else 1f, animation.settleSpring(), label = "favorite-folder-scale")
    val background by animateColorAsState(if (pressed) folderColor.copy(alpha = .16f) else Color.Transparent, animation.settleSpring(), label = "favorite-folder-background")
    val indent = if (row.level == 0) 11.dp else 26.dp

    Box(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().height(if (row.level == 0) 50.dp else 43.dp)
                .clip(RoundedCornerShape(14.dp)).background(background).padding(start = indent, end = 5.dp)
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .combinedClickable(interactionSource, indication = null, onClick = onClick, onLongClick = onLongClick),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(FolderIcon, "文件夹", tint = folderColor, modifier = Modifier.size(if (row.level == 0) 27.dp else 21.dp))
            Spacer(Modifier.width(if (row.level == 0) 8.dp else 7.dp))
            Text(row.label, color = LocalAppColorScheme.current.text.primary, fontSize = if (row.level == 0) 18.sp else 17.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(row.count.toString(), color = secondary, fontSize = 13.sp, modifier = Modifier.width(28.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Icon(
                imageVector = if (row.collapsed) KeyboardArrowRightIcon else KeyboardArrowDownIcon,
                contentDescription = if (row.collapsed) "展开文件夹" else "收起文件夹",
                tint = secondary,
                modifier = Modifier.size(24.dp),
            )
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
            DropdownMenuItem(modifier = Modifier.height(40.dp), enabled = false, text = { Text("编辑文件夹", color = LocalAppColorScheme.current.text.placeholder, fontWeight = FontWeight.SemiBold) }, onClick = {})
            ComposeDropdownDivider(dark)
            val actions = if (row.level == 0) listOf("新建文件夹", "重命名", "删除") else listOf("重命名", "删除")
            actions.forEachIndexed { index, label ->
                if (index > 0) ComposeDropdownDivider(dark)
                val deleteAction = label == "删除"
                DropdownMenuItem(
                    modifier = Modifier.height(40.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    text = { Text(label) },
                    trailingIcon = if (deleteAction) {
                        { Icon(DeleteIcon, contentDescription = "删除文件夹", tint = LocalAppColorScheme.current.text.destructive, modifier = Modifier.size(20.dp)) }
                    } else {
                        { Icon(if (label == "新建文件夹") CreateNewFolderIcon else EditIcon, contentDescription = label, tint = LocalAppColorScheme.current.text.primary, modifier = Modifier.size(20.dp)) }
                    },
                    onClick = {
                        onMenuDismiss()
                        when {
                            row.level == 0 && index == 0 -> onShowSubfolderEditor(row.path)
                            index == if (row.level == 0) 1 else 0 -> onShowFolderEditor(row.path) { renamed ->
                                val parent = row.path.substringBeforeLast('/', "")
                                onRenameFolder(row.path, listOf(parent, renamed).filter { it.isNotBlank() }.joinToString("/"))
                            }
                            else -> onConfirm("删除文件夹", "将删除文件夹内的所有收藏，确定继续吗？", "删除") { onDeleteFolder(row.path) }
                        }
                    },
                )
            }
        }
    }
}
