package com.luckyalanzhou.barcodegenerator.data.network.client

import com.luckyalanzhou.barcodegenerator.domain.LanShareFile
import com.luckyalanzhou.barcodegenerator.domain.LanShareSession
import com.luckyalanzhou.barcodegenerator.data.network.protocol.LanShareLimits
import com.luckyalanzhou.barcodegenerator.data.network.protocol.toLanShareFile
import com.luckyalanzhou.barcodegenerator.data.network.protocol.multipartFileName

import com.luckyalanzhou.barcodegenerator.*
import com.luckyalanzhou.barcodegenerator.domain.AppLogger


import android.content.Context
import android.net.Uri
import org.json.JSONArray
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** App 端访问浏览器分享服务的 HTTP 客户端；不包含服务端生命周期逻辑。 */
internal class LanShareClient(
    private val context: Context,
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

    fun upload(session: LanShareSession, uri: Uri): String {
        val name = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            cursor.moveToFirst()
            cursor.getString(cursor.getColumnIndexOrThrow(android.provider.OpenableColumns.DISPLAY_NAME))
        } ?: "附件"
        val size = context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
        if (size < 0) error("无法确定文件大小，请先将文件保存到本机")
        require(size <= LanShareLimits.MAX_FILE_BYTES) { "单个文件不能超过 5 GB" }
        val boundary = "----BarcodeShare${System.currentTimeMillis()}"
        val header = "--$boundary\r\nContent-Disposition: form-data; name=\"attachment\"; filename=\"${multipartFileName(name)}\"\r\nContent-Type: application/octet-stream\r\n\r\n".toByteArray()
        val footer = "\r\n--$boundary--\r\n".toByteArray()
        val connection = (URL(session.baseUrl + "/api/upload").openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 120_000
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            // NanoHTTPD 对 chunked 大请求会尝试构造整段字符串；固定长度可让其直接落到临时文件。
            setFixedLengthStreamingMode(header.size.toLong() + size + footer.size)
        }
        try {
            var copiedBytes = 0L
            connection.outputStream.buffered().use { output ->
                output.write(header)
                context.contentResolver.openInputStream(uri)?.use { copiedBytes = it.copyTo(output, 16 * 1024) }
                    ?: error("无法读取附件")
                output.write(footer)
            }
            require(copiedBytes == size) { "附件读取不完整：$copiedBytes/$size 字节" }
            if (connection.responseCode !in 200..299) error("上传失败：${connection.responseCode}")
            return connection.inputStream.bufferedReader().use { it.readText().trim() }.also {
                logger.record("lan", "file uploaded name=$name size=$size", null)
            }.ifBlank { error("上传完成但未收到文件标识") }
        } finally {
            connection.disconnect()
        }
    }

    fun uploadText(session: LanShareSession, text: String): String {
        val name = text.trim().take(100).ifBlank { "消息" }
        val body = text.toByteArray(Charsets.UTF_8)
        val boundary = "----BarcodeShare${System.currentTimeMillis()}"
        val header = "--$boundary\r\nContent-Disposition: form-data; name=\"attachment\"; filename=\"${multipartFileName(name)}\"\r\nContent-Type: text/plain; charset=utf-8\r\n\r\n".toByteArray()
        val footer = "\r\n--$boundary--\r\n".toByteArray()
        val connection = (URL(session.baseUrl + "/api/upload").openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 30_000
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            setFixedLengthStreamingMode(header.size.toLong() + body.size + footer.size)
        }
        try {
            connection.outputStream.buffered().use { output ->
                output.write(header)
                output.write(body)
                output.write(footer)
            }
            if (connection.responseCode !in 200..299) error("发送失败：${connection.responseCode}")
            return connection.inputStream.bufferedReader().use { it.readText().trim() }.also {
                logger.record("lan", "text sent length=${body.size}", null)
            }.ifBlank { error("发送完成但未收到文件标识") }
        } finally {
            connection.disconnect()
        }
    }

    fun download(session: LanShareSession, id: String, destination: Uri) =
        request(session, "/api/download/${Uri.encode(id)}") { connection ->
            val temporary = File.createTempFile("lan-download-", ".part", context.cacheDir)
            try {
                temporary.outputStream().use { output ->
                    connection.inputStream.use { input ->
                        input.copyTo(output)
                    }
                }
                context.contentResolver.openOutputStream(destination)?.use { output ->
                    temporary.inputStream().use { it.copyTo(output) }
                } ?: error("无法写入文件")
                logger.record("lan", "file downloaded id=$id bytes=${temporary.length()}", null)
            } finally {
                temporary.delete()
            }
        }

    fun downloadPreview(session: LanShareSession, id: String, destination: File) =
        request(session, "/api/download/${Uri.encode(id)}") { connection ->
            destination.parentFile?.mkdirs()
            val temporary = File.createTempFile(".${destination.name}.", ".part", destination.parentFile)
            try {
                temporary.outputStream().use { output ->
                    connection.inputStream.use { it.copyTo(output) }
                }
                if (destination.exists()) destination.delete()
                require(temporary.renameTo(destination)) { "无法保存图片预览" }
            } finally {
                temporary.delete()
            }
        }

    private fun <T> request(session: LanShareSession, path: String, output: Boolean = false, block: (HttpURLConnection) -> T): T {
        require(isRouterLanHost(Uri.parse(session.baseUrl).host)) { "分享地址不在当前路由器网关子网内" }
        val connection = (URL(session.baseUrl + path).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 30_000
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
