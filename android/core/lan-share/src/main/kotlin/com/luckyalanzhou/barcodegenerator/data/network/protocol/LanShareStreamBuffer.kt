package com.luckyalanzhou.barcodegenerator.data.network.protocol

import java.io.InputStream
import java.io.OutputStream

/** Per-transfer buffer large enough to avoid tiny writes without excessive memory use. */
const val LAN_SHARE_STREAM_BUFFER_SIZE = 128 * 1024

internal fun copyLanShareUpload(
    input: InputStream,
    output: OutputStream,
    totalBytes: Long,
    onProgress: (uploadedBytes: Long, totalBytes: Long) -> Unit,
): Long {
    val buffer = ByteArray(LAN_SHARE_STREAM_BUFFER_SIZE)
    var copied = 0L
    onProgress(0L, totalBytes)
    while (true) {
        val read = input.read(buffer)
        if (read < 0) break
        if (read == 0) continue
        output.write(buffer, 0, read)
        copied += read
        onProgress(copied, totalBytes)
    }
    return copied
}
