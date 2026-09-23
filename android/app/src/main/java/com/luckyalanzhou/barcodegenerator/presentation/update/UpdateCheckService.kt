package com.luckyalanzhou.barcodegenerator.presentation.update

import com.luckyalanzhou.barcodegenerator.BuildConfig
import com.luckyalanzhou.barcodegenerator.presentation.UpdateCheckResult
import com.luckyalanzhou.barcodegenerator.domain.UpdateSecurity
import com.luckyalanzhou.barcodegenerator.domain.AppLogger

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.util.Comparator
import java.util.Locale

/** GitHub 更新元数据数据源；只负责请求和解析，不持有 ViewModel 状态。 */
class UpdateCheckService(private val logger: AppLogger) {
    suspend fun check(): UpdateCheckResult = withContext(Dispatchers.IO) {
        try {
            val connection = (URL("https://api.github.com/repos/luckyalanzhou/Barcode-generator-for-android/releases?per_page=100")
                .openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "BarcodeGenerator/${BuildConfig.VERSION_NAME}")
            }
            val releases = try {
                if (connection.responseCode !in 200..299) throw IllegalStateException("GitHub HTTP ${connection.responseCode}")
                connection.inputStream.bufferedReader().use { JSONArray(it.readText()) }
            } finally {
                connection.disconnect()
            }
            val release = (0 until releases.length())
                .mapNotNull { releases.optJSONObject(it) }
                .filter { it.optString("tag_name").startsWith(BuildConfig.UPDATE_TAG_PREFIX) }
                .maxWithOrNull(Comparator { left, right ->
                    UpdateSecurity.compareVersions(
                        parseAppVersion(left.optString("tag_name")) ?: "0.0.0",
                        parseAppVersion(right.optString("tag_name")) ?: "0.0.0",
                    )
                })
            val releaseTag = release?.optString("tag_name")?.takeIf { it.isNotBlank() }
                ?: return@withContext UpdateCheckResult.Failed("暂时无法获取更新信息")
            val apkAsset = release.optJSONArray("assets")?.let { assets ->
                (0 until assets.length()).mapNotNull { assets.optJSONObject(it) }.firstOrNull { asset ->
                    val assetName = asset.optString("name")
                    val official = BuildConfig.UPDATE_TAG_PREFIX == "android-v" &&
                        assetName.matches(Regex("""^BarcodeGenerator[0-9]+\\.[0-9]+\\.[0-9]+(?:\\.[0-9]+)?\\.apk$"""))
                    val channel = BuildConfig.UPDATE_TAG_PREFIX != "android-v" &&
                        assetName.startsWith(BuildConfig.APK_FILE_PREFIX) && assetName.endsWith(".apk", true)
                    official || channel
                }
            }
            val downloadUrl = apkAsset?.optString("browser_download_url")?.takeIf { it.isNotBlank() }
                ?: return@withContext UpdateCheckResult.Failed("暂时无法获取更新信息")
            val latest = parseAppVersion(releaseTag)
                ?: return@withContext UpdateCheckResult.Failed("版本信息格式不正确")
            val expectedSize = apkAsset.optLong("size", 0L).takeIf { it > 0L }
            val releaseSha256 = release.optString("body")
                .lineSequence()
                .map(String::trim)
                .firstOrNull { it.startsWith("sha256:", ignoreCase = true) }
                ?.substringAfter(':')
                ?.trim()
                ?.lowercase(Locale.US)
                ?.takeIf { it.matches(Regex("[0-9a-f]{64}")) }
            val assetSha256 = apkAsset.optString("digest").removePrefix("sha256:").trim().lowercase(Locale.US)
                .takeIf { it.matches(Regex("[0-9a-f]{64}")) }
            // GitHub's asset digest can remain stale after an in-place asset replacement.
            // Prefer the hash calculated from the exact APK by the Beta workflow.
            val expectedSha256 = releaseSha256 ?: assetSha256
            val releaseVersionCode = release.optString("body")
                .lineSequence()
                .map(String::trim)
                .firstOrNull { it.startsWith("versionCode:") }
                ?.substringAfter(':')
                ?.trim()
                ?.toLongOrNull()
            val versionNameChanged = UpdateSecurity.compareVersions(latest, BuildConfig.VERSION_NAME) > 0
            val betaBuildChanged = BuildConfig.UPDATE_TAG_PREFIX != "android-v" &&
                releaseVersionCode != null && releaseVersionCode > BuildConfig.VERSION_CODE.toLong()
            if (versionNameChanged || betaBuildChanged) {
                UpdateCheckResult.Available(latest, downloadUrl, expectedSize, expectedSha256)
            } else UpdateCheckResult.UpToDate
        } catch (error: Exception) {
            logger.record("update", "check failed", error)
            UpdateCheckResult.Failed(error.message ?: "检查更新失败，请稍后重试")
        }
    }

    private fun parseAppVersion(tag: String): String? {
        val parts = tag.trim().removePrefix(BuildConfig.UPDATE_TAG_PREFIX).removePrefix("v").split(".")
        if (parts.size !in 3..4 || parts.take(3).any { it.isEmpty() || it.length > 9 || it.toLongOrNull() == null }) return null
        if (parts.size == 4 && (parts[3].isEmpty() || parts[3].length > 12 || parts[3].toLongOrNull() == null)) return null
        return parts.take(3).joinToString(".")
    }
}
