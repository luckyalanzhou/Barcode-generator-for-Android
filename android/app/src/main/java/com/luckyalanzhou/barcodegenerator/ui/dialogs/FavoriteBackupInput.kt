package com.luckyalanzhou.barcodegenerator.ui.dialogs

import com.luckyalanzhou.barcodegenerator.domain.MAX_FAVORITES_BACKUP_INPUT_BYTES
import java.io.ByteArrayOutputStream
import java.io.InputStream

internal fun readFavoritesBackupBounded(
    input: InputStream,
    maxBytes: Long = MAX_FAVORITES_BACKUP_INPUT_BYTES.toLong(),
): ByteArray {
    require(maxBytes >= 0L)
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(16 * 1024)
    var total = 0L
    while (true) {
        val count = input.read(buffer)
        if (count < 0) break
        if (count == 0) continue
        require(count.toLong() <= maxBytes - total) {
            "备份文件超过 ${maxBytes / (1024L * 1024L)} MB 限制"
        }
        output.write(buffer, 0, count)
        total += count
    }
    return output.toByteArray()
}
