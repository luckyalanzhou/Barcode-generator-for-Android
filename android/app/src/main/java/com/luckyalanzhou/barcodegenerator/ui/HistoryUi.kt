package com.luckyalanzhou.barcodegenerator.ui

import com.luckyalanzhou.barcodegenerator.BarcodeImageColors
import com.luckyalanzhou.barcodegenerator.barcodeFormats
import com.luckyalanzhou.barcodegenerator.MainActivity

import android.graphics.Bitmap
import android.graphics.Canvas
import android.content.Intent
import androidx.core.graphics.createBitmap
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private data class ResultPageImage(val bitmap: Bitmap, val label: String)

/** 为当前结果批次生成统一的合成图片。 */
private fun MainActivity.createResultPageImage(): ResultPageImage? {
    val resultItems = viewModel.resultUiState.value.items
    val images = resultItems.mapNotNull { item ->
        viewModel.createBarcodeImage(
            item.text,
            barcodeFormats.firstOrNull { it.first == item.format }?.second ?: com.google.zxing.BarcodeFormat.CODE_128,
            settingsViewModel.style,
            isDark(),
            resources.displayMetrics.density,
        )
    }
    if (images.isEmpty()) return null
    val width = images.maxOf { it.width }
    val spacing = if (resultItems.all { it.format == "Code 128-B" }) {
        (settingsViewModel.style.margin * resources.displayMetrics.density).toInt().coerceAtLeast(0)
    } else 0
    val height = images.sumOf { it.height } + spacing * (images.size - 1)
    val pageImage = createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(pageImage)
    canvas.drawColor(BarcodeImageColors.background(isDark()))
    var top = 0
    images.forEach { image ->
        canvas.drawBitmap(image, (width - image.width) / 2f, top.toFloat(), null)
        top += image.height + spacing
    }
    return ResultPageImage(pageImage, "本页生成的 ${images.size} 个条码")
}

/** 结果页分享菜单：保存图片、保存文件或发送给其他应用。 */
internal fun MainActivity.shareResultPage() {
    val result = createResultPageImage()
    if (result == null) {
        toast("没有可分享的条码")
        return
    }
    showComposeDialog(compact = true, metricsLabel = null) { dismiss ->
        val dark = isDark()
        ComposeGlassDialogCard(dark) {
            Text("分享结果", color = LocalBarcodeThemeColors.current.primary, fontSize = 18.sp)
            DialogAction("保存为图片", dark, { saveBitmap(result.bitmap, result.label); dismiss() }, modifier = Modifier.fillMaxWidth().padding(top = 10.dp))
            DialogAction("保存到文件", dark, {
                pendingResultImage = result.bitmap
                pendingResultImageLabel = result.label
                dismiss()
                launchExternalActivity(
                    Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                        type = "image/png"
                        putExtra(Intent.EXTRA_TITLE, result.label.replace(Regex("[^A-Za-z0-9._-]+"), "_") + ".png")
                        addCategory(Intent.CATEGORY_OPENABLE)
                    },
                    MainActivity.REQUEST_RESULT_IMAGE_FILE,
                )
            }, modifier = Modifier.fillMaxWidth().padding(top = 10.dp))
            DialogAction("发送给其他应用", dark, { shareBitmap(result.bitmap, result.label); dismiss() }, modifier = Modifier.fillMaxWidth().padding(top = 10.dp))
        }
    }
}
