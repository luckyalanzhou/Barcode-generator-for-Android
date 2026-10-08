package com.luckyalanzhou.barcodegenerator.data.network.client

import com.luckyalanzhou.barcodegenerator.domain.LanShareFile
import com.luckyalanzhou.barcodegenerator.domain.LanShareSession
import com.luckyalanzhou.barcodegenerator.domain.LanShareUploadSource
import com.luckyalanzhou.barcodegenerator.data.network.protocol.LanShareLimits
import com.luckyalanzhou.barcodegenerator.data.network.protocol.LAN_SHARE_STREAM_BUFFER_SIZE
import com.luckyalanzhou.barcodegenerator.data.network.protocol.copyLanShareUpload
import com.luckyalanzhou.barcodegenerator.data.network.protocol.toLanShareFile
import com.luckyalanzhou.barcodegenerator.data.network.protocol.uploadedMimeType

import com.luckyalanzhou.barcodegenerator.domain.AppLogger


import android.net.Uri
import org.json.JSONArray
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CancellationException

/** App 端访问浏览器分享服务的 HTTP 客户端；不包含服务端生命周期逻辑。 */
internal class LanShareClient(
    private val isRouterLanHost: (String?) -> Boolean,
    private val logger: AppLogger,
) {
    private class ActiveUpload {
        var connection: HttpURLConnection? = null
        var cancelled: Boolean = false
    }

    private val uploadLock = Any()
    private val activeUploads = mutableMapOf<String, ActiveUpload>()

    /** 从分享服务获取文件清单，并将响应内容转换为领域文件模型。 */
    fun list(session: LanShareSession) = request(session, "/api/files") { connection ->
        JSONArray(connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }).let { json ->
            (0 until json.length()).map { index ->
                json.getJSONObject(index).let {
                    val name = it.getString("name")
                    LanShareFile(
                        it.getString("id"),
                        name,
                        it.getLong("size"),
                        it.getLong("modifiedAt"),
                        it.optString("sender", "peer"),
                        uploadedMimeType(name, it.optString("mimeType")),
                    )
                }
            }
        }
    }

    /** 流式上传 App 文件，报告进度并登记任务 ID，以便界面取消正在进行的请求。 */
    fun upload(
        session: LanShareSession,
        transferId: String,
        source: LanShareUploadSource,
        onProgress: (uploadedBytes: Long, totalBytes: Long) -> Unit,
    ): String {
        val name = source.name.ifBlank { "附件" }
        val size = source.size
        if (size < 0) error("无法确定文件大小，请先将文件保存到本机")
        require(size <= LanShareLimits.MAX_FILE_BYTES) { "单个文件不能超过 10 GiB" }
        val activeUpload = ActiveUpload()
        synchronized(uploadLock) {
            check(transferId !in activeUploads) { "上传任务标识已存在" }
            activeUploads[transferId] = activeUpload
        }
        try {
            return uploadRaw(session, transferId, activeUpload, name, size, source.mimeType, onProgress) {
                source.openStream()
            }.also {
                logger.record("lan", "file uploaded name=$name size=$size", null)
            }
        } finally {
            synchronized(uploadLock) {
                if (activeUploads[transferId] === activeUpload) activeUploads.remove(transferId)
            }
        }
    }

    /** 标记指定上传任务已取消并断开其 HTTP 连接；不会影响其他并行上传。 */
    fun cancelUpload(transferId: String) {
        val connection = synchronized(uploadLock) {
            activeUploads[transferId]?.also { it.cancelled = true }?.connection
        }
        connection?.disconnect()
    }

    private fun uploadRaw(
        session: LanShareSession,
        transferId: String,
        activeUpload: ActiveUpload,
        name: String,
        size: Long,
        mimeType: String?,
        onProgress: (uploadedBytes: Long, totalBytes: Long) -> Unit,
        openStream: () -> java.io.InputStream?,
    ): String {
        require(size in 1..LanShareLimits.MAX_FILE_BYTES) { "单个文件不能超过 10 GiB" }
        val url = URL(session.baseUrl + "/upload?name=${Uri.encode(name)}&client=app")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 120_000
            requestMethod = "PUT"
            setRequestProperty("X-File-Size", size.toString())
            setRequestProperty("Content-Type", uploadedMimeType(name, mimeType))
            doOutput = true
            setFixedLengthStreamingMode(size)
        }
        val canStart = synchronized(uploadLock) {
            if (activeUploads[transferId] !== activeUpload || activeUpload.cancelled) {
                false
            } else {
                activeUpload.connection = connection
                true
            }
        }
        if (!canStart) {
            connection.disconnect()
            throw CancellationException("上传已取消")
        }
        try {
            val copiedBytes = openStream()?.use { input ->
                connection.outputStream.buffered(LAN_SHARE_STREAM_BUFFER_SIZE).use { output ->
                    copyLanShareUpload(input, output, size, onProgress)
                }
            } ?: error("无法读取附件")
            require(copiedBytes == size) { "附件读取不完整：$copiedBytes/$size 字节" }
            if (connection.responseCode !in 200..299) {
                val reason = connection.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText().trim() }.orEmpty()
                error("上传失败：${connection.responseCode}${reason.takeIf(String::isNotBlank)?.let { "：$it" }.orEmpty()}")
            }
            return connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText().trim() }
                .ifBlank { error("上传完成但未收到文件标识") }
        } finally {
            synchronized(uploadLock) {
                if (activeUploads[transferId] === activeUpload && activeUpload.connection === connection) {
                    activeUpload.connection = null
                }
            }
            connection.disconnect()
        }
    }

    /** 下载原文件到同目录临时文件，完成后替换目标文件，避免留下不完整下载结果。 */
    fun downloadToFile(session: LanShareSession, id: String, destination: File) =
        request(session, "/dl/${Uri.encode(id)}") { connection ->
            destination.parentFile?.mkdirs()
            val temporary = File.createTempFile(".${destination.name}.", ".part", destination.parentFile)
            try {
                temporary.outputStream().use { output ->
                    connection.inputStream.use { it.copyTo(output, LAN_SHARE_STREAM_BUFFER_SIZE) }
                }
                if (destination.exists()) destination.delete()
                require(temporary.renameTo(destination)) { "无法保存下载文件" }
                logger.record("lan", "file downloaded id=$id bytes=${destination.length()}", null)
            } finally {
                temporary.delete()
            }
        }

    /** 下载有字节上限的图片预览副本；原始下载仍走 [downloadToFile]。 */
    fun downloadPreview(session: LanShareSession, id: String, destination: File, maxBytes: Long) =
        request(session, "/api/preview/${Uri.encode(id)}", readTimeoutMs = 120_000) { connection ->
            val declaredSize = connection.getHeaderFieldLong("Content-Length", -1L)
            require(maxBytes >= 0L) { "图片预览大小限制无效" }
            require(declaredSize < 0L || declaredSize <= maxBytes) {
                "图片预览超过 ${maxBytes / (1024L * 1024L)} MB 限制"
            }
            destination.parentFile?.mkdirs()
            val temporary = File.createTempFile(".${destination.name}.", ".part", destination.parentFile)
            try {
                temporary.outputStream().buffered(LAN_SHARE_STREAM_BUFFER_SIZE).use { output ->
                    connection.inputStream.use { copyLanSharePreview(it, output, maxBytes) }
                }
                if (destination.exists()) destination.delete()
                require(temporary.renameTo(destination)) { "无法保存图片预览" }
            } finally {
                temporary.delete()
            }
        }

    private fun <T> request(
        session: LanShareSession,
        path: String,
        output: Boolean = false,
        readTimeoutMs: Int = 30_000,
        block: (HttpURLConnection) -> T,
    ): T {
        require(isRouterLanHost(Uri.parse(session.baseUrl).host)) { "分享地址不在当前路由器网关子网内" }
        val connection = (URL(session.baseUrl + path).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = readTimeoutMs
            requestMethod = if (output) "POST" else "GET"
            doOutput = output
            if (output) setRequestProperty("Content-Type", "application/octet-stream")
        }
        return try {
            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                val detail = runCatching {
                    (connection.errorStream ?: connection.inputStream).bufferedReader(Charsets.UTF_8).use { it.readText().trim() }
                }.getOrNull().orEmpty()
                error("连接失败：$responseCode${detail.takeIf { it.isNotBlank() }?.let { "：$it" }.orEmpty()}")
            }
            block(connection)
        } finally {
            connection.disconnect()
        }
    }
}
