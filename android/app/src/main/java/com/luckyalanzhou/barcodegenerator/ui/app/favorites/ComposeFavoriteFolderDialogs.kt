package com.luckyalanzhou.barcodegenerator.ui.app.favorites

import com.luckyalanzhou.barcodegenerator.R
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
import android.view.Window

/** 文件夹编辑 Compose 弹窗，校验规则与原编辑器一致。 */
internal fun MainActivity.showFolderEditorCompose(
    dataState: BarcodeDataState,
    initial: String = "",
    backdropSourceWindow: Window? = null,
    onSaved: suspend (String) -> Boolean,
) {
    var operationInProgress = false
    showComposeDialog(
        compact = false,
        canCancel = { !operationInProgress },
        backdropSourceWindow = backdropSourceWindow,
    ) { dismiss ->
        val dark = isDark()
        var value by remember { mutableStateOf(favoriteFolderEditorInitialValue(initial)) }
        var saving by remember { mutableStateOf(false) }
        ComposeGlassDialogCard(dark) {
            Text(
                if (initial.isBlank()) getString(R.string.folder_create) else getString(R.string.folder_rename),
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
                label = { Text(getString(R.string.folder_name), color = LocalAppColorScheme.current.text.secondary) },
            )
            Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.End) {
                DialogAction(getString(R.string.action_cancel), dark, { if (!saving) dismiss() })
                DialogAction(if (saving) getString(R.string.saving) else getString(R.string.action_save), dark, {
                    val name = value.trim()
                    val targetPath = favoriteFolderRenameTargetPath(initial, name)
                    val knownFolderPaths = dataState.folders + dataState.groups.map { it.folder }
                    when {
                        name.isBlank() -> toast(getString(R.string.folder_name_required))
                        !isValidFavoriteFolderName(name) -> toast(getString(R.string.folder_name_slash))
                        !isValidFavoriteFolderPath(targetPath) -> toast(getString(R.string.favorite_invalid_folder))
                        knownFolderPaths.any { it == targetPath && it != initial } -> toast(getString(R.string.folder_name_duplicate))
                        initial.isNotBlank() && !isSafeFavoriteFolderRename(initial, targetPath, knownFolderPaths) ->
                            toast(getString(R.string.folder_destination_invalid))
                        !saving -> {
                            saving = true
                            operationInProgress = true
                            lifecycleScope.launch {
                                try {
                                    if (onSaved(name)) dismiss()
                                    else toast(getString(R.string.folder_save_check_name))
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (error: Exception) {
                                    DebugLog.record("favorites", "收藏文件夹保存失败", error)
                                    toast(getString(R.string.folder_save_failed))
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
internal fun MainActivity.showSubfolderEditorCompose(
    dataState: BarcodeDataState,
    parent: String,
    onCreated: ((String) -> Unit)? = null,
    backdropSourceWindow: Window? = null,
    onCreateFolder: suspend (String) -> Boolean,
) {
    var operationInProgress = false
    showComposeDialog(
        compact = false,
        canCancel = { !operationInProgress },
        backdropSourceWindow = backdropSourceWindow,
    ) { dismiss ->
        val dark = isDark()
        var value by remember { mutableStateOf("") }
        var saving by remember { mutableStateOf(false) }
        ComposeGlassDialogCard(dark) {
            Text(getString(R.string.folder_create), color = LocalAppColorScheme.current.text.primary, fontSize = 18.sp)
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
                label = { Text(getString(R.string.folder_name), color = LocalAppColorScheme.current.text.secondary) },
            )
            Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.End) {
                DialogAction(getString(R.string.action_cancel), dark, { if (!saving) dismiss() })
                DialogAction(if (saving) getString(R.string.saving) else getString(R.string.action_save), dark, {
                    val child = value.trim()
                    val path = "$parent/$child"
                    when {
                        child.isBlank() -> toast(getString(R.string.folder_name_required))
                        child.contains('/') -> toast(getString(R.string.name_slash))
                        path in dataState.folders || dataState.groups.any { it.folder == path } ->
                            toast(getString(R.string.folder_name_duplicate))
                        !saving -> {
                            saving = true
                            operationInProgress = true
                            lifecycleScope.launch {
                                try {
                                    if (onCreateFolder(path)) {
                                        onCreated?.invoke(child)
                                        dismiss()
                                    } else {
                                        toast(getString(R.string.folder_create_check_name))
                                    }
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (error: Exception) {
                                    DebugLog.record("favorites", "创建收藏文件夹失败", error)
                                    toast(getString(R.string.folder_create_failed))
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
