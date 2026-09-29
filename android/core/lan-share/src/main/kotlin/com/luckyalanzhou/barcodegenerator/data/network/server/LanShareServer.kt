package com.luckyalanzhou.barcodegenerator.data.network.server

import com.luckyalanzhou.barcodegenerator.data.network.protocol.LanShareLimits
import com.luckyalanzhou.barcodegenerator.data.network.protocol.LAN_SHARE_STREAM_BUFFER_SIZE
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
import java.net.URLDecoder
import java.util.UUID
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/** 浏览器端服务：HTTP 文件接口和 WebSocket 会话消息。 */
internal class LanShareServer(
    host: String,
    port: Int,
    private val folder: File,
    private val logger: AppLogger,
    previewCacheFolder: File = File(folder.parentFile, ".lan-share-web-preview"),
    private val emitRealtimeEvent: (LanShareRealtimeEvent) -> Unit = {},
    private val connectionDisconnectGraceMs: Long = DEFAULT_CONNECTION_DISCONNECT_GRACE_MS,
) : NanoWSD(host, port) {
    private companion object {
        const val DEFAULT_CONNECTION_DISCONNECT_GRACE_MS = 3_000L
        const val MAX_CHUNK_LINE_BYTES = 8 * 1024
        const val MAX_CHUNK_TRAILER_BYTES = 16 * 1024
        const val MAX_CHAT_MESSAGE_BYTES = 64 * 1024
        const val APP_UPLOAD_CLIENT = "app"
    }

    internal data class SessionSnapshot(
        val files: List<LanShareFile>,
        val messages: List<LanShareMessage>,
    )

    private val webSockets = CopyOnWriteArraySet<NanoWSD.WebSocket>()
    private val webSocketStateLock = Any()
    private val connectionStateExecutor = Executors.newSingleThreadScheduledExecutor { task ->
        Thread(task, "lan-share-connection-state").apply { isDaemon = true }
    }
    private var pendingDisconnect: ScheduledFuture<*>? = null
    private var connectionGeneration = 0L
    private var reportedConnected = false
    private var stopped = false
    private val chatMessages = mutableListOf<LanShareMessage>()
    private val uploadedMimeTypes = ConcurrentHashMap<String, String>()
    private val webImagePreviewCache = LanShareWebImagePreviewCache(previewCacheFolder)
    private val uploadLock = Any()
    private var reservedUploadBytes = 0L

    private data class UploadBody(val expectedBytes: Long, val isChunked: Boolean)

    /** Accept a fixed-length request or a correctly declared HTTP/1.1 chunked body. */
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
        return UploadBody(expectedBytes, isChunked)
    }

    /** Capacity reservation is brief; file/network I/O happens outside this lock. */
    private fun reserveUploadCapacity(size: Long): Boolean = synchronized(uploadLock) {
        val stored = folder.listFiles().orEmpty().filter(::isCommittedSharedFile).sumOf { it.length() }
        if (stored > LanShareLimits.MAX_ROOM_BYTES ||
            reservedUploadBytes > LanShareLimits.MAX_ROOM_BYTES - stored ||
            size > LanShareLimits.MAX_ROOM_BYTES - stored - reservedUploadBytes
        ) {
            false
        } else {
            reservedUploadBytes += size
            true
        }
    }

    private fun releaseUploadCapacity(bytes: Long) = synchronized(uploadLock) {
        reservedUploadBytes = (reservedUploadBytes - bytes).coerceAtLeast(0L)
    }

    /** Persist directly to one same-directory staging file, then atomically publish it. */
    private fun receiveUpload(
        session: IHTTPSession,
        body: UploadBody,
        target: File,
        reservation: Long,
        mimeType: String,
        transferId: String?,
    ) {
        var reservationHeld = true
        var temporary: File? = null
        try {
            val stagingFile = File.createTempFile(".lan-upload-", ".part", folder)
            temporary = stagingFile
            val receivedBytes = BufferedOutputStream(
                FileOutputStream(stagingFile),
                LAN_SHARE_STREAM_BUFFER_SIZE,
            ).use { output ->
                if (body.isChunked) {
                    copyChunkedBody(session.inputStream, output, body.expectedBytes)
                } else {
                    copyFixedLengthBody(session.inputStream, output, body.expectedBytes)
                }
            }
            require(receivedBytes == body.expectedBytes && stagingFile.length() == body.expectedBytes) {
                "上传内容大小不匹配：$receivedBytes/${body.expectedBytes} 字节"
            }
            synchronized(uploadLock) {
                check(!target.exists()) { "目标文件已存在" }
                check(stagingFile.renameTo(target)) { "无法保存上传文件" }
                uploadedMimeTypes[target.name] = mimeType
                reservedUploadBytes = (reservedUploadBytes - reservation).coerceAtLeast(0L)
                reservationHeld = false
            }
            notifyFilesChanged(target, transferId)
        } finally {
            temporary?.delete()
            if (reservationHeld) releaseUploadCapacity(reservation)
        }
    }

    private fun copyFixedLengthBody(input: java.io.InputStream, output: BufferedOutputStream, expectedBytes: Long): Long {
        val buffer = ByteArray(LAN_SHARE_STREAM_BUFFER_SIZE)
        var copied = 0L
        while (copied < expectedBytes) {
            val read = input.read(buffer, 0, minOf(buffer.size.toLong(), expectedBytes - copied).toInt())
            if (read < 0) throw IOException("上传内容提前结束：$copied/$expectedBytes 字节")
            if (read == 0) continue
            output.write(buffer, 0, read)
            copied += read
        }
        return copied
    }

    private fun copyChunkedBody(input: java.io.InputStream, output: BufferedOutputStream, expectedBytes: Long): Long {
        val buffer = ByteArray(LAN_SHARE_STREAM_BUFFER_SIZE)
        var copied = 0L
        var trailerBytes = 0
        while (true) {
            val chunkLine = readHttpLine(input)
            val chunkSizeText = chunkLine.substringBefore(';').trim()
            val chunkSize = chunkSizeText.takeIf {
                it.isNotEmpty() && it.all { value ->
                    value in '0'..'9' || value in 'a'..'f' || value in 'A'..'F'
                }
            }
                ?.toLongOrNull(16) ?: throw IOException("无效的分块上传长度")
            if (chunkSize == 0L) {
                while (true) {
                    val trailer = readHttpLine(input)
                    trailerBytes += trailer.length + 2
                    if (trailerBytes > MAX_CHUNK_TRAILER_BYTES) throw IOException("上传请求尾部过长")
                    if (trailer.isEmpty()) break
                }
                break
            }
            if (chunkSize > expectedBytes - copied) throw IOException("上传内容超过声明大小")

            var remaining = chunkSize
            while (remaining > 0L) {
                val read = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                if (read < 0) throw IOException("分块上传内容提前结束")
                if (read == 0) continue
                output.write(buffer, 0, read)
                copied += read
                remaining -= read
            }
            if (input.read() != '\r'.code || input.read() != '\n'.code) {
                throw IOException("分块上传格式无效")
            }
        }
        if (copied != expectedBytes) throw IOException("上传内容大小不匹配：$copied/$expectedBytes 字节")
        return copied
    }

    private fun readHttpLine(input: java.io.InputStream): String {
        val line = ByteArrayOutputStream()
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
    }

    fun browserConnected() = synchronized(webSocketStateLock) { webSockets.isNotEmpty() }

    fun messagesSnapshot(): List<LanShareMessage> = synchronized(chatMessages) { chatMessages.toList() }

    fun sendLocalMessage(text: String): LanShareMessage = publishMessage(text, "app")
        ?: throw IllegalArgumentException("文字消息为空或超过 64 KB")

    /** Accept one browser chat message after applying the same bound used by the App host. */
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
        emitRealtimeEvent(LanShareRealtimeEvent.MessageAdded(message))
        return message
    }

    override fun openWebSocket(handshake: IHTTPSession): NanoWSD.WebSocket = object : NanoWSD.WebSocket(handshake) {
        override fun onOpen() {
            addWebSocket(this)
        }

        override fun onClose(code: NanoWSD.WebSocketFrame.CloseCode, reason: String, initiatedByRemote: Boolean) {
            removeWebSocket(this)
        }

        override fun onMessage(message: NanoWSD.WebSocketFrame) {
            // 客户端在 onopen 后请求快照；浏览器已安装 onmessage，不会漏掉首批文件和聊天记录。
            val payloadText = message.textPayload ?: return
            if (payloadText == "sync") {
                runCatching { send(sessionSnapshotEvent()) }
                return
            }
            val payload = runCatching { JSONObject(payloadText) }.getOrNull() ?: return
            if (payload.optString("type") != "message") return
            val clientId = handshake.parameters["client"]?.firstOrNull().orEmpty()
            val posted = receiveBrowserMessage(clientId, payload.optString("text"))
            if (posted == null) {
                val reason = if (payload.optString("text").toByteArray(Charsets.UTF_8).size > MAX_CHAT_MESSAGE_BYTES) {
                    "消息不能超过 64 KB"
                } else "消息不能为空"
                runCatching { send(JSONObject().put("type", "error").put("message", reason).toString()) }
            }
        }

        override fun onPong(pong: NanoWSD.WebSocketFrame) = Unit
        override fun onException(exception: IOException) { removeWebSocket(this) }
    }

    private fun addWebSocket(socket: NanoWSD.WebSocket) {
        synchronized(webSocketStateLock) {
            if (stopped) return
            webSockets.add(socket)
            connectionGeneration++
            pendingDisconnect?.cancel(false)
            pendingDisconnect = null
            if (!reportedConnected) {
                reportedConnected = true
                emitRealtimeEvent(LanShareRealtimeEvent.ConnectionChanged(true))
            }
        }
    }

    private fun removeWebSocket(socket: NanoWSD.WebSocket) {
        synchronized(webSocketStateLock) {
            if (stopped || !webSockets.remove(socket) || webSockets.isNotEmpty() || !reportedConnected || pendingDisconnect != null) return
            val generation = ++connectionGeneration
            pendingDisconnect = connectionStateExecutor.schedule({
                synchronized(webSocketStateLock) {
                    if (generation != connectionGeneration || webSockets.isNotEmpty() || !reportedConnected) return@synchronized
                    pendingDisconnect = null
                    reportedConnected = false
                    emitRealtimeEvent(LanShareRealtimeEvent.ConnectionChanged(false))
                }
            }, connectionDisconnectGraceMs, TimeUnit.MILLISECONDS)
        }
    }

    override fun stop() {
        synchronized(webSocketStateLock) {
            stopped = true
            connectionGeneration++
            pendingDisconnect?.cancel(false)
            pendingDisconnect = null
            webSockets.clear()
            reportedConnected = false
        }
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
        file?.let { emitRealtimeEvent(LanShareRealtimeEvent.FileAdded(toLanShareFile(it, "peer", uploadedMimeTypes[it.name]))) }
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
                if (socket.isOpen) socket.send(eventWithFile) else removeWebSocket(socket)
            }
                .onFailure { removeWebSocket(socket) }
        }
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
                if (socket.isOpen) socket.send(event) else removeWebSocket(socket)
            }
                .onFailure { removeWebSocket(socket) }
        }
    }

    override fun serve(session: IHTTPSession): Response =
        super.serve(session).apply { addHeader("Referrer-Policy", "no-referrer") }

    override fun serveHttp(session: IHTTPSession): Response {
        val requestPath = session.uri.substringBefore('?')
        return try {
            when {
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
                    if (!reserveUploadCapacity(body.expectedBytes)) {
                        return newFixedLengthResponse(Response.Status.BAD_REQUEST, MIME_PLAINTEXT, "房间文件总大小不能超过 100 GB")
                    }
                    receiveUpload(session, body, target, body.expectedBytes, mimeType, transferId)
                    newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, target.name)
                }

                session.method == Method.GET && requestPath.startsWith("/api/preview/") -> {
                    val file = sharedFile(folder, decodePathSegment(requestPath.substringAfterLast('/')))
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
                    val file = sharedFile(folder, decodePathSegment(requestPath.substringAfterLast('/')))
                    if (file == null) newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "未找到文件")
                    else newFixedLengthResponse(Response.Status.OK, fileMimeType(file), FileInputStream(file), file.length()).apply {
                        addHeader("Content-Length", file.length().toString())
                        addHeader("Cache-Control", "no-store, no-cache, must-revalidate")
                        addHeader(
                            "Content-Disposition",
                            "${if (fileMimeType(file).startsWith("image/")) "inline" else "attachment"}; filename=\"${toLanShareFile(file, "peer", fileMimeType(file)).name}\""
                        )
                    }
                }

                else -> newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "not found")
            }
        } catch (error: Exception) {
            logger.record("lan-server", "request failed path=${session.uri.substringBefore('?')}", error)
            newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "传输失败")
        }
    }

    private fun decodePathSegment(value: String): String = runCatching {
        URLDecoder.decode(value.replace("+", "%2B"), Charsets.UTF_8.name())
    }.getOrDefault(value)
}
