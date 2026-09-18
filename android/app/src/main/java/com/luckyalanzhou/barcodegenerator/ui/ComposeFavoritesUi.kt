package com.luckyalanzhou.barcodegenerator

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    val groupItems: List<CodeItem> = emptyList()
)

@OptIn(ExperimentalFoundationApi::class)
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
    val hapticView = LocalView.current
    val favoritesState by viewModel.dataState.collectAsStateWithLifecycle()
    val treeState by viewModel.favoriteTreeUiState.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    var folderMenu by remember { mutableStateOf<Pair<String, Int>?>(null) }
    var fileMenu by remember { mutableStateOf<FavoriteGroup?>(null) }
    val animation = rememberComposeAnimationConfig()
    val primary = if (dark) ComposeColor(0xfff2f4f8) else ComposeColor(0xff182230)
    val secondary = if (dark) ComposeColor(0xffaeb9c9) else ComposeColor(0xff6b7280)
    val inputColor = if (dark) ComposeColor(0xff202936) else ComposeColor(0xfff7f9fc)
    val rootFolderColor = if (dark) ComposeColor(0xff9bc8f5) else ComposeColor(0xff527ca8)
    val childFolderColor = if (dark) ComposeColor(0xffe0b383) else ComposeColor(0xff9b7a57)
    val fileColor = if (dark) ComposeColor(0xff9bd8c0) else ComposeColor(0xff5c8c7b)
    // rows 依赖可变业务对象的完整内容；不缓存，确保重命名、移动、删除和条码修改后
    // 即使 Activity 只触发了普通重组，列表也不会继续显示旧快照。
    val normalizedQuery = query.trim().lowercase(Locale.getDefault())
    val folderPaths = (favoritesState.folders + favoritesState.groups.map { it.folder })
        .filter { it.isNotBlank() }.distinct().toSet()
    val expandedSearchPaths = favoriteSearchExpandedPaths(favoritesState, normalizedQuery)
    LaunchedEffect(folderPaths, favoritesState.groups) {
        viewModel.syncFavoriteTree(folderPaths)
    }
    LaunchedEffect(normalizedQuery, expandedSearchPaths) {
        viewModel.updateFavoriteSearch(expandedSearchPaths, normalizedQuery.isNotEmpty())
    }
    val visibleCollapsedFolders = if (normalizedQuery.isEmpty()) {
        treeState.collapsedFolders
    } else {
        treeState.collapsedFolders - expandedSearchPaths
    }
    val rows = composeFavoriteRows(favoritesState, normalizedQuery, visibleCollapsedFolders)

    Column(Modifier.fillMaxWidth().padding(bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            singleLine = true,
            textStyle = TextStyle(color = primary, fontSize = 17.sp),
            placeholder = { Text("搜索名称、文件夹或内容", color = secondary, fontSize = 17.sp) },
            leadingIcon = { Icon(painterResource(R.drawable.ic_search), "搜索", tint = secondary) },
            shape = RoundedCornerShape(14.dp)
        )
        if (rows.isEmpty()) {
            Text(if (favoritesState.groups.isEmpty()) "还没有收藏" else "没有匹配的收藏", color = secondary, fontSize = 17.sp, modifier = Modifier.fillMaxWidth().padding(top = 40.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        } else {
            rows.forEach { row ->
                if (row.folder) {
                    val folderColor = if (row.level == 0) rootFolderColor else childFolderColor
                    val interactionSource = remember(row.path) { MutableInteractionSource() }
                    val pressed by interactionSource.collectIsPressedAsState()
                    val pressScale by animateFloatAsState(
                        targetValue = if (pressed) 0.965f else 1f,
                        animationSpec = animation.settleSpring(),
                    )
                    val pressColor by animateColorAsState(
                        targetValue = if (pressed) folderColor.copy(alpha = if (dark) .22f else .12f) else ComposeColor.Transparent,
                        animationSpec = animation.settleSpring(),
                    )
                    Box(Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().height(if (row.level == 0) 50.dp else 43.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(pressColor)
                                .padding(start = if (row.level == 0) 11.dp else 26.dp, end = 5.dp)
                                .graphicsLayer { scaleX = pressScale; scaleY = pressScale }
                                    .combinedClickable(
                                    interactionSource = interactionSource,
                                    indication = null,
                                    onClick = {
                                        viewModel.toggleFavoriteFolder(row.path, folderPaths)
                                    },
                                    onLongClick = {
                                        hapticView.performHapticFeedback(
                                            android.view.HapticFeedbackConstants.LONG_PRESS,
                                            android.view.HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING,
                                        )
                                        folderMenu = row.path to row.level
                                    }
                                ),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(painterResource(R.drawable.ic_folder), "文件夹", tint = folderColor, modifier = Modifier.size(if (row.level == 0) 27.dp else 21.dp))
                            Spacer(Modifier.width(if (row.level == 0) 8.dp else 7.dp))
                            Text(row.label, color = folderColor, fontSize = if (row.level == 0) 18.sp else 17.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(row.count.toString(), color = secondary, fontSize = 13.sp, modifier = Modifier.width(28.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                            Text(if (row.collapsed) "›" else "⌄", color = secondary, fontSize = 22.sp, modifier = Modifier.width(25.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        }
                        val folderActions = if (row.level == 0) listOf("新建文件夹", "重命名", "删除") else listOf("重命名", "删除")
                        AnchoredDropdownMenu(
                            dark = dark,
                            expanded = folderMenu?.first == row.path,
                            onDismissRequest = { folderMenu = null },
                            shape = RoundedCornerShape(16.dp),
                            containerColor = if (dark) ComposeColor(0xff252a33).copy(alpha = .98f) else ComposeColor.White.copy(alpha = .94f),
                            tonalElevation = 0.dp,
                            shadowElevation = 3.dp,
                            menuWidth = 160.dp,
                        ) {
                            DropdownMenuItem(
                                enabled = false,
                                text = { Text("编辑文件夹", fontWeight = FontWeight.SemiBold) },
                                onClick = {},
                            )
                            ComposeDropdownDivider(dark)
                            folderActions.forEachIndexed { index, label ->
                                if (index > 0) ComposeDropdownDivider(dark)
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = {
                                        folderMenu = null
                                        when {
                                            row.level == 0 && index == 0 -> onShowSubfolderEditor(row.path)
                                            index == if (row.level == 0) 1 else 0 -> onShowFolderEditor(row.path) { renamed ->
                                                val parent = row.path.substringBeforeLast('/', "")
                                                val renamedPath = listOf(parent, renamed).filter { it.isNotBlank() }.joinToString("/")
                                                viewModel.renameFavoriteFolderAndPersist(row.path, renamedPath)
                                            }
                                            else -> onConfirm("删除文件夹", "将删除文件夹内的所有收藏，确定继续吗？", "删除") {
                                                viewModel.deleteFavoriteFolderAndPersist(row.path)
                                            }
                                        }
                                    },
                                )
                            }
                        }
                    }
                } else {
                    val group = row.group ?: return@forEach
                    val interactionSource = remember(group.id) { MutableInteractionSource() }
                    val pressed by interactionSource.collectIsPressedAsState()
                    val pressScale by animateFloatAsState(
                        targetValue = if (pressed) 0.965f else 1f,
                        animationSpec = animation.settleSpring(),
                    )
                    val pressColor by animateColorAsState(
                        targetValue = if (pressed) fileColor.copy(alpha = if (dark) .22f else .12f) else ComposeColor.Transparent,
                        animationSpec = animation.settleSpring(),
                    )
                    Box(Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().height(44.dp).padding(start = if (row.level <= 1) 20.dp else 38.dp, end = 4.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(pressColor)
                                .graphicsLayer { scaleX = pressScale; scaleY = pressScale }
                                .combinedClickable(
                                    interactionSource = interactionSource,
                                    indication = null,
                                    onClick = {
                                        viewModel.openFavoriteGroup(group)
                                    },
                                    onLongClick = {
                                        hapticView.performHapticFeedback(
                                            android.view.HapticFeedbackConstants.LONG_PRESS,
                                            android.view.HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING,
                                        )
                                        fileMenu = group
                                    }
                                ),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(painterResource(R.drawable.ic_attachment), "收藏文件", tint = fileColor, modifier = Modifier.size(21.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(group.name, color = fileColor, fontSize = 17.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(group.savedAt)), color = secondary, fontSize = 11.sp, maxLines = 1)
                        }
                        AnchoredDropdownMenu(
                            dark = dark,
                            expanded = fileMenu?.id == group.id,
                            onDismissRequest = { fileMenu = null },
                            shape = RoundedCornerShape(16.dp),
                            containerColor = if (dark) ComposeColor(0xff252a33).copy(alpha = .98f) else ComposeColor.White.copy(alpha = .94f),
                            tonalElevation = 0.dp,
                            shadowElevation = 3.dp,
                            menuWidth = 160.dp,
                        ) {
                            DropdownMenuItem(
                                enabled = false,
                                text = { Text("编辑收藏文件", fontWeight = FontWeight.SemiBold) },
                                onClick = {},
                            )
                            ComposeDropdownDivider(dark)
                            listOf("移动", "重命名", "删除").forEachIndexed { index, label ->
                                if (index > 0) ComposeDropdownDivider(dark)
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = {
                                        fileMenu = null
                                        when (index) {
                                            0 -> onShowMoveDialog(group)
                                            1 -> onShowRenameDialog(group)
                                            else -> onConfirm("删除收藏", "确定删除“${group.name}”吗？", "删除") {
                                                viewModel.deleteFavoriteGroupAndPersist(group.id)
                                            }
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
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
            groups.forEach { group -> result += ComposeFavoriteRow(group.folder, group.name, level + 1, 0, false, false, group, group.itemIds.mapNotNull { id -> state.items.firstOrNull { it.id == id } }) }
            children.forEach { child -> renderFolder("$path/$child", level + 1) }
        }
    }
    roots.forEach { renderFolder(it, 0) }
    return result
}

private fun favoriteSearchExpandedPaths(state: BarcodeDataState, query: String): Set<String> {
    if (query.isEmpty()) return emptySet()
    fun matches(group: FavoriteGroup): Boolean =
        group.folder.lowercase(Locale.getDefault()).contains(query) ||
            group.name.lowercase(Locale.getDefault()).contains(query) ||
            group.itemIds.any { id ->
                state.items.firstOrNull { it.id == id }?.text?.lowercase(Locale.getDefault())?.contains(query) == true
            }
    return state.groups.filter(::matches).flatMap { group ->
        val parts = group.folder.split('/')
        parts.indices.map { parts.take(it + 1).joinToString("/") }
    }.toSet()
}
