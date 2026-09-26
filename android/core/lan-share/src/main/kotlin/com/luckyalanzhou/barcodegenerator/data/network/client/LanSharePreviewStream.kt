package com.luckyalanzhou.barcodegenerator.data.network.client

import com.luckyalanzhou.barcodegenerator.domain.LAN_SHARE_PREVIEW_MAX_FILE_BYTES
import java.io.InputStream
import java.io.OutputStream

internal const val MAX_LAN_SHARE_PREVIEW_BYTES = LAN_SHARE_PREVIEW_MAX_FILE_BYTES

internal fun copyLanSharePreview(
    input: InputStream,
    output: OutputStream,
    maxBytes: Long = MAX_LAN_SHARE_PREVIEW_BYTES,
): Long {
    require(maxBytes >= 0L) { "图片预览大小限制无效" }
    val buffer = ByteArray(16 * 1024)
    var total = 0L
    while (true) {
        val read = input.read(buffer)
        if (read < 0) break
        if (read == 0) continue
        require(total + read <= maxBytes) { "图片预览超过 ${maxBytes / (1024L * 1024L)} MB 限制" }
        output.write(buffer, 0, read)
        total += read
    }
    return total
}
