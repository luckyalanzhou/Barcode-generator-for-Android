package com.luckyalanzhou.barcodegenerator.data.network.client

import com.luckyalanzhou.barcodegenerator.domain.LanShareFile
import com.luckyalanzhou.barcodegenerator.domain.LanShareSession
import com.luckyalanzhou.barcodegenerator.domain.LanShareUploadSource
import com.luckyalanzhou.barcodegenerator.data.network.protocol.LanShareLimits
import com.luckyalanzhou.barcodegenerator.data.network.protocol.LAN_SHARE_STREAM_BUFFER_SIZE
import com.luckyalanzhou.barcodegenerator.data.network.protocol.toLanShareFile

import com.luckyalanzhou.barcodegenerator.domain.AppLogger


import android.net.Uri
import org.json.JSONArray
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** App 端访问浏览器分享服务的 HTTP 客户端；不包含服务端生命周期逻辑。 */
internal class LanShareClient(
    private val isRouterLanHost: (String?) -> Boolean,
    private val logger: AppLogger,
) {
    fun list(session: LanShareSession) = request(session, "/api/files") { connection ->
        JSONArray(connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }).let { json ->
            (0 until json.length()).map { index ->
                json.getJSONObject(index).let {
                    LanShareFile(
                        it.getString("id"),
                        it.getString("name"),
                        it.getLong("size"),
                        it.getLong("modifiedAt"),
                        it.optString("sender", "peer")
                    )
                }
            }
        }
    }

    fun upload(session: LanShareSession, source: LanShareUploadSource): String {
        val name = source.name.ifBlank { "附件" }
        val size = source.size
        if (size < 0) error("无法确定文件大小，请先将文件保存到本机")
        require(size <= LanShareLimits.MAX_FILE_BYTES) { "单个文件不能超过 5 GB" }
        return uploadRaw(session, name, size) { source.openStream() }.also {
            logger.record("lan", "file uploaded name=$name size=$size", null)
        }
    }

    fun uploadText(session: LanShareSession, text: String): String {
        val name = text.trim().take(100).ifBlank { "消息" }
        val body = text.toByteArray(Charsets.UTF_8)
        return uploadRaw(session, name, body.size.toLong()) { body.inputStream() }.also {
            logger.record("lan", "text sent length=${body.size}", null)
        }
    }

    private fun uploadRaw(
        session: LanShareSession,
        name: String,
        size: Long,
        openStream: () -> java.io.InputStream?,
    ): String {
        require(size in 1..LanShareLimits.MAX_FILE_BYTES) { "单个文件不能超过 5 GB" }
        val url = URL(session.baseUrl + "/upload?name=${Uri.encode(name)}&client=app")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 120_000
            requestMethod = "PUT"
            setRequestProperty("X-Lan-Token", session.accessToken)
            setRequestProperty("X-File-Size", size.toString())
            setRequestProperty("Content-Type", "application/octet-stream")
            doOutput = true
            setFixedLengthStreamingMode(size)
        }
        try {
            val copiedBytes = openStream()?.use { input ->
                connection.outputStream.buffered(LAN_SHARE_STREAM_BUFFER_SIZE).use { output ->
                    input.copyTo(output, LAN_SHARE_STREAM_BUFFER_SIZE)
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
            connection.disconnect()
        }
    }

    fun downloadToFile(session: LanShareSession, id: String, destination: File) =
        request(session, "/api/download/${Uri.encode(id)}") { connection ->
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
            setRequestProperty("X-Lan-Token", session.accessToken)
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
