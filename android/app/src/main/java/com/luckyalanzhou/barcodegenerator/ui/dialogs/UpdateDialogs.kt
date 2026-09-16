package com.luckyalanzhou.barcodegenerator

import android.content.pm.PackageManager
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Comparator
import java.util.Locale

/**
 * 更新检查与 APK 安全校验业务。
 *
 * 更新确认、下载进度、失败重试和安装权限界面全部由 ComposeUpdateDialogs.kt 绘制；
 * 本文件只保留网络、版本、签名和文件系统逻辑。
 */
internal fun MainActivity.checkForUpdates(silent: Boolean = false) {
    DebugLog.record("update", "check started silent=${silent} current=${BuildConfig.VERSION_NAME}")
    lifecycleScope.launch(Dispatchers.IO) {
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
                if (connection.responseCode !in 200..299) {
                    throw IllegalStateException("GitHub HTTP ${connection.responseCode}")
                }
                connection.inputStream.bufferedReader().use { JSONArray(it.readText()) }
            } finally {
                connection.disconnect()
            }
            val release = (0 until releases.length())
                .mapNotNull { releases.optJSONObject(it) }
                .filter { it.optString("tag_name").startsWith(BuildConfig.UPDATE_TAG_PREFIX) }
                .maxWithOrNull(Comparator { left, right ->
                    compareVersions(
                        parseAppVersion(left.optString("tag_name")) ?: "0.0.0",
                        parseAppVersion(right.optString("tag_name")) ?: "0.0.0",
                    )
                })
            val tag = release?.optString("tag_name")?.takeIf { it.isNotBlank() }
            val apkAsset = release?.optJSONArray("assets")?.let { assets ->
                (0 until assets.length())
                    .mapNotNull { assets.optJSONObject(it) }
                    .firstOrNull { asset ->
                        asset.optString("name").startsWith(BuildConfig.APK_FILE_PREFIX) &&
                            asset.optString("name").endsWith(".apk", true)
                    }
            }
            val apkUrl = apkAsset?.optString("browser_download_url")?.takeIf { it.isNotBlank() }
            val expectedSize = apkAsset?.optLong("size", 0L)?.takeIf { it > 0L }
            val expectedSha256 = apkAsset?.optString("digest")
                ?.removePrefix("sha256:")
                ?.trim()
                ?.lowercase(Locale.US)
                ?.takeIf { it.matches(Regex("[0-9a-f]{64}")) }

            withContext(Dispatchers.Main) {
                val releaseTag = tag ?: run {
                    if (!silent) toast("暂时无法获取更新信息")
                    return@withContext
                }
                val downloadUrl = apkUrl ?: run {
                    if (!silent) toast("暂时无法获取更新信息")
                    return@withContext
                }
                val latest = parseAppVersion(releaseTag) ?: run {
                    if (!silent) toast("版本信息格式不正确")
                    return@withContext
                }
                val updateAvailable = compareVersions(latest, BuildConfig.VERSION_NAME) > 0
                DebugLog.record(
                    "update",
                    "release=${releaseTag} latest=${latest} available=${updateAvailable} asset=${apkAsset?.optString("name")}",
                )
                availableUpdateUrl = if (updateAvailable) downloadUrl else null
                availableUpdateExpectedSize = if (updateAvailable) expectedSize else null
                availableUpdateSha256 = if (updateAvailable) expectedSha256 else null
                if (page == "settings") render()
                if (updateAvailable && !updateDialogShowing) {
                    updateDialogShowing = true
                    showUpdateAvailableDialog(latest, downloadUrl, expectedSize, expectedSha256)
                } else if (!updateAvailable && !silent) {
                    showIos26NoticeDialog("当前已是最新版本")
                }
            }
        } catch (error: Exception) {
            DebugLog.record("update", "check failed", error)
            if (!silent) withContext(Dispatchers.Main) { toast("检查更新失败，请稍后重试") }
        }
    }
}

internal fun MainActivity.showUpdateAvailableDialog(
    latest: String,
    downloadUrl: String,
    expectedSize: Long?,
    expectedSha256: String?,
    simulateOnly: Boolean = false,
    showMetrics: Boolean = false,
) {
    showUpdateAvailableDialogCompose(latest, downloadUrl, expectedSize, expectedSha256, simulateOnly, showMetrics)
}

internal fun MainActivity.downloadAndInstall(
    apkUrl: String,
    expectedSize: Long? = availableUpdateExpectedSize,
    expectedSha256: String? = availableUpdateSha256,
    simulateOnly: Boolean = false,
    showMetrics: Boolean = false,
) {
    downloadAndInstallCompose(apkUrl, expectedSize, expectedSha256, simulateOnly, showMetrics)
}

internal fun MainActivity.validateDownloadedApk(file: File) {
    val signingFlags = if (android.os.Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
    val info = packageManager.getPackageArchiveInfo(
        file.absolutePath,
        signingFlags,
    ) ?: throw IllegalStateException("无法读取 APK 信息")
    if (info.packageName != packageName) throw IllegalStateException("APK 包名与当前应用不一致")
    val versionCode = if (android.os.Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
    if (versionCode <= BuildConfig.VERSION_CODE) throw IllegalStateException("APK 版本不是当前版本的更高版本")
    val downloaded = if (android.os.Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners else info.signatures
    val installedInfo = packageManager.getPackageInfo(
        packageName,
        signingFlags,
    )
    val installed = if (android.os.Build.VERSION.SDK_INT >= 28) installedInfo.signingInfo?.apkContentsSigners else installedInfo.signatures
    if (downloaded.isNullOrEmpty() || installed.isNullOrEmpty() ||
        downloaded.map { it.toCharsString() }.toSet() != installed.map { it.toCharsString() }.toSet()
    ) {
        throw IllegalStateException("APK 签名与当前应用不一致")
    }
}

internal fun MainActivity.installApk(file: File) {
    installApkCompose(file)
}

/** Release 标签为当前渠道前缀加版本号；构建号不参与应用版本比较。 */
internal fun MainActivity.parseAppVersion(releaseTag: String): String? {
    val value = releaseTag.trim().removePrefix(BuildConfig.UPDATE_TAG_PREFIX).removePrefix("v")
    val parts = value.split(".")
    if (parts.size < 3 || parts.size > 4 ||
        parts.take(3).any { it.isEmpty() || it.length > 9 || it.toLongOrNull() == null }
    ) return null
    if (parts.size == 4 && (parts[3].isEmpty() || parts[3].length > 12 || parts[3].toLongOrNull() == null)) return null
    return parts.take(3).joinToString(".")
}

internal fun MainActivity.compareVersions(a: String, b: String): Int =
    UpdateSecurity.compareVersions(a, b)
