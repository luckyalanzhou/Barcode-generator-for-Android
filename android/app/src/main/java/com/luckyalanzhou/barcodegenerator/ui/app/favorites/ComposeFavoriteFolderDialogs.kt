package com.luckyalanzhou.barcodegenerator.ui.app.favorites

import com.luckyalanzhou.barcodegenerator.presentation.*
import com.luckyalanzhou.barcodegenerator.ui.app.*
import com.luckyalanzhou.barcodegenerator.ui.dialogs.DialogAction
import com.luckyalanzhou.barcodegenerator.ui.dialogs.ComposeGlassDialogCard

import com.luckyalanzhou.barcodegenerator.ui.theme.*

import com.luckyalanzhou.barcodegenerator.MainActivity

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import com.luckyalanzhou.barcodegenerator.presentation.BarcodeDataState
import com.luckyalanzhou.barcodegenerator.presentation.favorites.favoriteFolderEditorInitialValue
import com.luckyalanzhou.barcodegenerator.presentation.favorites.isSafeFavoriteFolderRename
import com.luckyalanzhou.barcodegenerator.presentation.favorites.isValidFavoriteFolderName
import com.luckyalanzhou.barcodegenerator.ui.feature.favorites.favoriteFolderRenameTargetPath
import com.luckyalanzhou.barcodegenerator.ui.feature.favorites.isValidFavoriteFolderPath
import com.luckyalanzhou.barcodegenerator.ui.support.logging.DebugLog

/** 文件夹编辑 Compose 弹窗，校验规则与原编辑器一致。 */
internal fun MainActivity.showFolderEditorCompose(dataState: BarcodeDataState, initial: String = "", onSaved: suspend (String) -> Boolean) {
    var operationInProgress = false
    showComposeDialog(compact = false, canCancel = { !operationInProgress }) { dismiss ->
        val dark = isDark()
        var value by remember { mutableStateOf(favoriteFolderEditorInitialValue(initial)) }
        var saving by remember { mutableStateOf(false) }
        ComposeGlassDialogCard(dark) {
            Text(
                if (initial.isBlank()) "新建文件夹" else "重命名文件夹",
                color = LocalAppColorScheme.current.text.primary,
                fontSize = 18.sp,
            )
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
                    cursorColor = LocalAppColorScheme.current.text.primary,
                ),
                label = { Text("文件夹名称", color = LocalAppColorScheme.current.text.secondary) },
            )
            Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.End) {
                DialogAction("取消", dark, { if (!saving) dismiss() })
                DialogAction(if (saving) "保存中…" else "保存", dark, {
                    val name = value.trim()
                    val targetPath = favoriteFolderRenameTargetPath(initial, name)
                    val knownFolderPaths = dataState.folders + dataState.groups.map { it.folder }
                    when {
                        name.isBlank() -> toast("请输入文件夹名称")
                        !isValidFavoriteFolderName(name) -> toast("文件夹名称不能包含斜杠")
                        !isValidFavoriteFolderPath(targetPath) -> toast("文件夹最多支持一级和二级，且名称不能包含斜杠")
                        knownFolderPaths.any { it == targetPath && it != initial } -> toast("已存在同名文件夹")
                        initial.isNotBlank() && !isSafeFavoriteFolderRename(initial, targetPath, knownFolderPaths) ->
                            toast("目标文件夹已存在或层级不合法")
                        !saving -> {
                            saving = true
                            operationInProgress = true
                            lifecycleScope.launch {
                                try {
                                    if (onSaved(name)) dismiss()
                                    else toast("文件夹保存失败，请检查名称后重试")
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (error: Exception) {
                                    DebugLog.record("favorites", "收藏文件夹保存失败", error)
                                    toast("文件夹保存失败，请重试")
                                } finally {
                                    saving = false
                                    operationInProgress = false
                                }
                            }
                        }
                    }
                }, modifier = Modifier.padding(start = 20.dp))
            }
        }
    }
}

/** 新建二级文件夹 Compose 弹窗，保持原有斜杠和重名校验。 */
internal fun MainActivity.showSubfolderEditorCompose(dataState: BarcodeDataState, parent: String, onCreated: ((String) -> Unit)? = null, onCreateFolder: suspend (String) -> Boolean) {
    var operationInProgress = false
    showComposeDialog(compact = false, canCancel = { !operationInProgress }) { dismiss ->
        val dark = isDark()
        var value by remember { mutableStateOf("") }
        var saving by remember { mutableStateOf(false) }
        ComposeGlassDialogCard(dark) {
            Text("新建文件夹", color = LocalAppColorScheme.current.text.primary, fontSize = 18.sp)
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
                    cursorColor = LocalAppColorScheme.current.text.primary,
                ),
                label = { Text("文件夹名称", color = LocalAppColorScheme.current.text.secondary) },
            )
            Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.End) {
                DialogAction("取消", dark, { if (!saving) dismiss() })
                DialogAction(if (saving) "保存中…" else "保存", dark, {
                    val child = value.trim()
                    val path = "$parent/$child"
                    when {
                        child.isBlank() -> toast("请输入文件夹名称")
                        child.contains('/') -> toast("名称不能包含斜杠")
                        path in dataState.folders || dataState.groups.any { it.folder == path } ->
                            toast("已存在同名文件夹")
                        !saving -> {
                            saving = true
                            operationInProgress = true
                            lifecycleScope.launch {
                                try {
                                    if (onCreateFolder(path)) {
                                        onCreated?.invoke(child)
                                        dismiss()
                                    } else {
                                        toast("创建文件夹失败，请检查名称后重试")
                                    }
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (error: Exception) {
                                    DebugLog.record("favorites", "创建收藏文件夹失败", error)
                                    toast("创建文件夹失败，请重试")
                                } finally {
                                    saving = false
                                    operationInProgress = false
                                }
                            }
                        }
                    }
                }, modifier = Modifier.padding(start = 20.dp))
            }
        }
    }
}
