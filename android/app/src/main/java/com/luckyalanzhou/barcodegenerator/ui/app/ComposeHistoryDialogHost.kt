package com.luckyalanzhou.barcodegenerator.ui.app

import com.luckyalanzhou.barcodegenerator.MainActivity
import com.luckyalanzhou.barcodegenerator.domain.CodeItem
import com.luckyalanzhou.barcodegenerator.ui.feature.history.ClearHistoryDialogContent
import com.luckyalanzhou.barcodegenerator.ui.feature.history.HistoryBatchPickerDialogContent

/** 承载历史记录弹窗并提供功能回调，不向功能页面暴露应用级状态。 */
internal fun MainActivity.showHistoryBatchPickerCompose(
    batch: List<CodeItem>,
    onEdit: (CodeItem) -> Unit,
) {
    showComposeDialog(compact = false) { dismiss ->
        HistoryBatchPickerDialogContent(
            batch = batch,
            dark = isDark(),
            onDismiss = dismiss,
            onEdit = { item -> window.decorView.post { onEdit(item) } },
        )
    }
}

internal fun MainActivity.showClearHistoryConfirmCompose(onConfirm: () -> Unit) {
    showComposeDialog(compact = false) { dismiss ->
        ClearHistoryDialogContent(
            dark = isDark(),
            onDismiss = dismiss,
            onConfirm = onConfirm,
        )
    }
}
