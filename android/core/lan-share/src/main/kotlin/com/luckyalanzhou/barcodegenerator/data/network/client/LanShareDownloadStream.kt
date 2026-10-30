package com.luckyalanzhou.barcodegenerator.data.network.client

import com.luckyalanzhou.barcodegenerator.data.network.protocol.LAN_SHARE_STREAM_BUFFER_SIZE
import com.luckyalanzhou.barcodegenerator.data.network.protocol.LanShareLimits
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.CancellationException

/** 原始文件下载逐块限制实际写入字节，避免无长度或虚报长度的响应耗尽本机存储。 */
internal fun copyLanShareDownload(
    input: InputStream,
    output: OutputStream,
    maxBytes: Long = LanShareLimits.MAX_FILE_BYTES,
    shouldCancel: () -> Boolean = { false },
): Long {
    require(maxBytes >= 0L) { "下载文件大小限制无效" }
    val buffer = ByteArray(LAN_SHARE_STREAM_BUFFER_SIZE)
    var copiedBytes = 0L
    while (true) {
        if (shouldCancel()) throw CancellationException("局域网下载已取消")
        val read = input.read(buffer)
        if (read < 0) break
        if (read == 0) continue
        if (shouldCancel()) throw CancellationException("局域网下载已取消")
        if (read.toLong() > maxBytes - copiedBytes) {
            throw IOException("下载文件超过 10 GiB 限制")
        }
        output.write(buffer, 0, read)
        copiedBytes += read
    }
    return copiedBytes
}
