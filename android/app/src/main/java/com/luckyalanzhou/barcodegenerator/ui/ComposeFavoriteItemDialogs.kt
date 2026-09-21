package com.luckyalanzhou.barcodegenerator.ui

import com.luckyalanzhou.barcodegenerator.MainActivity
import com.luckyalanzhou.barcodegenerator.barcodeFormats
import com.luckyalanzhou.barcodegenerator.domain.CodeItem
import com.luckyalanzhou.barcodegenerator.domain.FavoriteGroup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

internal fun MainActivity.showFavoriteRenameDialogCompose(group: FavoriteGroup) {
    showComposeDialog(compact = false) { dismiss ->
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
                        composeAppShellActions().navigateTo(AppRoute.Favorites)
                    }
                }, modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

internal fun MainActivity.showFavoriteMoveDialogCompose(group: FavoriteGroup) {
    showComposeDialog(compact = false) { dismiss ->
        val dark = isDark()
        val dataState by viewModel.dataState.collectAsStateWithLifecycle()
        val folders = dataState.folders.filter { it.isNotBlank() }
        if (folders.isEmpty()) {
            LaunchedEffect(Unit) {
                dismiss()
                showIos26NoticeDialogCompose("请先创建文件夹")
            }
            return@showComposeDialog
        }
        var selected by remember { mutableStateOf(group.folder.takeIf { it in folders } ?: folders.first()) }
        ComposeGlassDialogCard(dark) {
            Text("移动收藏", color = LocalBarcodeThemeColors.current.primary, fontSize = 18.sp)
            ComposeChoiceField(selected, folders, dark, onSelected = { selected = it })
            Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.End) {
                DialogAction("取消", dark, dismiss)
                DialogAction("移动", dark, {
                    viewModel.moveFavoriteGroupAndPersist(group.id, selected)
                    dismiss()
                    composeAppShellActions().navigateTo(AppRoute.Favorites)
                }, modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

internal fun MainActivity.showGroupEditorCompose(group: FavoriteGroup) {
    showComposeDialog(compact = false) { dismiss ->
        val dark = isDark()
        var name by remember { mutableStateOf(group.name) }
        var folder by remember { mutableStateOf(group.folder) }
        ComposeGlassDialogCard(dark) {
            Text("编辑收藏", color = LocalBarcodeThemeColors.current.primary, fontSize = 18.sp)
            OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth().padding(top = 12.dp), singleLine = true, shape = RoundedCornerShape(8.dp), colors = OutlinedTextFieldDefaults.colors(focusedTextColor = LocalBarcodeThemeColors.current.primary, unfocusedTextColor = LocalBarcodeThemeColors.current.primary, focusedLabelColor = LocalBarcodeThemeColors.current.primary, unfocusedLabelColor = LocalBarcodeThemeColors.current.primary, cursorColor = LocalBarcodeThemeColors.current.primary), label = { Text("收藏文件名", color = LocalBarcodeThemeColors.current.primary) })
            OutlinedTextField(
                folder,
                { folder = it },
                Modifier.fillMaxWidth().padding(top = 10.dp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = LocalBarcodeThemeColors.current.primary,
                    unfocusedTextColor = LocalBarcodeThemeColors.current.primary,
                    focusedLabelColor = LocalBarcodeThemeColors.current.primary,
                    unfocusedLabelColor = LocalBarcodeThemeColors.current.secondary,
                    cursorColor = LocalBarcodeThemeColors.current.primary,
                ),
                label = { Text("文件夹", color = LocalBarcodeThemeColors.current.secondary) },
            )
            Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.End) {
                DialogAction("取消", dark, dismiss)
                DialogAction("删除", dark, {
                    dismiss()
                    showComposeConfirmDialog("删除收藏", "确定删除“${group.name}”吗？", "删除") {
                        viewModel.deleteFavoriteGroupAndPersist(group.id)
                        viewModel.clearSelectedFavoriteGroup()
                        composeAppShellActions().navigateTo(AppRoute.Favorites)
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
    showComposeDialog(compact = false) { dismiss ->
        val dark = isDark()
        var value by remember { mutableStateOf(item.text) }
        var selectedIndex by remember { mutableIntStateOf(barcodeFormats.indexOfFirst { it.first == item.format }.coerceAtLeast(0)) }
        ComposeGlassDialogCard(dark) {
            Text("编辑条目", color = LocalBarcodeThemeColors.current.primary, fontSize = 18.sp)
            OutlinedTextField(
                value,
                { value = it },
                Modifier.fillMaxWidth().padding(top = 12.dp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = LocalBarcodeThemeColors.current.primary,
                    unfocusedTextColor = LocalBarcodeThemeColors.current.primary,
                    focusedLabelColor = LocalBarcodeThemeColors.current.primary,
                    unfocusedLabelColor = LocalBarcodeThemeColors.current.secondary,
                    focusedPlaceholderColor = LocalBarcodeThemeColors.current.secondary,
                    unfocusedPlaceholderColor = LocalBarcodeThemeColors.current.secondary,
                    cursorColor = LocalBarcodeThemeColors.current.primary,
                ),
                label = { Text("条码内容", color = LocalBarcodeThemeColors.current.secondary) },
            )
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
    showComposeDialog(compact = false) { dismiss ->
        val dark = isDark()
        var value by remember { mutableStateOf(item.folder) }
        ComposeGlassDialogCard(dark) {
            Text("移动到文件夹", color = LocalBarcodeThemeColors.current.primary, fontSize = 18.sp)
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = LocalBarcodeThemeColors.current.primary,
                    unfocusedTextColor = LocalBarcodeThemeColors.current.primary,
                    focusedLabelColor = LocalBarcodeThemeColors.current.primary,
                    unfocusedLabelColor = LocalBarcodeThemeColors.current.secondary,
                    focusedPlaceholderColor = LocalBarcodeThemeColors.current.secondary,
                    unfocusedPlaceholderColor = LocalBarcodeThemeColors.current.secondary,
                    cursorColor = LocalBarcodeThemeColors.current.primary,
                ),
                label = { Text("文件夹", color = LocalBarcodeThemeColors.current.secondary) },
                placeholder = { Text("例如：工作、商品、旅行", color = LocalBarcodeThemeColors.current.secondary) },
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
