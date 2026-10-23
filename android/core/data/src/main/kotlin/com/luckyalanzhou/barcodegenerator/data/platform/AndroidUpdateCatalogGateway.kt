package com.luckyalanzhou.barcodegenerator.data.platform

import com.luckyalanzhou.barcodegenerator.domain.AppLogger
import com.luckyalanzhou.barcodegenerator.domain.UpdateCatalogGateway
import com.luckyalanzhou.barcodegenerator.domain.UpdateLookupResult
import com.luckyalanzhou.barcodegenerator.domain.UpdateSecurity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Comparator
import java.util.Locale

internal const val MAX_UPDATE_CATALOG_RESPONSE_BYTES = 1 * 1024 * 1024

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
                readUpdateCatalogJson(connection.inputStream)
            } finally {
                connection.disconnect()
            }
            parseUpdateCatalogResponse(
                releases = releases,
                updateTagPrefix = updateTagPrefix,
                apkFilePrefix = apkFilePrefix,
                currentVersionName = currentVersionName,
                currentVersionCode = currentVersionCode,
            )
        } catch (error: Exception) {
            logger.record("update", "check failed", error)
            UpdateLookupResult.Failed(error.message ?: "检查更新失败，请稍后重试")
        }
    }
}

internal fun readUpdateCatalogJson(inputStream: InputStream): JSONArray {
    val responseBytes = inputStream.use { stream ->
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        var totalBytes = 0
        while (true) {
            val requestedBytes = minOf(buffer.size, MAX_UPDATE_CATALOG_RESPONSE_BYTES - totalBytes + 1)
            val bytesRead = stream.read(buffer, 0, requestedBytes)
            if (bytesRead < 0) break
            if (bytesRead == 0) {
                val nextByte = stream.read()
                if (nextByte < 0) break
                if (totalBytes == MAX_UPDATE_CATALOG_RESPONSE_BYTES) {
                    throw IOException("更新信息过大，请稍后重试")
                }
                output.write(nextByte)
                totalBytes++
                continue
            }
            totalBytes += bytesRead
            if (totalBytes > MAX_UPDATE_CATALOG_RESPONSE_BYTES) {
                throw IOException("更新信息过大，请稍后重试")
            }
            output.write(buffer, 0, bytesRead)
        }
        output.toByteArray()
    }
    return JSONArray(String(responseBytes, Charsets.UTF_8))
}

internal fun parseUpdateCatalogResponse(
    releases: JSONArray,
    updateTagPrefix: String,
    apkFilePrefix: String,
    currentVersionName: String,
    currentVersionCode: Long,
): UpdateLookupResult {
    val release = (0 until releases.length())
        .mapNotNull { releases.optJSONObject(it) }
        .filter { it.optString("tag_name").startsWith(updateTagPrefix) }
        .maxWithOrNull(Comparator { left, right ->
            UpdateSecurity.compareVersions(
                parseUpdateAppVersionTag(left.optString("tag_name"), updateTagPrefix) ?: "0.0.0",
                parseUpdateAppVersionTag(right.optString("tag_name"), updateTagPrefix) ?: "0.0.0",
            )
        })
    val releaseTag = release?.optString("tag_name")?.takeIf { it.isNotBlank() }
        ?: return UpdateLookupResult.Failed("暂时无法获取更新信息")
    val apkAsset = release.optJSONArray("assets")?.let { assets ->
        (0 until assets.length()).mapNotNull { assets.optJSONObject(it) }.firstOrNull { asset ->
            isUpdateApkAssetName(asset.optString("name"), updateTagPrefix, apkFilePrefix)
        }
    }
    val downloadUrl = apkAsset?.optString("browser_download_url")?.takeIf { it.isNotBlank() }
        ?: return UpdateLookupResult.Failed("暂时无法获取更新信息")
    val latest = parseUpdateAppVersionTag(releaseTag, updateTagPrefix)
        ?: return UpdateLookupResult.Failed("版本信息格式不正确")
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
    return if (versionNameChanged || betaBuildChanged) {
        if (expectedSha256 == null) {
            UpdateLookupResult.Failed("该版本缺少 SHA-256 校验信息，无法安全更新")
        } else {
            UpdateLookupResult.Available(latest, downloadUrl, expectedSize, expectedSha256)
        }
    } else UpdateLookupResult.UpToDate
}

/** 从更新 tag 提取 SemVer；第四段仅用于兼容旧 Beta build 序号。 */
internal fun parseUpdateAppVersionTag(tag: String, updateTagPrefix: String): String? {
    val parts = tag.trim().removePrefix(updateTagPrefix).removePrefix("v").split(".")
    if (parts.size !in 3..4) return null
    val semanticVersion = parts.take(3).joinToString(".")
    if (UpdateSecurity.parseVersion(semanticVersion)?.size != 3) return null
    if (parts.size == 4 && (parts[3].isEmpty() || parts[3].length > 12 || parts[3].toLongOrNull() == null)) return null
    return semanticVersion
}

internal fun isUpdateApkAssetName(assetName: String, updateTagPrefix: String, apkFilePrefix: String): Boolean {
    val officialVersionSuffix = "[0-9]+\\.[0-9]+\\.[0-9]+(?:\\.[0-9]+)?\\.apk"
    val official = updateTagPrefix == "android-v" && (
        assetName.matches(Regex("^${Regex.escape(apkFilePrefix)}$officialVersionSuffix")) ||
            assetName.matches(Regex("^BarcodeGenerator$officialVersionSuffix"))
        )
    val channel = updateTagPrefix != "android-v" &&
        assetName.startsWith(apkFilePrefix) && assetName.endsWith(".apk", true)
    return official || channel
}
