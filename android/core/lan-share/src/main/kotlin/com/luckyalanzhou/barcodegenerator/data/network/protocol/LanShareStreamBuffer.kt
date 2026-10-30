package com.luckyalanzhou.barcodegenerator.data.network.protocol

import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.CancellationException

/** 每次传输使用足够大的缓冲区以减少过小写入，同时避免占用过多内存。 */
const val LAN_SHARE_STREAM_BUFFER_SIZE = 128 * 1024

internal fun copyLanShareUpload(
    input: InputStream,
    output: OutputStream,
    totalBytes: Long,
    shouldCancel: () -> Boolean = { false },
    onProgress: (uploadedBytes: Long, totalBytes: Long) -> Unit,
): Long {
    val buffer = ByteArray(LAN_SHARE_STREAM_BUFFER_SIZE)
    var copied = 0L
    onProgress(0L, totalBytes)
    while (true) {
        if (shouldCancel()) throw CancellationException("上传已取消")
        val read = input.read(buffer)
        if (read < 0) break
        if (read == 0) continue
        if (shouldCancel()) throw CancellationException("上传已取消")
        output.write(buffer, 0, read)
        copied += read
        onProgress(copied, totalBytes)
    }
    return copied
}
