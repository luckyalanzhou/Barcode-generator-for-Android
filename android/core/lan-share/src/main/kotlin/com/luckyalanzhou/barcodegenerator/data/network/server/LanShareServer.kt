package com.luckyalanzhou.barcodegenerator.data.network.server

import com.luckyalanzhou.barcodegenerator.data.network.protocol.LanShareLimits
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
import android.net.Uri
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoWSD
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
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

    private fun uploadLimitError(size: Long): String? = when {
        size > LanShareLimits.MAX_FILE_BYTES -> "单个文件不能超过 5 GB"
        folder.listFiles().orEmpty().filter(::isCommittedSharedFile).sumOf { it.length() } > LanShareLimits.MAX_ROOM_BYTES - size -> "房间文件总大小不能超过 100 GB"
        else -> null
    }

    private fun reserveUploadCapacity(session: IHTTPSession, multipart: Boolean): Long? = synchronized(uploadLock) {
        // 在写入 NanoHTTPD 的临时文件前必须有长度；否则分块请求可先耗尽磁盘，
        // 使 5 GB / 100 GB 限制在写入后才生效。
        val declared = declaredUploadBytes(session)
            ?: return null
        val allowed = LanShareLimits.MAX_FILE_BYTES + if (multipart) 128L * 1024L else 0L
        if (declared !in 1..allowed) return null
        val stored = folder.listFiles().orEmpty().filter(::isCommittedSharedFile).sumOf { it.length() }
        if (stored > LanShareLimits.MAX_ROOM_BYTES - reservedUploadBytes - declared) return null
        reservedUploadBytes += declared
        declared
    }

    private fun uploadCapacityError(session: IHTTPSession, multipart: Boolean): String? {
        val declared = declaredUploadBytes(session)
            ?: return "上传请求缺少文件大小"
        val allowed = LanShareLimits.MAX_FILE_BYTES + if (multipart) 128L * 1024L else 0L
        if (declared !in 1..allowed) return "单个文件不能超过 5 GB"
        return null
    }

    /** 浏览器不能设置受保护的 Content-Length；网页 PUT 同时声明精确文件大小。 */
    private fun declaredUploadBytes(session: IHTTPSession): Long? =
        session.headers["content-length"]?.toLongOrNull()
            ?: session.headers["x-file-size"]?.toLongOrNull()

    private fun releaseUploadCapacity(bytes: Long) = synchronized(uploadLock) {
        reservedUploadBytes = (reservedUploadBytes - bytes).coerceAtLeast(0L)
    }

    /** 先写同目录临时文件，完整复制后再改名，避免半截 ZIP/图片进入分享记录。 */
    private fun commitUpload(source: File, target: File) {
        val temporary = File(folder, ".${target.name}.${System.nanoTime()}.part")
        try {
            val copied = source.inputStream().use { input ->
                temporary.outputStream().use { output -> input.copyTo(output, 16 * 1024) }
            }
            require(copied == source.length()) { "上传内容读取不完整：$copied/${source.length()} 字节" }
            require(temporary.renameTo(target)) { "无法保存上传文件" }
        } finally {
            temporary.delete()
        }
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
                        val name = safeFileName(session.parameters["attachment"]?.firstOrNull().orEmpty().substringAfterLast('/'))
                        val target = File(folder, "app_${System.nanoTime()}_$name")
                        val error = synchronized(uploadLock) {
                            uploadLimitError(source.length()) ?: run {
                                commitUpload(source, target)
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
                        val submittedName = Uri.decode(session.parameters["name"]?.firstOrNull().orEmpty()).ifBlank { "附件" }
                        val clientId = safeBrowserClientId(session.parameters["client"]?.firstOrNull().orEmpty())
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
                                commitUpload(source, target)
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

    private fun decodePathSegment(value: String): String = runCatching {
        URLDecoder.decode(value.replace("+", "%2B"), Charsets.UTF_8.name())
    }.getOrDefault(value)
}
