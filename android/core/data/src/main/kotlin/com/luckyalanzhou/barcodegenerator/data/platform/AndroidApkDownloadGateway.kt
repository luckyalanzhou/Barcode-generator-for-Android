package com.luckyalanzhou.barcodegenerator.data.platform

import android.content.Context
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
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.coroutineContext

/** APK 下载连接工厂；内部接缝供模块测试使用。 */
internal fun interface ApkDownloadConnectionFactory {
    fun open(url: URL): HttpURLConnection
}

/** Android 文件与网络适配器，负责安全下载 APK 并校验摘要。 */
class AndroidApkDownloadGateway private constructor(
    private val context: Context,
    private val logger: AppLogger,
    private val userAgent: String,
    private val connectionFactory: (URL) -> HttpURLConnection,
) : ApkDownloadGateway {
    constructor(
        context: Context,
        logger: AppLogger,
        userAgent: String,
    ) : this(context, logger, userAgent, { url -> url.openConnection() as HttpURLConnection })

    internal constructor(
        context: Context,
        logger: AppLogger,
        userAgent: String,
        connectionFactory: ApkDownloadConnectionFactory,
    ) : this(context, logger, userAgent, connectionFactory::open)

    private val activeConnections = ConcurrentHashMap.newKeySet<HttpURLConnection>()
    private val activePartialFiles = ConcurrentHashMap.newKeySet<File>()

    override fun cancel() {
        activeConnections.toList().forEach { it.disconnect() }
    }
    override suspend fun download(
        apkUrl: String,
        expectedSize: Long?,
        expectedSha256: String?,
        onProgress: (progress: Int, indeterminate: Boolean, status: String) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        val requestId = UUID.randomUUID().toString()
        val temp = File(context.cacheDir, "$UPDATE_APK_DOWNLOAD_FILE_PREFIX$requestId$UPDATE_APK_PARTIAL_FILE_SUFFIX")
        val shareDirectory = File(context.cacheDir, "$UPDATE_APK_SHARE_CACHE_DIRECTORY")
        check(shareDirectory.isDirectory || shareDirectory.mkdirs()) { "无法创建更新包分享目录" }
        val official = File(shareDirectory, "$UPDATE_APK_DOWNLOAD_FILE_PREFIX$requestId.apk")
        var connection: HttpURLConnection? = null
        activePartialFiles.add(temp)
        try {
            val removedPartials = cleanupOrphanedApkPartFiles(context.cacheDir, activePartialFiles)
            if (removedPartials > 0) {
                logger.recordSafely("update", "removed orphaned APK partial files count=$removedPartials")
            }
            val limit = UpdateSecurity.MAX_APK_DOWNLOAD_BYTES
            require(expectedSha256 != null) { "该版本缺少 SHA-256 校验信息，无法安全更新" }
            require(expectedSize == null || expectedSize <= limit) { "更新包超过 500 MB 限制" }
            var currentUrl = URL(apkUrl)
            HttpsUpdateRedirectPolicy.requireAllowedGitHubReleaseUrl(currentUrl)
            var redirectsFollowed = 0
            while (connection == null) {
                coroutineContext.ensureActive()
                val nextConnection = connectionFactory(currentUrl)
                connection = nextConnection
                activeConnections.add(nextConnection)
                coroutineContext.ensureActive()
                nextConnection.apply {
                    connectTimeout = 15000
                    readTimeout = 30000
                    instanceFollowRedirects = false
                    setRequestProperty("User-Agent", userAgent)
                }
                val responseCode = nextConnection.responseCode
                if (HttpsUpdateRedirectPolicy.isRedirect(responseCode)) {
                    val nextUrl = HttpsUpdateRedirectPolicy.resolve(
                        currentUrl,
                        nextConnection.getHeaderField("Location"),
                        redirectsFollowed,
                    )
                    nextConnection.disconnect()
                    activeConnections.remove(nextConnection)
                    connection = null
                    currentUrl = nextUrl
                    redirectsFollowed++
                    continue
                }
                require(responseCode in 200..299) { "HTTP $responseCode" }
            }
            val downloadConnection = checkNotNull(connection)
            logger.recordSafely(
                "update",
                "download response=${downloadConnection.responseCode} contentLength=${downloadConnection.contentLengthLong}",
            )
            val total = downloadConnection.contentLengthLong.takeIf { it > 0L } ?: expectedSize
            require(total == null || total <= limit) { "更新包超过 500 MB 限制" }
            temp.delete()
            downloadConnection.inputStream.use { input ->
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
                            onProgress(progress, false, "正在下载")
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
                while (input.read(buffer).also { count = it } != -1) {
                    coroutineContext.ensureActive()
                    digest.update(buffer, 0, count)
                }
                digest.digest().joinToString("") { "%02x".format(it) }
            }
            require(actual.equals(expectedSha256, true)) { "SHA-256 校验失败" }
            coroutineContext.ensureActive()
            official.delete()
            require(temp.renameTo(official)) { "无法保存更新文件" }
            logger.recordSafely("update", "download validated size=${official.length()}")
            return@withContext official
        } finally {
            connection?.disconnect()
            connection?.let(activeConnections::remove)
            temp.delete()
            activePartialFiles.remove(temp)
        }
    }
}
