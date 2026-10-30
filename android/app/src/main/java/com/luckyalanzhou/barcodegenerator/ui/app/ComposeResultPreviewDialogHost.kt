package com.luckyalanzhou.barcodegenerator.ui.app

import com.luckyalanzhou.barcodegenerator.MainActivity
import com.luckyalanzhou.barcodegenerator.barcodeFormats
import com.luckyalanzhou.barcodegenerator.domain.CodeItem
import com.luckyalanzhou.barcodegenerator.ui.feature.results.preview.PreviewDialogContent
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

/** 负责接入预览生成和 Android 媒体操作的应用层边界。 */
internal fun MainActivity.previewCompose(item: CodeItem) {
    val formatId = barcodeFormats.firstOrNull { it.displayName == item.format }?.id ?: run {
        toast("不支持的条码格式")
        return
    }
    val style = settingsViewModel.style.copy()
    val dark = isDark()
    val density = resources.displayMetrics.density
    lifecycleScope.launch {
        val bitmap = resultsViewModel.createBarcodePreviewImage(
            item.text,
            formatId,
            style,
            dark,
            density,
        ) ?: run {
            toast("内容不符合该格式")
            return@launch
        }
        var transferredToDialog = false
        try {
            if (isFinishing || isDestroyed) return@launch
            showComposeDialog(compact = false) { dismiss ->
                PreviewDialogContent(
                    format = item.format,
                    text = item.text,
                    bitmap = bitmap,
                    dark = dark,
                    onSave = { saveBitmap(bitmap, item.text); dismiss() },
                    onShare = { shareBitmap(bitmap, item.text); dismiss() },
                    onDismiss = dismiss,
                )
            }
            transferredToDialog = true
        } finally {
            if (!transferredToDialog && !bitmap.isRecycled) bitmap.recycle()
        }
    }
}
