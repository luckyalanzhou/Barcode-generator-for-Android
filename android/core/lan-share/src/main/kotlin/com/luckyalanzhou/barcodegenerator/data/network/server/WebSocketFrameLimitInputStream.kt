package com.luckyalanzhou.barcodegenerator.data.network.server

import fi.iki.elonen.NanoWSD
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.Socket
import java.net.SocketTimeoutException
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

internal const val LAN_SHARE_WEBSOCKET_MAX_FRAME_BYTES = 512 * 1024
internal const val LAN_SHARE_WEBSOCKET_MAX_MESSAGE_BYTES = 512 * 1024
internal const val LAN_SHARE_HTTP_REQUEST_HEADER_TIMEOUT_MS = 30_000L

/**
 * 在 NanoWSD 读取完整帧并分配 payload 前，检查 WebSocket 帧及分片消息的声明长度。
 * HTTP 头逐字节读到边界后再切换模式，避免 BufferedInputStream 预读 WebSocket 帧头。
 */
internal class WebSocketFrameLimitInputStream(
    private val source: InputStream,
    private val maxFrameBytes: Int,
    private val maxMessageBytes: Int,
    private val closeInput: (webSocketUpgrade: Boolean) -> Unit = { source.close() },
    private val requestSocket: Socket? = null,
    private val httpRequestHeaderTimeoutMillis: Long = LAN_SHARE_HTTP_REQUEST_HEADER_TIMEOUT_MS,
) : InputStream() {
    private enum class Mode { HttpHeaders, WebSocketFrames, PassThrough }
    private enum class FramePart { FirstByte, SecondByte, ExtendedLength, MaskingKey, Payload }

    private var mode = Mode.HttpHeaders
    private val httpHeader = ByteArrayOutputStream()
    private var httpHeaderSuffix = 0
    private var httpHeaderStartedAtNanos: Long? = null
    private var originalHttpHeaderSocketTimeoutMillis: Int? = null
    private var framePart = FramePart.FirstByte
    private var frameOpcode = 0
    private var frameFinal = false
    private var frameMasked = false
    private var payloadLengthCode = 0
    private var extendedLengthBytesRemaining = 0
    private var maskingKeyBytesRemaining = 0
    private var declaredPayloadLength = 0L
    private var lengthExceedsNanoWsdIntegerLimit = false
    private var checkedLengthForNanoWsd = 0L
    private var pendingFrameFailure: NanoWSD.WebSocketException? = null
    private var payloadBytesRemaining = 0L
    private var fragmentedMessageActive = false
    private var fragmentedMessageBytes = 0L
    private var finishFragmentedMessageAfterPayload = false

    init {
        require(maxFrameBytes > 0)
        require(maxMessageBytes > 0)
        require(httpRequestHeaderTimeoutMillis > 0L)
    }

    override fun read(): Int = when (mode) {
        Mode.HttpHeaders -> readHttpHeaderByte().also { if (it >= 0) observeHttpHeaderByte(it) }
        Mode.WebSocketFrames -> source.read().also { if (it >= 0) observeWebSocketByte(it) }
        Mode.PassThrough -> source.read()
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (offset < 0 || length < 0 || length > buffer.size - offset) throw IndexOutOfBoundsException()
        if (length == 0) return 0
        return when (mode) {
            Mode.HttpHeaders -> readHttpHeaders(buffer, offset, length)
            Mode.WebSocketFrames -> readWebSocketFrames(buffer, offset, length)
            Mode.PassThrough -> source.read(buffer, offset, length)
        }
    }

    override fun available(): Int = source.available()

    override fun close() = closeInput(mode == Mode.WebSocketFrames)

    private fun readHttpHeaders(buffer: ByteArray, offset: Int, length: Int): Int {
        var count = 0
        while (count < length && mode == Mode.HttpHeaders) {
            val value = readHttpHeaderByte()
            if (value < 0) return if (count == 0) -1 else count
            buffer[offset + count] = value.toByte()
            count++
            observeHttpHeaderByte(value)
        }
        return count
    }

    private fun observeHttpHeaderByte(value: Int) {
        httpHeader.write(value)
        httpHeaderSuffix = (httpHeaderSuffix shl 8 or (value and 0xff)) and 0xffff_ffff.toInt()
        if (httpHeaderSuffix == HTTP_HEADER_END) {
            val lines = String(httpHeader.toByteArray(), StandardCharsets.US_ASCII).split("\r\n")
            val isWebSocketUpgrade = lines.drop(1).any { line ->
                val separator = line.indexOf(':')
                separator > 0 &&
                    line.substring(0, separator).trim().equals("upgrade", ignoreCase = true) &&
                    line.substring(separator + 1).split(',').any { it.trim().equals("websocket", ignoreCase = true) }
            }
            mode = if (isWebSocketUpgrade) Mode.WebSocketFrames else Mode.PassThrough
            httpHeader.reset()
            restoreHttpHeaderSocketTimeout()
        } else if (httpHeader.size() >= MAX_HTTP_HEADER_TRACK_BYTES) {
            // NanoHTTPD 自身会拒绝超长请求头；无需继续缓存或探测它。
            mode = Mode.PassThrough
            httpHeader.reset()
            restoreHttpHeaderSocketTimeout()
        }
    }

    /** 为当前 HTTP 请求头设置累计期限；每次底层读取都缩短 SO_TIMEOUT，慢速滴流不能重置总时限。 */
    private fun readHttpHeaderByte(): Int {
        val socket = requestSocket ?: return source.read()
        val startedAt = httpHeaderStartedAtNanos ?: System.nanoTime().also {
            httpHeaderStartedAtNanos = it
            originalHttpHeaderSocketTimeoutMillis = socket.soTimeout
        }
        val deadlineNanos = TimeUnit.MILLISECONDS.toNanos(httpRequestHeaderTimeoutMillis)
        val elapsedNanos = System.nanoTime() - startedAt
        val remainingNanos = deadlineNanos - elapsedNanos
        if (remainingNanos <= 0L) {
            restoreHttpHeaderSocketTimeout()
            throw SocketTimeoutException("HTTP request headers exceeded the configured total deadline")
        }

        val remainingMillis = ((remainingNanos + 999_999L) / 1_000_000L)
            .coerceIn(1L, Int.MAX_VALUE.toLong())
            .toInt()
        val originalTimeout = requireNotNull(originalHttpHeaderSocketTimeoutMillis)
        socket.soTimeout = if (originalTimeout > 0) minOf(originalTimeout, remainingMillis) else remainingMillis
        val value = try {
            source.read()
        } catch (error: IOException) {
            restoreHttpHeaderSocketTimeout()
            throw error
        }
        if (value < 0) restoreHttpHeaderSocketTimeout()
        else if (System.nanoTime() - startedAt >= deadlineNanos) {
            restoreHttpHeaderSocketTimeout()
            throw SocketTimeoutException("HTTP request headers exceeded the configured total deadline")
        }
        return value
    }

    /** 普通 HTTP 响应完成后，keep-alive 连接的下一次请求重新开始独立请求头期限。 */
    internal fun prepareForNextHttpRequest() {
        if (mode != Mode.PassThrough) return
        restoreHttpHeaderSocketTimeout()
        httpHeader.reset()
        httpHeaderSuffix = 0
        mode = Mode.HttpHeaders
    }

    private fun restoreHttpHeaderSocketTimeout() {
        val timeout = originalHttpHeaderSocketTimeoutMillis
        originalHttpHeaderSocketTimeoutMillis = null
        httpHeaderStartedAtNanos = null
        val socket = requestSocket ?: return
        if (timeout != null && !socket.isClosed) {
            try {
                socket.soTimeout = timeout
            } catch (_: IOException) {
                // 连接已关闭时不影响请求头超时或 handler 清理。
            }
        }
    }

    private fun readWebSocketFrames(buffer: ByteArray, offset: Int, length: Int): Int {
        val boundedLength = minOf(length, bytesNeededForCurrentPart())
        val count = source.read(buffer, offset, boundedLength)
        if (count <= 0) return count
        repeat(count) { index -> observeWebSocketByte(buffer[offset + index].toInt() and 0xff) }
        return count
    }

    private fun bytesNeededForCurrentPart(): Int = when (framePart) {
        FramePart.FirstByte, FramePart.SecondByte -> 1
        FramePart.ExtendedLength -> extendedLengthBytesRemaining
        FramePart.MaskingKey -> maskingKeyBytesRemaining
        FramePart.Payload -> minOf(payloadBytesRemaining, Int.MAX_VALUE.toLong()).toInt()
    }.coerceAtLeast(1)

    private fun observeWebSocketByte(value: Int) {
        when (framePart) {
            FramePart.FirstByte -> {
                frameOpcode = value and 0x0f
                frameFinal = value and 0x80 != 0
                lengthExceedsNanoWsdIntegerLimit = false
                checkedLengthForNanoWsd = 0L
                pendingFrameFailure = null
                finishFragmentedMessageAfterPayload = false
                framePart = FramePart.SecondByte
            }

            FramePart.SecondByte -> {
                frameMasked = value and 0x80 != 0
                payloadLengthCode = value and 0x7f
                when (payloadLengthCode) {
                    in 0..125 -> {
                        declaredPayloadLength = payloadLengthCode.toLong()
                        acceptDeclaredLength()
                    }

                    126 -> {
                        declaredPayloadLength = 0L
                        extendedLengthBytesRemaining = 2
                        framePart = FramePart.ExtendedLength
                    }

                    else -> {
                        declaredPayloadLength = 0L
                        extendedLengthBytesRemaining = 8
                        framePart = FramePart.ExtendedLength
                    }
                }
            }

            FramePart.ExtendedLength -> {
                if (payloadLengthCode == 127 && extendedLengthBytesRemaining == 8 && value and 0x80 != 0) {
                    pendingFrameFailure = frameTooLarge("invalid 64-bit WebSocket payload length")
                    lengthExceedsNanoWsdIntegerLimit = true
                } else if (pendingFrameFailure == null) {
                    val nextLength = declaredPayloadLength * 256L + value
                    if (nextLength > maxFrameBytes) {
                        pendingFrameFailure = frameTooLarge("WebSocket payload exceeds configured limit")
                    } else {
                        declaredPayloadLength = nextLength
                    }
                }
                if (!lengthExceedsNanoWsdIntegerLimit && payloadLengthCode == 127) {
                    val nextCheckedLength = checkedLengthForNanoWsd * 256L + value
                    if (nextCheckedLength > Int.MAX_VALUE) {
                        lengthExceedsNanoWsdIntegerLimit = true
                    } else {
                        checkedLengthForNanoWsd = nextCheckedLength
                    }
                }
                extendedLengthBytesRemaining--
                if (extendedLengthBytesRemaining == 0) acceptDeclaredLength()
            }

            FramePart.MaskingKey -> {
                maskingKeyBytesRemaining--
                if (maskingKeyBytesRemaining == 0) beginPayload()
            }

            FramePart.Payload -> {
                payloadBytesRemaining--
                if (payloadBytesRemaining == 0L) finishFrame()
            }
        }
    }

    private fun acceptDeclaredLength() {
        if (pendingFrameFailure == null) {
            if (declaredPayloadLength > maxFrameBytes) {
                pendingFrameFailure = frameTooLarge("WebSocket frame exceeds $maxFrameBytes bytes")
            } else {
                when (frameOpcode) {
                    OPCODE_TEXT, OPCODE_BINARY -> {
                        if (!fragmentedMessageActive) {
                            if (declaredPayloadLength > maxMessageBytes) {
                                pendingFrameFailure = frameTooLarge("WebSocket message exceeds $maxMessageBytes bytes")
                            } else if (!frameFinal) {
                                fragmentedMessageActive = true
                                fragmentedMessageBytes = declaredPayloadLength
                            }
                        }
                    }

                    OPCODE_CONTINUATION -> if (fragmentedMessageActive) {
                        if (declaredPayloadLength > maxMessageBytes - fragmentedMessageBytes) {
                            pendingFrameFailure = frameTooLarge("fragmented WebSocket message exceeds $maxMessageBytes bytes")
                        } else {
                            fragmentedMessageBytes += declaredPayloadLength
                            finishFragmentedMessageAfterPayload = frameFinal
                        }
                    }
                }
            }
        }

        if (frameMasked) {
            if (pendingFrameFailure != null && lengthExceedsNanoWsdIntegerLimit) {
                // NanoWSD 会在掩码键读取前拒绝超 Int 长度；先从底层流消费完整掩码键，避免关闭时复位并丢弃 1009。
                repeat(MASKING_KEY_BYTES) {
                    if (source.read() < 0) throw IOException("incomplete WebSocket masking key")
                }
                throw requireNotNull(pendingFrameFailure)
            }
            maskingKeyBytesRemaining = MASKING_KEY_BYTES
            framePart = FramePart.MaskingKey
        } else {
            beginPayload()
        }
    }

    private fun beginPayload() {
        pendingFrameFailure?.let { throw it }
        payloadBytesRemaining = declaredPayloadLength
        framePart = FramePart.Payload
        if (payloadBytesRemaining == 0L) finishFrame()
    }

    private fun finishFrame() {
        if (finishFragmentedMessageAfterPayload) {
            fragmentedMessageActive = false
            fragmentedMessageBytes = 0L
        }
        framePart = FramePart.FirstByte
    }

    private fun frameTooLarge(reason: String) = NanoWSD.WebSocketException(
        NanoWSD.WebSocketFrame.CloseCode.MessageTooBig,
        reason,
    )

    private companion object {
        const val MAX_HTTP_HEADER_TRACK_BYTES = 16 * 1024
        const val HTTP_HEADER_END = 0x0d0a0d0a
        const val MASKING_KEY_BYTES = 4
        const val OPCODE_CONTINUATION = 0x0
        const val OPCODE_TEXT = 0x1
        const val OPCODE_BINARY = 0x2
    }
}
