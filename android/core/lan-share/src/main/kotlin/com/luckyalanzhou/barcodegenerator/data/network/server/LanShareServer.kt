package com.luckyalanzhou.barcodegenerator.data.network.server

import com.luckyalanzhou.barcodegenerator.data.network.protocol.LanShareLimits
import com.luckyalanzhou.barcodegenerator.data.network.protocol.LAN_SHARE_STREAM_BUFFER_SIZE
import com.luckyalanzhou.barcodegenerator.data.network.protocol.isCommittedSharedFile
import com.luckyalanzhou.barcodegenerator.data.network.protocol.listFiles
import com.luckyalanzhou.barcodegenerator.data.network.protocol.mimeTypeForName
import com.luckyalanzhou.barcodegenerator.data.network.protocol.safeBrowserClientId
import com.luckyalanzhou.barcodegenerator.data.network.protocol.safeFileName
import com.luckyalanzhou.barcodegenerator.data.network.protocol.sharedFile
import com.luckyalanzhou.barcodegenerator.data.network.protocol.toLanShareFile
import com.luckyalanzhou.barcodegenerator.data.preview.LanShareWebImagePreviewCache
import com.luckyalanzhou.barcodegenerator.data.network.web.LanShareWebAccessPage
import com.luckyalanzhou.barcodegenerator.data.network.web.LanShareWebTemplates
import com.luckyalanzhou.barcodegenerator.domain.AppLogger
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
import java.util.concurrent.CopyOnWriteArraySet

/** 浏览器端服务：HTTP 文件接口和 WebSocket 实时文件事件。 */
internal class LanShareServer(
    host: String,
    port: Int,
    private val folder: File,
    private val accessToken: String,
    manualCode: String,
    private val logger: AppLogger,
    previewCacheFolder: File = File(folder.parentFile, ".lan-share-web-preview"),
) : NanoWSD(host, port) {
    private val accessControl = LanShareAccessControl(accessToken)
    private val manualCodeGate = LanShareManualCodeGate(manualCode)
    @Volatile private var lastBrowserRequestAt = 0L
    @Volatile private var fileVersion = 0L
    private val webSockets = CopyOnWriteArraySet<NanoWSD.WebSocket>()
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
    private fun receiveUpload(session: IHTTPSession, body: UploadBody, target: File, reservation: Long) {
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
                reservedUploadBytes = (reservedUploadBytes - reservation).coerceAtLeast(0L)
                reservationHeld = false
            }
            notifyFilesChanged(target)
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

    fun browserConnected() = System.currentTimeMillis() - lastBrowserRequestAt < 4_500L

    override fun openWebSocket(handshake: IHTTPSession): NanoWSD.WebSocket = object : NanoWSD.WebSocket(handshake) {
        override fun onOpen() {
            webSockets.add(this)
            lastBrowserRequestAt = System.currentTimeMillis()
            // 新网页刚连上时补发当前版本，填补页面初始读取与 WebSocket 建连之间的文件事件。
            runCatching { send(JSONObject().put("type", "files").put("version", fileVersion).toString()) }
        }

        override fun onClose(code: NanoWSD.WebSocketFrame.CloseCode, reason: String, initiatedByRemote: Boolean) {
            webSockets.remove(this)
        }

        override fun onMessage(message: NanoWSD.WebSocketFrame) {
            // 客户端在 onopen 后请求快照；此时浏览器已安装 onmessage，不会漏掉首批文件。
            if (message.textPayload == "sync") runCatching { send(fileSnapshotEvent()) }
        }

        override fun onPong(pong: NanoWSD.WebSocketFrame) = Unit
        override fun onException(exception: IOException) { webSockets.remove(this) }
    }

    private fun notifyFilesChanged(file: File? = null) {
        fileVersion++
        if (webSockets.isEmpty()) return
        val event = JSONObject().put("type", "files").put("version", fileVersion).toString()
        val eventWithFile = file?.let { uploaded ->
            val record = toLanShareFile(uploaded, "peer")
            JSONObject(event).put("file", JSONObject()
                .put("id", record.id)
                .put("name", record.name)
                .put("size", record.size)
                .put("modifiedAt", record.modifiedAt)
                .put("sender", record.sender)
            ).toString()
        } ?: event
        webSockets.toList().forEach { socket ->
            runCatching { if (socket.isOpen) socket.send(eventWithFile) }
                .onFailure { webSockets.remove(socket) }
        }
    }

    private fun fileSnapshotEvent(): String = JSONObject().put("type", "snapshot").put("files", JSONArray(
        listFiles(folder, "peer").map { record -> JSONObject()
            .put("id", record.id)
            .put("name", record.name)
            .put("size", record.size)
            .put("modifiedAt", record.modifiedAt)
            .put("sender", record.sender)
        }
    )).toString()

    override fun serve(session: IHTTPSession): Response {
        if (session.method == Method.POST && session.uri.substringBefore('?') == "/join") {
            return joinWithManualCode(session)
        }
        val authorized = accessControl.allows(
            session.parameters["token"]?.singleOrNull(),
            session.headers["x-lan-token"],
        )
        if (!authorized) {
            if (session.method == Method.GET && session.uri.substringBefore('?') == "/") {
                return newFixedLengthResponse(
                    Response.Status.OK,
                    "text/html; charset=utf-8",
                    LanShareWebAccessPage.render(),
                ).apply {
                    addHeader("Cache-Control", "no-store")
                    addHeader("Referrer-Policy", "no-referrer")
                }
            }
            return newFixedLengthResponse(Response.Status.FORBIDDEN, MIME_PLAINTEXT, "需要有效的分享访问码")
                .apply { addHeader("Cache-Control", "no-store") }
        }
        return super.serve(session).apply { addHeader("Referrer-Policy", "no-referrer") }
    }

    private fun joinWithManualCode(session: IHTTPSession): Response {
        val length = session.headers["content-length"]?.toLongOrNull()
        if (length == null || length !in 1..128 ||
            !session.headers["content-type"].orEmpty().startsWith("application/x-www-form-urlencoded", ignoreCase = true)
        ) return newFixedLengthResponse(Response.Status.BAD_REQUEST, MIME_PLAINTEXT, "无效的访问码请求")
        val submitted = try {
            session.parseBody(HashMap())
            session.parameters["code"]?.singleOrNull()
        } catch (_: Exception) {
            return newFixedLengthResponse(Response.Status.BAD_REQUEST, MIME_PLAINTEXT, "无法读取访问码")
        }
        return when (manualCodeGate.check(session.remoteIpAddress, submitted)) {
            LanShareManualCodeGate.Result.ACCEPTED -> newFixedLengthResponse(
                Response.Status.REDIRECT_SEE_OTHER, MIME_PLAINTEXT, "",
            ).apply { addHeader("Location", "/?token=$accessToken") }
            LanShareManualCodeGate.Result.INVALID -> newFixedLengthResponse(
                Response.Status.FORBIDDEN, "text/html; charset=utf-8",
                LanShareWebAccessPage.render("访问码错误，请返回后重试"),
            )
            LanShareManualCodeGate.Result.TOO_MANY_ATTEMPTS -> newFixedLengthResponse(
                Response.Status.TOO_MANY_REQUESTS, "text/html; charset=utf-8",
                LanShareWebAccessPage.render("尝试次数过多，请五分钟后重试或扫描二维码"),
            )
        }.apply {
            addHeader("Cache-Control", "no-store")
            addHeader("Referrer-Policy", "no-referrer")
        }
    }

    override fun serveHttp(session: IHTTPSession): Response {
        if (session.headers["user-agent"].orEmpty().contains("Mozilla", ignoreCase = true)) {
            lastBrowserRequestAt = System.currentTimeMillis()
        }
        val requestPath = session.uri.substringBefore('?')
        return try {
            when {
                session.method == Method.GET && requestPath == "/api/files" -> newFixedLengthResponse(
                    Response.Status.OK,
                    "application/json; charset=utf-8",
                    JSONArray(listFiles(folder, "peer").map {
                        JSONObject().put("id", it.id).put("name", it.name).put("size", it.size)
                            .put("modifiedAt", it.modifiedAt).put("sender", it.sender)
                    }).toString()
                ).apply { addHeader("Cache-Control", "no-store, no-cache, must-revalidate") }

                session.method == Method.GET && requestPath == "/api/presence" -> newFixedLengthResponse(
                    Response.Status.OK,
                    MIME_PLAINTEXT,
                    "ok"
                ).apply { addHeader("Cache-Control", "no-store, no-cache, must-revalidate") }

                session.method == Method.GET && requestPath == "/api/events" -> {
                    val since = session.parameters["since"]?.firstOrNull()?.toLongOrNull() ?: 0L
                    val deadline = System.currentTimeMillis() + 25_000L
                    while (fileVersion <= since && System.currentTimeMillis() < deadline) Thread.sleep(120L)
                    newFixedLengthResponse(
                        Response.Status.OK,
                        "application/json; charset=utf-8",
                        JSONObject().put("version", fileVersion).toString()
                    ).apply { addHeader("Cache-Control", "no-store, no-cache, must-revalidate") }
                }

                session.method == Method.GET && requestPath == "/" -> newFixedLengthResponse(
                    Response.Status.OK,
                    "text/html; charset=utf-8",
                    LanShareWebTemplates.page()
                ).apply { addHeader("Cache-Control", "no-store, no-cache, must-revalidate") }

                session.method == Method.PUT && requestPath == "/upload" -> {
                    val body = uploadBody(session)
                        ?: return newFixedLengthResponse(Response.Status.BAD_REQUEST, MIME_PLAINTEXT, "上传请求长度无效")
                    if (body.expectedBytes !in 1..LanShareLimits.MAX_FILE_BYTES) {
                        return newFixedLengthResponse(Response.Status.BAD_REQUEST, MIME_PLAINTEXT, "单个文件不能超过 5 GB")
                    }
                    val submittedName = session.parameters["name"]?.firstOrNull().orEmpty().ifBlank { "附件" }
                    val client = session.parameters["client"]?.firstOrNull().orEmpty()
                    val name = safeFileName(submittedName)
                    val targetPrefix = System.nanoTime()
                    val target = if (client == APP_UPLOAD_CLIENT) {
                        File(folder, "app_${targetPrefix}_$name")
                    } else {
                        File(folder, "web_${targetPrefix}_${safeBrowserClientId(client)}_$name")
                    }
                    if (!reserveUploadCapacity(body.expectedBytes)) {
                        return newFixedLengthResponse(Response.Status.BAD_REQUEST, MIME_PLAINTEXT, "房间文件总大小不能超过 100 GB")
                    }
                    receiveUpload(session, body, target, body.expectedBytes)
                    newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, target.name)
                }

                session.method == Method.GET && requestPath.startsWith("/api/preview/") -> {
                    val file = sharedFile(folder, decodePathSegment(requestPath.substringAfterLast('/')))
                    if (file == null) newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "未找到文件")
                    else {
                        val preview = webImagePreviewCache.getOrCreate(file)
                        if (preview == null) {
                            newFixedLengthResponse(
                                Response.Status.NOT_ACCEPTABLE,
                                MIME_PLAINTEXT,
                                "此图片格式暂不支持网页预览，请点击文件名下载原图",
                            )
                        } else {
                            newFixedLengthResponse(Response.Status.OK, "image/jpeg", FileInputStream(preview), preview.length()).apply {
                                addHeader("Content-Length", preview.length().toString())
                                addHeader("Cache-Control", "no-store, no-cache, must-revalidate")
                                addHeader("X-Content-Type-Options", "nosniff")
                            }
                        }
                    }
                }

                session.method == Method.GET && requestPath.startsWith("/api/download/") -> {
                    val file = sharedFile(folder, decodePathSegment(requestPath.substringAfterLast('/')))
                    if (file == null) newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "未找到文件")
                    else newFixedLengthResponse(Response.Status.OK, mimeTypeForName(file.name), FileInputStream(file), file.length()).apply {
                        addHeader("Content-Length", file.length().toString())
                        addHeader("Cache-Control", "no-store, no-cache, must-revalidate")
                        addHeader(
                            "Content-Disposition",
                            "${if (mimeTypeForName(file.name).startsWith("image/")) "inline" else "attachment"}; filename=\"${toLanShareFile(file, "peer").name}\""
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

    private companion object {
        const val MAX_CHUNK_LINE_BYTES = 8 * 1024
        const val MAX_CHUNK_TRAILER_BYTES = 16 * 1024
        const val APP_UPLOAD_CLIENT = "app"
    }

    private fun decodePathSegment(value: String): String = runCatching {
        URLDecoder.decode(value.replace("+", "%2B"), Charsets.UTF_8.name())
    }.getOrDefault(value)
}
