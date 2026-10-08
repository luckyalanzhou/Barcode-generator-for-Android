package com.luckyalanzhou.barcodegenerator.ui.feature.favorites

import com.luckyalanzhou.barcodegenerator.ui.feature.favorites.tree.ComposeFavoriteRow

import com.luckyalanzhou.barcodegenerator.ui.animation.ComposeAnimationConfig
import com.luckyalanzhou.barcodegenerator.ui.component.menu.LocalLongPressMenuHost
import com.luckyalanzhou.barcodegenerator.ui.component.menu.TabLongPressAction
import com.luckyalanzhou.barcodegenerator.ui.component.menu.TabLongPressMenuState
import com.luckyalanzhou.barcodegenerator.ui.component.boundsOnScreen
import com.luckyalanzhou.barcodegenerator.ui.component.performLightMenuOpenHaptic

import com.luckyalanzhou.barcodegenerator.ui.theme.*

import com.luckyalanzhou.barcodegenerator.icons.CreateNewFolderIcon
import com.luckyalanzhou.barcodegenerator.icons.DeleteIcon
import com.luckyalanzhou.barcodegenerator.icons.EditIcon
import com.luckyalanzhou.barcodegenerator.icons.FolderIcon
import com.luckyalanzhou.barcodegenerator.icons.KeyboardArrowDownIcon
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 收藏文件夹行：点击切换展开/折叠；长按打开文件夹操作菜单，重命名、建子文件夹和删除由回调执行。
 * 本行只呈现给定层级与展开状态，不在组件内部维护文件夹树。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun FavoriteFolderRow(
    row: ComposeFavoriteRow,
    dark: Boolean,
    secondary: Color,
    folderColor: Color,
    animation: ComposeAnimationConfig,
    onClick: () -> Unit,
    onShowSubfolderEditor: (String) -> Unit,
    onShowFolderEditor: (String, (String) -> Unit) -> Unit,
    onConfirm: (String, String, String, () -> Unit) -> Unit,
    onRenameFolder: (String, String) -> Unit,
    onDeleteFolder: (String) -> Unit,
) {
    val interactionSource = remember(row.path) { MutableInteractionSource() }
    val menuHost = LocalLongPressMenuHost.current
    val view = LocalView.current
    val focus = remember(row.path) { FocusRequester() }
    var anchor by remember(row.path) { mutableStateOf(Rect.Zero) }
    var titleAnchor by remember(row.path) { mutableStateOf(Rect.Zero) }
    DisposableEffect(row.path) { onDispose { anchor = Rect.Zero } }
    // 长按菜单中的删除会先弹出确认框，确认后删除该文件夹及其内部收藏。
    val openMenu: () -> Unit = {
        view.performLightMenuOpenHaptic()
        val labels = if (row.level == 0) listOf("新建文件夹", "重命名", "删除") else listOf("重命名", "删除")
        menuHost(TabLongPressMenuState(anchor, FolderIcon, row.label, folderColor, dark,
            actions = labels.map { label ->
                TabLongPressAction(label, when (label) {
                    "新建文件夹" -> CreateNewFolderIcon
                    "重命名" -> EditIcon
                    else -> DeleteIcon
                }) {
                    when (label) {
                        "新建文件夹" -> onShowSubfolderEditor(row.path)
                        "重命名" -> onShowFolderEditor(row.path) { renamed ->
                            val parent = row.path.substringBeforeLast('/', "")
                            onRenameFolder(row.path, listOf(parent, renamed).filter { it.isNotBlank() }.joinToString("/"))
                        }
                        else -> onConfirm("删除文件夹", "将删除文件夹内的所有收藏，确定继续吗？", "删除") { onDeleteFolder(row.path) }
                    }
                }
            }, restoreFocus = { if (anchor != Rect.Zero) focus.requestFocus() },
            title = "编辑文件夹", tabAnchor = false,
            menuAnchorBoundsOnScreen = titleAnchor.takeIf { it != Rect.Zero } ?: anchor,
            sourceContent = {
                FavoriteFolderRowContent(row, secondary, folderColor, { if (row.collapsed) -90f else 0f })
            }))
    }
    val pressed by interactionSource.collectIsPressedAsState()
    val reduceMotion = com.luckyalanzhou.barcodegenerator.ui.theme.LocalVisualEffectsPolicy.current.reduceMotion
    // 按下短暂轻压，松手或滚动取消时弹性恢复；原有 combinedClickable 负责手势仲裁。
    val scale = animateFloatAsState(if (pressed && !reduceMotion) .97f else 1f,
        if (reduceMotion) androidx.compose.animation.core.snap() else if (pressed) androidx.compose.animation.core.tween(100)
        else animation.settleSpring(), label = "favorite-folder-scale")
    val background = animateColorAsState(if (pressed) folderColor.copy(alpha = .16f) else Color.Transparent, if (reduceMotion) androidx.compose.animation.core.snap() else animation.settleSpring(), label = "favorite-folder-background")
    val arrowRotation = animateFloatAsState(
        targetValue = if (row.collapsed) -90f else 0f,
        animationSpec = if (reduceMotion) androidx.compose.animation.core.snap() else androidx.compose.animation.core.tween(160),
        label = "favorite-folder-arrow-rotation",
    )

    Box(Modifier.fillMaxWidth()) {
        Box(
            Modifier.fillMaxWidth().height(if (row.level == 0) 50.dp else 43.dp)
                .onGloballyPositioned { anchor = it.boundsOnScreen() }
                .focusRequester(focus)
                .clip(RoundedCornerShape(14.dp))
                .drawBehind { drawRoundRect(color = background.value, cornerRadius = CornerRadius(14.dp.toPx())) }
                .graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                }
                .combinedClickable(
                    interactionSource,
                    indication = null,
                    onClick = onClick,
                    onLongClick = openMenu,
                    onLongClickLabel = "编辑文件夹",
                    hapticFeedbackEnabled = false,
                ),
        ) {
            FavoriteFolderRowContent(row, secondary, folderColor, { arrowRotation.value }) { titleAnchor = it }
        }
    }
}

/** 正常行和长按预览保持同样的层级缩进、字体、数量及箭头。 */
@Composable
private fun FavoriteFolderRowContent(
    row: ComposeFavoriteRow, secondary: Color, folderColor: Color,
    arrowRotation: () -> Float, onTitleBounds: (Rect) -> Unit = {},
) {
    Row(Modifier.fillMaxSize().padding(start = 11.dp + 20.dp * row.level, end = 5.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(FolderIcon, "文件夹", tint = folderColor, modifier = Modifier.size(if (row.level == 0) 27.dp else 21.dp))
        Spacer(Modifier.width(if (row.level == 0) 8.dp else 7.dp))
        Text(row.label, color = LocalAppColorScheme.current.text.primary,
            fontSize = if (row.level == 0) 18.sp else 16.sp,
            fontWeight = if (row.level == 0) FontWeight.SemiBold else FontWeight.Medium,
            modifier = Modifier.weight(1f).onGloballyPositioned { onTitleBounds(it.boundsOnScreen()) },
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(row.count.toString(), color = secondary, fontSize = 13.sp, modifier = Modifier.width(28.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Icon(KeyboardArrowDownIcon, if (row.collapsed) "展开文件夹" else "收起文件夹", tint = secondary,
            modifier = Modifier.size(24.dp).graphicsLayer { rotationZ = arrowRotation() })
    }
}
