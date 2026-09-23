package com.luckyalanzhou.barcodegenerator.data.platform

import android.content.Context
import androidx.core.net.toUri
import com.luckyalanzhou.barcodegenerator.domain.AppLogger
import com.luckyalanzhou.barcodegenerator.domain.ApkDownloadGateway
import com.luckyalanzhou.barcodegenerator.domain.UpdateSecurity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

/** Android file/network adapter for secure APK download and checksum validation. */
class AndroidApkDownloadGateway(
    private val context: Context,
    private val logger: AppLogger,
    private val userAgent: String,
) : ApkDownloadGateway {
    override suspend fun download(
        apkUrl: String,
        expectedSize: Long?,
        expectedSha256: String?,
        onProgress: (progress: Int, indeterminate: Boolean, status: String) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        val temp = File(context.cacheDir, "barcode-generator-update.apk.part")
        val official = File(context.cacheDir, "barcode-generator-update.apk")
        var connection: HttpURLConnection? = null
        try {
            val limit = UpdateSecurity.MAX_APK_DOWNLOAD_BYTES
            require(expectedSha256 != null) { "该版本缺少 SHA-256 校验信息，无法安全更新" }
            require(expectedSize == null || expectedSize <= limit) { "更新包超过 500 MB 限制" }
            require(apkUrl.toUri().scheme.equals("https", ignoreCase = true)) { "更新包必须使用 HTTPS 下载" }
            connection = URL(apkUrl).openConnection() as HttpURLConnection
            connection.apply {
                connectTimeout = 15000
                readTimeout = 30000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", userAgent)
            }
            require(connection.responseCode in 200..299) { "HTTP ${connection.responseCode}" }
            logger.record("update", "download response=${connection.responseCode} contentLength=${connection.contentLengthLong}", null)
            val total = connection.contentLengthLong.takeIf { it > 0L } ?: expectedSize
            require(total == null || total <= limit) { "更新包超过 500 MB 限制" }
            temp.delete()
            connection.inputStream.use { input ->
                temp.outputStream().use { output ->
                    val buffer = ByteArray(16 * 1024)
                    var done = 0L
                    var count: Int
                    while (input.read(buffer).also { count = it } != -1) {
                        coroutineContext.ensureActive()
                        require(done + count <= limit) { "更新包超过 500 MB 限制" }
                        output.write(buffer, 0, count)
                        done += count
                        if (total != null) {
                            val progress = (done * 100 / total).toInt().coerceIn(0, 100)
                            onProgress(progress, false, "已下载 ${progress}%")
                        } else {
                            onProgress(0, true, "正在下载… ${done / 1024} KB")
                        }
                    }
                }
            }
            require(temp.isFile && temp.length() > 0L) { "APK 为空" }
            require(expectedSize == null || temp.length() == expectedSize) { "文件大小校验失败：${temp.length()} / $expectedSize" }
            val digest = MessageDigest.getInstance("SHA-256")
            val actual = temp.inputStream().use { input ->
                val buffer = ByteArray(16 * 1024)
                var count: Int
                while (input.read(buffer).also { count = it } != -1) digest.update(buffer, 0, count)
                digest.digest().joinToString("") { "%02x".format(it) }
            }
            require(actual.equals(expectedSha256, true)) { "SHA-256 校验失败" }
            official.delete()
            require(temp.renameTo(official)) { "无法保存更新文件" }
            context.cacheDir.listFiles()
                ?.filter { it.name.startsWith("barcode-generator-update") && it != official }
                ?.forEach { it.delete() }
            logger.record("update", "download validated size=${official.length()}", null)
            return@withContext official
        } finally {
            connection?.disconnect()
            if (!official.isFile) temp.delete()
        }
    }
}
