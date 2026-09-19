package com.luckyalanzhou.barcodegenerator.ui

import com.luckyalanzhou.barcodegenerator.*
import com.luckyalanzhou.barcodegenerator.data.*
import com.luckyalanzhou.barcodegenerator.domain.*

import com.luckyalanzhou.barcodegenerator.ui.AppRoute
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Column
import com.luckyalanzhou.barcodegenerator.icons.CreateNewFolderIcon
import com.luckyalanzhou.barcodegenerator.icons.FolderIcon
import com.luckyalanzhou.barcodegenerator.icons.KeyboardArrowDownIcon
import com.luckyalanzhou.barcodegenerator.icons.KeyboardArrowRightIcon

/** 文件夹编辑 Compose 弹窗，校验规则与原编辑器一致。 */
internal fun MainActivity.showFolderEditorCompose(initial: String = "", showMetrics: Boolean = false, onSaved: (String) -> Unit) {
    showComposeDialog(compact = false, metricsLabel = if (showMetrics) "文件夹编辑弹窗" else null) { dismiss ->
        val dark = isDark()
        var value by remember { mutableStateOf(initial) }
        ComposeGlassDialogCard(dark) {
            Text(
                if (initial.isBlank()) "新建文件夹" else "重命名文件夹",
                color = LocalBarcodeThemeColors.current.primary,
                fontSize = 18.sp,
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
                        viewModel.dataState.value.folders.any { it == targetPath && it != initial } -> toast("已存在同名文件夹")
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
            Text("新建文件夹", color = LocalBarcodeThemeColors.current.primary, fontSize = 18.sp)
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
                        path in viewModel.dataState.value.folders -> toast("已存在同名文件夹")
                        else -> {
                            viewModel.createFavoriteFolder(path)
                            onCreated?.invoke(child)
                            dismiss()
                        }
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
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onSelected: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var buttonWidth by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    Box(modifier) {
        OutlinedButton(
            onClick = { if (enabled) expanded = true },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth().onGloballyPositioned { buttonWidth = it.size.width },
            shape = RoundedCornerShape(8.dp),
        ) {
            Text(value, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        AnchoredDropdownMenu(
            dark = dark,
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = RoundedCornerShape(16.dp),
            containerColor = LocalBarcodeThemeColors.current.surfaceOverlay,
            tonalElevation = 0.dp,
            shadowElevation = 1.dp,
            menuWidth = buttonWidth.takeIf { it > 0 }?.let { with(density) { it.toDp() } },
        ) {
            options.forEachIndexed { index, option ->
                if (index > 0) ComposeDropdownDivider(dark)
                DropdownMenuItem(
                    modifier = Modifier.height(40.dp),
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
            Text("重命名收藏", color = LocalBarcodeThemeColors.current.primary, fontSize = 18.sp)
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                singleLine = true,
                shape = RoundedCornerShape(8.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = LocalBarcodeThemeColors.current.primary,
                    unfocusedTextColor = LocalBarcodeThemeColors.current.primary,
                    focusedLabelColor = LocalBarcodeThemeColors.current.primary,
                    unfocusedLabelColor = LocalBarcodeThemeColors.current.primary,
                    cursorColor = LocalBarcodeThemeColors.current.primary,
                ),
                label = { Text("收藏文件名", color = LocalBarcodeThemeColors.current.primary) },
            )
            Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.End) {
                DialogAction("取消", dark, dismiss)
                DialogAction("保存", dark, {
                    val name = value.trim()
                    if (name.isBlank()) toast("请输入收藏文件名")
                    else {
                        viewModel.renameFavoriteGroupAndPersist(group.id, name)
                        dismiss()
                        viewModel.navigateTo(AppRoute.Favorites)
                    }
                }, modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

internal fun MainActivity.showFavoriteMoveDialogCompose(group: FavoriteGroup) {
    val folders = viewModel.dataState.value.folders.filter { it.isNotBlank() }
    if (folders.isEmpty()) {
        showIos26NoticeDialogCompose("请先创建文件夹")
        return
    }
    showComposeDialog(compact = false, metricsLabel = null) { dismiss ->
        val dark = isDark()
        var selected by remember { mutableStateOf(group.folder.takeIf { it in folders } ?: folders.first()) }
        ComposeGlassDialogCard(dark) {
            Text("移动收藏", color = LocalBarcodeThemeColors.current.primary, fontSize = 18.sp)
            ComposeChoiceField(selected, folders, dark, onSelected = { selected = it })
            Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.End) {
                DialogAction("取消", dark, dismiss)
                DialogAction("移动", dark, {
                    viewModel.moveFavoriteGroupAndPersist(group.id, selected)
                    dismiss()
                    viewModel.navigateTo(AppRoute.Favorites)
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
            Text("编辑收藏", color = LocalBarcodeThemeColors.current.primary, fontSize = 18.sp)
            OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth().padding(top = 12.dp), singleLine = true, shape = RoundedCornerShape(8.dp), colors = OutlinedTextFieldDefaults.colors(focusedTextColor = LocalBarcodeThemeColors.current.primary, unfocusedTextColor = LocalBarcodeThemeColors.current.primary, focusedLabelColor = LocalBarcodeThemeColors.current.primary, unfocusedLabelColor = LocalBarcodeThemeColors.current.primary, cursorColor = LocalBarcodeThemeColors.current.primary), label = { Text("收藏文件名", color = LocalBarcodeThemeColors.current.primary) })
            OutlinedTextField(folder, { folder = it }, Modifier.fillMaxWidth().padding(top = 10.dp), singleLine = true, label = { Text("文件夹") })
            Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.End) {
                DialogAction("取消", dark, dismiss)
                DialogAction("删除", dark, {
                    dismiss()
                    showComposeConfirmDialog("删除收藏", "确定删除“${group.name}”吗？", "删除") {
                        viewModel.deleteFavoriteGroupAndPersist(group.id)
                        viewModel.clearSelectedFavoriteGroup()
                        viewModel.navigateTo(AppRoute.Favorites)
                    }
                }, modifier = Modifier.padding(start = 8.dp))
                DialogAction("保存", dark, {
                    val cleanName = name.trim()
                    val cleanFolder = folder.trim().ifEmpty { "默认" }
                    if (cleanName.isBlank()) toast("请输入收藏文件名")
                    else if (!isValidFavoriteFolderPath(cleanFolder)) toast("文件夹最多支持一级和二级，且名称不能包含斜杠")
                    else {
                        viewModel.updateFavoriteGroupAndPersist(group.id, cleanName, cleanFolder)
                        dismiss()
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
        var selectedIndex by remember { mutableIntStateOf(barcodeFormats.indexOfFirst { it.first == item.format }.coerceAtLeast(0)) }
        ComposeGlassDialogCard(dark) {
            Text("编辑条目", color = LocalBarcodeThemeColors.current.primary, fontSize = 18.sp)
            OutlinedTextField(value, { value = it }, Modifier.fillMaxWidth().padding(top = 12.dp), singleLine = true, label = { Text("条码内容") })
            ComposeChoiceField(barcodeFormats[selectedIndex].first, barcodeFormats.map { it.first }, dark) { choice ->
                selectedIndex = barcodeFormats.indexOfFirst { it.first == choice }.coerceAtLeast(0)
            }
            Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.End) {
                DialogAction("取消", dark, dismiss)
                DialogAction("删除", dark, {
                    dismiss()
                    showComposeConfirmDialog("删除条目", "确定删除此条码吗？", "删除") {
                        viewModel.deleteBarcodeItem(item.id)
                    }
                }, modifier = Modifier.padding(start = 8.dp))
                DialogAction("保存", dark, {
                    val text = value
                    if (text.isBlank()) toast("请输入条码内容")
                    else {
                        viewModel.updateBarcodeItem(item.id, text, barcodeFormats[selectedIndex].first)
                        dismiss()
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
            Text("移动到文件夹", color = LocalBarcodeThemeColors.current.primary, fontSize = 18.sp)
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
                        viewModel.persistItems()
                        dismiss()
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

private fun MainActivity.showClearHistoryConfirmCompose(onConfirm: () -> Unit) {
    showComposeDialog(compact = false, metricsLabel = null) { dismiss ->
        val dark = isDark()
        ComposeGlassDialogCard(dark) {
            Text(
                "一键清空历史记录",
                color = LocalBarcodeThemeColors.current.primary,
                fontSize = 18.sp,
            )
            Row(
                Modifier.fillMaxWidth().padding(top = 16.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                DialogAction("取消", dark, dismiss)
                DialogAction(
                    "确定",
                    dark,
                    { onConfirm(); dismiss() },
                    modifier = Modifier.padding(start = 8.dp),
                    destructive = true,
                )
            }
        }
    }
}

internal fun MainActivity.confirmClearCompose(favoritesOnly: Boolean) {
    if (!favoritesOnly) {
        showClearHistoryConfirmCompose {
            viewModel.clearHistoryAndPersist()
        }
        return
    }
    showComposeConfirmDialog(
        title = "清空收藏",
        message = "确定删除全部收藏吗？",
        positive = "删除",
    ) {
        viewModel.clearFavoritesAndPersist()
    }
}

internal fun MainActivity.saveResultAsFavoriteCompose() {
    val resultState = viewModel.resultUiState.value
    if (resultState.items.isEmpty()) return
    val editingGroup = resultState.selectedFavoriteGroup?.takeIf { resultState.returnPage == AppRoute.Favorites }
    val dataState = viewModel.dataState.value
    val folders = (dataState.folders + dataState.groups.map { it.folder }).filter { it.isNotBlank() }.distinct().toMutableList()
    showComposeDialog(compact = false, metricsLabel = null) { dismiss ->
        val dark = isDark()
        val roots = folders.map { it.substringBefore('/') }.distinct().sorted()
        var selectedRoot by remember { mutableStateOf(editingGroup?.folder?.substringBefore('/').takeIf { it in roots }.orEmpty()) }
        var selectedChild by remember { mutableStateOf(editingGroup?.folder.orEmpty().substringAfter('/', "").takeIf { it.isNotBlank() }.orEmpty()) }
        val childOptions = folders.filter { it.startsWith("$selectedRoot/") }.map { it.removePrefix("$selectedRoot/") }.filter { !it.contains('/') }.distinct().sorted()
        val selectedFolder = if (selectedRoot.isNotBlank() && selectedChild.isNotBlank()) "$selectedRoot/$selectedChild" else ""
        var name by remember { mutableStateOf(editingGroup?.name.orEmpty()) }
        fun persistFavorite(target: FavoriteGroup?, folder: String, cleanName: String) {
            viewModel.saveResultAsFavorite(
                resultItemIds = resultState.items.map { it.id },
                editingGroupId = editingGroup?.id,
                targetGroupId = target?.id,
                folder = folder,
                name = cleanName,
            )
            toast("已保存到 " + folder)
        }
        ComposeGlassDialogCard(dark) {
            Text(
                if (editingGroup == null) "保存到收藏" else "编辑收藏",
                color = LocalBarcodeThemeColors.current.primary,
                fontSize = 18.sp,
            )
            Text("选择收藏保存位置", color = LocalBarcodeThemeColors.current.secondary, fontSize = 14.sp, modifier = Modifier.padding(top = 12.dp))
            if (roots.isEmpty()) {
                Text("暂无一级文件夹，请先新建", color = LocalBarcodeThemeColors.current.secondary, fontSize = 15.sp, modifier = Modifier.padding(top = 8.dp))
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Icon(FolderIcon, "一级文件夹", tint = LocalBarcodeThemeColors.current.folder, modifier = Modifier.size(24.dp))
                        Text("① 一级文件夹", color = LocalBarcodeThemeColors.current.primary, fontSize = 16.sp, modifier = Modifier.padding(start = 8.dp))
                    }
                    ComposeChoiceField(
                        value = selectedRoot.ifBlank { "选择一级文件夹" },
                        options = roots,
                        dark = dark,
                        modifier = Modifier.padding(start = 32.dp),
                        onSelected = { selectedRoot = it; selectedChild = "" },
                    )
                    Row(
                        modifier = Modifier.padding(start = 12.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    ) {
                        Box(Modifier.width(20.dp).height(28.dp)) {
                            Box(
                                Modifier.padding(start = 7.dp).width(2.dp).height(28.dp)
                                    .background(LocalBarcodeThemeColors.current.button),
                            )
                        }
                        Icon(FolderIcon, "二级文件夹", tint = LocalBarcodeThemeColors.current.childFolder, modifier = Modifier.size(24.dp))
                        Text("② 二级文件夹", color = LocalBarcodeThemeColors.current.primary, fontSize = 16.sp, modifier = Modifier.padding(start = 8.dp))
                    }
                    ComposeChoiceField(
                        value = selectedChild.ifBlank { "选择二级文件夹" },
                        options = childOptions,
                        dark = dark,
                        modifier = Modifier.padding(start = 52.dp),
                        enabled = selectedRoot.isNotBlank() && childOptions.isNotEmpty(),
                        onSelected = { selectedChild = it },
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                        .background(LocalBarcodeThemeColors.current.input, RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("当前位置", color = LocalBarcodeThemeColors.current.secondary, fontSize = 12.sp)
                        Row(Modifier.padding(top = 4.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            if (selectedRoot.isBlank()) {
                                Text("未选择一级文件夹", color = LocalBarcodeThemeColors.current.secondary, fontSize = 14.sp)
                            } else {
                                Icon(FolderIcon, "当前一级文件夹", tint = LocalBarcodeThemeColors.current.accent, modifier = Modifier.size(18.dp))
                                Text(selectedRoot, color = LocalBarcodeThemeColors.current.primary, fontSize = 14.sp, modifier = Modifier.padding(start = 4.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (selectedChild.isNotBlank()) {
                                    Icon(KeyboardArrowRightIcon, "层级", tint = LocalBarcodeThemeColors.current.secondary, modifier = Modifier.size(20.dp).padding(horizontal = 2.dp))
                                    Icon(FolderIcon, "当前二级文件夹", tint = LocalBarcodeThemeColors.current.accent, modifier = Modifier.size(18.dp))
                                    Text(selectedChild, color = LocalBarcodeThemeColors.current.primary, fontSize = 14.sp, modifier = Modifier.padding(start = 4.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                    Icon(KeyboardArrowDownIcon, "展开层级", tint = LocalBarcodeThemeColors.current.secondary, modifier = Modifier.size(20.dp))
                }
                if (selectedRoot.isNotBlank() && childOptions.isEmpty()) Text("该一级文件夹暂无二级文件夹，请先新建", color = LocalBarcodeThemeColors.current.secondary, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = {
                        showFolderEditorCompose { folder ->
                            if (folder !in folders) folders.add(folder)
                            viewModel.createFavoriteFolder(folder)
                            selectedRoot = folder
                            selectedChild = ""
                        }
                    },
                    modifier = Modifier.weight(1f).height(40.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Icon(CreateNewFolderIcon, "新建一级文件夹", modifier = Modifier.size(20.dp))
                    Text("新建一级文件夹", fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp), maxLines = 1)
                }
                OutlinedButton(
                    onClick = {
                        if (selectedRoot.isBlank()) toast("请先选择一级文件夹")
                        else showSubfolderEditorCompose(selectedRoot) { child ->
                            val path = "$selectedRoot/$child"
                            if (path !in folders) folders.add(path)
                            selectedChild = child
                        }
                    },
                    modifier = Modifier.weight(1f).height(40.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Icon(CreateNewFolderIcon, "新建二级文件夹", modifier = Modifier.size(20.dp))
                    Text("新建二级文件夹", fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp), maxLines = 1)
                }
            }
            Text("收藏文件名", color = LocalBarcodeThemeColors.current.primary, fontSize = 14.sp, modifier = Modifier.padding(top = 12.dp))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                singleLine = true,
                shape = RoundedCornerShape(8.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = LocalBarcodeThemeColors.current.primary,
                    unfocusedTextColor = LocalBarcodeThemeColors.current.primary,
                    focusedLabelColor = LocalBarcodeThemeColors.current.primary,
                    unfocusedLabelColor = LocalBarcodeThemeColors.current.primary,
                    cursorColor = LocalBarcodeThemeColors.current.primary,
                ),
                label = { Text("收藏文件名", color = LocalBarcodeThemeColors.current.primary) },
            )
            Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.End) {
                DialogAction("取消", dark, dismiss)
                DialogAction("保存", dark, {
                    val cleanName = name.trim()
                    when {
                        cleanName.isEmpty() -> toast("请输入收藏文件名")
                        selectedFolder.isBlank() -> toast("请选择文件夹")
                        else -> {
                            val conflict = viewModel.dataState.value.groups.firstOrNull {
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
