package com.luckyalanzhou.barcodegenerator

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height

/** 文件夹编辑 Compose 弹窗，校验规则与原编辑器一致。 */
internal fun MainActivity.showFolderEditorCompose(initial: String = "", showMetrics: Boolean = false, onSaved: (String) -> Unit) {
    showComposeDialog(compact = false, metricsLabel = if (showMetrics) "文件夹编辑弹窗" else null) { dismiss ->
        val dark = isDark()
        var value by remember { mutableStateOf(initial) }
        ComposeGlassDialogCard(dark) {
            Text(
                if (initial.isBlank()) "新建文件夹" else "重命名文件夹",
                color = if (dark) Color(0xfff2f4f8) else Color(0xff182230),
                fontSize = 20.sp,
            )
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                singleLine = true,
                label = { Text("文件夹名称") },
            )
            Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.End) {
                DialogAction("取消", dark, dismiss)
                DialogAction("保存", dark, {
                    val name = value.trim()
                    val parent = initial.substringBeforeLast('/', "")
                    val targetPath = listOf(parent, name).filter { it.isNotBlank() }.joinToString("/")
                    when {
                        name.isBlank() -> toast("请输入文件夹名称")
                        !isValidFavoriteFolderPath(targetPath) -> toast("文件夹最多支持一级和二级，且名称不能包含斜杠")
                        favoriteFolders.any { it == targetPath && it != initial } -> toast("已存在同名文件夹")
                        else -> { onSaved(name); dismiss() }
                    }
                }, modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

/** 新建二级文件夹 Compose 弹窗，保持原有斜杠和重名校验。 */
internal fun MainActivity.showSubfolderEditorCompose(parent: String, onCreated: ((String) -> Unit)? = null) {
    showComposeDialog(compact = false, metricsLabel = null) { dismiss ->
        val dark = isDark()
        var value by remember { mutableStateOf("") }
        ComposeGlassDialogCard(dark) {
            Text("新建文件夹", color = if (dark) Color(0xfff2f4f8) else Color(0xff182230), fontSize = 20.sp)
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                singleLine = true,
                label = { Text("文件夹名称") },
            )
            Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.End) {
                DialogAction("取消", dark, dismiss)
                DialogAction("保存", dark, {
                    val child = value.trim()
                    val path = "$parent/$child"
                    when {
                        child.isBlank() -> toast("请输入文件夹名称")
                        child.contains('/') -> toast("名称不能包含斜杠")
                        path in favoriteFolders -> toast("已存在同名文件夹")
                        else -> { favoriteFolders.add(path); saveFavoriteFolders(); onCreated?.invoke(child) ?: render(); dismiss() }
                    }
                }, modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

@Composable
private fun ComposeChoiceField(
    value: String,
    options: List<String>,
    dark: Boolean,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onSelected: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(
            onClick = { if (enabled) expanded = true },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(value, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = RoundedCornerShape(16.dp),
            containerColor = if (dark) Color(0xff252a33).copy(alpha = .98f) else Color.White.copy(alpha = .94f),
            tonalElevation = 0.dp,
            shadowElevation = 3.dp,
        ) {
            options.forEachIndexed { index, option ->
                if (index > 0) ComposeDropdownDivider(dark)
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = { onSelected(option); expanded = false },
                )
            }
        }
    }
}

internal fun MainActivity.showFavoriteRenameDialogCompose(group: FavoriteGroup) {
    showComposeDialog(compact = false, metricsLabel = null) { dismiss ->
        val dark = isDark()
        var value by remember { mutableStateOf(group.name) }
        ComposeGlassDialogCard(dark) {
            Text("重命名收藏", color = if (dark) Color(0xfff2f4f8) else Color(0xff182230), fontSize = 20.sp)
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                singleLine = true,
                label = { Text("收藏文件名") },
            )
            Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.End) {
                DialogAction("取消", dark, dismiss)
                DialogAction("保存", dark, {
                    val name = value.trim()
                    if (name.isBlank()) toast("请输入收藏文件名")
                    else {
                        group.name = name
                        saveFavoriteGroups()
                        dismiss()
                        showFavoriteGroups()
                    }
                }, modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

internal fun MainActivity.showFavoriteMoveDialogCompose(group: FavoriteGroup) {
    val folders = favoriteFolders.filter { it.isNotBlank() }
    if (folders.isEmpty()) {
        showIos26NoticeDialogCompose("请先创建文件夹")
        return
    }
    showComposeDialog(compact = false, metricsLabel = null) { dismiss ->
        val dark = isDark()
        var selected by remember { mutableStateOf(group.folder.takeIf { it in folders } ?: folders.first()) }
        ComposeGlassDialogCard(dark) {
            Text("移动收藏", color = if (dark) Color(0xfff2f4f8) else Color(0xff182230), fontSize = 20.sp)
            ComposeChoiceField(selected, folders, dark, onSelected = { selected = it })
            Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.End) {
                DialogAction("取消", dark, dismiss)
                DialogAction("移动", dark, {
                    group.folder = selected
                    if (group.folder.isNotBlank() && group.folder !in favoriteFolders) favoriteFolders.add(group.folder)
                    saveAllFavorites()
                    dismiss()
                    showFavoriteGroups()
                }, modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

internal fun MainActivity.showGroupEditorCompose(group: FavoriteGroup) {
    showComposeDialog(compact = false, metricsLabel = null) { dismiss ->
        val dark = isDark()
        var name by remember { mutableStateOf(group.name) }
        var folder by remember { mutableStateOf(group.folder) }
        ComposeGlassDialogCard(dark) {
            Text("编辑收藏", color = if (dark) Color(0xfff2f4f8) else Color(0xff182230), fontSize = 20.sp)
            OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth().padding(top = 12.dp), singleLine = true, label = { Text("收藏文件名") })
            OutlinedTextField(folder, { folder = it }, Modifier.fillMaxWidth().padding(top = 10.dp), singleLine = true, label = { Text("文件夹") })
            Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.End) {
                DialogAction("取消", dark, dismiss)
                DialogAction("删除", dark, {
                    dismiss()
                    showComposeConfirmDialog("删除收藏", "确定删除“\${group.name}”吗？", "删除") {
                        favoriteGroups.removeAll { it.id == group.id }
                        if (group.folder.isNotBlank() && group.folder !in favoriteFolders) favoriteFolders.add(group.folder)
                        selectedFavoriteGroup = null
                        saveAllFavorites()
                        page = "favorites"
                        render()
                    }
                }, modifier = Modifier.padding(start = 8.dp))
                DialogAction("保存", dark, {
                    val cleanName = name.trim()
                    val cleanFolder = folder.trim().ifEmpty { "默认" }
                    if (cleanName.isBlank()) toast("请输入收藏文件名")
                    else if (!isValidFavoriteFolderPath(cleanFolder)) toast("文件夹最多支持一级和二级，且名称不能包含斜杠")
                    else {
                        group.name = cleanName
                        group.folder = cleanFolder
                        if (cleanFolder !in favoriteFolders) favoriteFolders.add(cleanFolder)
                        selectedFavoriteGroup = group
                        saveAllFavorites()
                        dismiss()
                        render()
                    }
                }, modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

internal fun MainActivity.showItemEditorCompose(item: CodeItem) {
    showComposeDialog(compact = false, metricsLabel = null) { dismiss ->
        val dark = isDark()
        var value by remember { mutableStateOf(item.text) }
        var selectedIndex by remember { mutableIntStateOf(formats.indexOfFirst { it.first == item.format }.coerceAtLeast(0)) }
        ComposeGlassDialogCard(dark) {
            Text("编辑条目", color = if (dark) Color(0xfff2f4f8) else Color(0xff182230), fontSize = 20.sp)
            OutlinedTextField(value, { value = it }, Modifier.fillMaxWidth().padding(top = 12.dp), singleLine = true, label = { Text("条码内容") })
            ComposeChoiceField(formats[selectedIndex].first, formats.map { it.first }, dark) { choice ->
                selectedIndex = formats.indexOfFirst { it.first == choice }.coerceAtLeast(0)
            }
            Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.End) {
                DialogAction("取消", dark, dismiss)
                DialogAction("删除", dark, {
                    dismiss()
                    showComposeConfirmDialog("删除条目", "确定删除此条码吗？", "删除") {
                        items.removeAll { it.id == item.id }
                        favoriteGroups.forEach { group -> group.itemIds.removeAll { id -> id == item.id } }
                        saveAllFavorites()
                        render()
                    }
                }, modifier = Modifier.padding(start = 8.dp))
                DialogAction("保存", dark, {
                    val text = value
                    if (text.isBlank()) toast("请输入条码内容")
                    else {
                        item.text = text
                        item.format = formats[selectedIndex].first
                        saveItems()
                        dismiss()
                        render()
                    }
                }, modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

internal fun MainActivity.moveToFolderCompose(item: CodeItem) {
    showComposeDialog(compact = false, metricsLabel = null) { dismiss ->
        val dark = isDark()
        var value by remember { mutableStateOf(item.folder) }
        ComposeGlassDialogCard(dark) {
            Text("移动到文件夹", color = if (dark) Color(0xfff2f4f8) else Color(0xff182230), fontSize = 20.sp)
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                singleLine = true,
                label = { Text("文件夹") },
                placeholder = { Text("例如：工作、商品、旅行") },
            )
            Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.End) {
                DialogAction("取消", dark, dismiss)
                DialogAction("保存", dark, {
                    val folder = value.trim().ifEmpty { "默认" }
                    if (!isValidFavoriteFolderPath(folder)) toast("文件夹最多支持一级和二级，且名称不能包含斜杠")
                    else {
                        item.folder = folder
                        saveItems()
                        dismiss()
                        render()
                    }
                }, modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

private fun isValidFavoriteFolderPath(value: String): Boolean {
    val parts = value.split('/')
    return parts.size in 1..2 && parts.all { part ->
        part.isNotBlank() && part != "." && part != ".." && !part.contains('\\')
    }
}

internal fun MainActivity.confirmClearCompose(favoritesOnly: Boolean) {
    showComposeConfirmDialog(
        title = if (favoritesOnly) "清空收藏" else "清空历史",
        message = if (favoritesOnly) "确定删除全部收藏吗？" else "仅清空历史记录，收藏内容不会删除。",
        positive = "删除",
    ) {
        if (favoritesOnly) {
            favoriteGroups.clear()
            items.forEach { it.favorite = false; it.folder = "默认" }
            saveAllFavorites()
        } else {
            items.forEach { it.inHistory = false }
            saveItems()
        }
        render()
    }
}

internal fun MainActivity.saveResultAsFavoriteCompose() {
    if (resultItems.isEmpty()) return
    val editingGroup = selectedFavoriteGroup?.takeIf { resultsReturnPage == "favorites" }
    val folders = (favoriteFolders + favoriteGroups.map { it.folder }).filter { it.isNotBlank() }.distinct().toMutableList()
    showComposeDialog(compact = false, metricsLabel = null) { dismiss ->
        val dark = isDark()
        val roots = folders.map { it.substringBefore('/') }.distinct().sorted()
        var selectedRoot by remember { mutableStateOf(editingGroup?.folder?.substringBefore('/').takeIf { it in roots }.orEmpty()) }
        var selectedChild by remember { mutableStateOf(editingGroup?.folder.orEmpty().substringAfter('/', "").takeIf { it.isNotBlank() }.orEmpty()) }
        val childOptions = folders.filter { it.startsWith("$selectedRoot/") }.map { it.removePrefix("$selectedRoot/") }.filter { !it.contains('/') }.distinct().sorted()
        val selectedFolder = if (selectedRoot.isNotBlank() && selectedChild.isNotBlank()) "$selectedRoot/$selectedChild" else ""
        var name by remember { mutableStateOf(editingGroup?.name.orEmpty()) }
        fun persistFavorite(target: FavoriteGroup?, folder: String, cleanName: String) {
            val savedAt = System.currentTimeMillis()
            resultItems.forEach { it.favorite = true; it.folder = folder }
            if (folder !in favoriteFolders) favoriteFolders.add(folder)
            if (target == null) favoriteGroups.add(0, FavoriteGroup(nextGroupId(), folder, cleanName, savedAt, resultItems.map { it.id }.toMutableList()))
            else {
                val index = favoriteGroups.indexOfFirst { it.id == target.id }
                if (index >= 0) favoriteGroups[index] = FavoriteGroup(target.id, folder, cleanName, savedAt, resultItems.map { it.id }.toMutableList())
            }
            items.filter { it.favorite && favoriteGroups.none { group -> group.itemIds.contains(it.id) } }.forEach { it.favorite = false }
            saveAllFavorites()
            selectedFavoriteGroup = null
            page = "favorites"
            render()
            toast("已保存到 " + folder)
        }
        ComposeGlassDialogCard(dark) {
            Text(
                if (editingGroup == null) "保存到收藏" else "编辑收藏",
                color = if (dark) Color(0xfff2f4f8) else Color(0xff182230),
                fontSize = 20.sp,
            )
            Text("先选择一级文件夹，再选择二级文件夹", color = if (dark) Color(0xffaeb9c9) else Color(0xff6b7280), fontSize = 14.sp, modifier = Modifier.padding(top = 12.dp))
            if (roots.isEmpty()) {
                Text("暂无一级文件夹，请先新建", color = if (dark) Color(0xffc5cedb) else Color(0xff667085), fontSize = 15.sp, modifier = Modifier.padding(top = 8.dp))
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ComposeChoiceField(
                        value = selectedRoot.ifBlank { "选择一级文件夹" },
                        options = roots,
                        dark = dark,
                        onSelected = { selectedRoot = it; selectedChild = "" },
                    )
                    ComposeChoiceField(
                        value = selectedChild.ifBlank { "选择二级文件夹" },
                        options = childOptions,
                        dark = dark,
                        enabled = selectedRoot.isNotBlank() && childOptions.isNotEmpty(),
                        onSelected = { selectedChild = it },
                    )
                }
                Text(
                    "当前位置：" + selectedRoot.ifBlank { "未选择一级文件夹" } +
                        if (selectedChild.isNotBlank()) " / $selectedChild" else "",
                    color = if (dark) Color(0xffaeb9c9) else Color(0xff667085),
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 6.dp),
                )
                if (selectedRoot.isNotBlank() && childOptions.isEmpty()) Text("该一级文件夹暂无二级文件夹，请先新建", color = if (dark) Color(0xffc5cedb) else Color(0xff667085), fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DialogAction(
                    "新建一级文件夹",
                    dark,
                    {
                        showFolderEditorCompose { folder ->
                            if (folder !in folders) folders.add(folder)
                            if (folder !in favoriteFolders) favoriteFolders.add(folder)
                            selectedRoot = folder
                            selectedChild = ""
                            saveFavoriteFolders()
                        }
                    },
                    modifier = Modifier.weight(1f),
                )
                DialogAction(
                    "新建二级文件夹",
                    dark,
                    {
                        if (selectedRoot.isBlank()) toast("请先选择一级文件夹")
                        else showSubfolderEditorCompose(selectedRoot) { child ->
                            val path = "$selectedRoot/$child"
                            if (path !in folders) folders.add(path)
                            selectedChild = child
                            saveFavoriteFolders()
                        }
                    },
                    modifier = Modifier.weight(1f),
                )
            }
            Text("收藏文件名", color = if (dark) Color(0xffaeb9c9) else Color(0xff6b7280), fontSize = 14.sp, modifier = Modifier.padding(top = 12.dp))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                singleLine = true,
                label = { Text("收藏文件名") },
            )
            Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.End) {
                DialogAction("取消", dark, dismiss)
                DialogAction("保存", dark, {
                    val cleanName = name.trim()
                    when {
                        cleanName.isEmpty() -> toast("请输入收藏文件名")
                        selectedFolder.isBlank() -> toast("请选择文件夹")
                        else -> {
                            val conflict = favoriteGroups.firstOrNull {
                                it.id != editingGroup?.id && it.folder == selectedFolder && it.name == cleanName
                            }
                            if (conflict == null) {
                                dismiss()
                                persistFavorite(editingGroup, selectedFolder, cleanName)
                            } else {
                                dismiss()
                                showComposeConfirmDialog(
                                    "覆盖收藏",
                                    "“" + selectedFolder + "/" + cleanName + "”已存在，是否覆盖？",
                                    "覆盖",
                                ) {
                                    if (editingGroup != null && editingGroup.id != conflict.id) favoriteGroups.removeAll { it.id == editingGroup.id }
                                    persistFavorite(conflict, selectedFolder, cleanName)
                                }
                            }
                        }
                    }
                }, modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}
