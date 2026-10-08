package com.luckyalanzhou.barcodegenerator.ui.app

import com.luckyalanzhou.barcodegenerator.MainActivity
import com.luckyalanzhou.barcodegenerator.barcodeFormats
import com.luckyalanzhou.barcodegenerator.domain.CodeItem
import com.luckyalanzhou.barcodegenerator.ui.feature.results.preview.PreviewDialogContent

/** Application boundary for preview generation and Android media actions. */
internal fun MainActivity.previewCompose(item: CodeItem) {
    val formatId = barcodeFormats.firstOrNull { it.displayName == item.format }?.id ?: run {
        toast("不支持的条码格式")
        return
    }
    val bitmap = resultsViewModel.createBarcodeImage(
        item.text,
        formatId,
        settingsViewModel.style,
        isDark(),
        resources.displayMetrics.density,
    ) ?: run {
        toast("内容不符合该格式")
        return
    }
    showComposeDialog(compact = false) { dismiss ->
        PreviewDialogContent(
            format = item.format,
            text = item.text,
            bitmap = bitmap,
            dark = isDark(),
            onSave = { saveBitmap(bitmap, item.text); dismiss() },
            onShare = { shareBitmap(bitmap, item.text); dismiss() },
            onDismiss = dismiss,
        )
    }
}
