package com.luckyalanzhou.barcodegenerator.ui.app

import com.luckyalanzhou.barcodegenerator.ui.app.*
import com.luckyalanzhou.barcodegenerator.ui.dialogs.*

import com.luckyalanzhou.barcodegenerator.ui.theme.*

import com.luckyalanzhou.barcodegenerator.BarcodeImageColors
import com.luckyalanzhou.barcodegenerator.barcodeFormats
import com.luckyalanzhou.barcodegenerator.BarcodeFormatIds
import com.luckyalanzhou.barcodegenerator.MainActivity
import com.luckyalanzhou.barcodegenerator.ui.feature.results.ResultExportAction
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale

private data class ResultPageImage(val bitmap: Bitmap, val label: String)

/** 为当前结果批次生成统一的合成图片。 */
private suspend fun MainActivity.createResultPageImage(): ResultPageImage? {
    val resultItems = resultsViewModel.resultUiState.value.items.toList()
    val style = settingsViewModel.style.copy()
    val dark = isDark()
    val density = resources.displayMetrics.density
    val fontScale = resources.configuration.fontScale
    // Measured viewport width also covers landscape, split screen and window insets.
    val contentWidth = resultImageContentWidthPx.takeIf { it > 0 } ?: return null
    return withContext(Dispatchers.Default) {
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
}

/** Prepare off the UI thread; both actions share the same all-or-nothing snapshot. */
private fun MainActivity.withResultPageImage(action: ResultExportAction, onReady: (ResultPageImage) -> Unit) {
    if (preparingResultExport) return
    resultExportAction = action
    lifecycleScope.launch {
        var sharingStarted = false
        try {
            val result = createResultPageImage()
            if (result == null) toast("条码图片准备失败，请重试") else {
                onReady(result)
                // PNG encoding and chooser launch own the busy state after preparation.
                sharingStarted = action == ResultExportAction.Share
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: OutOfMemoryError) {
            toast("图片占用内存过大，请减少条码数量后重试")
        } catch (_: Exception) {
            toast("条码图片准备失败，请重试")
        } finally {
            if (!sharingStarted) resultExportAction = null
        }
    }
}

/** Sharing means opening the system Sharesheet, not a second app-target picker. */
internal fun MainActivity.shareResultPage() = withResultPageImage(ResultExportAction.Share) { result ->
    shareBitmap(result.bitmap, result.label, onStarted = { resultExportAction = ResultExportAction.Share },
        onFinished = {
            if (!result.bitmap.isRecycled) result.bitmap.recycle()
            resultExportAction = null
        })
}

/** Saving remains discoverable without adding a step to system sharing. */
internal fun MainActivity.saveResultPage() = withResultPageImage(ResultExportAction.Save) { result ->
    showComposeDialog(compact = true) { dismiss ->
        val dark = isDark()
        ComposeGlassDialogCard(dark) {
            Text("保存条码", color = LocalAppColorScheme.current.text.primary, fontSize = 18.sp)
            Text(result.label, color = LocalAppColorScheme.current.text.secondary, fontSize = 14.sp,
                modifier = Modifier.padding(top = 6.dp))
            Image(result.bitmap.asImageBitmap(), "待保存的条码图片", contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth().heightIn(max = 160.dp).padding(top = 10.dp))
            DialogAction("保存到相册", dark, { saveBitmap(result.bitmap, result.label); dismiss() }, modifier = Modifier.fillMaxWidth().padding(top = 10.dp))
            DialogAction("保存到文件", dark, {
                dismiss()
                saveResultImageDocument(result)
            }, modifier = Modifier.fillMaxWidth().padding(top = 10.dp))
        }
    }
}

private fun MainActivity.saveResultImageDocument(result: ResultPageImage) {
    if (pendingResultImageFile != null || preparingResultExport) return
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
            pendingResultImageFile = file
            launchExternalActivity(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_TITLE, shareImageFileName(result.label))
                addCategory(Intent.CATEGORY_OPENABLE)
            }, MainActivity.REQUEST_RESULT_IMAGE_FILE)
        } catch (error: CancellationException) {
            temporary?.delete()
            pendingResultImageFile = null
            throw error
        } catch (_: Exception) {
            temporary?.delete()
            pendingResultImageFile = null
            toast("准备文件失败，请重试")
        } finally {
            resultExportAction = null
        }
    }
}
