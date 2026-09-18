package com.luckyalanzhou.barcodegenerator

import com.luckyalanzhou.barcodegenerator.icons.CreateNewFolderIcon
import com.luckyalanzhou.barcodegenerator.icons.DeleteIcon
import com.luckyalanzhou.barcodegenerator.icons.DriveFileMoveIcon
import com.luckyalanzhou.barcodegenerator.icons.EditIcon
import com.luckyalanzhou.barcodegenerator.icons.FavoriteFilledIcon
import com.luckyalanzhou.barcodegenerator.icons.FolderIcon
import com.luckyalanzhou.barcodegenerator.icons.KeyboardArrowDownIcon
import com.luckyalanzhou.barcodegenerator.icons.KeyboardArrowRightIcon
import com.luckyalanzhou.barcodegenerator.icons.SearchIcon

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class ComposeFavoriteRow(
    val path: String,
    val label: String,
    val level: Int,
    val count: Int,
    val collapsed: Boolean,
    val folder: Boolean,
    val group: FavoriteGroup? = null,
)

@Composable
internal fun ComposeFavoritesPage(
    viewModel: BarcodeViewModel,
    dark: Boolean,
    onShowSubfolderEditor: (String) -> Unit,
    onShowFolderEditor: (String, (String) -> Unit) -> Unit,
    onShowMoveDialog: (FavoriteGroup) -> Unit,
    onShowRenameDialog: (FavoriteGroup) -> Unit,
    onConfirm: (String, String, String, () -> Unit) -> Unit,
) {
    val favoritesState by viewModel.dataState.collectAsStateWithLifecycle()
    val treeState by viewModel.favoriteTreeUiState.collectAsStateWithLifecycle()
    val hapticView = LocalView.current
    var query by remember { mutableStateOf("") }
    var folderMenu by remember { mutableStateOf<Pair<String, Int>?>(null) }
    var fileMenu by remember { mutableStateOf<FavoriteGroup?>(null) }
    val animation = rememberComposeAnimationConfig()
    val primary = if (dark) Color(0xfff2f4f8) else Color(0xff182230)
    val secondary = if (dark) Color(0xffaeb9c9) else Color(0xff6b7280)
    val rootFolderColor = if (dark) Color(0xff9bc8f5) else Color(0xff527ca8)
    val childFolderColor = if (dark) Color(0xffe0b383) else Color(0xff9b7a57)
    val fileColor = if (dark) Color(0xff9bd8c0) else Color(0xff5c8c7b)
    val normalizedQuery = query.trim().lowercase(Locale.getDefault())
    val folderPaths = (favoritesState.folders + favoritesState.groups.map { it.folder })
        .filter { it.isNotBlank() }.distinct().toSet()
    val expandedSearchPaths = favoriteSearchExpandedPaths(favoritesState, normalizedQuery)

    LaunchedEffect(folderPaths, favoritesState.groups) { viewModel.syncFavoriteTree(folderPaths) }
    LaunchedEffect(normalizedQuery, expandedSearchPaths) { viewModel.updateFavoriteSearch(expandedSearchPaths, normalizedQuery.isNotEmpty()) }

    val visibleCollapsedFolders = if (normalizedQuery.isEmpty()) treeState.collapsedFolders else treeState.collapsedFolders - expandedSearchPaths
    val rows = composeFavoriteRows(favoritesState, normalizedQuery, visibleCollapsedFolders)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item(key = "favorite-search") {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                textStyle = TextStyle(color = primary, fontSize = 17.sp),
                placeholder = { Text("搜索名称、文件夹或内容", color = secondary, fontSize = 17.sp) },
                leadingIcon = { Icon(SearchIcon, "搜索", tint = secondary) },
                shape = RoundedCornerShape(14.dp),
            )
        }

        if (rows.isEmpty()) {
            item(key = "favorite-empty") {
                Text(
                    if (favoritesState.groups.isEmpty()) "还没有收藏" else "没有匹配的收藏",
                    color = secondary,
                    fontSize = 17.sp,
                    modifier = Modifier.fillMaxWidth().padding(top = 40.dp),
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            items(
                items = rows,
                key = { row -> if (row.folder) "folder-${row.path}" else "group-${row.group?.id}" },
                contentType = { row -> if (row.folder) "folder" else "favorite-group" },
            ) { row ->
                if (row.folder) {
                    FavoriteFolderRow(
                        row = row,
                        dark = dark,
                        secondary = secondary,
                        folderColor = if (row.level == 0) rootFolderColor else childFolderColor,
                        animation = animation,
                        expandedPaths = folderPaths,
                        hapticView = hapticView,
                        menuExpanded = folderMenu?.first == row.path,
                        onMenuDismiss = { folderMenu = null },
                        onClick = { viewModel.toggleFavoriteFolder(row.path, folderPaths) },
                        onLongClick = {
                            hapticView.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS, android.view.HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING)
                            folderMenu = row.path to row.level
                        },
                        onShowSubfolderEditor = onShowSubfolderEditor,
                        onShowFolderEditor = onShowFolderEditor,
                        onConfirm = onConfirm,
                        viewModel = viewModel,
                    )
                } else {
                    row.group?.let { group ->
                        FavoriteGroupRow(
                            row = row,
                            group = group,
                            dark = dark,
                            secondary = secondary,
                            fileColor = fileColor,
                            animation = animation,
                            hapticView = hapticView,
                            menuExpanded = fileMenu?.id == group.id,
                            onMenuDismiss = { fileMenu = null },
                            onClick = { viewModel.openFavoriteGroup(group) },
                            onLongClick = {
                                hapticView.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS, android.view.HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING)
                                fileMenu = group
                            },
                            onShowMoveDialog = onShowMoveDialog,
                            onShowRenameDialog = onShowRenameDialog,
                            onConfirm = onConfirm,
                            viewModel = viewModel,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FavoriteFolderRow(
    row: ComposeFavoriteRow,
    dark: Boolean,
    secondary: Color,
    folderColor: Color,
    animation: ComposeAnimationConfig,
    expandedPaths: Set<String>,
    hapticView: android.view.View,
    menuExpanded: Boolean,
    onMenuDismiss: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onShowSubfolderEditor: (String) -> Unit,
    onShowFolderEditor: (String, (String) -> Unit) -> Unit,
    onConfirm: (String, String, String, () -> Unit) -> Unit,
    viewModel: BarcodeViewModel,
) {
    val interactionSource = remember(row.path) { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) .965f else 1f, animation.settleSpring(), label = "favorite-folder-scale")
    val background by animateColorAsState(if (pressed) folderColor.copy(alpha = if (dark) .22f else .12f) else Color.Transparent, animation.settleSpring(), label = "favorite-folder-background")
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
            Text(row.label, color = folderColor, fontSize = if (row.level == 0) 18.sp else 17.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(row.count.toString(), color = secondary, fontSize = 13.sp, modifier = Modifier.width(28.dp), textAlign = TextAlign.Center)
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
            containerColor = if (dark) Color(0xff252a33).copy(alpha = .98f) else Color.White.copy(alpha = .94f),
            tonalElevation = 0.dp,
            shadowElevation = 1.dp,
            menuWidth = 160.dp,
        ) {
            DropdownMenuItem(modifier = Modifier.height(40.dp), enabled = false, text = { Text("编辑文件夹", fontWeight = FontWeight.SemiBold) }, onClick = {})
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
                        { Icon(DeleteIcon, contentDescription = "删除文件夹", tint = if (dark) Color(0xffffb0b0) else Color(0xffe58b8b), modifier = Modifier.size(20.dp)) }
                    } else {
                        { Icon(if (label == "新建文件夹") CreateNewFolderIcon else EditIcon, contentDescription = label, tint = Color(0xff1f1f1f), modifier = Modifier.size(20.dp)) }
                    },
                    onClick = {
                        onMenuDismiss()
                        when {
                            row.level == 0 && index == 0 -> onShowSubfolderEditor(row.path)
                            index == if (row.level == 0) 1 else 0 -> onShowFolderEditor(row.path) { renamed ->
                                val parent = row.path.substringBeforeLast('/', "")
                                viewModel.renameFavoriteFolderAndPersist(row.path, listOf(parent, renamed).filter { it.isNotBlank() }.joinToString("/"))
                            }
                            else -> onConfirm("删除文件夹", "将删除文件夹内的所有收藏，确定继续吗？", "删除") { viewModel.deleteFavoriteFolderAndPersist(row.path) }
                        }
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FavoriteGroupRow(
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
    onConfirm: (String, String, String, () -> Unit) -> Unit,
    viewModel: BarcodeViewModel,
) {
    val interactionSource = remember(group.id) { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) .965f else 1f, animation.settleSpring(), label = "favorite-group-scale")
    val background by animateColorAsState(if (pressed) fileColor.copy(alpha = if (dark) .22f else .12f) else Color.Transparent, animation.settleSpring(), label = "favorite-group-background")

    Box(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().height(44.dp).padding(start = if (row.level <= 1) 20.dp else 38.dp, end = 4.dp)
                .clip(RoundedCornerShape(14.dp)).background(background).graphicsLayer { scaleX = scale; scaleY = scale }
                .combinedClickable(interactionSource, indication = null, onClick = onClick, onLongClick = onLongClick),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(FavoriteFilledIcon, "已收藏文件", tint = fileColor, modifier = Modifier.size(21.dp))
            Spacer(Modifier.width(8.dp))
            Text(group.name, color = fileColor, fontSize = 17.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(group.savedAt)), color = secondary, fontSize = 11.sp, maxLines = 1)
        }
        AnchoredDropdownMenu(
            dark = dark,
            expanded = menuExpanded,
            onDismissRequest = onMenuDismiss,
            shape = RoundedCornerShape(16.dp),
            containerColor = if (dark) Color(0xff252a33).copy(alpha = .98f) else Color.White.copy(alpha = .94f),
            tonalElevation = 0.dp,
            shadowElevation = 1.dp,
            menuWidth = 160.dp,
        ) {
            DropdownMenuItem(modifier = Modifier.height(40.dp), enabled = false, text = { Text("编辑收藏文件", fontWeight = FontWeight.SemiBold) }, onClick = {})
            ComposeDropdownDivider(dark)
            listOf("移动", "重命名", "删除").forEachIndexed { index, label ->
                if (index > 0) ComposeDropdownDivider(dark)
                DropdownMenuItem(
                    modifier = Modifier.height(40.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    text = { Text(label) },
                    trailingIcon = {
                        val icon = when (index) {
                            0 -> DriveFileMoveIcon
                            1 -> EditIcon
                            else -> DeleteIcon
                        }
                        Icon(icon, contentDescription = label, tint = if (index == 2) { if (dark) Color(0xffffb0b0) else Color(0xffe58b8b) } else Color(0xff1f1f1f), modifier = Modifier.size(20.dp))
                    },
                    onClick = {
                        onMenuDismiss()
                        when (index) {
                            0 -> onShowMoveDialog(group)
                            1 -> onShowRenameDialog(group)
                            else -> onConfirm("删除收藏", "确定删除“${group.name}”吗？", "删除") { viewModel.deleteFavoriteGroupAndPersist(group.id) }
                        }
                    },
                )
            }
        }
    }
}

private fun composeFavoriteRows(state: BarcodeDataState, query: String, collapsedFolders: Set<String>): List<ComposeFavoriteRow> {
    val folders = (state.folders + state.groups.map { it.folder }).filter { it.isNotBlank() }.distinct()
    val roots = folders.map { it.substringBefore('/') }.distinct().sorted()
    fun matches(group: FavoriteGroup): Boolean = query.isEmpty() || group.folder.lowercase(Locale.getDefault()).contains(query) || group.name.lowercase(Locale.getDefault()).contains(query) || group.itemIds.any { id -> state.items.firstOrNull { it.id == id }?.text?.lowercase(Locale.getDefault())?.contains(query) == true }
    val result = mutableListOf<ComposeFavoriteRow>()
    fun renderFolder(path: String, level: Int) {
        val prefix = "$path/"
        val children = folders.filter { it.startsWith(prefix) && !it.removePrefix(prefix).contains('/') }.map { it.removePrefix(prefix) }.distinct().sorted()
        val groups = state.groups.filter { it.folder == path && matches(it) }
        val descendants = state.groups.filter { it.folder.startsWith(prefix) && matches(it) }
        if (query.isNotEmpty() && groups.isEmpty() && descendants.isEmpty()) return
        val collapsed = path in collapsedFolders
        result += ComposeFavoriteRow(path, path.substringAfterLast('/'), level, if (level == 0) children.size else groups.size, collapsed, true)
        if (!collapsed) {
            groups.forEach { group -> result += ComposeFavoriteRow(group.folder, group.name, level + 1, 0, false, false, group) }
            children.forEach { child -> renderFolder("$path/$child", level + 1) }
        }
    }
    roots.forEach { renderFolder(it, 0) }
    return result
}

private fun favoriteSearchExpandedPaths(state: BarcodeDataState, query: String): Set<String> {
    if (query.isEmpty()) return emptySet()
    fun matches(group: FavoriteGroup): Boolean = group.folder.lowercase(Locale.getDefault()).contains(query) || group.name.lowercase(Locale.getDefault()).contains(query) || group.itemIds.any { id -> state.items.firstOrNull { it.id == id }?.text?.lowercase(Locale.getDefault())?.contains(query) == true }
    return state.groups.filter(::matches).flatMap { group ->
        val parts = group.folder.split('/')
        parts.indices.map { parts.take(it + 1).joinToString("/") }
    }.toSet()
}
