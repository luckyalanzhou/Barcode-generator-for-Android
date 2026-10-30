package com.luckyalanzhou.barcodegenerator.ui.app

import com.luckyalanzhou.barcodegenerator.ui.app.*
import com.luckyalanzhou.barcodegenerator.ui.dialogs.*

import com.luckyalanzhou.barcodegenerator.ui.theme.*

import com.luckyalanzhou.barcodegenerator.BarcodeImageColors
import com.luckyalanzhou.barcodegenerator.barcodeFormats
import com.luckyalanzhou.barcodegenerator.BarcodeFormatIds
import com.luckyalanzhou.barcodegenerator.MainActivity
import com.luckyalanzhou.barcodegenerator.ui.support.logging.DebugLog
import com.luckyalanzhou.barcodegenerator.ui.feature.results.ResultExportAction
import com.luckyalanzhou.barcodegenerator.ui.feature.results.startExclusiveResultExport
import com.luckyalanzhou.barcodegenerator.ui.feature.results.composeResultRowImage
import com.luckyalanzhou.barcodegenerator.ui.feature.results.consumeCompleteExportBatch
import com.luckyalanzhou.barcodegenerator.ui.feature.results.resultExportDimensions
import com.luckyalanzhou.barcodegenerator.ui.feature.results.resultImageRowSize
import com.luckyalanzhou.barcodegenerator.ui.feature.results.resultImageSpacing
import com.luckyalanzhou.barcodegenerator.ui.feature.results.resultPageBackground

import android.graphics.Bitmap
import android.graphics.Canvas
import android.content.Intent
import java.io.File
import androidx.core.graphics.createBitmap
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale

private data class ResultPageImage(val bitmap: Bitmap, val label: String)

/** Android 无法启动系统文件选择器时，回滚待处理文件状态。 */
internal fun launchPendingDocumentFile(
    file: File,
    setPendingFile: (File?) -> Unit,
    launch: () -> Boolean,
): Boolean {
    setPendingFile(file)
    var launched = false
    try {
        launched = launch()
        return launched
    } finally {
        if (!launched) {
            setPendingFile(null)
            file.delete()
        }
    }
}

/** 导出准备结束时，仅在系统文件选择器没有接管临时文件时释放忙碌状态。 */
internal fun finishResultExportPreparation(
    pendingDocumentFile: File?,
    releaseExport: () -> Unit,
) {
    if (pendingDocumentFile == null) releaseExport()
}

/** 系统文件选择请求结束后统一删除源文件并释放导出状态。 */
internal fun finishPendingResultImageDocument(
    file: File?,
    releaseExport: () -> Unit,
) {
    try {
        file?.delete()
    } finally {
        releaseExport()
    }
}

/** Activity 重建时，根据仍等待系统选择器返回的文件恢复导出忙碌状态。 */
internal fun restorePendingResultExportAction(
    hasPendingDocumentFile: Boolean,
    restoreAction: (ResultExportAction?) -> Unit,
) {
    if (hasPendingDocumentFile) restoreAction(ResultExportAction.Save)
}

/** 为当前结果批次生成统一的合成图片。 */
private suspend fun MainActivity.createResultPageImage(): ResultPageImage? =
    handoffCancellableResource(
        prepare = {
            val resultItems = resultsViewModel.resultUiState.value.items.toList()
    val style = settingsViewModel.style.copy()
    val dark = isDark()
    val density = resources.displayMetrics.density
    val fontScale = resources.configuration.fontScale
    // 使用测得的视口宽度，以适配横屏、分屏和窗口 Insets。
    val contentWidth = resultImageContentWidthPx.takeIf { it > 0 } ?: return@handoffCancellableResource null
    withContext(Dispatchers.Default) {
        if (resultItems.isEmpty()) return@withContext null
        val rowSizes = resultItems.map { item ->
            resultImageRowSize(item, style, contentWidth, density, fontScale)
        }
        val spacing = resultImageSpacing(style.margin, density)
        val outerPadding = (8 * density).toInt().coerceAtLeast(0)
        val pageSize = resultExportDimensions(
            rowWidths = rowSizes.map { it.width },
            rowHeights = rowSizes.map { it.height },
            spacing = spacing,
            outerPadding = outerPadding,
        ) ?: return@withContext null

        val pageImage = createBitmap(pageSize.width, pageSize.height, Bitmap.Config.ARGB_8888)
        var completed = false
        try {
            val canvas = Canvas(pageImage)
            canvas.drawColor(resultPageBackground(dark))
            var top = outerPadding
            val batchComplete = consumeCompleteExportBatch(
                items = resultItems,
                render = { item ->
                    resultsViewModel.createBarcodeImage(
                        item.text,
                        barcodeFormats.firstOrNull { it.displayName == item.format }?.id ?: BarcodeFormatIds.CODE_128,
                        style,
                        dark,
                        density,
                        // 深色结果页保留浅色条码静区，供外部扫描枪稳定识别。
                        withBackground = dark,
                    )?.let { bitmap -> item to bitmap }
                },
                consume = { (item, barcode) ->
                    val row = composeResultRowImage(barcode, item, style, dark, contentWidth, density, fontScale)
                    try {
                        canvas.drawBitmap(row, (pageSize.width - row.width) / 2f, top.toFloat(), null)
                        top += row.height + spacing
                    } finally {
                        row.recycle()
                    }
                },
                release = { (_, barcode) -> if (!barcode.isRecycled) barcode.recycle() },
            )
            if (!batchComplete) return@withContext null
            completed = true
            ResultPageImage(pageImage, "本页生成的 ${resultItems.size} 个条码")
        } finally {
            if (!completed && !pageImage.isRecycled) pageImage.recycle()
        }
    }
        },
        release = { it.recycle() },
    )

/** 在 UI 线程外准备图片；分享与保存共用同一份完整快照，任一步骤失败都不输出半成品。 */
private fun MainActivity.withResultPageImage(action: ResultExportAction, onReady: (ResultPageImage) -> Unit) {
    if (preparingResultExport) return
    resultExportAction = action
    lifecycleScope.launch {
        var sharingStarted = false
        DebugLog.actionStarted("result_image_prepare", "action=$action")
        try {
            val result = createResultPageImage()
            if (result == null) {
                DebugLog.record("results", "result_image_prepare failed reason=empty_or_invalid_layout action=$action")
                toast("条码图片准备失败，请重试")
            } else {
                DebugLog.actionSucceeded("result_image_prepare", "width=${result.bitmap.width} height=${result.bitmap.height}")
                onReady(result)
                // 准备完成后，由 PNG 编码和系统分享面板接管忙碌状态。
                sharingStarted = action == ResultExportAction.Share
            }
        } catch (error: CancellationException) {
            DebugLog.record("results", "result_image_prepare cancelled action=$action")
            throw error
        } catch (error: OutOfMemoryError) {
            DebugLog.actionFailed("result_image_prepare", error, "action=$action")
            toast("图片占用内存过大，请减少条码数量后重试")
        } catch (error: Exception) {
            DebugLog.actionFailed("result_image_prepare", error, "action=$action")
            toast("条码图片准备失败，请重试")
        } finally {
            if (!sharingStarted) resultExportAction = null
        }
    }
}

/** 分享操作打开系统分享面板，不再额外弹出应用目标选择器。 */
internal fun MainActivity.shareResultPage() = withResultPageImage(ResultExportAction.Share) { result ->
    shareBitmap(result.bitmap, result.label, onStarted = { resultExportAction = ResultExportAction.Share },
        onFinished = {
            result.recycle()
            resultExportAction = null
        })
}

/** 保存入口保持独立且易于发现，不在系统分享流程中增加额外步骤。 */
internal fun MainActivity.saveResultPage() = withResultPageImage(ResultExportAction.Save) { result ->
    var handedOffToSave = false
    showComposeDialog(compact = true) { dismiss ->
        DisposableEffect(result) {
            onDispose { if (!handedOffToSave) result.recycle() }
        }
        val dark = isDark()
        ComposeGlassDialogCard(dark) {
            Text("保存条码", color = LocalAppColorScheme.current.text.primary, fontSize = 18.sp)
            Text(result.label, color = LocalAppColorScheme.current.text.secondary, fontSize = 14.sp,
                modifier = Modifier.padding(top = 6.dp))
            Image(result.bitmap.asImageBitmap(), "待保存的条码图片", contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth().heightIn(max = 160.dp).padding(top = 10.dp))
            DialogAction("保存到相册", dark, {
                val started = startExclusiveResultExport(
                    currentAction = resultExportAction,
                    requestedAction = ResultExportAction.Save,
                    updateAction = { resultExportAction = it },
                ) { finish ->
                    saveBitmap(result.bitmap, result.label) {
                        result.recycle()
                        finish()
                    }
                }
                if (started) {
                    handedOffToSave = true
                    dismiss()
                }
            }, modifier = Modifier.fillMaxWidth().padding(top = 10.dp))
            DialogAction("保存到文件", dark, {
                handedOffToSave = true
                dismiss()
                saveResultImageDocument(result)
            }, modifier = Modifier.fillMaxWidth().padding(top = 10.dp))
        }
    }
}

private fun MainActivity.saveResultImageDocument(result: ResultPageImage) {
    if (pendingResultImageFile != null || preparingResultExport) {
        result.recycle()
        return
    }
    resultExportAction = ResultExportAction.Save
    lifecycleScope.launch {
        var temporary: File? = null
        try {
            val file = withContext(Dispatchers.IO) {
                File.createTempFile("result-export-", ".png", cacheDir).also {
                    temporary = it
                    it.outputStream().use { output ->
                        check(result.bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
                    }
                }
            }
            val launched = launchPendingDocumentFile(file, { pendingResultImageFile = it }) {
                launchExternalActivity(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    type = "image/png"
                    putExtra(Intent.EXTRA_TITLE, shareImageFileName(result.label))
                    addCategory(Intent.CATEGORY_OPENABLE)
                }, MainActivity.REQUEST_RESULT_IMAGE_FILE)
            }
            if (!launched) DebugLog.record("results", "result_image_document launch failed")
        } catch (error: CancellationException) {
            temporary?.delete()
            pendingResultImageFile = null
            throw error
        } catch (error: Exception) {
            DebugLog.actionFailed("result_image_document_prepare", error)
            temporary?.delete()
            pendingResultImageFile = null
            toast("准备文件失败，请重试")
        } finally {
            result.recycle()
            finishResultExportPreparation(pendingResultImageFile) { resultExportAction = null }
        }
    }
}

/** 结果页合成图由当前导出流程独占，结束后立即释放大位图的 native 像素内存。 */
private fun ResultPageImage.recycle() {
    if (!bitmap.isRecycled) bitmap.recycle()
}
