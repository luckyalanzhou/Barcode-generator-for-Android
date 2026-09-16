package com.luckyalanzhou.barcodegenerator

import android.graphics.Bitmap
import android.graphics.Canvas
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 结果页由 ComposeResultsUi.kt 绘制；此入口只负责触发根 Compose 重组。 */
internal fun MainActivity.showResults() {
    // 结果页不仅需要重组，还必须同步根路由和顶部/底部页面状态。
    page = "results"
    render()
}

/** 兼容旧业务调用，历史页实际由 ComposeAppShell 路由。 */
internal fun MainActivity.showList(favoritesOnly: Boolean) {
    page = if (favoritesOnly) "favorites" else "history"
    composeShellRevision.intValue++
}

/** 将当前结果批次合成为一张图片交给系统分享面板。 */
internal fun MainActivity.shareResultPage() {
    val images = resultItems.mapNotNull { item ->
        encode(item.text, formats.firstOrNull { it.first == item.format }?.second ?: com.google.zxing.BarcodeFormat.CODE_128)
    }
    if (images.isEmpty()) {
        toast("没有可分享的条码")
        return
    }
    val width = images.maxOf { it.width }
    val spacing = if (resultItems.all { it.format == "Code 128-B" }) dp(style.margin).coerceAtLeast(0) else 0
    val height = images.sumOf { it.height } + spacing * (images.size - 1)
    val pageImage = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(pageImage)
    canvas.drawColor(style.bgColor)
    var top = 0
    images.forEach { image ->
        canvas.drawBitmap(image, (width - image.width) / 2f, top.toFloat(), null)
        top += image.height + spacing
    }
    shareBitmap(pageImage, "本页生成的 ${images.size} 个条码")
}

internal fun MainActivity.formatHistoryTime(time: Long): String {
    val date = Date(time)
    val now = Date()
    val day = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
    return if (day.format(date) == day.format(now)) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(date)
    } else {
        SimpleDateFormat("M/d HH:mm", Locale.getDefault()).format(date)
    }
}
