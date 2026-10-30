package com.luckyalanzhou.barcodegenerator.data.network.server

import com.luckyalanzhou.barcodegenerator.data.network.protocol.LanShareLimits
import com.luckyalanzhou.barcodegenerator.data.network.recordSafely
import com.luckyalanzhou.barcodegenerator.data.network.protocol.LAN_SHARE_WEBSOCKET_HEARTBEAT_INTERVAL_MS
import com.luckyalanzhou.barcodegenerator.data.network.protocol.LAN_SHARE_WEBSOCKET_HEARTBEAT_TIMEOUT_MS
import com.luckyalanzhou.barcodegenerator.data.network.protocol.isCommittedSharedFile
import com.luckyalanzhou.barcodegenerator.data.network.protocol.mimeTypeForName
import com.luckyalanzhou.barcodegenerator.data.network.protocol.uploadedMimeType
import com.luckyalanzhou.barcodegenerator.data.network.protocol.safeBrowserClientId
import com.luckyalanzhou.barcodegenerator.data.network.protocol.safeFileName
import com.luckyalanzhou.barcodegenerator.data.network.protocol.sharedFile
import com.luckyalanzhou.barcodegenerator.data.network.protocol.toLanShareFile
import com.luckyalanzhou.barcodegenerator.data.preview.LanShareWebImagePreviewCache
import com.luckyalanzhou.barcodegenerator.data.network.web.LanShareWebTemplates
import com.luckyalanzhou.barcodegenerator.domain.AppLogger
import com.luckyalanzhou.barcodegenerator.domain.LanShareFile
import com.luckyalanzhou.barcodegenerator.domain.LanShareMessage
import com.luckyalanzhou.barcodegenerator.domain.LanShareRealtimeEvent
import com.luckyalanzhou.barcodegenerator.domain.LAN_SHARE_MAX_SESSION_MESSAGES
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoWSD
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.util.UUID
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CancellationException
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * 浏览器端分享服务：HTTP 负责页面、文件上传/下载和图片预览，WebSocket 负责连接状态与文字消息。
 * 上传先流式写入同目录临时文件，校验完整后再发布为可见文件，避免失败留下半成品。
 */
internal class LanShareServer(
    host: String,
    port: Int,
    private val folder: File,
    private val logger: AppLogger,
    previewCacheFolder: File = File(folder.parentFile, ".lan-share-web-preview"),
    private val emitRealtimeEvent: (LanShareRealtimeEvent) -> Unit = {},
    private val connectionDisconnectGraceMs: Long = DEFAULT_CONNECTION_DISCONNECT_GRACE_MS,
    private val webSocketHeartbeatIntervalMs: Long = LAN_SHARE_WEBSOCKET_HEARTBEAT_INTERVAL_MS,
    private val webSocketHeartbeatTimeoutMs: Long = LAN_SHARE_WEBSOCKET_HEARTBEAT_TIMEOUT_MS,
    private val maxConcurrentUploads: Int = 4,
    private val availableUploadBytes: () -> Long = { folder.usableSpace },
    private val beforeUploadCommit: () -> Unit = {},
    private val uploadTimeoutMillisForBytes: (Long) -> Long = ::lanShareUploadTotalTimeoutMillis,
    private val deleteUploadStagingFile: (File) -> Boolean = File::delete,
    private val maxConcurrentClientConnections: Int = MAX_CLIENT_HANDLERS,
    private val httpRequestHeaderTimeoutMillis: Long = LAN_SHARE_HTTP_REQUEST_HEADER_TIMEOUT_MS,
) : NanoWSD(host, port) {
    private companion object {
        const val DEFAULT_CONNECTION_DISCONNECT_GRACE_MS = 3_000L
        const val MAX_CLIENT_HANDLERS = 32
        const val UPLOAD_READ_BUFFER_SIZE = 512 * 1024
        const val UPLOAD_WRITE_BUFFER_SIZE = 256 * 1024
        const val MAX_CHUNK_LINE_BYTES = 8 * 1024
        const val MAX_CHUNK_TRAILER_BYTES = 16 * 1024
        const val MAX_SMALL_REJECTED_CHUNK_DRAIN_BYTES = 64 * 1024L
        const val SMALL_REJECTED_CHUNK_DRAIN_TIMEOUT_MS = 1_000L
        const val MAX_CHAT_MESSAGE_BYTES = 64 * 1024
        const val APP_UPLOAD_CLIENT = "app"
        const val DISK_RESERVE_BYTES = 16L * 1024L * 1024L
        const val BEARER_PREFIX = "Bearer "
        const val MAX_PAIRING_FAILURES = 5
        const val MAX_TRACKED_PAIRING_ADDRESSES = 256
        const val PAIRING_WINDOW_MS = 60_000L

        fun createSessionToken(): String = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(ByteArray(32).also(SecureRandom()::nextBytes))

        fun createPairingCode(): String = SecureRandom().nextInt(100_000_000).toString().padStart(8, '0')
    }

    internal data class SessionSnapshot(
        val files: List<LanShareFile>,
        val messages: List<LanShareMessage>,
    )

    private val webSockets = CopyOnWriteArraySet<BrowserWebSocket>()
    private val webSocketStateLock = Any()
    private val connectionStateExecutor = Executors.newSingleThreadScheduledExecutor { task ->
        Thread(task, "lan-share-connection-state").apply { isDaemon = true }
    }
    private var pendingDisconnect: ScheduledFuture<*>? = null
    private var connectionGeneration = 0L
    private var reportedConnected = false
    @Volatile private var stopped = false
    private val chatMessages = mutableListOf<LanShareMessage>()
    private val uploadedMimeTypes = ConcurrentHashMap<String, String>()
    private val clientHandlerRunner = BoundedNanoHttpdAsyncRunner(maxConcurrentClientConnections)
    private val webImagePreviewCache = LanShareWebImagePreviewCache(previewCacheFolder)
    private data class PairingAttempts(val windowStartedAtMs: Long, val failures: Int)
    private val pairingAttemptsLock = Any()
    private val pairingAttemptsByAddress = mutableMapOf<String, PairingAttempts>()
    private val uploadLock = Any()
    private var reservedUploadBytes = 0L
    private var reservedUploadFiles = 0
    private var activeUploads = 0
    private enum class UploadRejection { Stopped, Busy, RoomFull, RoomFileLimit, DiskFull }
    private val currentClientSocket = ThreadLocal<Socket>()
    private val currentClientInput = ThreadLocal<WebSocketFrameLimitInputStream>()
    internal val sessionToken: String = createSessionToken()
    internal val pairingCode: String = createPairingCode()
    internal val activeClientHandlerCount: Int
        get() = clientHandlerRunner.activeHandlerCount

    init {
        require(sessionToken.matches(Regex("[A-Za-z0-9_-]{43}")))
    }

    // NanoHTTPD 的 WebSocket 不公开底层 Socket；在标准连接处理边界捕获，不用反射。
    override fun createClientHandler(socket: Socket, inputStream: InputStream): NanoHTTPD.ClientHandler {
        val guardedInput = WebSocketFrameLimitInputStream(
                source = inputStream,
                maxFrameBytes = LAN_SHARE_WEBSOCKET_MAX_FRAME_BYTES,
                maxMessageBytes = LAN_SHARE_WEBSOCKET_MAX_MESSAGE_BYTES,
                requestSocket = socket,
                httpRequestHeaderTimeoutMillis = httpRequestHeaderTimeoutMillis,
                // 出错时 NanoWSD 会先关闭输入流，再关闭输出流。关闭 SocketInputStream 也会关闭整个 Socket，
                // 截断已排队的 1009 Close 帧；因此应先半关闭输入端。
                closeInput = { webSocketUpgrade ->
                    // NanoWSD 的 doClose 先关输入、再关输出。WebSocket 升级后不提前关底层输入半边：
                    // Windows 会把 shutdownInput 后尚未消费的数据作为 TCP reset，丢掉已写出的 1009 帧；
                    // 随后的 SocketOutputStream.close() 会完成整个 socket 的关闭。
                    if (!webSocketUpgrade || socket.isClosed) inputStream.close()
                },
            )
        return object : ClientHandler(guardedInput, socket) {
            override fun run() {
                currentClientSocket.set(socket)
                currentClientInput.set(guardedInput)
                try {
                    super.run()
                } finally {
                    currentClientInput.remove()
                    currentClientSocket.remove()
                }
            }
        }
    }

    /** 小请求可限时消费完后返回原因；大请求立即拒绝，不为报错再接收整个大文件。 */
    private fun drainSmallRejectedUpload(session: IHTTPSession, body: UploadBody): Boolean {
        if (body.isChunked || body.expectedBytes > 64 * 1024) return false
        val socket = currentClientSocket.get() ?: return false
        val previousTimeout = socket.soTimeout
        try {
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1)
            val buffer = ByteArray(8 * 1024)
            var remaining = body.expectedBytes
            while (remaining > 0) {
                val timeLeft = deadline - System.nanoTime()
                if (timeLeft <= 0) return false
                socket.soTimeout = TimeUnit.NANOSECONDS.toMillis(timeLeft).coerceIn(1, 1_000).toInt()
                val count = session.inputStream.read(buffer, 0, minOf(remaining, buffer.size.toLong()).toInt())
                if (count <= 0) break
                remaining -= count
            }
            return remaining == 0L
        } catch (_: IOException) {
            // 断开或超时仍关闭连接；不能继续解析未消费完的上传体。
        } finally { if (!socket.isClosed) socket.soTimeout = previousTimeout }
        return false
    }
    private val heartbeatTask: ScheduledFuture<*>

    init {
        require(maxConcurrentUploads > 0)
        require(maxConcurrentClientConnections > 0)
        require(httpRequestHeaderTimeoutMillis > 0L)
        require(webSocketHeartbeatIntervalMs > 0L)
        require(webSocketHeartbeatTimeoutMs > webSocketHeartbeatIntervalMs)
        setAsyncRunner(clientHandlerRunner)
        setServerSocketFactory(object : NanoHTTPD.ServerSocketFactory {
            override fun create(): ServerSocket = object : ServerSocket() {
                init {
                    recordReceiveBufferDiagnostic(
                        label = "listener receiveBufferDefaultBytes",
                        readBufferSize = { this.receiveBufferSize },
                        note = "; OS defaults/autotuning preserved",
                    )
                }

                override fun accept(): Socket = super.accept().also { accepted ->
                    recordReceiveBufferDiagnostic(
                        label = "accepted socket receiveBufferBytes",
                        readBufferSize = { accepted.receiveBufferSize },
                    )
                }
            }
        })
        heartbeatTask = connectionStateExecutor.scheduleAtFixedRate(
            ::sendWebSocketHeartbeats,
            webSocketHeartbeatIntervalMs,
            webSocketHeartbeatIntervalMs,
            TimeUnit.MILLISECONDS,
        )
    }

    private data class UploadBody(
        val expectedBytes: Long,
        val isChunked: Boolean,
        val framing: String,
    )

    /** 单次请求的计时数据暂存在内存中，完成后统一记录，不按缓冲区逐次写日志。 */
    private data class UploadMetrics(
        var bodyReadNanos: Long = 0L,
        var fileWriteNanos: Long = 0L,
        var framingNanos: Long = 0L,
        var flushNanos: Long = 0L,
        var readCalls: Long = 0L,
        var httpChunks: Long = 0L,
    )

    private fun recordReceiveBufferDiagnostic(
        label: String,
        readBufferSize: () -> Int,
        note: String = "",
    ) {
        val bytes = runCatching(readBufferSize).getOrNull() ?: return
        logger.recordSafely("lan-server", "$label=$bytes$note")
    }

    /** 解析固定长度或格式正确的 HTTP/1.1 分块请求体，并校验声明长度是否一致。 */
    private fun uploadBody(session: IHTTPSession): UploadBody? {
        val rawContentLength = session.headers["content-length"]
        val contentLength = rawContentLength?.toLongOrNull()
        if (rawContentLength != null && contentLength == null) return null

        val rawFileSize = session.headers["x-file-size"]
        val fileSize = rawFileSize?.toLongOrNull()
        if (rawFileSize != null && fileSize == null) return null

        val transferEncoding = session.headers["transfer-encoding"]
            ?.split(',')
            ?.map { it.trim().lowercase() }
            ?.filter(String::isNotEmpty)
        val isChunked = transferEncoding != null
        if (isChunked && transferEncoding != listOf("chunked")) return null
        if (isChunked && contentLength != null) return null
        if (contentLength != null && fileSize != null && contentLength != fileSize) return null

        val expectedBytes = contentLength ?: fileSize ?: return null
        if (isChunked && fileSize == null) return null
        return UploadBody(
            expectedBytes = expectedBytes,
            isChunked = isChunked,
            framing = when {
                isChunked -> "chunked"
                contentLength != null -> "content-length"
                else -> "declared-size"
            },
        )
    }

    /** 在同一把锁内预留文件名额和字节容量；上传的网络读取和文件内容写入在锁外执行。 */
    private fun reserveUploadCapacity(size: Long): UploadRejection? = synchronized(uploadLock) {
        if (stopped) return@synchronized UploadRejection.Stopped
        if (activeUploads >= maxConcurrentUploads) return@synchronized UploadRejection.Busy
        val committedFiles = folder.listFiles().orEmpty().filter(::isCommittedSharedFile)
        if (committedFiles.size + reservedUploadFiles >= LanShareLimits.MAX_ROOM_FILES) {
            return@synchronized UploadRejection.RoomFileLimit
        }
        val stored = committedFiles.sumOf { it.length() }
        if (stored > LanShareLimits.MAX_ROOM_BYTES ||
            reservedUploadBytes > LanShareLimits.MAX_ROOM_BYTES - stored ||
            size > LanShareLimits.MAX_ROOM_BYTES - stored - reservedUploadBytes
        ) {
            UploadRejection.RoomFull
        } else {
            val usable = (availableUploadBytes() - DISK_RESERVE_BYTES).coerceAtLeast(0L)
            if (reservedUploadBytes > usable || size > usable - reservedUploadBytes) {
                return@synchronized UploadRejection.DiskFull
            }
            reservedUploadBytes += size
            reservedUploadFiles++
            activeUploads++
            null
        }
    }

    private fun releaseUploadCapacity(bytes: Long) = synchronized(uploadLock) {
        reservedUploadBytes = (reservedUploadBytes - bytes).coerceAtLeast(0L)
        reservedUploadFiles = (reservedUploadFiles - 1).coerceAtLeast(0)
    }

    /** 将收到的数据直接写入同目录临时文件，完整校验后再原子发布，期间不暴露未完成文件。 */
    private fun receiveUpload(
        session: IHTTPSession,
        body: UploadBody,
        target: File,
        reservation: Long,
        mimeType: String,
        transferId: String?,
        source: String,
    ) {
        var reservationHeld = true
        var temporary: File? = null
        var receivedBytes = 0L
        var committed = false
        var deadlineInput: LanShareUploadDeadlineInputStream? = null
        val metrics = UploadMetrics()
        val startedAtNanos = System.nanoTime()
        try {
            val uploadSocket = currentClientSocket.get()
                ?: throw IOException("无法启用上传总时限")
            val timedInput = LanShareUploadDeadlineInputStream(
                source = session.inputStream,
                socket = uploadSocket,
                timeoutMillis = uploadTimeoutMillisForBytes(body.expectedBytes),
            )
            deadlineInput = timedInput
            val stagingFile = File.createTempFile(".lan-upload-", ".part", folder)
            temporary = stagingFile
            val bodyStartedAtNanos = System.nanoTime()
            val copiedBytes = BufferedOutputStream(
                FileOutputStream(stagingFile),
                UPLOAD_WRITE_BUFFER_SIZE,
            ).use { output ->
                val bytes = if (body.isChunked) {
                    copyChunkedBody(timedInput, output, body.expectedBytes, metrics) { receivedBytes = it }
                } else {
                    copyFixedLengthBody(timedInput, output, body.expectedBytes, metrics) { receivedBytes = it }
                }
                val flushStartedAtNanos = System.nanoTime()
                try {
                    output.flush()
                } finally {
                    metrics.flushNanos += System.nanoTime() - flushStartedAtNanos
                }
                bytes
            }
            val bodyElapsedNanos = System.nanoTime() - bodyStartedAtNanos
            require(copiedBytes == body.expectedBytes && stagingFile.length() == body.expectedBytes) {
                "上传内容大小不匹配：$copiedBytes/${body.expectedBytes} 字节"
            }
            timedInput.ensureWithinDeadline()
            beforeUploadCommit()
            val commitStartedAtNanos = System.nanoTime()
            synchronized(uploadLock) {
                check(!stopped) { "分享服务已停止" }
                check(!target.exists()) { "目标文件已存在" }
                timedInput.ensureWithinDeadline()
                check(stagingFile.renameTo(target)) { "无法保存上传文件" }
                committed = true
                uploadedMimeTypes[target.name] = mimeType
                reservedUploadBytes = (reservedUploadBytes - reservation).coerceAtLeast(0L)
                reservedUploadFiles = (reservedUploadFiles - 1).coerceAtLeast(0)
                reservationHeld = false
            }
            val commitElapsedNanos = System.nanoTime() - commitStartedAtNanos
            notifyFilesChanged(target, transferId)
            val bodyElapsedMs = nanosToMillis(bodyElapsedNanos)
            val bodyMbps = if (bodyElapsedNanos > 0L) {
                body.expectedBytes * 8_000.0 / bodyElapsedNanos
            } else {
                0.0
            }
            recordSafely(
                "lan-server",
                "upload complete source=$source framing=${body.framing} bytes=${body.expectedBytes} " +
                    "bodyMs=$bodyElapsedMs bodyMbps=${"%.1f".format(java.util.Locale.US, bodyMbps)} " +
                    "readMs=${nanosToMillis(metrics.bodyReadNanos)} writeMs=${nanosToMillis(metrics.fileWriteNanos)} " +
                    "framingMs=${nanosToMillis(metrics.framingNanos)} flushMs=${nanosToMillis(metrics.flushNanos)} " +
                    "commitMs=${nanosToMillis(commitElapsedNanos)} totalMs=${nanosToMillis(System.nanoTime() - startedAtNanos)} " +
                    "readCalls=${metrics.readCalls} httpChunks=${metrics.httpChunks}",
                null,
            )
        } catch (error: Exception) {
            if (committed) {
                recordSafely(
                    "lan-server",
                    "upload committed but post-commit work failed source=$source framing=${body.framing}",
                    error,
                )
                return
            }
            val safeTransferId = transferId
                ?.take(64)
                ?.filter { it.isLetterOrDigit() || it == '-' || it == '_' }
                ?.ifEmpty { "unknown" }
                ?: "unknown"
            recordSafely(
                "lan-server",
                "upload failed transfer=$safeTransferId source=$source framing=${body.framing} received=$receivedBytes " +
                    "expected=${body.expectedBytes} committed=$committed " +
                    "readMs=${nanosToMillis(metrics.bodyReadNanos)} writeMs=${nanosToMillis(metrics.fileWriteNanos)} " +
                    "framingMs=${nanosToMillis(metrics.framingNanos)} flushMs=${nanosToMillis(metrics.flushNanos)} " +
                    "elapsedMs=${nanosToMillis(System.nanoTime() - startedAtNanos)} readCalls=${metrics.readCalls} " +
                    "httpChunks=${metrics.httpChunks}",
                error,
            )
            throw error
        } finally {
            try {
                deadlineInput?.restoreSocketTimeout()
            } finally {
                try {
                    temporary?.takeIf(File::exists)?.let { stagingFile ->
                        var cleanupError: Exception? = null
                        val deleted = try {
                            deleteUploadStagingFile(stagingFile)
                        } catch (error: Exception) {
                            cleanupError = error
                            false
                        }
                        if (!deleted && stagingFile.exists()) {
                            recordSafely(
                                "lan-server",
                                "upload staging cleanup failed name=${stagingFile.name}",
                                cleanupError,
                            )
                        }
                    }
                } finally {
                    try {
                        if (reservationHeld) releaseUploadCapacity(reservation)
                    } finally {
                        synchronized(uploadLock) { activeUploads = (activeUploads - 1).coerceAtLeast(0) }
                    }
                }
            }
        }
    }

    private fun copyFixedLengthBody(
        input: java.io.InputStream,
        output: BufferedOutputStream,
        expectedBytes: Long,
        metrics: UploadMetrics,
        onProgress: (Long) -> Unit,
    ): Long {
        val buffer = ByteArray(UPLOAD_READ_BUFFER_SIZE)
        var copied = 0L
        while (copied < expectedBytes) {
            val readStartedAtNanos = System.nanoTime()
            val read = try {
                input.read(buffer, 0, minOf(buffer.size.toLong(), expectedBytes - copied).toInt())
            } finally {
                metrics.bodyReadNanos += System.nanoTime() - readStartedAtNanos
                metrics.readCalls++
            }
            if (read < 0) throw IOException("上传内容提前结束：$copied/$expectedBytes 字节")
            if (read == 0) continue
            val writeStartedAtNanos = System.nanoTime()
            try {
                output.write(buffer, 0, read)
            } finally {
                metrics.fileWriteNanos += System.nanoTime() - writeStartedAtNanos
            }
            copied += read
            onProgress(copied)
        }
        return copied
    }

    private fun copyChunkedBody(
        input: java.io.InputStream,
        output: BufferedOutputStream,
        expectedBytes: Long,
        metrics: UploadMetrics,
        onProgress: (Long) -> Unit,
    ): Long {
        val buffer = ByteArray(UPLOAD_READ_BUFFER_SIZE)
        var copied = 0L
        var trailerBytes = 0
        while (true) {
            val chunkLine = readHttpLine(input, metrics)
            val chunkSize = parseChunkSize(chunkLine)
            if (chunkSize == 0L) {
                while (true) {
                    val trailer = readHttpLine(input, metrics)
                    trailerBytes += trailer.length + 2
                    if (trailerBytes > MAX_CHUNK_TRAILER_BYTES) throw IOException("上传请求尾部过长")
                    if (trailer.isEmpty()) break
                }
                break
            }
            if (chunkSize > expectedBytes - copied) {
                drainSmallRejectedChunkedRemainder(input, chunkSize, metrics)
                throw IOException("上传内容超过声明大小")
            }
            metrics.httpChunks++

            var remaining = chunkSize
            while (remaining > 0L) {
                val readStartedAtNanos = System.nanoTime()
                val read = try {
                    input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                } finally {
                    metrics.bodyReadNanos += System.nanoTime() - readStartedAtNanos
                    metrics.readCalls++
                }
                if (read < 0) throw IOException("分块上传内容提前结束")
                if (read == 0) continue
                val writeStartedAtNanos = System.nanoTime()
                try {
                    output.write(buffer, 0, read)
                } finally {
                    metrics.fileWriteNanos += System.nanoTime() - writeStartedAtNanos
                }
                copied += read
                remaining -= read
                onProgress(copied)
            }
            val framingStartedAtNanos = System.nanoTime()
            val validTerminator = try {
                input.read() == '\r'.code && input.read() == '\n'.code
            } finally {
                metrics.framingNanos += System.nanoTime() - framingStartedAtNanos
            }
            if (!validTerminator) {
                throw IOException("分块上传格式无效")
            }
        }
        if (copied != expectedBytes) throw IOException("上传内容大小不匹配：$copied/$expectedBytes 字节")
        return copied
    }

    /** 小型超长 chunk 可限时消费完请求体，避免未读正文使 TCP 复位并丢弃 HTTP 错误响应。 */
    private fun drainSmallRejectedChunkedRemainder(
        input: InputStream,
        firstChunkBytes: Long,
        metrics: UploadMetrics,
    ): Boolean {
        if (firstChunkBytes !in 0..MAX_SMALL_REJECTED_CHUNK_DRAIN_BYTES) return false
        val socket = currentClientSocket.get() ?: return false
        val previousTimeout = socket.soTimeout
        val deadlineNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(SMALL_REJECTED_CHUNK_DRAIN_TIMEOUT_MS)
        val buffer = ByteArray(8 * 1024)
        var consumedBytes = 0L

        fun applyRemainingTimeout(): Boolean {
            val remainingNanos = deadlineNanos - System.nanoTime()
            if (remainingNanos <= 0L) return false
            socket.soTimeout = TimeUnit.NANOSECONDS.toMillis(remainingNanos).coerceIn(1, SMALL_REJECTED_CHUNK_DRAIN_TIMEOUT_MS).toInt()
            return true
        }

        fun drainPayload(byteCount: Long): Boolean {
            if (byteCount < 0L || byteCount > MAX_SMALL_REJECTED_CHUNK_DRAIN_BYTES - consumedBytes) return false
            var remaining = byteCount
            while (remaining > 0L) {
                if (!applyRemainingTimeout()) return false
                val count = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                if (count <= 0) return false
                remaining -= count
                consumedBytes += count
            }
            return true
        }

        fun readChunkLine(): String? {
            if (!applyRemainingTimeout()) return null
            val line = readHttpLine(input, metrics)
            consumedBytes += line.length + 2L
            return line.takeIf { consumedBytes <= MAX_SMALL_REJECTED_CHUNK_DRAIN_BYTES }
        }

        fun consumeCrlf(): Boolean {
            if (!applyRemainingTimeout()) return false
            val carriageReturn = input.read()
            if (carriageReturn != '\r'.code) return false
            if (!applyRemainingTimeout()) return false
            val lineFeed = input.read()
            if (lineFeed != '\n'.code) return false
            consumedBytes += 2L
            return consumedBytes <= MAX_SMALL_REJECTED_CHUNK_DRAIN_BYTES
        }

        try {
            if (!drainPayload(firstChunkBytes) || !consumeCrlf()) return false
            var trailerBytes = 0
            while (true) {
                val chunkLine = readChunkLine() ?: return false
                val chunkSize = parseChunkSize(chunkLine)
                if (chunkSize == 0L) {
                    while (true) {
                        val trailer = readChunkLine() ?: return false
                        trailerBytes += trailer.length + 2
                        if (trailerBytes > MAX_CHUNK_TRAILER_BYTES) return false
                        if (trailer.isEmpty()) return true
                    }
                }
                if (!drainPayload(chunkSize) || !consumeCrlf()) return false
            }
        } catch (_: IOException) {
            return false
        } finally {
            if (!socket.isClosed) runCatching { socket.soTimeout = previousTimeout }
        }
        return false
    }

    private fun parseChunkSize(chunkLine: String): Long {
        val chunkSizeText = chunkLine.substringBefore(';').trim()
        return chunkSizeText.takeIf {
            it.isNotEmpty() && it.all { value ->
                value in '0'..'9' || value in 'a'..'f' || value in 'A'..'F'
            }
        }
            ?.toLongOrNull(16) ?: throw IOException("无效的分块上传长度")
    }

    private fun readHttpLine(input: java.io.InputStream, metrics: UploadMetrics): String {
        val framingStartedAtNanos = System.nanoTime()
        val line = ByteArrayOutputStream()
        try {
            while (line.size() <= MAX_CHUNK_LINE_BYTES) {
                val value = input.read()
                if (value < 0) throw IOException("分块上传意外结束")
                if (value == '\r'.code) {
                    if (input.read() != '\n'.code) throw IOException("分块上传行结束符无效")
                    return line.toString(Charsets.US_ASCII.name())
                }
                if (value == '\n'.code || value > 0x7f) throw IOException("分块上传行格式无效")
                line.write(value)
            }
            throw IOException("分块上传行过长")
        } finally {
            metrics.framingNanos += System.nanoTime() - framingStartedAtNanos
        }
    }

    private fun nanosToMillis(nanos: Long): Long = TimeUnit.NANOSECONDS.toMillis(nanos)

    fun browserConnected() = synchronized(webSocketStateLock) { webSockets.isNotEmpty() }

    fun messagesSnapshot(): List<LanShareMessage> = synchronized(chatMessages) { chatMessages.toList() }

    fun sendLocalMessage(text: String): LanShareMessage = publishMessage(text, "app")
        ?: throw IllegalArgumentException("文字消息为空或超过 64 KB")

    /** 应用与 App 主机相同的消息长度上限后，接收一条浏览器聊天消息。 */
    internal fun receiveBrowserMessage(clientId: String, text: String): LanShareMessage? {
        val sender = "browser:${safeBrowserClientId(clientId)}"
        return publishMessage(text, sender)
    }

    private fun publishMessage(text: String, sender: String): LanShareMessage? {
        if (text.isBlank() || text.toByteArray(Charsets.UTF_8).size > MAX_CHAT_MESSAGE_BYTES) return null
        val message = synchronized(chatMessages) {
            val previousTimestamp = chatMessages.lastOrNull()?.createdAt ?: 0L
            LanShareMessage(
                id = UUID.randomUUID().toString(),
                text = text,
                sender = sender,
                createdAt = maxOf(System.currentTimeMillis(), previousTimestamp + 1),
            ).also {
                chatMessages += it
                if (chatMessages.size > LAN_SHARE_MAX_SESSION_MESSAGES) chatMessages.removeAt(0)
            }
        }
        if (webSockets.isNotEmpty()) {
            broadcast(JSONObject().put("type", "message").put("message", messageJson(message)).toString())
        }
        emitRealtimeEventSafely(LanShareRealtimeEvent.MessageAdded(message), "message")
        return message
    }

    /** 实时状态回调失败不能撤销已提交的消息或文件；取消、中断和 JVM Error 仍向上传播。 */
    private fun emitRealtimeEventSafely(event: LanShareRealtimeEvent, eventName: String) {
        try {
            emitRealtimeEvent(event)
        } catch (error: CancellationException) {
            throw error
        } catch (error: InterruptedException) {
            Thread.currentThread().interrupt()
            throw error
        } catch (error: Exception) {
            recordSafely("lan-server", "realtime $eventName event callback failed", error)
        }
    }

    override fun openWebSocket(handshake: IHTTPSession): NanoWSD.WebSocket = BrowserWebSocket(handshake)

    private inner class BrowserWebSocket(handshake: IHTTPSession) : NanoWSD.WebSocket(handshake) {
        private val browserClientId = handshake.parameters["client"]?.firstOrNull().orEmpty()
        private val transport = requireNotNull(currentClientSocket.get())
        private val outbound = WebSocketWriteQueue(connectionStateExecutor) { reason, error ->
            if (reason != "closed") logger.recordSafely("lan-server", "websocket outbound stopped reason=$reason", error, error)
            runCatching { transport.close() }
            removeWebSocket(this)
        }
        fun sendQueued(event: String) = outbound.enqueue { send(event) }
        fun sendSnapshotQueued() = outbound.enqueueSnapshot {
            val snapshot = try {
                sessionSnapshotEvent()
            } catch (error: Exception) {
                logger.recordSafely("lan-server", "websocket snapshot failed", error, error)
                return@enqueueSnapshot
            }
            send(snapshot)
        }
        fun pingQueued(payload: ByteArray) = outbound.enqueue { ping(payload) }
        fun stopWriting() = outbound.close()

        @Synchronized
        override fun sendFrame(frame: NanoWSD.WebSocketFrame) {
            if (frame.opCode != NanoWSD.WebSocketFrame.OpCode.Close) {
                super.sendFrame(frame)
                return
            }

            // NanoWSD emits a Close frame through several single-byte writes; batch it before the socket closes.
            debugFrameSent(frame)
            val encoded = ByteArrayOutputStream()
            frame.write(encoded)
            val output = transport.getOutputStream()
            output.write(encoded.toByteArray())
            output.flush()
        }

        @Volatile var lastPongAtNanos: Long = System.nanoTime()
            private set

        override fun onOpen() {
            lastPongAtNanos = System.nanoTime()
            if (!addWebSocket(this)) stopWriting()
        }

        override fun onClose(code: NanoWSD.WebSocketFrame.CloseCode, reason: String, initiatedByRemote: Boolean) {
            stopWriting()
            removeWebSocket(this)
        }

        override fun onMessage(message: NanoWSD.WebSocketFrame) {
            // 客户端在 onopen 后请求快照；浏览器已安装 onmessage，不会漏掉首批文件和聊天记录。
            val payloadText = message.textPayload ?: return
            if (payloadText == "sync") {
                sendSnapshotQueued()
                return
            }
            val payload = runCatching { JSONObject(payloadText) }.getOrNull() ?: return
            if (payload.optString("type") != "message") return
            val posted = receiveBrowserMessage(browserClientId, payload.optString("text"))
            if (posted == null) {
                val reason = if (payload.optString("text").toByteArray(Charsets.UTF_8).size > MAX_CHAT_MESSAGE_BYTES) {
                    "消息不能超过 64 KB"
                } else "消息不能为空"
                runCatching { sendQueued(JSONObject().put("type", "error").put("message", reason).toString()) }
            }
        }

        override fun onPong(pong: NanoWSD.WebSocketFrame) {
            lastPongAtNanos = System.nanoTime()
        }

        override fun onException(exception: IOException) {
            val hasQueuedSizeLimitClose = exception is NanoWSD.WebSocketException &&
                exception.code == NanoWSD.WebSocketFrame.CloseCode.MessageTooBig &&
                isOpen
            val closeFrameQueued = if (hasQueuedSizeLimitClose) {
                // NanoWSD 的读取异常路径只关闭流，不会把异常携带的 close code 发给浏览器。
                runCatching {
                    close(exception.code, exception.reason, false)
                    true
                }
                    .onFailure { logger.recordSafely("lan-server", "websocket size-limit close failed", it, it) }
                    .getOrDefault(false)
            } else false
            logger.recordSafely("lan-server", "websocket receive failed", exception, exception)
            // close() 将帧写入 NanoWSD 输出流；让 NanoWSD 随后的 doClose 先关闭并冲刷该流，
            // 再由 onClose 停止写队列。此处提前关底层 Socket 会截断缓冲中的 Close 帧。
            if (!closeFrameQueued) stopWriting()
            removeWebSocket(this)
        }
    }

    private fun sendWebSocketHeartbeats() {
        if (stopped) return
        val nowNanos = System.nanoTime()
        val timeoutNanos = TimeUnit.MILLISECONDS.toNanos(webSocketHeartbeatTimeoutMs)
        webSockets.toList().forEach { socket ->
            if (!socket.isOpen) {
                removeWebSocket(socket)
            } else if (nowNanos - socket.lastPongAtNanos >= timeoutNanos) {
                logger.recordSafely("lan-server", "websocket heartbeat timed out")
                runCatching {
                    socket.stopWriting()
                }
                removeWebSocket(socket)
            } else {
                runCatching { socket.pingQueued(nowNanos.toString().toByteArray(Charsets.US_ASCII)) }
                    .onFailure { error ->
                        logger.recordSafely("lan-server", "websocket heartbeat failed", error, error)
                        runCatching {
                            socket.stopWriting()
                        }
                        removeWebSocket(socket)
                    }
            }
        }
    }

    private fun addWebSocket(socket: BrowserWebSocket): Boolean =
        synchronized(webSocketStateLock) {
            if (stopped || webSockets.size >= 16) return@synchronized false
            webSockets.add(socket)
            connectionGeneration++
            pendingDisconnect?.cancel(false)
            pendingDisconnect = null
            if (!reportedConnected) {
                reportedConnected = true
                emitRealtimeEventSafely(LanShareRealtimeEvent.ConnectionChanged(true), "connection")
            }
            true
        }

    private fun removeWebSocket(socket: BrowserWebSocket) {
        synchronized(webSocketStateLock) {
            if (stopped || !webSockets.remove(socket) || webSockets.isNotEmpty() || !reportedConnected || pendingDisconnect != null) return
            val generation = ++connectionGeneration
            pendingDisconnect = connectionStateExecutor.schedule({
                synchronized(webSocketStateLock) {
                    if (generation != connectionGeneration || webSockets.isNotEmpty() || !reportedConnected) return@synchronized
                    pendingDisconnect = null
                    reportedConnected = false
                    emitRealtimeEventSafely(LanShareRealtimeEvent.ConnectionChanged(false), "connection")
                }
            }, connectionDisconnectGraceMs, TimeUnit.MILLISECONDS)
        }
    }

    override fun stop() {
        synchronized(uploadLock) {
            stopped = true
        }
        val peers = synchronized(webSocketStateLock) {
            connectionGeneration++
            pendingDisconnect?.cancel(false)
            pendingDisconnect = null
            val snapshot = webSockets.toList()
            webSockets.clear()
            reportedConnected = false
            snapshot
        }
        peers.forEach { it.stopWriting() }
        heartbeatTask.cancel(false)
        connectionStateExecutor.shutdownNow()
        super.stop()
    }

    private fun fileRecordsSnapshot(sender: String) = folder.listFiles().orEmpty()
        .filter(::isCommittedSharedFile)
        .sortedBy { it.lastModified() }
        .map { file -> toLanShareFile(file, sender, uploadedMimeTypes[file.name]) }

    fun filesSnapshot(sender: String) = fileRecordsSnapshot(sender)

    private fun fileMimeType(file: File): String = uploadedMimeTypes[file.name] ?: mimeTypeForName(file.name)

    private fun notifyFilesChanged(file: File? = null, transferId: String? = null) {
        file?.let { uploaded ->
            emitRealtimeEventSafely(
                LanShareRealtimeEvent.FileAdded(
                    toLanShareFile(uploaded, "peer", uploadedMimeTypes[uploaded.name]),
                ),
                "file",
            )
        }
        if (webSockets.isEmpty()) return
        val event = JSONObject().put("type", "files").toString()
        val eventWithFile = file?.let { uploaded ->
            val record = toLanShareFile(uploaded, "peer", uploadedMimeTypes[uploaded.name])
            val payload = JSONObject(event).put("file", JSONObject()
                .put("id", record.id)
                .put("name", record.name)
                .put("size", record.size)
                .put("modifiedAt", record.modifiedAt)
                .put("sender", record.sender)
                .put("mimeType", record.mimeType)
            )
            transferId?.let { payload.put("transferId", it) }
            payload.toString()
        } ?: event
        webSockets.toList().forEach { socket ->
            runCatching {
                if (socket.isOpen) socket.sendQueued(eventWithFile) else removeWebSocket(socket)
            }
                .onFailure { removeWebSocket(socket) }
        }
    }

    /** 诊断写入失败不能改变已完成的网络操作结果；致命 Error 仍向上传播。 */
    private fun recordSafely(tag: String, message: String, error: Throwable?) {
        logger.recordSafely(tag, message, error, error)
    }

    internal fun sessionSnapshot(): SessionSnapshot = SessionSnapshot(
        files = fileRecordsSnapshot("peer"),
        messages = messagesSnapshot(),
    )

    private fun sessionSnapshotEvent(): String = sessionSnapshot().let { snapshot ->
        JSONObject().put("type", "snapshot")
        .put("files", JSONArray(snapshot.files.map { record -> JSONObject()
            .put("id", record.id)
            .put("name", record.name)
            .put("size", record.size)
            .put("modifiedAt", record.modifiedAt)
            .put("sender", record.sender)
            .put("mimeType", record.mimeType)
        }))
        .put("messages", JSONArray(snapshot.messages.map(::messageJson)))
        .toString()
    }

    private fun messageJson(message: LanShareMessage) = JSONObject()
        .put("id", message.id)
        .put("text", message.text)
        .put("sender", message.sender)
        .put("createdAt", message.createdAt)

    private fun broadcast(event: String) {
        webSockets.toList().forEach { socket ->
            runCatching {
                if (socket.isOpen) socket.sendQueued(event) else removeWebSocket(socket)
            }
                .onFailure { removeWebSocket(socket) }
        }
    }

    override fun serve(session: IHTTPSession): Response {
        try {
            if (session.uri.substringBefore('?') == "/ws" && !isAuthorized(session)) {
                return unauthorizedResponse()
            }
            return super.serve(session).apply { addHeader("Referrer-Policy", "no-referrer") }
        } finally {
            currentClientInput.get()?.prepareForNextHttpRequest()
        }
    }

    /** 根据 HTTP 方法和路径分发网页、文件清单、上传、预览和下载请求。 */
    override fun serveHttp(session: IHTTPSession): Response {
        val requestPath = session.uri.substringBefore('?')
        if (requestPath !in setOf("/", "/api/pair") && !isAuthorized(session)) {
            return unauthorizedResponse()
        }
        return try {
            when {
                session.method == Method.POST && requestPath == "/api/pair" -> pairBrowser(session)

                session.method == Method.GET && requestPath == "/api/session" -> newFixedLengthResponse(
                    Response.Status.OK,
                    MIME_PLAINTEXT,
                    "",
                ).apply { addHeader("Cache-Control", "no-store") }

                session.method == Method.GET && requestPath == "/api/files" -> newFixedLengthResponse(
                    Response.Status.OK,
                    "application/json; charset=utf-8",
                    JSONArray(fileRecordsSnapshot("peer").map {
                        JSONObject().put("id", it.id).put("name", it.name).put("size", it.size)
                            .put("modifiedAt", it.modifiedAt).put("sender", it.sender)
                            .put("mimeType", it.mimeType)
                    }).toString()
                ).apply { addHeader("Cache-Control", "no-store, no-cache, must-revalidate") }

                session.method == Method.GET && requestPath == "/" -> newFixedLengthResponse(
                    Response.Status.OK,
                    "text/html; charset=utf-8",
                    LanShareWebTemplates.page()
                ).apply { addHeader("Cache-Control", "no-store, no-cache, must-revalidate") }

                session.method == Method.PUT && requestPath == "/upload" -> {
                    val body = uploadBody(session)
                        ?: return newFixedLengthResponse(Response.Status.BAD_REQUEST, MIME_PLAINTEXT, "上传请求长度无效")
                    if (body.expectedBytes !in 1..LanShareLimits.MAX_FILE_BYTES) {
                        return newFixedLengthResponse(Response.Status.BAD_REQUEST, MIME_PLAINTEXT, "单个文件不能超过 10 GiB")
                    }
                    val submittedName = session.parameters["name"]?.firstOrNull().orEmpty().ifBlank { "附件" }
                    val client = session.parameters["client"]?.firstOrNull().orEmpty()
                    val transferId = session.parameters["transfer"]?.firstOrNull()
                        ?.takeIf { it.matches(Regex("[A-Za-z0-9_-]{8,64}")) }
                    val name = safeFileName(submittedName)
                    val mimeType = uploadedMimeType(name, session.headers["content-type"])
                    val targetPrefix = System.nanoTime()
                    val target = if (client == APP_UPLOAD_CLIENT) {
                        File(folder, "app_${targetPrefix}_$name")
                    } else {
                        File(folder, "web_${targetPrefix}_${safeBrowserClientId(client)}_$name")
                    }
                    val rejection = reserveUploadCapacity(body.expectedBytes)
                    if (rejection != null) {
                        val message = when (rejection) {
                            UploadRejection.Stopped -> "分享服务已停止"
                            UploadRejection.Busy -> "同时上传任务过多，请等待当前传输完成后重试"
                            UploadRejection.RoomFull -> "房间文件总大小不能超过 100 GB"
                            UploadRejection.RoomFileLimit -> "房间最多允许 1,000 个文件"
                            UploadRejection.DiskFull -> "设备可用存储空间不足，请清理后重试"
                        }
                        logger.recordSafely("lan-server", "upload rejected reason=$rejection bytes=${body.expectedBytes}")
                        val bodyConsumed = drainSmallRejectedUpload(session, body)
                        return newFixedLengthResponse(
                            if (rejection == UploadRejection.RoomFull || rejection == UploadRejection.RoomFileLimit) {
                                Response.Status.BAD_REQUEST
                            } else {
                                Response.Status.SERVICE_UNAVAILABLE
                            },
                            MIME_PLAINTEXT, message,
                        ).apply { closeConnection(!bodyConsumed) }
                    }
                    receiveUpload(
                        session = session,
                        body = body,
                        target = target,
                        reservation = body.expectedBytes,
                        mimeType = mimeType,
                        transferId = transferId,
                        source = if (client == APP_UPLOAD_CLIENT) "app" else "browser",
                    )
                    newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, target.name)
                }

                session.method == Method.GET && requestPath.startsWith("/api/preview/") -> {
                    val file = sharedFile(folder, decodeLanSharePathSegment(requestPath.substringAfterLast('/')))
                    if (file == null) newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "未找到文件")
                    else {
                        val preview = webImagePreviewCache.getOrCreate(file, fileMimeType(file))
                        if (preview == null) {
                            newFixedLengthResponse(
                                Response.Status.NOT_ACCEPTABLE,
                                MIME_PLAINTEXT,
                                "此图片格式暂不支持网页预览，请点击文件名下载原图",
                            )
                        } else {
                            val previewMimeType = if (preview == file) fileMimeType(file) else "image/jpeg"
                            newFixedLengthResponse(Response.Status.OK, previewMimeType, FileInputStream(preview), preview.length()).apply {
                                addHeader("Content-Length", preview.length().toString())
                                addHeader("Cache-Control", "no-store, no-cache, must-revalidate")
                                addHeader("X-Content-Type-Options", "nosniff")
                            }
                        }
                    }
                }

                session.method == Method.GET && requestPath.startsWith("/dl/") -> {
                    val file = sharedFile(folder, decodeLanSharePathSegment(requestPath.substringAfterLast('/')))
                    if (file == null) newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "未找到文件")
                    else newFixedLengthResponse(Response.Status.OK, fileMimeType(file), FileInputStream(file), file.length()).apply {
                        addHeader("Content-Length", file.length().toString())
                        addHeader("Cache-Control", "no-store, no-cache, must-revalidate")
                        addHeader("X-Content-Type-Options", "nosniff")
                        addHeader(
                            "Content-Disposition",
                            "attachment; filename=\"${toLanShareFile(file, "peer", fileMimeType(file)).name}\""
                        )
                    }
                }

                else -> newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "not found")
            }
        } catch (error: Exception) {
            logger.recordSafely(
                "lan-server",
                "request failed path=${session.uri.substringBefore('?')}",
                error,
                error,
            )
            newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "传输失败")
        }
    }

    private fun isAuthorized(session: IHTTPSession): Boolean {
        val headerToken = session.headers["authorization"]
            ?.takeIf { it.startsWith(BEARER_PREFIX) }
            ?.removePrefix(BEARER_PREFIX)
        val queryTokens = session.parameters["token"].orEmpty()
        val queryToken = queryTokens.singleOrNull()
        val candidate = headerToken ?: queryToken ?: return false
        return MessageDigest.isEqual(
            sessionToken.toByteArray(Charsets.US_ASCII),
            candidate.toByteArray(Charsets.US_ASCII),
        )
    }

    private fun unauthorizedResponse(): Response = newFixedLengthResponse(
        Response.Status.UNAUTHORIZED,
        MIME_PLAINTEXT,
        "需要有效的局域网会话凭据",
    ).apply {
        addHeader("WWW-Authenticate", "Bearer realm=\"lan-share\"")
        addHeader("Cache-Control", "no-store")
    }

    private fun pairBrowser(session: IHTTPSession): Response {
        val address = currentClientSocket.get()?.inetAddress?.hostAddress ?: "unknown"
        val now = System.currentTimeMillis()
        return synchronized(pairingAttemptsLock) {
            pairingAttemptsByAddress.entries.removeAll { now - it.value.windowStartedAtMs >= PAIRING_WINDOW_MS }
            val previous = pairingAttemptsByAddress[address]
            if (previous != null && previous.failures >= MAX_PAIRING_FAILURES) {
                return@synchronized noStoreResponse(Response.Status.TOO_MANY_REQUESTS, "配对尝试过多，请稍后再试")
            }
            if (previous == null && pairingAttemptsByAddress.size >= MAX_TRACKED_PAIRING_ADDRESSES) {
                return@synchronized noStoreResponse(Response.Status.TOO_MANY_REQUESTS, "当前配对请求过多，请稍后再试")
            }

            val suppliedCode = session.headers["x-lan-pairing-code"]
            if (suppliedCode != null && MessageDigest.isEqual(
                    pairingCode.toByteArray(Charsets.US_ASCII),
                    suppliedCode.toByteArray(Charsets.US_ASCII),
                )
            ) {
                pairingAttemptsByAddress.remove(address)
                return@synchronized newFixedLengthResponse(
                    Response.Status.OK,
                    "application/json; charset=utf-8",
                    JSONObject().put("token", sessionToken).toString(),
                ).apply { addHeader("Cache-Control", "no-store") }
            }

            pairingAttemptsByAddress[address] = PairingAttempts(
                previous?.windowStartedAtMs ?: now,
                (previous?.failures ?: 0) + 1,
            )
            noStoreResponse(Response.Status.UNAUTHORIZED, "配对码无效")
        }
    }

    private fun noStoreResponse(status: Response.Status, message: String) =
        newFixedLengthResponse(status, MIME_PLAINTEXT, message).apply { addHeader("Cache-Control", "no-store") }

}

/** 将 URL 路径段解码；格式错误保留原值，程序级错误必须继续传播。 */
internal fun decodeLanSharePathSegment(
    value: String,
    decoder: (String, String) -> String = { encoded, charset -> URLDecoder.decode(encoded, charset) },
): String = try {
    decoder(value.replace("+", "%2B"), Charsets.UTF_8.name())
} catch (_: IllegalArgumentException) {
    value
}
