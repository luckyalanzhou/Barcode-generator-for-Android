package com.luckyalanzhou.barcodegenerator.ui.app.favorites

import com.luckyalanzhou.barcodegenerator.MainActivity
import com.luckyalanzhou.barcodegenerator.presentation.BarcodeDataState
import com.luckyalanzhou.barcodegenerator.presentation.ResultUiState
import com.luckyalanzhou.barcodegenerator.ui.app.*
import com.luckyalanzhou.barcodegenerator.ui.app.platform.showComposeConfirmDialog
import com.luckyalanzhou.barcodegenerator.ui.support.logging.DebugLog
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * 显示结果收藏对话框；表单负责交互，Activity 持有异步保存、Dialog 关闭和导航。
 */
internal fun MainActivity.saveResultAsFavoriteCompose(
    resultState: ResultUiState,
    dataState: BarcodeDataState,
    findExistingGroupId: (String, String, Set<Long>) -> Long?,
    onSave: suspend (List<Long>, Long?, Long?, String, String) -> Boolean,
    onCreateFolder: suspend (String) -> Boolean,
    onSaved: () -> Unit,
) {
    var saveInProgress = false
    showComposeDialog(compact = false, canCancel = { !saveInProgress }) { dismiss ->
        val dark = isDark()
        if (resultState.items.isEmpty()) {
            LaunchedEffect(Unit) { dismiss() }
            return@showComposeDialog
        }
        var saving by remember { mutableStateOf(false) }

        fun persistFavorite(request: FavoriteSaveRequest, onCommitted: () -> Unit) {
            if (saving) return
            saving = true
            saveInProgress = true
            // Activity 生命周期持有等待任务：覆盖确认 Dialog 时不会取消保存。
            lifecycleScope.launch {
                try {
                    val saved = onSave(
                        request.itemIds,
                        request.editingGroupId,
                        request.targetGroupId,
                        request.folder,
                        request.name,
                    )
                    if (saved) onCommitted()
                    else toast("该文件夹下已有同名收藏，或保存失败；请检查名称后重试")
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    DebugLog.record("favorites", "保存收藏提交失败", error)
                    toast("收藏保存失败，输入已保留，请重试")
                } finally {
                    saving = false
                    saveInProgress = false
                }
            }
        }

        fun finishSave(folder: String) {
            // 先关闭原生 Dialog，再在下一帧切换根路由，避免在 Compose 点击回调
            // 仍在执行时同时替换宿主组合树。
            dismiss()
            window.decorView.post {
                if (!isFinishing && !isDestroyed) {
                    toast("已保存到 $folder")
                    onSaved()
                }
            }
        }

        FavoriteSaveForm(
            resultState = resultState,
            dataState = dataState,
            dark = dark,
            saving = saving,
            findExistingGroupId = findExistingGroupId,
            onSaveRequest = { request ->
                persistFavorite(request) { finishSave(request.folder) }
            },
            onOpenRootFolderEditor = { onFolderCreated ->
                showFolderEditorCompose(dataState) { folder ->
                    if (onCreateFolder(folder)) {
                        onFolderCreated(folder)
                        true
                    } else {
                        false
                    }
                }
            },
            onOpenChildFolderEditor = { root, onFolderCreated ->
                showSubfolderEditorCompose(
                    dataState = dataState,
                    parent = root,
                    onCreated = onFolderCreated,
                    onCreateFolder = onCreateFolder,
                )
            },
            onShowOverwriteConfirmation = { title, message, onConfirm ->
                showComposeConfirmDialog(title, message, "覆盖", onConfirm)
            },
            onValidationMessage = { toast(it) },
            onCancel = { if (!saving) dismiss() },
        )
    }
}
