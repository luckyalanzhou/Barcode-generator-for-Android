package com.luckyalanzhou.barcodegenerator

import android.net.Uri
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoWSD
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.util.concurrent.CopyOnWriteArraySet

/** 浏览器端服务：HTTP 文件接口和 WebSocket 实时文件事件。 */
internal class LanShareServer(port: Int, private val folder: File) : NanoWSD(port) {
    @Volatile private var lastBrowserRequestAt = 0L
    @Volatile private var fileVersion = 0L
    private val webSockets = CopyOnWriteArraySet<NanoWSD.WebSocket>()
    private val uploadLock = Any()
    private var reservedUploadBytes = 0L

    private fun uploadLimitError(size: Long): String? = when {
        size > LanShareLimits.MAX_FILE_BYTES -> "单个文件不能超过 5 GB"
        folder.listFiles().orEmpty().filter { it.isFile }.sumOf { it.length() } > LanShareLimits.MAX_ROOM_BYTES - size -> "房间文件总大小不能超过 100 GB"
        else -> null
    }

    private fun reserveUploadCapacity(session: IHTTPSession, multipart: Boolean): Long? = synchronized(uploadLock) {
        // 在写入 NanoHTTPD 的临时文件前必须有长度；否则分块请求可先耗尽磁盘，
        // 使 5 GB / 100 GB 限制在写入后才生效。
        val declared = session.headers["content-length"]?.toLongOrNull()
            ?: return null
        val allowed = LanShareLimits.MAX_FILE_BYTES + if (multipart) 128L * 1024L else 0L
        if (declared !in 1..allowed) return null
        val stored = folder.listFiles().orEmpty().filter { it.isFile }.sumOf { it.length() }
        if (stored > LanShareLimits.MAX_ROOM_BYTES - reservedUploadBytes - declared) return null
        reservedUploadBytes += declared
        declared
    }

    private fun uploadCapacityError(session: IHTTPSession, multipart: Boolean): String? {
        val declared = session.headers["content-length"]?.toLongOrNull()
            ?: return "上传请求缺少文件大小"
        val allowed = LanShareLimits.MAX_FILE_BYTES + if (multipart) 128L * 1024L else 0L
        if (declared !in 1..allowed) return "单个文件不能超过 5 GB"
        return null
    }

    private fun releaseUploadCapacity(bytes: Long) = synchronized(uploadLock) {
        reservedUploadBytes = (reservedUploadBytes - bytes).coerceAtLeast(0L)
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
                    val since = session.parms["since"]?.toLongOrNull() ?: 0L
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

                session.method == Method.POST && requestPath == "/api/upload" -> {
                    uploadCapacityError(session, multipart = true)?.let {
                        return newFixedLengthResponse(Response.Status.BAD_REQUEST, MIME_PLAINTEXT, it)
                    }
                    val reservation = reserveUploadCapacity(session, multipart = true)
                        ?: return newFixedLengthResponse(Response.Status.BAD_REQUEST, MIME_PLAINTEXT, "房间文件总大小不能超过 100 GB")
                    try {
                        val bodies = HashMap<String, String>()
                        session.parseBody(bodies)
                        val source = bodies["attachment"]?.let(::File)
                            ?: return newFixedLengthResponse(Response.Status.BAD_REQUEST, MIME_PLAINTEXT, "未读取到附件")
                        val name = safeFileName(session.parms["attachment"].orEmpty().substringAfterLast('/'))
                        val target = File(folder, "app_${System.nanoTime()}_$name")
                        val error = synchronized(uploadLock) {
                            uploadLimitError(source.length()) ?: run {
                                source.copyTo(target, overwrite = true)
                                notifyFilesChanged(target)
                                null
                            }
                        }
                        if (error == null) newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, target.name)
                        else newFixedLengthResponse(Response.Status.BAD_REQUEST, MIME_PLAINTEXT, error)
                    } finally {
                        releaseUploadCapacity(reservation)
                    }
                }

                session.method == Method.PUT && requestPath == "/upload" -> {
                    uploadCapacityError(session, multipart = false)?.let {
                        return newFixedLengthResponse(Response.Status.BAD_REQUEST, MIME_PLAINTEXT, it)
                    }
                    val reservation = reserveUploadCapacity(session, multipart = false)
                        ?: return newFixedLengthResponse(Response.Status.BAD_REQUEST, MIME_PLAINTEXT, "房间文件总大小不能超过 100 GB")
                    try {
                        val submittedName = Uri.decode(session.parms["name"].orEmpty()).ifBlank { "附件" }
                        val clientId = safeBrowserClientId(session.parms["client"].orEmpty())
                        val name = safeFileName(submittedName)
                        val targetPrefix = System.nanoTime()
                        val files = HashMap<String, String>()
                        session.parseBody(files)
                        val temporaryFile = files["content"]
                            ?: return newFixedLengthResponse(Response.Status.BAD_REQUEST, MIME_PLAINTEXT, "未读取到上传内容")
                        val target = File(folder, "web_${targetPrefix}_${clientId}_$name")
                        val source = File(temporaryFile)
                        val error = synchronized(uploadLock) {
                            uploadLimitError(source.length()) ?: run {
                                source.copyTo(target, overwrite = true)
                                notifyFilesChanged(target)
                                null
                            }
                        }
                        if (error == null) newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, target.name)
                        else newFixedLengthResponse(Response.Status.BAD_REQUEST, MIME_PLAINTEXT, error)
                    } finally {
                        releaseUploadCapacity(reservation)
                    }
                }

                session.method == Method.GET && requestPath.startsWith("/api/download/") -> {
                    val file = sharedFile(folder, Uri.decode(requestPath.substringAfterLast('/')))
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
            DebugLog.record("lan-server", "request failed uri=${session.uri}", error)
            newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "传输失败")
        }
    }
}
