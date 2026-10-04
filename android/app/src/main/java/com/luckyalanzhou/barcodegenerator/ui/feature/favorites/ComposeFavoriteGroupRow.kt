package com.luckyalanzhou.barcodegenerator.ui.feature.favorites

import com.luckyalanzhou.barcodegenerator.ui.app.ComposeAnimationConfig
import com.luckyalanzhou.barcodegenerator.ui.app.LocalLongPressMenuHost
import com.luckyalanzhou.barcodegenerator.ui.app.TabLongPressAction
import com.luckyalanzhou.barcodegenerator.ui.app.TabLongPressMenuState
import com.luckyalanzhou.barcodegenerator.ui.component.boundsOnScreen

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
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
    onClick: () -> Unit,
    onShowMoveDialog: (FavoriteGroup) -> Unit,
    onShowRenameDialog: (FavoriteGroup) -> Unit,
    onEdit: (FavoriteGroup) -> Unit,
    onConfirm: (String, String, String, () -> Unit) -> Unit,
    onDelete: (FavoriteGroup) -> Unit,
) {
    val interactionSource = remember(group.id) { MutableInteractionSource() }
    val menuHost = LocalLongPressMenuHost.current
    val view = LocalView.current
    val focus = remember(group.id) { FocusRequester() }
    var anchor by remember(group.id) { mutableStateOf(Rect.Zero) }
    DisposableEffect(group.id) { onDispose { anchor = Rect.Zero } }
    val openMenu: () -> Unit = {
        view.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
        menuHost(TabLongPressMenuState(anchor, AttachFileIcon, group.name, fileColor, dark,
            actions = listOf(
                TabLongPressAction("查看", VisibilityIcon) { onEdit(group) },
                TabLongPressAction("移动", DriveFileMoveIcon) { onShowMoveDialog(group) },
                TabLongPressAction("重命名", EditIcon) { onShowRenameDialog(group) },
                TabLongPressAction("删除", DeleteIcon) {
                    onConfirm("删除收藏", "确定删除“${group.name}”吗？", "删除") { onDelete(group) }
                },
            ), restoreFocus = { if (anchor != Rect.Zero) focus.requestFocus() },
            title = "编辑收藏文件", tabAnchor = false))
    }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale = animateFloatAsState(if (pressed) .965f else 1f, animation.settleSpring(), label = "favorite-group-scale")
    val background = animateColorAsState(if (pressed) fileColor.copy(alpha = .16f) else Color.Transparent, animation.settleSpring(), label = "favorite-group-background")

    Box(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().height(44.dp)
                .onGloballyPositioned { anchor = it.boundsOnScreen() }
                .focusRequester(focus)
                .padding(start = if (row.level <= 1) 20.dp else 38.dp, end = 4.dp)
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
                    onLongClickLabel = "编辑收藏文件",
                    hapticFeedbackEnabled = false,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(AttachFileIcon, "收藏文件", tint = fileColor, modifier = Modifier.size(21.dp))
            Spacer(Modifier.width(8.dp))
            Text(group.name, color = LocalAppColorScheme.current.text.primary, fontSize = 17.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(SimpleDateFormat("MM-dd HH:mm", Locale.ROOT).format(Date(group.savedAt)), color = LocalAppColorScheme.current.text.placeholder, fontSize = 11.sp, maxLines = 1)
        }
    }
}
