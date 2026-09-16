package com.luckyalanzhou.barcodegenerator

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield

/** 启动后后台补齐缺失条码图片；不阻塞首屏，也不删除已有缓存。 */
internal fun MainActivity.prewarmBarcodeImages() {
    val snapshot = (items.filter { it.inHistory || it.favorite } + favoriteGroups.flatMap { group ->
        group.itemIds.mapNotNull { id -> items.firstOrNull { it.id == id } }
    }).distinctBy { it.id }.map { it.copy() }
    val width = style.barWidth.toInt().coerceIn(120, 360)
    val height = style.barHeight.coerceIn(30, 150).coerceAtLeast(1)
    val textSize = style.textSize.coerceIn(10f, 24f)
    val showFormat = style.showFormat
    val dark = isDark()

    lifecycleScope.launch(Dispatchers.Default) {
        snapshot.forEach { item ->
            val key = localBarcodeFileStore.imageKey(item, width, height, textSize, showFormat, dark)
            if (localBarcodeFileStore.readImage(key) == null) {
                val encoded = encode(
                    item.text,
                    formats.firstOrNull { it.first == item.format }?.second
                        ?: com.google.zxing.BarcodeFormat.CODE_128,
                    withBackground = dark,
                )
                if (encoded != null) {
                    val image = if (item.format == "Code 128-B") {
                        addBarcodeQuietZone(
                            trimBarcodeHorizontal(encoded),
                            if (dark) AndroidColor.WHITE else AndroidColor.TRANSPARENT,
                        )
                    } else encoded
                    localBarcodeFileStore.writeImage(key, image)
                }
            }
            // 让出调度机会，避免缓存数量较多时占满后台线程。
            yield()
        }
    }
}

