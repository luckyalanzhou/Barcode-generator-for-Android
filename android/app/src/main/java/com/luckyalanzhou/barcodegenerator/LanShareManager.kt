package com.luckyalanzhou.barcodegenerator

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoWSD
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.URL
import java.io.IOException
import java.util.concurrent.CopyOnWriteArraySet

/** 单次会话的统一传输记录；App 与浏览器均以该记录生成消息气泡。 */
data class LanShareFile(val id: String, val name: String, val size: Long, val modifiedAt: Long, val sender: String = "peer")
data class LanShareSession(val baseUrl: String)

/** 仅在同一局域网使用的临时文件房间；地址由随机端口标识当前会话。 */
class LanShareManager(private val context: Context) {
    companion object {
        const val MAX_FILE_BYTES = 5L * 1024L * 1024L * 1024L
        const val MAX_ROOM_BYTES = 100L * 1024L * 1024L * 1024L

        internal fun areOnSameRouterSubnet(local: Inet4Address, remote: Inet4Address, prefixLength: Int): Boolean {
            if (prefixLength !in 0..32) return false
            val localValue = local.address.fold(0L) { value, byte -> (value shl 8) or (byte.toInt() and 0xff).toLong() }
            val remoteValue = remote.address.fold(0L) { value, byte -> (value shl 8) or (byte.toInt() and 0xff).toLong() }
            val mask = if (prefixLength == 0) 0L else (0xffff_ffffL shl (32 - prefixLength)) and 0xffff_ffffL
            return (localValue and mask) == (remoteValue and mask)
        }
    }
    private val folder = File(context.filesDir, "lan-share").apply { mkdirs() }
    private var server: LanShareServer? = null
    private var lastPort: Int? = null

    /** 分享服务只使用 Wi-Fi 默认网关所在子网的 IPv4 地址。 */
    fun isOnLocalNetwork(): Boolean {
        return routerIpv4Addresses().isNotEmpty()
    }

    /** 扫码地址必须和当前路由器网关的 IPv4 子网一致，不能硬编码某个地址段。 */
    fun isRouterLanHost(host: String?): Boolean = runCatching {
        val remote = java.net.InetAddress.getByName(host) as? Inet4Address ?: return@runCatching false
        routerIpv4Addresses().any { local -> areOnSameRouterSubnet(local.address as Inet4Address, remote, local.prefixLength) }
    }.getOrDefault(false)

    private fun routerIpv4Addresses(): List<android.net.LinkAddress> = run {
        val connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        // 不使用 activeNetwork：VPN 或容器网络可能接管它，并返回 172.x 等虚拟地址。
        // 仅枚举系统标记为 Wi-Fi 的网络，并且必须同时找到该 Wi-Fi 的 IPv4 默认网关。
        connectivity.allNetworks.asSequence()
            .filter { network ->
                connectivity.getNetworkCapabilities(network)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
            }
            .flatMap { network ->
                val properties = connectivity.getLinkProperties(network) ?: return@flatMap emptySequence()
                val gateway = properties.routes.firstOrNull { route ->
                    route.isDefaultRoute && route.gateway is Inet4Address
                }?.gateway as? Inet4Address ?: return@flatMap emptySequence()
                properties.linkAddresses.asSequence().filter { address ->
                    val local = address.address as? Inet4Address ?: return@filter false
                    !local.isLoopbackAddress && !local.isAnyLocalAddress && !local.isMulticastAddress &&
                        areOnSameRouterSubnet(local, gateway, address.prefixLength)
                }
            }
            .toList()
    }

    fun start(): LanShareSession = start(clearSharedFiles = true)

    private fun start(clearSharedFiles: Boolean): LanShareSession {
        check(isOnLocalNetwork()) { "Error 当前不处于局域网" }
        stop()
        if (clearSharedFiles) clearFiles()
        val address = routerIpv4Addresses()
            .mapNotNull { it.address as? Inet4Address }
            .firstOrNull()
            ?.hostAddress ?: error("未连接到局域网")
        val ports = (18080..28080).filter { it != lastPort }.shuffled() + listOfNotNull(lastPort)
        val running = ports.firstNotNullOfOrNull { port ->
            runCatching { LanShareServer(port, folder).also { it.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false) } }.getOrNull()
        } ?: error("无法启动局域网分享服务")
        server = running
        lastPort = running.listeningPort
        return LanShareSession("http://$address:${running.listeningPort}")
    }

    fun restart(): LanShareSession {
        return start(clearSharedFiles = false)
    }

    fun browserConnected() = server?.browserConnected() == true

    fun stop(clearSharedFiles: Boolean = false) { server?.stop(); server = null; if (clearSharedFiles) clearFiles() }
    fun localFiles() = listFiles(folder, "app")
    fun localFile(id: String): File? = sharedFile(folder, id)
    private fun clearFiles() { folder.listFiles().orEmpty().forEach { it.delete() } }

    fun list(session: LanShareSession) = request(session, "/api/files") { connection ->
        JSONArray(connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }).let { json ->
            (0 until json.length()).map { index -> json.getJSONObject(index).let { LanShareFile(it.getString("id"), it.getString("name"), it.getLong("size"), it.getLong("modifiedAt"), it.optString("sender", "peer")) } }
        }
    }

    fun upload(session: LanShareSession, uri: Uri): String {
        val name = context.contentResolver.query(uri, null, null, null, null)?.use { cursor -> cursor.moveToFirst(); cursor.getString(cursor.getColumnIndexOrThrow(android.provider.OpenableColumns.DISPLAY_NAME)) } ?: "附件"
        val size = context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
        if (size < 0) error("无法确定文件大小，请先将文件保存到本机")
        require(size <= MAX_FILE_BYTES) { "单个文件不能超过 5 GB" }
        val boundary = "----BarcodeShare${System.currentTimeMillis()}"
        val header = "--$boundary\r\nContent-Disposition: form-data; name=\"attachment\"; filename=\"${multipartFileName(name)}\"\r\nContent-Type: application/octet-stream\r\n\r\n".toByteArray()
        val footer = "\r\n--$boundary--\r\n".toByteArray()
        val connection = (URL(session.baseUrl + "/api/upload").openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000; readTimeout = 120_000; requestMethod = "POST"; doOutput = true
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            // NanoHTTPD 对 chunked 大请求会尝试构造整段字符串；固定长度可让其直接落到临时文件。
            setFixedLengthStreamingMode(header.size.toLong() + size + footer.size)
        }
        try {
            connection.outputStream.buffered().use { output ->
                output.write(header)
                context.contentResolver.openInputStream(uri)?.use { it.copyTo(output, 16 * 1024) } ?: error("无法读取附件")
                output.write(footer)
            }
            if (connection.responseCode !in 200..299) error("上传失败：${connection.responseCode}")
            return connection.inputStream.bufferedReader().use { it.readText().trim() }
                .ifBlank { error("上传完成但未收到文件标识") }
        } finally { connection.disconnect() }
    }

    fun uploadText(session: LanShareSession, text: String): String {
        val name = text.trim().take(100).ifBlank { "消息" }
        val body = text.toByteArray(Charsets.UTF_8)
        val boundary = "----BarcodeShare${System.currentTimeMillis()}"
        val header = "--$boundary\r\nContent-Disposition: form-data; name=\"attachment\"; filename=\"${multipartFileName(name)}\"\r\nContent-Type: text/plain; charset=utf-8\r\n\r\n".toByteArray()
        val footer = "\r\n--$boundary--\r\n".toByteArray()
        val connection = (URL(session.baseUrl + "/api/upload").openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000; readTimeout = 30_000; requestMethod = "POST"; doOutput = true
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            setFixedLengthStreamingMode(header.size.toLong() + body.size + footer.size)
        }
        try {
            connection.outputStream.buffered().use { output -> output.write(header); output.write(body); output.write(footer) }
            if (connection.responseCode !in 200..299) error("发送失败：${connection.responseCode}")
            return connection.inputStream.bufferedReader().use { it.readText().trim() }
                .ifBlank { error("发送完成但未收到文件标识") }
        } finally { connection.disconnect() }
    }

    fun download(session: LanShareSession, id: String, destination: Uri) = request(session, "/api/download/${Uri.encode(id)}") { connection ->
        context.contentResolver.openOutputStream(destination)?.use { output -> connection.inputStream.use { it.copyTo(output) } } ?: error("无法写入文件")
    }

    fun downloadPreview(session: LanShareSession, id: String, destination: File) = request(session, "/api/download/${Uri.encode(id)}") { connection ->
        destination.parentFile?.mkdirs()
        destination.outputStream().use { output -> connection.inputStream.use { it.copyTo(output) } }
    }

    private fun <T> request(session: LanShareSession, path: String, output: Boolean = false, block: (HttpURLConnection) -> T): T {
        require(isRouterLanHost(Uri.parse(session.baseUrl).host)) { "分享地址不在当前路由器网关子网内" }
        val connection = (URL(session.baseUrl + path).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000; readTimeout = 30_000; requestMethod = if (output) "POST" else "GET"; doOutput = output
            if (output) setRequestProperty("Content-Type", "application/octet-stream")
        }
        return try { val value = block(connection); if (connection.responseCode !in 200..299) error("连接失败：${connection.responseCode}"); value } finally { connection.disconnect() }
    }

    private class LanShareServer(port: Int, private val folder: File) : NanoWSD(port) {
        @Volatile private var lastBrowserRequestAt = 0L
        @Volatile private var fileVersion = 0L
        private val webSockets = CopyOnWriteArraySet<NanoWSD.WebSocket>()
        private val uploadLock = Any()
        private var reservedUploadBytes = 0L

        private fun uploadLimitError(size: Long): String? = when {
            size > MAX_FILE_BYTES -> "单个文件不能超过 5 GB"
            folder.listFiles().orEmpty().filter { it.isFile }.sumOf { it.length() } > MAX_ROOM_BYTES - size -> "房间文件总大小不能超过 100 GB"
            else -> null
        }

        private fun reserveUploadCapacity(session: IHTTPSession, multipart: Boolean): Long? = synchronized(uploadLock) {
            // 在写入 NanoHTTPD 的临时文件前必须有长度；否则分块请求可先耗尽磁盘，
            // 使 5 GB / 100 GB 限制在写入后才生效。
            val declared = session.headers["content-length"]?.toLongOrNull()
                ?: return null
            val allowed = MAX_FILE_BYTES + if (multipart) 128L * 1024L else 0L
            if (declared !in 1..allowed) return null
            val stored = folder.listFiles().orEmpty().filter { it.isFile }.sumOf { it.length() }
            if (stored > MAX_ROOM_BYTES - reservedUploadBytes - declared) return null
            reservedUploadBytes += declared
            declared
        }

        private fun uploadCapacityError(session: IHTTPSession, multipart: Boolean): String? {
            val declared = session.headers["content-length"]?.toLongOrNull()
                ?: return "上传请求缺少文件大小"
            val allowed = MAX_FILE_BYTES + if (multipart) 128L * 1024L else 0L
            if (declared !in 1..allowed) return "单个文件不能超过 5 GB"
            return null
        }

        private fun releaseUploadCapacity(bytes: Long) = synchronized(uploadLock) { reservedUploadBytes = (reservedUploadBytes - bytes).coerceAtLeast(0L) }

        fun browserConnected() = System.currentTimeMillis() - lastBrowserRequestAt < 4_500L

        override fun openWebSocket(handshake: IHTTPSession): NanoWSD.WebSocket = object : NanoWSD.WebSocket(handshake) {
            override fun onOpen() {
                webSockets.add(this)
                lastBrowserRequestAt = System.currentTimeMillis()
                // 新网页刚连上时补发当前版本，填补页面初始读取与 WebSocket 建连之间的文件事件。
                runCatching { send(JSONObject().put("type", "files").put("version", fileVersion).toString()) }
            }
            override fun onClose(code: NanoWSD.WebSocketFrame.CloseCode, reason: String, initiatedByRemote: Boolean) { webSockets.remove(this) }
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
            webSockets.toList().forEach { socket -> runCatching { if (socket.isOpen) socket.send(eventWithFile) }.onFailure { webSockets.remove(socket) } }
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
            if (session.headers["user-agent"].orEmpty().contains("Mozilla", ignoreCase = true)) lastBrowserRequestAt = System.currentTimeMillis()
            val requestPath = session.uri.substringBefore('?')
            return try {
                when {
                    session.method == Method.GET && requestPath == "/api/files" -> newFixedLengthResponse(Response.Status.OK, "application/json; charset=utf-8", JSONArray(listFiles(folder, "peer").map { JSONObject().put("id", it.id).put("name", it.name).put("size", it.size).put("modifiedAt", it.modifiedAt).put("sender", it.sender) }).toString()).apply { addHeader("Cache-Control", "no-store, no-cache, must-revalidate") }
                    session.method == Method.GET && requestPath == "/api/presence" -> newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, "ok").apply { addHeader("Cache-Control", "no-store, no-cache, must-revalidate") }
                    session.method == Method.GET && requestPath == "/api/events" -> {
                        val since = session.parms["since"]?.toLongOrNull() ?: 0L
                        val deadline = System.currentTimeMillis() + 25_000L
                        while (fileVersion <= since && System.currentTimeMillis() < deadline) Thread.sleep(120L)
                        newFixedLengthResponse(Response.Status.OK, "application/json; charset=utf-8", JSONObject().put("version", fileVersion).toString()).apply { addHeader("Cache-Control", "no-store, no-cache, must-revalidate") }
                    }
                    session.method == Method.GET && requestPath == "/" -> newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", LanShareWebTemplates.page()).apply { addHeader("Cache-Control", "no-store, no-cache, must-revalidate") }
                    session.method == Method.POST && requestPath == "/api/upload" -> {
                        uploadCapacityError(session, multipart = true)?.let { return newFixedLengthResponse(Response.Status.BAD_REQUEST, MIME_PLAINTEXT, it) }
                        val reservation = reserveUploadCapacity(session, multipart = true) ?: return newFixedLengthResponse(Response.Status.BAD_REQUEST, MIME_PLAINTEXT, "房间文件总大小不能超过 100 GB")
                        try {
                            val bodies = HashMap<String, String>(); session.parseBody(bodies)
                            val source = bodies["attachment"]?.let(::File) ?: return newFixedLengthResponse(Response.Status.BAD_REQUEST, MIME_PLAINTEXT, "未读取到附件")
                            val name = safeFileName(session.parms["attachment"].orEmpty().substringAfterLast('/'))
                            val target = File(folder, "app_${System.nanoTime()}_$name")
                            val error = synchronized(uploadLock) { uploadLimitError(source.length()) ?: run { source.copyTo(target, overwrite = true); notifyFilesChanged(target); null } }
                            if (error == null) newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, target.name) else newFixedLengthResponse(Response.Status.BAD_REQUEST, MIME_PLAINTEXT, error)
                        } finally { releaseUploadCapacity(reservation) }
                    }
                    session.method == Method.PUT && requestPath == "/upload" -> {
                        uploadCapacityError(session, multipart = false)?.let { return newFixedLengthResponse(Response.Status.BAD_REQUEST, MIME_PLAINTEXT, it) }
                        val reservation = reserveUploadCapacity(session, multipart = false) ?: return newFixedLengthResponse(Response.Status.BAD_REQUEST, MIME_PLAINTEXT, "房间文件总大小不能超过 100 GB")
                        try {
                            val submittedName = Uri.decode(session.parms["name"].orEmpty()).ifBlank { "附件" }
                            val clientId = safeBrowserClientId(session.parms["client"].orEmpty())
                            val name = safeFileName(submittedName)
                            val targetPrefix = System.nanoTime()
                            val files = HashMap<String, String>(); session.parseBody(files)
                            val temporaryFile = files["content"] ?: return newFixedLengthResponse(Response.Status.BAD_REQUEST, MIME_PLAINTEXT, "未读取到上传内容")
                            val target = File(folder, "web_${targetPrefix}_${clientId}_$name")
                            val source = File(temporaryFile)
                            val error = synchronized(uploadLock) { uploadLimitError(source.length()) ?: run { source.copyTo(target, overwrite = true); notifyFilesChanged(target); null } }
                            if (error == null) newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, target.name) else newFixedLengthResponse(Response.Status.BAD_REQUEST, MIME_PLAINTEXT, error)
                        } finally { releaseUploadCapacity(reservation) }
                    }
                    session.method == Method.GET && requestPath.startsWith("/api/download/") -> {
                        val file = sharedFile(folder, Uri.decode(requestPath.substringAfterLast('/')))
                        if (file == null) newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "未找到文件")
                        else newFixedLengthResponse(Response.Status.OK, mimeTypeForName(file.name), FileInputStream(file), file.length()).apply { addHeader("Content-Disposition", "${if (mimeTypeForName(file.name).startsWith("image/")) "inline" else "attachment"}; filename=\"${toLanShareFile(file, "peer").name}\"") }
                    }
                    else -> newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "not found")
                }
            } catch (_: Exception) { newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "传输失败") }
        }

    }
}

private fun multipartFileName(value: String) = value.replace(Regex("[\r\n\"]"), "_")
private fun safeFileName(value: String) = value.replace(Regex("[\\\\/:*?\"<>|\r\n]"), "_").take(100).ifBlank { "附件" }
private fun safeBrowserClientId(value: String) = value.takeIf { it.matches(Regex("c[a-zA-Z0-9_-]{8,63}")) } ?: "clegacy"

/** 共享目录只允许直接子文件，拒绝编码后的 ../ 等路径穿越。 */
private fun sharedFile(folder: File, id: String): File? {
    val root = folder.canonicalFile
    val candidate = File(root, id).canonicalFile
    return candidate.takeIf { it.isFile && it.parentFile == root }
}

private fun mimeTypeForName(name: String) = when (name.substringAfterLast('.', "").lowercase()) {
    "jpg", "jpeg" -> "image/jpeg"
    "png" -> "image/png"
    "gif" -> "image/gif"
    "webp" -> "image/webp"
    "heic" -> "image/heic"
    "heif" -> "image/heif"
    "mp4" -> "video/mp4"
    else -> "application/octet-stream"
}

private fun listFiles(folder: File, sender: String) = folder.listFiles().orEmpty().filter { it.isFile }.sortedBy { it.lastModified() }.map { file -> toLanShareFile(file, sender) }

internal fun toLanShareFile(file: File, sender: String): LanShareFile {
    val browserMatch = Regex("^web_-?\\d+_(c[a-zA-Z0-9_-]{8,63})_(.*)$").matchEntire(file.name)
    val fromBrowser = file.name.startsWith("web_")
    val fromApp = file.name.startsWith("app_")
    val displayName = browserMatch?.groupValues?.get(2)
        ?: if (fromBrowser || fromApp) file.name.substringAfter('_').substringAfter('_', file.name) else file.name.substringAfter('_', file.name)
    val source = browserMatch?.groupValues?.get(1)?.let { "browser:$it" }
        ?: if (fromBrowser) "browser" else if (fromApp) "app" else sender
    return LanShareFile(file.name, displayName, file.length(), file.lastModified(), source)
}
