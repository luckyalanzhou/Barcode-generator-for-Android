package com.luckyalanzhou.barcodegenerator.ui.feature.favorites

import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import com.luckyalanzhou.barcodegenerator.R
import com.luckyalanzhou.barcodegenerator.ui.feature.favorites.tree.ComposeFavoriteRow
import com.luckyalanzhou.barcodegenerator.ui.support.localization.rememberApplicationFormattingLocale

import com.luckyalanzhou.barcodegenerator.ui.animation.ComposeAnimationConfig
import com.luckyalanzhou.barcodegenerator.ui.component.menu.LocalLongPressMenuHost
import com.luckyalanzhou.barcodegenerator.ui.component.menu.TabLongPressAction
import com.luckyalanzhou.barcodegenerator.ui.component.menu.TabLongPressMenuState
import com.luckyalanzhou.barcodegenerator.ui.component.performLightMenuOpenHaptic

import com.luckyalanzhou.barcodegenerator.ui.theme.*

import com.luckyalanzhou.barcodegenerator.domain.FavoriteGroup
import com.luckyalanzhou.barcodegenerator.icons.AttachFileIcon
import com.luckyalanzhou.barcodegenerator.icons.DeleteIcon
import com.luckyalanzhou.barcodegenerator.icons.DriveFileMoveIcon
import com.luckyalanzhou.barcodegenerator.icons.EditIcon
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date

/**
 * 收藏文件行：点击打开条码文件，长按弹出编辑菜单；移动、重命名、编辑和删除分别委托给上层回调。
 * 收藏状态按收藏文件身份判断，而不是按文件内某一条码的内容判断。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun FavoriteGroupRow(
    row: ComposeFavoriteRow,
    modifier: Modifier,
    group: FavoriteGroup,
    dark: Boolean,
    secondary: Color,
    fileColor: Color,
    animation: ComposeAnimationConfig,
    onClick: () -> Unit,
    onShowMoveDialog: (FavoriteGroup) -> Unit,
    onShowRenameDialog: (FavoriteGroup) -> Unit,
    onEdit: (FavoriteGroup) -> Unit,
    onConfirm: (String, String, String, suspend () -> Unit) -> Unit,
    onDelete: suspend (FavoriteGroup) -> Unit,
    searchPath: String? = null,
) {
    val localizedResources = LocalResources.current
    val interactionSource = remember(group.id) { MutableInteractionSource() }
    val menuHost = LocalLongPressMenuHost.current
    val view = LocalView.current
    val focus = remember(group.id) { FocusRequester() }
    val coordinates = remember(group.id) { FavoriteRowCoordinates() }
    DisposableEffect(group.id) { onDispose { coordinates.clear() } }
    // 点按打开结果，长按菜单提供编辑条码及文件管理操作。
    val openMenu: () -> Unit = {
        view.performLightMenuOpenHaptic()
        val rowBounds = coordinates.rowBounds()
        menuHost(TabLongPressMenuState(rowBounds, group.name, dark,
            actions = listOf(
                TabLongPressAction(localizedResources.getString(R.string.edit_barcodes), EditIcon) { onEdit(group) },
                TabLongPressAction(localizedResources.getString(R.string.action_move), DriveFileMoveIcon) { onShowMoveDialog(group) },
                TabLongPressAction(localizedResources.getString(R.string.action_rename), EditIcon) { onShowRenameDialog(group) },
                TabLongPressAction(localizedResources.getString(R.string.action_delete), DeleteIcon) {
                    onConfirm(localizedResources.getString(R.string.favorite_delete), localizedResources.getString(R.string.favorite_delete_confirm, group.name), localizedResources.getString(R.string.action_delete)) { onDelete(group) }
                },
            ), restoreFocus = { if (coordinates.rowBounds() != androidx.compose.ui.geometry.Rect.Zero) focus.requestFocus() },
            title = localizedResources.getString(R.string.favorite_file_edit), tabAnchor = false,
            menuAnchorBoundsOnScreen = coordinates.titleBounds() ?: rowBounds,
            sourceContent = { FavoriteGroupRowContent(row, group, fileColor, searchPath) }))
    }
    val pressed by interactionSource.collectIsPressedAsState()
    val reduceMotion = com.luckyalanzhou.barcodegenerator.ui.theme.LocalVisualEffectsPolicy.current.reduceMotion
    val scale = animateFloatAsState(if (pressed && !reduceMotion) .97f else 1f,
        if (reduceMotion) androidx.compose.animation.core.snap() else if (pressed) androidx.compose.animation.core.tween(100)
        else animation.settleSpring(), label = "favorite-group-scale")
    val background = animateColorAsState(if (pressed) fileColor.copy(alpha = .16f) else Color.Transparent, if (reduceMotion) androidx.compose.animation.core.snap() else animation.settleSpring(), label = "favorite-group-background")

    Box(
        modifier.fillMaxWidth().heightIn(min = 48.dp)
            .onGloballyPositioned { coordinates.row = it }
            .focusRequester(focus)
            .clip(RoundedCornerShape(14.dp))
            .drawWithContent {
                scale(scale.value) {
                    drawRoundRect(color = background.value, cornerRadius = CornerRadius(14.dp.toPx()))
                    this@drawWithContent.drawContent()
                }
            }
            .combinedClickable(
                interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick,
                onLongClick = openMenu,
                onLongClickLabel = stringResource(R.string.favorite_file_edit),
                hapticFeedbackEnabled = false,
            ),
        contentAlignment = Alignment.CenterStart,
    ) {
        FavoriteGroupRowContent(row, group, fileColor, searchPath) { coordinates.title = it }
    }
}

/** 预览与原收藏行共用文件名、时间及层级缩进，长按不替换文字样式。 */
@Composable
private fun FavoriteGroupRowContent(
    row: ComposeFavoriteRow, group: FavoriteGroup, fileColor: Color,
    searchPath: String?,
    onTitleCoordinates: (LayoutCoordinates) -> Unit = {},
) {
    val locale = rememberApplicationFormattingLocale()
    val time = remember(group.savedAt, locale) {
        val pattern = android.text.format.DateFormat.getBestDateTimePattern(locale, "MdHm")
        SimpleDateFormat(pattern, locale).format(Date(group.savedAt))
    }
    Row(Modifier.fillMaxWidth().padding(
        start = 11.dp + 20.dp * row.level, end = 4.dp,
        top = if (searchPath != null) 6.dp else 0.dp,
        bottom = if (searchPath != null) 6.dp else 0.dp,
    ),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(AttachFileIcon, stringResource(R.string.favorite_file), tint = fileColor, modifier = Modifier.size(21.dp))
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f).padding(end = 8.dp)) {
            Text(group.name, color = LocalAppColorScheme.current.text.primary, fontSize = 16.sp,
                fontWeight = FontWeight.Normal, modifier = Modifier.onGloballyPositioned(onTitleCoordinates),
                style = androidx.compose.material3.LocalTextStyle.current.copy(background = Color.Transparent),
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (searchPath != null) {
                Text(searchPath, color = LocalAppColorScheme.current.text.secondary, fontSize = 12.sp,
                    lineHeight = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Text(time, color = LocalAppColorScheme.current.text.secondary, fontSize = 11.sp, maxLines = 1,
            style = androidx.compose.material3.LocalTextStyle.current.copy(background = Color.Transparent))
    }
}
