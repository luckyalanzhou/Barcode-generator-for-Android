package com.luckyalanzhou.barcodegenerator.data.platform

import android.content.Context
import android.content.ContextWrapper
import com.luckyalanzhou.barcodegenerator.domain.AppLogger
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.rules.TemporaryFolder
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlinx.coroutines.runBlocking

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AndroidApkDownloadGatewayTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun diagnosticLoggerFailureDoesNotBlockVerifiedApkDelivery() = runBlocking {
        val cacheDir = temporaryFolder.newFolder("download-cache")
        val payload = "verified-apk-payload".toByteArray()
        val gateway = gateway(cacheDir, payload, AppLogger { _, _, _ ->
            throw IOException("diagnostic storage unavailable")
        })

        val downloaded = gateway.download(
            apkUrl = TRUSTED_RELEASE_URL,
            expectedSize = payload.size.toLong(),
            expectedSha256 = payload.sha256(),
            onProgress = { _, _, _ -> },
        )

        assertTrue(downloaded.isFile)
        assertArrayEquals(payload, downloaded.readBytes())
        val downloadDirectory = checkNotNull(downloaded.parentFile)
        assertEquals(File(cacheDir, UPDATE_APK_SHARE_CACHE_DIRECTORY).canonicalFile, downloadDirectory.canonicalFile)
        assertEquals(listOf(downloaded), downloadDirectory.listFiles()?.toList())
        assertFalse(cacheDir.listFiles().orEmpty().any { it.name.endsWith(".apk.part") })
    }

    @Test
    fun actualDownloadFailureRemovesPartialAndDoesNotLeaveCompletedApk() = runBlocking {
        val cacheDir = temporaryFolder.newFolder("failed-download-cache")
        val gateway = gateway(
            cacheDir = cacheDir,
            inputStream = object : InputStream() {
                private var sentPrefix = false

                override fun read(): Int = throw IOException("download interrupted")

                override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                    if (!sentPrefix) {
                        sentPrefix = true
                        val prefix = byteArrayOf(1, 2, 3)
                        prefix.copyInto(buffer, offset)
                        return minOf(prefix.size, length)
                    }
                    throw IOException("download interrupted")
                }
            },
            logger = AppLogger { _, _, _ -> },
        )

        val failure = runCatching {
            gateway.download(
                apkUrl = TRUSTED_RELEASE_URL,
                expectedSize = null,
                expectedSha256 = "0".repeat(64),
                onProgress = { _, _, _ -> },
            )
        }.exceptionOrNull()

        assertTrue(failure is IOException)
        assertFalse(cacheDir.listFiles().orEmpty().any { it.name.endsWith(".apk.part") })
        assertFalse(File(cacheDir, UPDATE_APK_SHARE_CACHE_DIRECTORY).listFiles().orEmpty().any { it.name.endsWith(".apk") })
    }

    private fun gateway(
        cacheDir: File,
        payload: ByteArray,
        logger: AppLogger,
    ): AndroidApkDownloadGateway = gateway(
        cacheDir = cacheDir,
        inputStream = ByteArrayInputStream(payload),
        contentLength = payload.size.toLong(),
        logger = logger,
    )

    private fun gateway(
        cacheDir: File,
        inputStream: InputStream,
        contentLength: Long = -1L,
        logger: AppLogger,
    ): AndroidApkDownloadGateway {
        val baseContext: Context = RuntimeEnvironment.getApplication()
        val context = object : ContextWrapper(baseContext) {
            override fun getCacheDir(): File = cacheDir
        }
        return AndroidApkDownloadGateway(
            context = context,
            logger = logger,
            userAgent = "AndroidApkDownloadGatewayTest",
            connectionFactory = ApkDownloadConnectionFactory { url ->
                assertEquals("github.com", url.host)
                object : HttpURLConnection(url) {
                    override fun connect() = Unit
                    override fun disconnect() = Unit
                    override fun usingProxy(): Boolean = false
                    override fun getResponseCode(): Int = HTTP_OK
                    override fun getContentLengthLong(): Long = contentLength
                    override fun getInputStream(): InputStream = inputStream
                }
            },
        )
    }

    private fun ByteArray.sha256(): String = MessageDigest.getInstance("SHA-256")
        .digest(this)
        .joinToString("") { "%02x".format(it) }

    private companion object {
        const val TRUSTED_RELEASE_URL =
            "https://github.com/luckyalanzhou/Barcode-generator-for-android/releases/download/android-v1.0.0/BarcodeGeneratorBeta.apk"
    }
}
