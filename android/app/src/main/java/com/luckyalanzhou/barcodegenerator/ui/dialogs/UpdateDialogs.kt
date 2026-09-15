package com.luckyalanzhou.barcodegenerator

import android.Manifest
import android.app.AlertDialog
import android.content.*
import android.content.pm.PackageManager
import android.graphics.*
import android.media.ExifInterface
import android.net.Uri
import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.withLock
import android.text.*
import android.view.*
import android.widget.*
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.FileProvider
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.zxing.*
import com.google.zxing.common.HybridBinarizer
import org.json.*
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.*
import kotlin.math.roundToInt

internal fun MainActivity.checkForUpdates(silent: Boolean = false) {
    DebugLog.record("update", "check started silent=$silent current=${BuildConfig.VERSION_NAME}")
    lifecycleScope.launch(Dispatchers.IO) {
        try {
            val connection = (URL("https://api.github.com/repos/luckyalanzhou/Barcode-generator-for-android/releases?per_page=100").openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"; connectTimeout = 8000; readTimeout = 8000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "BarcodeGenerator/${BuildConfig.VERSION_NAME}")
            }
            if (connection.responseCode !in 200..299) throw IllegalStateException("GitHub HTTP ${connection.responseCode}")
            val releases = connection.inputStream.bufferedReader().use { JSONArray(it.readText()) }.also { connection.disconnect() }
            val release = (0 until releases.length())
                .mapNotNull { releases.optJSONObject(it) }
                .filter { it.optString("tag_name").startsWith(BuildConfig.UPDATE_TAG_PREFIX) }
                .maxWithOrNull(Comparator { left, right ->
                    compareVersions(parseAppVersion(left.optString("tag_name")) ?: "0.0.0", parseAppVersion(right.optString("tag_name")) ?: "0.0.0")
                })
            val tag = release?.optString("tag_name")?.takeIf { it.isNotBlank() }
            val apkAsset = release?.optJSONArray("assets")?.let { assets ->
                (0 until assets.length()).mapNotNull { assets.optJSONObject(it) }
                    .firstOrNull { asset -> asset.optString("name").startsWith(BuildConfig.APK_FILE_PREFIX) && asset.optString("name").endsWith(".apk", true) }
            }
            val apkUrl = apkAsset?.optString("browser_download_url")?.takeIf { it.isNotBlank() }
            val expectedSize = apkAsset?.optLong("size", 0L)?.takeIf { it > 0L }
            val expectedSha256 = apkAsset?.optString("digest")?.removePrefix("sha256:")?.trim()?.lowercase(Locale.US)?.takeIf { it.matches(Regex("[0-9a-f]{64}")) }
            withContext(Dispatchers.Main) {
                val releaseTag = tag ?: run { if (!silent) toast("暂时无法获取更新信息"); return@withContext }
                val downloadUrl = apkUrl ?: run { if (!silent) toast("暂时无法获取更新信息"); return@withContext }
                val latest = parseAppVersion(releaseTag) ?: run { if (!silent) toast("版本信息格式不正确"); return@withContext }
                val updateAvailable = compareVersions(latest, BuildConfig.VERSION_NAME) > 0
                DebugLog.record("update", "release=$releaseTag latest=$latest available=$updateAvailable asset=${apkAsset?.optString("name")}")
                availableUpdateUrl = if (updateAvailable) downloadUrl else null
                availableUpdateExpectedSize = if (updateAvailable) expectedSize else null
                availableUpdateSha256 = if (updateAvailable) expectedSha256 else null
                if (page == "settings") render()
                if (updateAvailable && !updateDialogShowing) {
                    updateDialogShowing = true
                    showUpdateAvailableDialog(latest, downloadUrl, expectedSize, expectedSha256)
                } else if (!updateAvailable && !silent) showIos26NoticeDialog("当前已是最新版本")
            }
        } catch (error: Exception) {
            DebugLog.record("update", "check failed", error)
            if (!silent) withContext(Dispatchers.Main) { toast("检查更新失败，请稍后重试") }
        }
    }
}

/** 更新操作使用独立的玻璃按钮，按钮可点击区域与可见边框完全一致。 */
private fun MainActivity.updateActionButton(label: String, primary: Boolean = false, action: () -> Unit): Button =
    styleButton(Button(this).apply {
        text = label
        textSize = 14f
        minWidth = 0; minimumWidth = 0
        minHeight = 0; minimumHeight = 0
        isAllCaps = false
        setPadding(dp(6), 0, dp(6), 0)
        setTextColor(if (primary) Color.WHITE else primaryText())
        background = glassButtonBackground().apply {
            if (primary) setColors(if (isDark()) intArrayOf(0xff2678db.toInt(), 0xff0a5fbc.toInt()) else intArrayOf(0xff2d82df.toInt(), 0xff1268c5.toInt()))
        }
        setOnClickListener { action() }
    })

private fun MainActivity.updateDivider() = View(this).apply {
    setBackgroundColor(if (isDark()) 0x33ffffff else 0x26475b7a)
}

/** 下载进度条：流光固定在真实进度前沿，随着进度推进而移动，不循环扫过未下载区域。 */
private class DownloadProgressView(context: Context) : View(context) {
    var progress: Int = 0
        set(value) {
            field = value.coerceIn(0, 100)
            invalidate()
        }
    var isIndeterminate: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    private val density = resources.displayMetrics.density
    private val track = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val light = Paint(Paint.ANTI_ALIAS_FLAG)
    private val outline = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    init {
        minimumHeight = (10 * density).roundToInt()
        setLayerType(View.LAYER_TYPE_SOFTWARE, null)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val inset = density
        rect.set(inset, inset, width - inset, height - inset)
        val radius = rect.height() / 2f
        track.style = Paint.Style.FILL
        track.color = if ((resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES) 0xff30343b.toInt() else 0xffe1e5eb.toInt()
        canvas.drawRoundRect(rect, radius, radius, track)

        if (isIndeterminate) {
            outline.style = Paint.Style.STROKE
            outline.strokeWidth = density
            outline.color = 0xff6b7280.toInt()
            canvas.drawRoundRect(rect, radius, radius, outline)
            return
        }

        val right = rect.left + rect.width() * (progress / 100f)
        if (right > rect.left) {
            fill.style = Paint.Style.FILL
            fill.shader = LinearGradient(rect.left, 0f, rect.right, 0f, 0xff3b82f6.toInt(), 0xff60a5fa.toInt(), Shader.TileMode.CLAMP)
            canvas.save()
            canvas.clipRect(rect.left, rect.top, right, rect.bottom)
            canvas.drawRoundRect(rect, radius, radius, fill)
            canvas.restore()
            fill.shader = null

            // 流光宽度固定在进度前沿，因此下载进度变化时会自然向前移动。
            val glowWidth = 18f * density
            light.style = Paint.Style.FILL
            light.shader = LinearGradient(right - glowWidth, 0f, right + glowWidth, 0f, intArrayOf(Color.TRANSPARENT, 0xccffffff.toInt(), Color.TRANSPARENT), null, Shader.TileMode.CLAMP)
            light.setShadowLayer(5f * density, 0f, 0f, 0xff60a5fa.toInt())
            canvas.save()
            canvas.clipRect(rect.left, rect.top, right.coerceAtMost(rect.right), rect.bottom)
            canvas.drawRect(right - glowWidth, rect.top, right, rect.bottom, light)
            canvas.restore()
            light.clearShadowLayer()
            light.shader = null
        }

        outline.style = Paint.Style.STROKE
        outline.strokeWidth = density
        outline.color = if ((resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES) 0xff6b7280.toInt() else 0xffb8c0cc.toInt()
        canvas.drawRoundRect(rect, radius, radius, outline)
    }
}

internal fun MainActivity.showUpdateAvailableDialog(latest: String, downloadUrl: String, expectedSize: Long?, expectedSha256: String?, simulateOnly: Boolean = false, showMetrics: Boolean = false) {
    val dialog = AlertDialog.Builder(this).create()
    val box = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(22), dp(22), dp(22), dp(16))
        addView(TextView(this@showUpdateAvailableDialog).apply {
            text = "发现新版本"
            textSize = 20f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            includeFontPadding = false
            setTextColor(primaryText())
        }, LinearLayout.LayoutParams(-1, dp(30)))
        addView(TextView(this@showUpdateAvailableDialog).apply {
            text = "检测到版本 $latest，是否立即更新？"
            textSize = 15f
            includeFontPadding = false
            setTextColor(secondaryText())
            setPadding(0, dp(8), 0, dp(16))
        }, LinearLayout.LayoutParams(-1, -2))
        addView(LinearLayout(this@showUpdateAvailableDialog).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(16), 0, 0)
            // 三个按钮保持完整点击区域，并通过更大的外边距拉开视觉间距。
            addView(updateActionButton("忽略更新") { availableUpdateUrl = null; updateDialogShowing = false; dialog.dismiss(); if (page == "settings") render() }, LinearLayout.LayoutParams(-2, dp(38)).apply { rightMargin = dp(8) })
            addView(updateActionButton("稍后更新") { updateDialogShowing = false; dialog.dismiss() }, LinearLayout.LayoutParams(-2, dp(38)).apply { leftMargin = dp(8); rightMargin = dp(8) })
            addView(updateActionButton("立即更新", primary = true) {
                updateDialogShowing = false
                if (simulateOnly) {
                    dialog.dismiss()
                } else {
                    // 等旧窗口完成 dismiss 后再创建下载窗口，避免 Android WindowManager
                    // 在同一点击回调中拒绝/吞掉新弹窗，表现为“立即更新无作用”。
                    dialog.setOnDismissListener(null)
                    dialog.dismiss()
                    window.decorView.post {
                        DebugLog.record("update", "immediate update clicked; starting download")
                        downloadAndInstall(downloadUrl, expectedSize, expectedSha256)
                    }
                }
            }, LinearLayout.LayoutParams(-2, dp(38)).apply { leftMargin = dp(8) })
        }, LinearLayout.LayoutParams(-1, dp(54)))
    }
    dialog.setView(box)
    dialog.setOnCancelListener { updateDialogShowing = false }
    val metricsPopup = if (showMetrics) showSimulationMetrics(box, "发现新版本弹窗") else null
    dialog.setOnDismissListener { metricsPopup?.dismiss() }
    showIos26Dialog(dialog, compact = true)
}

internal fun MainActivity.downloadAndInstall(apkUrl: String, expectedSize: Long? = availableUpdateExpectedSize, expectedSha256: String? = availableUpdateSha256, simulateOnly: Boolean = false, showMetrics: Boolean = false) {
    if (updateDownloadRunning) {
        DebugLog.record("update", "download ignored because another download is running")
        return
    }
    updateDownloadRunning = true
    DebugLog.record("update", "download dialog shown url=$apkUrl expectedSize=$expectedSize shaPresent=${expectedSha256 != null}")
    val progress = DownloadProgressView(this)
    val status = TextView(this).apply { text = "准备下载…"; textSize = 14f; setTextColor(secondaryText()); setPadding(0, dp(10), 0, 0) }
    val dialog = AlertDialog.Builder(this).create()
    var job: kotlinx.coroutines.Job? = null
    val cancelButton = updateActionButton("取消下载") {
        // 取消按钮必须立即释放下载锁；否则用户马上重新检查更新时会被旧任务状态拦截，
        // 表现为“立即更新”没有下载进度弹窗。协程 finally 会继续负责断开连接和清理临时文件。
        updateDownloadRunning = false
        job?.cancel()
        dialog.dismiss()
    }
    val box = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        // 下载进度弹窗保持紧凑，减少标题、进度和按钮之间的无效留白。
        setPadding(dp(22), dp(16), dp(22), dp(10))
        addView(TextView(this@downloadAndInstall).apply { text = "下载更新"; textSize = 20f; typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL); includeFontPadding = false; setTextColor(primaryText()) }, LinearLayout.LayoutParams(-1, dp(30)))
        addView(progress, LinearLayout.LayoutParams(-1, dp(8)).apply { topMargin = dp(10) })
        addView(status, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
        addView(LinearLayout(this@downloadAndInstall).apply {
            // “取消下载”按钮靠右排列，按钮本身仍保持完整点击范围。
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            addView(cancelButton, LinearLayout.LayoutParams(-2, dp(38)))
        }, LinearLayout.LayoutParams(-1, dp(46)).apply { topMargin = dp(8) })
    }
    dialog.setView(box)
    val metricsPopup = if (showMetrics) showSimulationMetrics(box, "下载进度弹窗") else null
    dialog.setOnDismissListener { metricsPopup?.dismiss() }
    showIos26Dialog(dialog)
    if (simulateOnly) {
        progress.isIndeterminate = false
        progress.progress = 50
        status.text = "已下载 50%（模拟）"
        updateDownloadRunning = false
        return
    }
    job = lifecycleScope.launch(Dispatchers.IO) {
        val temp = File(cacheDir, "barcode-generator-update.apk.part")
        val official = File(cacheDir, "barcode-generator-update.apk")
        var connection: HttpURLConnection? = null
        try {
            val limit = UpdateSecurity.MAX_APK_DOWNLOAD_BYTES
            require(expectedSha256 != null) { "该版本缺少 SHA-256 校验信息，无法安全更新" }
            require(expectedSize == null || expectedSize <= limit) { "更新包超过 500 MB 限制" }
            require(Uri.parse(apkUrl).scheme.equals("https", ignoreCase = true)) { "更新包必须使用 HTTPS 下载" }
            connection = URL(apkUrl).openConnection() as HttpURLConnection
            connection!!.apply { connectTimeout = 15000; readTimeout = 30000; instanceFollowRedirects = true; setRequestProperty("User-Agent", "BarcodeGenerator/${BuildConfig.VERSION_NAME}") }
            if (connection!!.responseCode !in 200..299) throw IllegalStateException("HTTP ${connection!!.responseCode}")
            DebugLog.record("update", "download response=${connection!!.responseCode} contentLength=${connection!!.contentLengthLong}")
            val total = connection!!.contentLengthLong.takeIf { it > 0 } ?: expectedSize
            require(total == null || total <= limit) { "更新包超过 500 MB 限制" }
            temp.delete()
            connection!!.inputStream.use { input -> temp.outputStream().use { output ->
                val buffer = ByteArray(16 * 1024); var done = 0L; var count: Int
                while (input.read(buffer).also { count = it } != -1) {
                    ensureActive(); require(done + count <= limit) { "更新包超过 500 MB 限制" }
                    output.write(buffer, 0, count); done += count
                    withContext(Dispatchers.Main) { if (total != null) { progress.isIndeterminate = false; progress.progress = (done * 100 / total).toInt().coerceIn(0, 100); status.text = "已下载 ${progress.progress}%" } else { progress.isIndeterminate = true; status.text = "正在下载… ${done / 1024} KB" } }
                }
            } }
            require(temp.isFile && temp.length() > 0L) { "APK 为空" }
            require(expectedSize == null || temp.length() == expectedSize) { "文件大小校验失败：${temp.length()} / $expectedSize" }
            val digest = MessageDigest.getInstance("SHA-256")
            val actual = temp.inputStream().use { input -> val buffer = ByteArray(16 * 1024); var count: Int; while (input.read(buffer).also { count = it } != -1) digest.update(buffer, 0, count); digest.digest().joinToString("") { "%02x".format(it) } }
            require(actual.equals(expectedSha256, true)) { "SHA-256 校验失败" }
            validateDownloadedApk(temp)
            DebugLog.record("update", "download validated size=${temp.length()}")
            official.delete(); require(temp.renameTo(official)) { "无法保存更新文件" }
            cacheDir.listFiles()?.filter { it.name.startsWith("barcode-generator-update") && it != official }?.forEach { it.delete() }
            withContext(Dispatchers.Main) { dialog.dismiss(); installApk(official) }
        } catch (error: Exception) {
            DebugLog.record("update", "download failed", error)
            temp.delete()
            withContext(Dispatchers.Main) { dialog.dismiss(); if (error !is kotlinx.coroutines.CancellationException) { val reason = error.message ?: "未知错误"; settingsStore.setUpdateError(reason); AlertDialog.Builder(this@downloadAndInstall).setTitle("更新下载失败").setMessage(reason).setPositiveButton("重新下载") { _, _ -> downloadAndInstall(apkUrl, expectedSize, expectedSha256) }.create().also { showIos26Dialog(it) } } }
        } finally { connection?.disconnect(); withContext(Dispatchers.Main) { updateDownloadRunning = false } }
    }
}


private fun MainActivity.validateDownloadedApk(file: File) {
    val info = packageManager.getPackageArchiveInfo(file.absolutePath, PackageManager.GET_SIGNING_CERTIFICATES or PackageManager.GET_SIGNATURES) ?: throw IllegalStateException("无法读取 APK 信息")
    if (info.packageName != packageName) throw IllegalStateException("APK 包名与当前应用不一致")
    val versionCode = if (android.os.Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
    if (versionCode <= BuildConfig.VERSION_CODE) throw IllegalStateException("APK 版本不是当前版本的更高版本")
    val downloaded = if (android.os.Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners else info.signatures
    val installedInfo = packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES or PackageManager.GET_SIGNATURES)
    val installed = if (android.os.Build.VERSION.SDK_INT >= 28) installedInfo.signingInfo?.apkContentsSigners else installedInfo.signatures
    if (downloaded.isNullOrEmpty() || installed.isNullOrEmpty() || downloaded.map { it.toCharsString() }.toSet() != installed.map { it.toCharsString() }.toSet()) throw IllegalStateException("APK 签名与当前应用不一致")
}

internal fun MainActivity.installApk(file: File) {
    try {
        if (!file.isFile || file.length() == 0L) { toast("更新文件不存在，请重新下载"); return }
        if (android.os.Build.VERSION.SDK_INT >= 26 && !packageManager.canRequestPackageInstalls()) {
            viewModel.pendingInstallPath = file.absolutePath
            AlertDialog.Builder(this).setTitle("需要允许安装未知应用").setMessage("为了安装应用更新，请在系统设置中允许“条码生成器”安装未知应用。开启后返回本应用，将自动继续安装。")
                .setNegativeButton("取消") { _, _ -> viewModel.pendingInstallPath = null }
                .setPositiveButton("去设置") { _, _ -> startActivity(Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName"))) }
                .setOnCancelListener { viewModel.pendingInstallPath = null }.create().also { showIos26Dialog(it) }; return
        }
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply { setDataAndType(uri, "application/vnd.android.package-archive"); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION); clipData = android.content.ClipData.newRawUri("APK", uri) }
        if (intent.resolveActivity(packageManager) == null) { toast("未找到可用的安装程序"); return }
        startActivity(intent)
    } catch (error: Exception) { val reason = error.message ?: "未知安装错误"; settingsStore.setUpdateError(reason); toast("安装失败：$reason") }
}

    /** Release 标签为当前渠道前缀加版本号；构建号绝不参与应用版本比较。 */

internal fun MainActivity.parseAppVersion(releaseTag: String): String? {
    val value = releaseTag.trim().removePrefix(BuildConfig.UPDATE_TAG_PREFIX).removePrefix("v")
    val parts = value.split(".")
    if (parts.size < 3 || parts.size > 4 || parts.take(3).any { it.isEmpty() || it.length > 9 || it.toLongOrNull() == null }) return null
    if (parts.size == 4 && (parts[3].isEmpty() || parts[3].length > 12 || parts[3].toLongOrNull() == null)) return null
    return parts.take(3).joinToString(".")
}


internal fun MainActivity.compareVersions(a: String, b: String): Int = UpdateSecurity.compareVersions(a, b)



