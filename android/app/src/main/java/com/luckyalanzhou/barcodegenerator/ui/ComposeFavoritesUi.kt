package com.luckyalanzhou.barcodegenerator

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color as ComposeColor
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
internal fun ComposeFavoritesPage(activity: MainActivity) {
    val anchor = LocalView.current
    var query by remember { mutableStateOf("") }
    var revision by remember { mutableIntStateOf(0) }
    var folderMenu by remember { mutableStateOf<Pair<String, Int>?>(null) }
    var fileMenu by remember { mutableStateOf<FavoriteGroup?>(null) }
    val dark = activity.isDark()
    val primary = if (dark) ComposeColor(0xfff2f4f8) else ComposeColor(0xff182230)
    val secondary = if (dark) ComposeColor(0xffaeb9c9) else ComposeColor(0xff6b7280)
    val inputColor = if (dark) ComposeColor(0xff202936) else ComposeColor(0xfff7f9fc)
    val rootFolderColor = ComposeColor(0xff527ca8)
    val childFolderColor = ComposeColor(0xff9b7a57)
    val fileColor = ComposeColor(0xff5c8c7b)
    val rows = remember(query, revision, activity.favoriteGroups.size, activity.favoriteFolders.size) {
        composeFavoriteRows(activity, query.trim().lowercase(Locale.getDefault()))
    }

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
            Text(if (activity.favoriteGroups.isEmpty()) "还没有收藏" else "没有匹配的收藏", color = secondary, fontSize = 17.sp, modifier = Modifier.fillMaxWidth().padding(top = 40.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        } else {
            rows.forEach { row ->
                if (row.folder) {
                    val folderColor = if (row.level == 0) rootFolderColor else childFolderColor
                    Box(Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().height(if (row.level == 0) 50.dp else 43.dp)
                                .padding(start = if (row.level == 0) 11.dp else 26.dp, end = 5.dp)
                                .combinedClickable(
                                    onClick = {
                                        val folders = (activity.favoriteFolders + activity.favoriteGroups.map { it.folder }).filter { it.isNotBlank() }.distinct()
                                        if (row.collapsed) activity.collapsedFavoriteFolders.remove(row.path)
                                        else activity.collapsedFavoriteFolders.addAll(folders.filter { it == row.path || it.startsWith("${row.path}/") })
                                        revision++
                                    },
                                    onLongClick = { anchor.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS); folderMenu = row.path to row.level }
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
                        DropdownMenu(
                            expanded = folderMenu?.first == row.path,
                            onDismissRequest = { folderMenu = null },
                            shape = RoundedCornerShape(16.dp),
                            containerColor = ComposeColor.White.copy(alpha = .94f),
                            tonalElevation = 0.dp,
                            shadowElevation = 3.dp,
                        ) {
                            folderActions.forEachIndexed { index, label ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = {
                                        folderMenu = null
                                        when {
                                            row.level == 0 && index == 0 -> activity.showSubfolderEditor(row.path)
                                            index == if (row.level == 0) 1 else 0 -> activity.showFolderEditor(row.path) { renamed ->
                                                activity.favoriteGroups.filter { it.folder == row.path || it.folder.startsWith("${row.path}/") }.forEach { group -> group.folder = if (group.folder == row.path) renamed else renamed + group.folder.removePrefix(row.path) }
                                                activity.favoriteFolders.filter { it == row.path || it.startsWith("${row.path}/") }.toList().forEach { old -> activity.favoriteFolders.remove(old); activity.favoriteFolders.add(if (old == row.path) renamed else renamed + old.removePrefix(row.path)) }
                                                activity.saveAllFavorites(); activity.render()
                                            }
                                            else -> activity.showComposeConfirmDialog("删除文件夹", "将删除文件夹内的所有收藏，确定继续吗？", "删除") {
                                                activity.favoriteGroups.removeAll { group -> group.folder == row.path || group.folder.startsWith("${row.path}/") }
                                                activity.favoriteFolders.removeAll { folder -> folder == row.path || folder.startsWith("${row.path}/") }
                                                activity.saveAllFavorites()
                                                activity.render()
                                            }
                                        }
                                    },
                                )
                            }
                        }
                    }
                } else {
                    val group = row.group ?: return@forEach
                    Box(Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().height(44.dp).padding(start = if (row.level <= 1) 20.dp else 38.dp, end = 4.dp)
                                .combinedClickable(
                                    onClick = {
                                        activity.resultItems = row.groupItems
                                        activity.showingHistoryResult = false
                                        activity.resultsReturnPage = "favorites"
                                        activity.selectedFavoriteGroup = group
                                        activity.page = "results"
                                        activity.render()
                                    },
                                    onLongClick = { anchor.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS); fileMenu = group }
                                ),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(painterResource(R.drawable.ic_attachment), "收藏文件", tint = fileColor, modifier = Modifier.size(21.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(group.name, color = fileColor, fontSize = 17.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(group.savedAt)), color = secondary, fontSize = 11.sp, maxLines = 1)
                        }
                        DropdownMenu(
                            expanded = fileMenu?.id == group.id,
                            onDismissRequest = { fileMenu = null },
                            shape = RoundedCornerShape(16.dp),
                            containerColor = ComposeColor.White.copy(alpha = .94f),
                            tonalElevation = 0.dp,
                            shadowElevation = 3.dp,
                        ) {
                            listOf("移动", "重命名", "删除").forEachIndexed { index, label ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = {
                                        fileMenu = null
                                        when (index) {
                                            0 -> activity.showFavoriteMoveDialog(group)
                                            1 -> activity.showFavoriteRenameDialog(group)
                                            else -> activity.showComposeConfirmDialog("删除收藏", "确定删除“${group.name}”吗？", "删除") {
                                                activity.favoriteGroups.removeAll { it.id == group.id }
                                                row.groupItems.forEach { item -> if (activity.favoriteGroups.none { group -> group.itemIds.contains(item.id) }) item.favorite = false }
                                                if (group.folder !in activity.favoriteFolders) activity.favoriteFolders.add(group.folder)
                                                activity.saveAllFavorites()
                                                activity.render()
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

private fun composeFavoriteRows(activity: MainActivity, query: String): List<ComposeFavoriteRow> {
    val folders = (activity.favoriteFolders + activity.favoriteGroups.map { it.folder }).filter { it.isNotBlank() }.distinct()
    val roots = folders.map { it.substringBefore('/') }.distinct().sorted()
    fun matches(group: FavoriteGroup): Boolean = query.isEmpty() || group.folder.lowercase(Locale.getDefault()).contains(query) || group.name.lowercase(Locale.getDefault()).contains(query) || group.itemIds.any { id -> activity.items.firstOrNull { it.id == id }?.text?.lowercase(Locale.getDefault())?.contains(query) == true }
    if (!activity.favoriteTreeInitialized) {
        activity.collapsedFavoriteFolders.addAll(folders)
        activity.favoriteTreeInitialized = true
    } else activity.collapsedFavoriteFolders.retainAll(folders)
    if (query.isNotEmpty()) {
        if (activity.favoriteCollapsedBeforeSearch == null) activity.favoriteCollapsedBeforeSearch = activity.collapsedFavoriteFolders.toSet()
        activity.favoriteGroups.filter(::matches).flatMap { group ->
            val parts = group.folder.split('/')
            parts.indices.map { parts.take(it + 1).joinToString("/") }
        }.forEach { activity.collapsedFavoriteFolders.remove(it) }
    } else {
        activity.favoriteCollapsedBeforeSearch?.let { previous ->
            activity.collapsedFavoriteFolders.clear()
            activity.collapsedFavoriteFolders.addAll(previous.filter { it in folders })
            activity.favoriteCollapsedBeforeSearch = null
        }
    }
    val result = mutableListOf<ComposeFavoriteRow>()
    fun renderFolder(path: String, level: Int) {
        val prefix = "$path/"
        val children = folders.filter { it.startsWith(prefix) && !it.removePrefix(prefix).contains('/') }.map { it.removePrefix(prefix) }.distinct().sorted()
        val groups = activity.favoriteGroups.filter { it.folder == path && matches(it) }
        val descendants = activity.favoriteGroups.filter { it.folder.startsWith(prefix) && matches(it) }
        if (query.isNotEmpty() && groups.isEmpty() && descendants.isEmpty()) return
        val collapsed = path in activity.collapsedFavoriteFolders
        result += ComposeFavoriteRow(path, path.substringAfterLast('/'), level, if (level == 0) children.size else groups.size, collapsed, true)
        if (!collapsed) {
            groups.forEach { group -> result += ComposeFavoriteRow(group.folder, group.name, level + 1, 0, false, false, group, group.itemIds.mapNotNull { id -> activity.items.firstOrNull { it.id == id } }) }
            children.forEach { child -> renderFolder("$path/$child", level + 1) }
        }
    }
    roots.forEach { renderFolder(it, 0) }
    return result
}
