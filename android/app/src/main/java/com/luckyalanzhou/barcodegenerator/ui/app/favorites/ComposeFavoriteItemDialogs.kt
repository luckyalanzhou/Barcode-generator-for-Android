package com.luckyalanzhou.barcodegenerator.ui.app.favorites

import com.luckyalanzhou.barcodegenerator.R
import com.luckyalanzhou.barcodegenerator.presentation.*
import com.luckyalanzhou.barcodegenerator.ui.app.*
import com.luckyalanzhou.barcodegenerator.ui.dialogs.*
import com.luckyalanzhou.barcodegenerator.ui.app.platform.showComposeDurableConfirmDialog
import com.luckyalanzhou.barcodegenerator.ui.app.platform.showIos26NoticeDialogCompose
import com.luckyalanzhou.barcodegenerator.ui.feature.editor.ComposeChoiceField
import com.luckyalanzhou.barcodegenerator.ui.feature.favorites.favoriteFolderChildren
import com.luckyalanzhou.barcodegenerator.ui.feature.favorites.favoriteFolderRoots
import com.luckyalanzhou.barcodegenerator.ui.feature.favorites.favoriteFolderDestination
import com.luckyalanzhou.barcodegenerator.ui.feature.favorites.isValidFavoriteFolderPath
import com.luckyalanzhou.barcodegenerator.ui.support.logging.DebugLog

import com.luckyalanzhou.barcodegenerator.ui.theme.*

import com.luckyalanzhou.barcodegenerator.MainActivity
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
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

internal fun MainActivity.showFavoriteRenameDialogCompose(group: FavoriteGroup, onRename: suspend (Long, String) -> Boolean, onNavigateFavorites: () -> Unit) {
    var operationInProgress = false
    showComposeDialog(compact = false, canCancel = { !operationInProgress }) { dismiss ->
        val dark = isDark()
        var value by remember { mutableStateOf(group.name) }
        var renaming by remember { mutableStateOf(false) }
        ComposeGlassDialogCard(dark) {
            Text(getString(R.string.favorite_rename), color = LocalAppColorScheme.current.text.primary, fontSize = 18.sp)
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
                label = { Text(getString(R.string.favorite_filename), color = LocalAppColorScheme.current.text.primary) },
            )
            Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.End) {
                DialogAction(getString(R.string.action_cancel), dark, { if (!renaming) dismiss() })
                DialogAction(if (renaming) getString(R.string.saving) else getString(R.string.action_save), dark, {
                    val name = value.trim()
                    if (name.isBlank()) toast(getString(R.string.favorite_filename_required))
                    else if (!renaming) {
                        renaming = true
                        operationInProgress = true
                        lifecycleScope.launch {
                            try {
                                if (onRename(group.id, name)) {
                                    dismiss()
                                    onNavigateFavorites()
                                } else {
                                    toast(getString(R.string.favorite_duplicate_name))
                                }
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (error: Exception) {
                                DebugLog.record("favorites", "重命名收藏提交失败", error)
                                toast(getString(R.string.favorite_rename_failed))
                            } finally {
                                renaming = false
                                operationInProgress = false
                            }
                        }
                    }
                }, modifier = Modifier.padding(start = 20.dp))
            }
        }
    }
}

internal fun MainActivity.showFavoriteMoveDialogCompose(group: FavoriteGroup, dataState: com.luckyalanzhou.barcodegenerator.presentation.BarcodeDataState, onMove: suspend (Long, String) -> Boolean, onNavigateFavorites: () -> Unit) {
    var operationInProgress = false
    showComposeDialog(compact = false, canCancel = { !operationInProgress }) { dismiss ->
        val dark = isDark()
        var moving by remember { mutableStateOf(false) }
        val folderPaths = (dataState.folders + dataState.groups.map { it.folder })
            .filter { it.isNotBlank() }
            .distinct()
        val roots = favoriteFolderRoots(folderPaths)
        if (roots.isEmpty()) {
            LaunchedEffect(Unit) {
                dismiss()
                showIos26NoticeDialogCompose(getString(R.string.favorite_create_folder_first))
            }
            return@showComposeDialog
        }
        var selectedRoot by remember {
            mutableStateOf(group.folder.substringBefore('/').takeIf { it in roots } ?: roots.firstOrNull().orEmpty())
        }
        var selectedChild by remember {
            mutableStateOf(group.folder.substringAfter('/', "").takeIf {
                it in favoriteFolderChildren(folderPaths, selectedRoot)
            }.orEmpty())
        }
        val childOptions = favoriteFolderChildren(folderPaths, selectedRoot)
        val targetFolder = favoriteFolderDestination(selectedRoot, selectedChild)
        ComposeGlassDialogCard(dark) {
            Text(getString(R.string.favorite_move), color = LocalAppColorScheme.current.text.primary, fontSize = 18.sp)
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(getString(R.string.favorite_root_folder_step), color = LocalAppColorScheme.current.text.primary, fontSize = 14.sp)
                ComposeChoiceField(
                    value = selectedRoot,
                    options = roots,
                    dark = dark,
                    onSelected = { root ->
                        selectedRoot = root
                        selectedChild = ""
                    },
                )
                Text(getString(R.string.favorite_nested_folder_optional), color = LocalAppColorScheme.current.text.primary, fontSize = 14.sp)
                ComposeChoiceField(
                    value = selectedChild,
                    options = listOf("") + childOptions,
                    dark = dark,
                    enabled = true,
                    optionLabel = { it.ifBlank { getString(R.string.favorite_root_only) } },
                    onSelected = { selectedChild = it },
                )
                Text(
                    getString(R.string.favorite_move_destination, targetFolder),
                    color = LocalAppColorScheme.current.text.secondary,
                    fontSize = 13.sp,
                )
            }
            Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.End) {
                DialogAction(getString(R.string.action_cancel), dark, { if (!moving) dismiss() })
                DialogAction(if (moving) getString(R.string.moving) else getString(R.string.action_move), dark, {
                    if (!moving) {
                        moving = true
                        operationInProgress = true
                        lifecycleScope.launch {
                            try {
                                if (onMove(group.id, targetFolder)) {
                                    dismiss()
                                    onNavigateFavorites()
                                } else {
                                    toast(getString(R.string.favorite_destination_duplicate))
                                }
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (error: Exception) {
                                DebugLog.record("favorites", "移动收藏提交失败", error)
                                toast(getString(R.string.favorite_move_failed))
                            } finally {
                                moving = false
                                operationInProgress = false
                            }
                        }
                    }
                }, modifier = Modifier.padding(start = 20.dp))
            }
        }
    }
}

internal fun MainActivity.showGroupEditorCompose(
    group: FavoriteGroup,
    onDelete: suspend (Long) -> Unit,
    onClearSelection: () -> Unit,
    onUpdate: (Long, String, String) -> Boolean,
    onNavigateFavorites: () -> Unit,
) {
    showComposeDialog(compact = false) { dismiss ->
        val dark = isDark()
        var name by remember { mutableStateOf(group.name) }
        var folder by remember { mutableStateOf(group.folder) }
        ComposeGlassDialogCard(dark) {
            Text(getString(R.string.favorite_edit), color = LocalAppColorScheme.current.text.primary, fontSize = 18.sp)
            OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth().padding(top = 12.dp), singleLine = true, shape = RoundedCornerShape(8.dp), colors = OutlinedTextFieldDefaults.colors(focusedTextColor = LocalAppColorScheme.current.text.primary, unfocusedTextColor = LocalAppColorScheme.current.text.primary, focusedLabelColor = LocalAppColorScheme.current.text.primary, unfocusedLabelColor = LocalAppColorScheme.current.text.primary, cursorColor = LocalAppColorScheme.current.text.primary), label = { Text(getString(R.string.favorite_filename), color = LocalAppColorScheme.current.text.primary) })
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
                label = { Text(getString(R.string.folder), color = LocalAppColorScheme.current.text.secondary) },
            )
            Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.End) {
                DialogAction(getString(R.string.action_cancel), dark, dismiss)
                DialogAction(getString(R.string.action_delete), dark, {
                    dismiss()
                    showComposeDurableConfirmDialog(getString(R.string.favorite_delete), getString(R.string.favorite_delete_confirm, group.name), getString(R.string.action_delete)) {
                        onDelete(group.id)
                        onClearSelection()
                        onNavigateFavorites()
                    }
                }, modifier = Modifier.padding(start = 20.dp), destructive = true)
                DialogAction(getString(R.string.action_save), dark, {
                    val cleanName = name.trim()
                    val cleanFolder = folder.trim().ifEmpty { "默认" }
                    if (cleanName.isBlank()) toast(getString(R.string.favorite_filename_required))
                    else if (!isValidFavoriteFolderPath(cleanFolder)) toast(getString(R.string.favorite_invalid_folder))
                    else {
                        if (onUpdate(group.id, cleanName, cleanFolder)) dismiss()
                        else toast(getString(R.string.favorite_duplicate_name_or_folder))
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
            Text(getString(R.string.favorite_move_to_folder), color = LocalAppColorScheme.current.text.primary, fontSize = 18.sp)
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
                    focusedPlaceholderColor = LocalAppColorScheme.current.text.secondary,
                    unfocusedPlaceholderColor = LocalAppColorScheme.current.text.secondary,
                    cursorColor = LocalAppColorScheme.current.text.primary,
                ),
                label = { Text(getString(R.string.folder), color = LocalAppColorScheme.current.text.secondary) },
                placeholder = { Text(getString(R.string.favorite_folder_examples), color = LocalAppColorScheme.current.text.secondary) },
            )
            Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.End) {
                DialogAction(getString(R.string.action_cancel), dark, dismiss)
                DialogAction(getString(R.string.action_save), dark, {
                    val folder = value.trim().ifEmpty { "默认" }
                    if (!isValidFavoriteFolderPath(folder)) toast(getString(R.string.favorite_invalid_folder))
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
