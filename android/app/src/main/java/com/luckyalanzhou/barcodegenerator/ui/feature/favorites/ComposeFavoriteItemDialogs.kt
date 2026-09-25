package com.luckyalanzhou.barcodegenerator.ui.feature.favorites

import com.luckyalanzhou.barcodegenerator.presentation.*
import com.luckyalanzhou.barcodegenerator.ui.app.*
import com.luckyalanzhou.barcodegenerator.ui.dialogs.*
import com.luckyalanzhou.barcodegenerator.ui.feature.editor.ComposeChoiceField

import com.luckyalanzhou.barcodegenerator.ui.theme.*

import com.luckyalanzhou.barcodegenerator.MainActivity
import com.luckyalanzhou.barcodegenerator.barcodeFormats
import com.luckyalanzhou.barcodegenerator.domain.CodeItem
import com.luckyalanzhou.barcodegenerator.domain.FavoriteGroup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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

internal fun MainActivity.showFavoriteRenameDialogCompose(group: FavoriteGroup, onRename: (Long, String) -> Boolean, onNavigateFavorites: () -> Unit) {
    showComposeDialog(compact = false) { dismiss ->
        val dark = isDark()
        var value by remember { mutableStateOf(group.name) }
        ComposeGlassDialogCard(dark) {
            Text("重命名收藏", color = LocalAppColorScheme.current.text.primary, fontSize = 18.sp)
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                singleLine = true,
                shape = RoundedCornerShape(8.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = LocalAppColorScheme.current.text.primary,
                    unfocusedTextColor = LocalAppColorScheme.current.text.primary,
                    focusedLabelColor = LocalAppColorScheme.current.text.primary,
                    unfocusedLabelColor = LocalAppColorScheme.current.text.primary,
                    cursorColor = LocalAppColorScheme.current.text.primary,
                ),
                label = { Text("收藏文件名", color = LocalAppColorScheme.current.text.primary) },
            )
            Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.End) {
                DialogAction("取消", dark, dismiss)
                DialogAction("保存", dark, {
                    val name = value.trim()
                    if (name.isBlank()) toast("请输入收藏文件名")
                    else {
                        if (onRename(group.id, name)) {
                            dismiss()
                            onNavigateFavorites()
                        } else {
                            toast("该文件夹下已有同名收藏，请更换名称")
                        }
                    }
                }, modifier = Modifier.padding(start = 20.dp))
            }
        }
    }
}

internal fun MainActivity.showFavoriteMoveDialogCompose(group: FavoriteGroup, dataState: com.luckyalanzhou.barcodegenerator.presentation.BarcodeDataState, onMove: (Long, String) -> Boolean, onNavigateFavorites: () -> Unit) {
    showComposeDialog(compact = false) { dismiss ->
        val dark = isDark()
        val folderPaths = (dataState.folders + dataState.groups.map { it.folder })
            .filter { it.isNotBlank() }
            .distinct()
        val roots = favoriteFolderRoots(folderPaths)
        if (roots.isEmpty()) {
            LaunchedEffect(Unit) {
                dismiss()
                showIos26NoticeDialogCompose("请先创建文件夹")
            }
            return@showComposeDialog
        }
        var selectedRoot by remember {
            mutableStateOf(group.folder.substringBefore('/').takeIf { it in roots } ?: roots.first())
        }
        var selectedChild by remember {
            mutableStateOf(group.folder.substringAfter('/', "").takeIf {
                it in favoriteFolderChildren(folderPaths, selectedRoot)
            }.orEmpty())
        }
        val childOptions = favoriteFolderChildren(folderPaths, selectedRoot)
        val targetFolder = favoriteMoveDestination(selectedRoot, selectedChild)
        ComposeGlassDialogCard(dark) {
            Text("移动收藏", color = LocalAppColorScheme.current.text.primary, fontSize = 18.sp)
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("① 一级文件夹", color = LocalAppColorScheme.current.text.primary, fontSize = 14.sp)
                ComposeChoiceField(
                    value = selectedRoot,
                    options = roots,
                    dark = dark,
                    onSelected = { root ->
                        selectedRoot = root
                        selectedChild = ""
                    },
                )
                Text("② 二级文件夹（可选）", color = LocalAppColorScheme.current.text.primary, fontSize = 14.sp)
                ComposeChoiceField(
                    value = selectedChild.ifBlank { FAVORITE_ROOT_ONLY_OPTION },
                    options = listOf(FAVORITE_ROOT_ONLY_OPTION) + childOptions,
                    dark = dark,
                    enabled = true,
                    onSelected = { child ->
                        selectedChild = child.takeUnless { it == FAVORITE_ROOT_ONLY_OPTION }.orEmpty()
                    },
                )
                Text(
                    "目标位置：$targetFolder",
                    color = LocalAppColorScheme.current.text.secondary,
                    fontSize = 13.sp,
                )
            }
            Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.End) {
                DialogAction("取消", dark, dismiss)
                DialogAction("移动", dark, {
                    if (onMove(group.id, targetFolder)) {
                        dismiss()
                        onNavigateFavorites()
                    } else {
                        toast("目标文件夹下已有同名收藏，请先重命名或更换文件夹")
                    }
                }, modifier = Modifier.padding(start = 20.dp))
            }
        }
    }
}

internal fun MainActivity.showGroupEditorCompose(
    group: FavoriteGroup,
    onDelete: (Long) -> Unit,
    onClearSelection: () -> Unit,
    onUpdate: (Long, String, String) -> Boolean,
    onNavigateFavorites: () -> Unit,
) {
    showComposeDialog(compact = false) { dismiss ->
        val dark = isDark()
        var name by remember { mutableStateOf(group.name) }
        var folder by remember { mutableStateOf(group.folder) }
        ComposeGlassDialogCard(dark) {
            Text("编辑收藏", color = LocalAppColorScheme.current.text.primary, fontSize = 18.sp)
            OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth().padding(top = 12.dp), singleLine = true, shape = RoundedCornerShape(8.dp), colors = OutlinedTextFieldDefaults.colors(focusedTextColor = LocalAppColorScheme.current.text.primary, unfocusedTextColor = LocalAppColorScheme.current.text.primary, focusedLabelColor = LocalAppColorScheme.current.text.primary, unfocusedLabelColor = LocalAppColorScheme.current.text.primary, cursorColor = LocalAppColorScheme.current.text.primary), label = { Text("收藏文件名", color = LocalAppColorScheme.current.text.primary) })
            OutlinedTextField(
                folder,
                { folder = it },
                Modifier.fillMaxWidth().padding(top = 10.dp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = LocalAppColorScheme.current.text.primary,
                    unfocusedTextColor = LocalAppColorScheme.current.text.primary,
                    focusedLabelColor = LocalAppColorScheme.current.text.primary,
                    unfocusedLabelColor = LocalAppColorScheme.current.text.secondary,
                    cursorColor = LocalAppColorScheme.current.text.primary,
                ),
                label = { Text("文件夹", color = LocalAppColorScheme.current.text.secondary) },
            )
            Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.End) {
                DialogAction("取消", dark, dismiss)
                DialogAction("删除", dark, {
                    dismiss()
                    showComposeConfirmDialog("删除收藏", "确定删除“${group.name}”吗？", "删除") {
                        onDelete(group.id)
                        onClearSelection()
                        onNavigateFavorites()
                    }
                }, modifier = Modifier.padding(start = 20.dp))
                DialogAction("保存", dark, {
                    val cleanName = name.trim()
                    val cleanFolder = folder.trim().ifEmpty { "默认" }
                    if (cleanName.isBlank()) toast("请输入收藏文件名")
                    else if (!isValidFavoriteFolderPath(cleanFolder)) toast("文件夹最多支持一级和二级，且名称不能包含斜杠")
                    else {
                        if (onUpdate(group.id, cleanName, cleanFolder)) dismiss()
                        else toast("该文件夹下已有同名收藏，请更换名称或文件夹")
                    }
                }, modifier = Modifier.padding(start = 20.dp))
            }
        }
    }
}

internal fun MainActivity.moveToFolderCompose(item: CodeItem, onPersist: (CodeItem) -> Unit) {
    showComposeDialog(compact = false) { dismiss ->
        val dark = isDark()
        var value by remember { mutableStateOf(item.folder) }
        ComposeGlassDialogCard(dark) {
            Text("移动到文件夹", color = LocalAppColorScheme.current.text.primary, fontSize = 18.sp)
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = LocalAppColorScheme.current.text.primary,
                    unfocusedTextColor = LocalAppColorScheme.current.text.primary,
                    focusedLabelColor = LocalAppColorScheme.current.text.primary,
                    unfocusedLabelColor = LocalAppColorScheme.current.text.secondary,
                    focusedPlaceholderColor = LocalAppColorScheme.current.text.placeholder,
                    unfocusedPlaceholderColor = LocalAppColorScheme.current.text.placeholder,
                    cursorColor = LocalAppColorScheme.current.text.primary,
                ),
                label = { Text("文件夹", color = LocalAppColorScheme.current.text.secondary) },
                placeholder = { Text("例如：工作、商品、旅行", color = LocalAppColorScheme.current.text.placeholder) },
            )
            Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.End) {
                DialogAction("取消", dark, dismiss)
                DialogAction("保存", dark, {
                    val folder = value.trim().ifEmpty { "默认" }
                    if (!isValidFavoriteFolderPath(folder)) toast("文件夹最多支持一级和二级，且名称不能包含斜杠")
                    else {
                        item.folder = folder
                        onPersist(item)
                        dismiss()
                    }
                }, modifier = Modifier.padding(start = 20.dp))
            }
        }
    }
}
