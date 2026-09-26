package com.luckyalanzhou.barcodegenerator.data.network.client

import com.luckyalanzhou.barcodegenerator.domain.LAN_SHARE_PREVIEW_MAX_FILE_BYTES
import java.io.InputStream
import java.io.OutputStream

internal const val MAX_LAN_SHARE_PREVIEW_BYTES = LAN_SHARE_PREVIEW_MAX_FILE_BYTES

internal fun copyLanSharePreview(input: InputStream, output: OutputStream): Long {
    val buffer = ByteArray(16 * 1024)
    var total = 0L
    while (true) {
        val read = input.read(buffer)
        if (read < 0) break
        if (read == 0) continue
        require(total + read <= MAX_LAN_SHARE_PREVIEW_BYTES) { "图片预览超过 64 MB 限制" }
        output.write(buffer, 0, read)
        total += read
    }
    return total
}
