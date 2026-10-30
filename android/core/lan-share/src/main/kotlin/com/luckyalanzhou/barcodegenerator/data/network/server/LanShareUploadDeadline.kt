package com.luckyalanzhou.barcodegenerator.data.network.server

import com.luckyalanzhou.barcodegenerator.data.network.protocol.LAN_SHARE_UPLOAD_MIN_BYTES_PER_SECOND
import com.luckyalanzhou.barcodegenerator.data.network.protocol.LAN_SHARE_UPLOAD_PAUSE_GRACE_MS
import com.luckyalanzhou.barcodegenerator.data.network.protocol.LAN_SHARE_UPLOAD_STARTUP_GRACE_MS
import com.luckyalanzhou.barcodegenerator.data.network.protocol.LanShareLimits
import java.io.FilterInputStream
import java.io.InputStream
import java.net.Socket
import java.net.SocketException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

/** 以声明文件大小和最低传输速率计算上传总时限，并容纳启动与一次短暂停顿。 */
internal fun lanShareUploadTotalTimeoutMillis(expectedBytes: Long): Long {
    require(expectedBytes in 0..LanShareLimits.MAX_FILE_BYTES)
    val fullSeconds = expectedBytes / LAN_SHARE_UPLOAD_MIN_BYTES_PER_SECOND
    val remainingBytes = expectedBytes % LAN_SHARE_UPLOAD_MIN_BYTES_PER_SECOND
    val partialSecondMillis = if (remainingBytes == 0L) {
        0L
    } else {
        (remainingBytes * 1_000L + LAN_SHARE_UPLOAD_MIN_BYTES_PER_SECOND - 1L) /
            LAN_SHARE_UPLOAD_MIN_BYTES_PER_SECOND
    }
    val transferMillis = TimeUnit.SECONDS.toMillis(fullSeconds) + partialSecondMillis
    return LAN_SHARE_UPLOAD_STARTUP_GRACE_MS + transferMillis + LAN_SHARE_UPLOAD_PAUSE_GRACE_MS
}

/** 每次底层读取都收紧 Socket 超时到剩余总时限，持续滴流不能重置上传期限。 */
internal class LanShareUploadDeadlineInputStream(
    source: InputStream,
    private val socket: Socket,
    timeoutMillis: Long,
) : FilterInputStream(source) {
    private val initialSocketTimeoutMillis = socket.soTimeout
    private val deadlineNanos: Long

    init {
        require(timeoutMillis > 0L)
        deadlineNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis)
    }

    override fun read(): Int {
        applyRemainingTimeout()
        val value = super.read()
        ensureWithinDeadline()
        return value
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        applyRemainingTimeout()
        val count = super.read(buffer, offset, length)
        ensureWithinDeadline()
        return count
    }

    /** 在文件写入或提交边界再次检查期限，避免最后一次读取后仍发布过期上传。 */
    fun ensureWithinDeadline() {
        if (deadlineNanos - System.nanoTime() <= 0L) {
            throw SocketTimeoutException("上传超过总时限")
        }
    }

    /** 恢复 HTTP 连接原有的单次读取超时，避免影响同一 socket 的后续处理。 */
    fun restoreSocketTimeout() {
        if (!socket.isClosed) {
            try {
                socket.soTimeout = initialSocketTimeoutMillis
            } catch (_: SocketException) {
                // 客户端可能已断开；清理仍需继续删除暂存文件并释放上传槽。
            }
        }
    }

    private fun applyRemainingTimeout() {
        val remainingNanos = deadlineNanos - System.nanoTime()
        if (remainingNanos <= 0L) throw SocketTimeoutException("上传超过总时限")
        if (socket.isClosed) throw SocketException("上传连接已关闭")

        val remainingMillis = TimeUnit.NANOSECONDS.toMillis(remainingNanos).coerceAtLeast(1L)
        val readTimeoutMillis = if (initialSocketTimeoutMillis == 0) {
            remainingMillis
        } else {
            minOf(initialSocketTimeoutMillis.toLong(), remainingMillis)
        }
        socket.soTimeout = readTimeoutMillis.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }
}
