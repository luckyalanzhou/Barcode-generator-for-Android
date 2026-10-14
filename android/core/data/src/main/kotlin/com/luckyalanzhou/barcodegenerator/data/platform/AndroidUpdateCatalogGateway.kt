package com.luckyalanzhou.barcodegenerator.data.platform

import com.luckyalanzhou.barcodegenerator.domain.AppLogger
import com.luckyalanzhou.barcodegenerator.domain.UpdateCatalogGateway
import com.luckyalanzhou.barcodegenerator.domain.UpdateLookupResult
import com.luckyalanzhou.barcodegenerator.domain.UpdateSecurity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.util.Comparator
import java.util.Locale

/** GitHub release metadata adapter; channel/build configuration is supplied by the app. */
class AndroidUpdateCatalogGateway(
    private val logger: AppLogger,
    private val updateTagPrefix: String,
    private val apkFilePrefix: String,
    private val currentVersionName: String,
    private val currentVersionCode: Long,
) : UpdateCatalogGateway {
    override suspend fun check(): UpdateLookupResult = withContext(Dispatchers.IO) {
        try {
            val connection = (URL("https://api.github.com/repos/luckyalanzhou/Barcode-generator-for-android/releases?per_page=100")
                .openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "BarcodeGenerator/$currentVersionName")
            }
            val releases = try {
                if (connection.responseCode !in 200..299) throw IllegalStateException("GitHub HTTP ${connection.responseCode}")
                connection.inputStream.bufferedReader().use { JSONArray(it.readText()) }
            } finally {
                connection.disconnect()
            }
            val release = (0 until releases.length())
                .mapNotNull { releases.optJSONObject(it) }
                .filter { it.optString("tag_name").startsWith(updateTagPrefix) }
                .maxWithOrNull(Comparator { left, right ->
                    UpdateSecurity.compareVersions(
                        parseAppVersion(left.optString("tag_name")) ?: "0.0.0",
                        parseAppVersion(right.optString("tag_name")) ?: "0.0.0",
                    )
                })
            val releaseTag = release?.optString("tag_name")?.takeIf { it.isNotBlank() }
                ?: return@withContext UpdateLookupResult.Failed("暂时无法获取更新信息")
            val apkAsset = release.optJSONArray("assets")?.let { assets ->
                (0 until assets.length()).mapNotNull { assets.optJSONObject(it) }.firstOrNull { asset ->
                    val assetName = asset.optString("name")
                    val official = updateTagPrefix == "android-v" &&
                        assetName.matches(Regex("""^BarcodeGenerator[0-9]+\.[0-9]+\.[0-9]+(?:\.[0-9]+)?\.apk$"""))
                    val channel = updateTagPrefix != "android-v" &&
                        assetName.startsWith(apkFilePrefix) && assetName.endsWith(".apk", true)
                    official || channel
                }
            }
            val downloadUrl = apkAsset?.optString("browser_download_url")?.takeIf { it.isNotBlank() }
                ?: return@withContext UpdateLookupResult.Failed("暂时无法获取更新信息")
            val latest = parseAppVersion(releaseTag)
                ?: return@withContext UpdateLookupResult.Failed("版本信息格式不正确")
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
            val expectedSha256 = releaseSha256 ?: assetSha256
            val releaseVersionCode = release.optString("body")
                .lineSequence()
                .map(String::trim)
                .firstOrNull { it.startsWith("versionCode:") }
                ?.substringAfter(':')
                ?.trim()
                ?.toLongOrNull()
            val versionNameChanged = UpdateSecurity.compareVersions(latest, currentVersionName) > 0
            val betaBuildChanged = updateTagPrefix != "android-v" &&
                releaseVersionCode != null && releaseVersionCode > currentVersionCode
            if (versionNameChanged || betaBuildChanged) {
                UpdateLookupResult.Available(latest, downloadUrl, expectedSize, expectedSha256)
            } else UpdateLookupResult.UpToDate
        } catch (error: Exception) {
            logger.record("update", "check failed", error)
            UpdateLookupResult.Failed(error.message ?: "检查更新失败，请稍后重试")
        }
    }

    private fun parseAppVersion(tag: String): String? {
        val parts = tag.trim().removePrefix(updateTagPrefix).removePrefix("v").split(".")
        if (parts.size !in 3..4 || parts.take(3).any { it.isEmpty() || it.length > 9 || it.toLongOrNull() == null }) return null
        if (parts.size == 4 && (parts[3].isEmpty() || parts[3].length > 12 || parts[3].toLongOrNull() == null)) return null
        return parts.take(3).joinToString(".")
    }
}
