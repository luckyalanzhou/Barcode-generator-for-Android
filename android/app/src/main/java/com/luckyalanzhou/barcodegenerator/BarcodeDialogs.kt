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
                availableUpdateUrl = if (updateAvailable) downloadUrl else null
                availableUpdateExpectedSize = if (updateAvailable) expectedSize else null
                availableUpdateSha256 = if (updateAvailable) expectedSha256 else null
                if (page == "settings") render()
                if (updateAvailable && !updateDialogShowing) {
                    updateDialogShowing = true
                    showUpdateAvailableDialog(latest, downloadUrl, expectedSize, expectedSha256)
                } else if (!updateAvailable && !silent) showIos26NoticeDialog("当前已是最新版本")
            }
        } catch (_: Exception) { if (!silent) withContext(Dispatchers.Main) { toast("检查更新失败，请稍后重试") } }
    }
}

/** 更新操作使用独立的玻璃按钮，按钮可点击区域与可见边框完全一致。 */
private fun MainActivity.updateActionButton(label: String, primary: Boolean = false, action: () -> Unit): Button =
    styleButton(Button(this).apply {
        text = label
        textSize = 14f
        minWidth = 0; minimumWidth = 0
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

private fun MainActivity.showUpdateAvailableDialog(latest: String, downloadUrl: String, expectedSize: Long?, expectedSha256: String?) {
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
        addView(updateDivider(), LinearLayout.LayoutParams(-1, dp(1)))
        addView(LinearLayout(this@showUpdateAvailableDialog).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(16), 0, 0)
            addView(updateActionButton("忽略更新") { availableUpdateUrl = null; updateDialogShowing = false; dialog.dismiss(); if (page == "settings") render() }, LinearLayout.LayoutParams(0, dp(42), 1f).apply { rightMargin = dp(6) })
            addView(updateActionButton("稍后更新") { updateDialogShowing = false; dialog.dismiss() }, LinearLayout.LayoutParams(0, dp(42), 1f).apply { setMargins(dp(3), 0, dp(3), 0) })
            addView(updateActionButton("立即更新", primary = true) { updateDialogShowing = false; dialog.dismiss(); downloadAndInstall(downloadUrl, expectedSize, expectedSha256) }, LinearLayout.LayoutParams(0, dp(42), 1f).apply { leftMargin = dp(6) })
        }, LinearLayout.LayoutParams(-1, dp(58)))
    }
    dialog.setView(box)
    dialog.setOnCancelListener { updateDialogShowing = false }
    showIos26Dialog(dialog, compact = true)
}

internal fun MainActivity.downloadAndInstall(apkUrl: String, expectedSize: Long? = availableUpdateExpectedSize, expectedSha256: String? = availableUpdateSha256) {
    if (updateDownloadRunning) return
    updateDownloadRunning = true
    val progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100 }
    val status = TextView(this).apply { text = "准备下载…"; textSize = 14f; setTextColor(secondaryText()); setPadding(0, dp(10), 0, 0) }
    val dialog = AlertDialog.Builder(this).create()
    val box = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(22), dp(22), dp(22), dp(16))
        addView(TextView(this@downloadAndInstall).apply { text = "下载更新"; textSize = 20f; typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL); includeFontPadding = false; setTextColor(primaryText()) }, LinearLayout.LayoutParams(-1, dp(30)))
        addView(progress, LinearLayout.LayoutParams(-1, dp(8)).apply { topMargin = dp(14) })
        addView(status, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(16) })
        addView(updateDivider(), LinearLayout.LayoutParams(-1, dp(1)))
        addView(updateActionButton("取消下载") { }, LinearLayout.LayoutParams(-1, dp(42)).apply { topMargin = dp(16) })
    }
    dialog.setView(box)
    var job: kotlinx.coroutines.Job? = null
    dialog.setOnShowListener { (box.getChildAt(box.childCount - 1) as Button).setOnClickListener { job?.cancel(); dialog.dismiss() } }
    showIos26Dialog(dialog)
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
            official.delete(); require(temp.renameTo(official)) { "无法保存更新文件" }
            cacheDir.listFiles()?.filter { it.name.startsWith("barcode-generator-update") && it != official }?.forEach { it.delete() }
            withContext(Dispatchers.Main) { dialog.dismiss(); installApk(official) }
        } catch (error: Exception) {
            temp.delete()
            withContext(Dispatchers.Main) { dialog.dismiss(); if (error !is kotlinx.coroutines.CancellationException) { val reason = error.message ?: "未知错误"; settingsStore.setUpdateError(reason); AlertDialog.Builder(this@downloadAndInstall).setTitle("更新下载失败").setMessage(reason).setNegativeButton("关闭", null).setPositiveButton("重新下载") { _, _ -> downloadAndInstall(apkUrl, expectedSize, expectedSha256) }.create().also { showIos26Dialog(it) } } }
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


internal fun MainActivity.saveResultAsFavorite() {
    val activity = this
    if (resultItems.isEmpty()) return
    // 只有从收藏结果页进入的编辑流程才更新原文件；普通生成一律新建收藏。
    val editingGroup = selectedFavoriteGroup?.takeIf { resultsReturnPage == "favorites" }
    val folders = favoriteFolders.toMutableList()
    // 新建收藏不预选任何文件夹，避免误存到上一次使用的位置。
    var selectedFolder = editingGroup?.folder?.takeIf { it in folders }.orEmpty()
    lateinit var folderButton: Button
    folderButton = styleButton(Button(this).apply {
        text = selectedFolder.ifBlank { "选择文件夹" }
        setOnClickListener {
            val topFolders = folders.filter { !it.contains("/") }.distinct()
            if (topFolders.isEmpty()) { toast("请先新建一级文件夹"); return@setOnClickListener }
            val selectedParent = selectedFolder.substringBefore('/').takeIf { it in topFolders }
            showMaterialDropdown(folderButton, topFolders, popupWidth = folderButton.width, selectedIndex = topFolders.indexOf(selectedParent), forceBelowAnchor = true) { which ->
                val parent = topFolders[which]
                val children = folders.filter { it.startsWith("$parent/") && !it.removePrefix("$parent/").contains("/") }
                val options = children.map { it.removePrefix("$parent/") }.toMutableList().apply { add("使用一级文件夹：$parent"); add("新建二级文件夹") }
                val selectedChild = selectedFolder.removePrefix("$parent/").takeIf { selectedFolder.startsWith("$parent/") }
                showMaterialDropdown(folderButton, options, popupWidth = folderButton.width, selectedIndex = children.indexOf(selectedChild), forceBelowAnchor = true) { childIndex ->
                    when {
                        childIndex < children.size -> { selectedFolder = children[childIndex]; folderButton.text = selectedFolder }
                        childIndex == children.size -> { selectedFolder = parent; folderButton.text = selectedFolder }
                        else -> showSubfolderEditor(parent) { child ->
                            val path = "$parent/$child"
                            if (path !in folders) folders.add(path)
                            if (path !in favoriteFolders) favoriteFolders.add(path)
                            selectedFolder = path; folderButton.text = selectedFolder; saveFavoriteFolders()
                        }
                    }
                }
            }
        }
    })
    val addFolder = styleButton(Button(this).apply {
        text = "新建文件夹"
        setOnClickListener { showFolderEditor { folder ->
            if (folder !in folders) folders.add(folder)
            if (folder !in favoriteFolders) favoriteFolders.add(folder)
            selectedFolder = folder; folderButton.text = folder; saveFavoriteFolders()
        } }
    }).apply { setBackgroundDrawable(glassButtonBackground()) }
    val nameInput = EditText(this).apply {
        hint = "输入收藏文件名"
        setText(editingGroup?.name.orEmpty())
        setSingleLine(true)
        setBackgroundResource(R.drawable.bg_input)
        setPadding(dp(12), 0, dp(12), 0)
    }
    val box = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(24), dp(4), dp(24), 0)
        addView(TextView(activity).apply { text = "选择文件夹"; textSize = 14f; setTextColor(secondaryText()); setPadding(0, 0, 0, dp(6)) })
        addView(folderButton, LinearLayout.LayoutParams(-1, dp(48)))
        addView(addFolder, LinearLayout.LayoutParams(-2, dp(42)).apply { setMargins(0, dp(8), 0, dp(16)) })
        addView(TextView(activity).apply { text = "收藏文件名"; textSize = 14f; setTextColor(secondaryText()); setPadding(0, 0, 0, dp(6)) })
        addView(nameInput, LinearLayout.LayoutParams(-1, dp(48)))
    }
    fun persistFavorite(target: FavoriteGroup?, folder: String, name: String) {
        val savedAt = System.currentTimeMillis()
        resultItems.forEach { it.favorite = true; it.folder = folder }
        if (folder !in favoriteFolders) favoriteFolders.add(folder)
        if (target == null) favoriteGroups.add(0, FavoriteGroup(nextGroupId(), folder, name, savedAt, resultItems.map { it.id }.toMutableList()))
        else {
            val index = favoriteGroups.indexOfFirst { it.id == target.id }
            if (index >= 0) favoriteGroups[index] = FavoriteGroup(target.id, folder, name, savedAt, resultItems.map { it.id }.toMutableList())
        }
        items.filter { it.favorite && favoriteGroups.none { group -> group.itemIds.contains(it.id) } }.forEach { it.favorite = false }
        saveAllFavorites(); selectedFavoriteGroup = null; page = "favorites"; render(); toast("已保存到 $folder")
    }
    val saveDialog = AlertDialog.Builder(this).setTitle(if (editingGroup == null) "保存到收藏" else "编辑收藏").setView(box).setNegativeButton("取消", null).setPositiveButton("保存") { _, _ ->
        val name = nameInput.text.toString().trim()
        if (name.isEmpty()) { toast("请输入收藏文件名"); return@setPositiveButton }
        if (selectedFolder.isBlank()) { toast("请选择文件夹"); return@setPositiveButton }
        val conflict = favoriteGroups.firstOrNull { it.id != editingGroup?.id && it.folder == selectedFolder && it.name == name }
        if (conflict == null) persistFavorite(editingGroup, selectedFolder, name)
        else {
            val overwriteDialog = AlertDialog.Builder(activity).setTitle("覆盖收藏").setMessage("“$selectedFolder/$name”已存在，是否覆盖？").setNegativeButton("取消", null).setPositiveButton("覆盖") { _, _ ->
            if (editingGroup != null && editingGroup.id != conflict.id) favoriteGroups.removeAll { it.id == editingGroup.id }
            persistFavorite(conflict, selectedFolder, name)
            }.create()
            showIos26Dialog(overwriteDialog)
        }
    }.create()
    showIos26Dialog(saveDialog)
}


internal fun MainActivity.scanWithCamera() {
        val activity = this
        openCamera(43)
    }


internal fun MainActivity.captureText() {
        val activity = this
        openCamera(MainActivity.REQUEST_TEXT_CAMERA)
}


internal fun MainActivity.openCamera(requestCode: Int) {
        val activity = this
        pendingCameraRequest = requestCode
        if (android.os.Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), MainActivity.REQUEST_CAMERA_PERMISSION)
            return
        }
        launchCamera(requestCode)
    }


internal fun MainActivity.launchCamera(requestCode: Int) {
        val activity = this
        val photoFile = File.createTempFile("barcode_camera_", ".jpg", cacheDir)
        val photoUri = FileProvider.getUriForFile(this, "$packageName.fileprovider", photoFile)
        pendingCameraUri = photoUri
        pendingCameraFile = photoFile
        val intent = Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE).apply {
            putExtra(android.provider.MediaStore.EXTRA_OUTPUT, photoUri)
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            startActivityForResult(intent, requestCode)
        } catch (_: Exception) {
            pendingCameraUri = null
            pendingCameraFile = null
            photoFile.delete()
            toast("当前设备没有可用的相机")
        }
    }


internal fun MainActivity.createFavoritesExport() {
    AlertDialog.Builder(this)
        .setTitle("导出收藏")
        .setItems(arrayOf("分享到其他应用", "保存到文件")) { _, which ->
            if (which == 0) shareFavoritesExport() else createFavoritesDocumentExport()
        }
        .create()
        .also { showIos26Dialog(it) }
}

/** 生成临时 ZIP 备份并交给系统分享面板，可发送至聊天、邮件、网盘或文件管理器。 */
private fun MainActivity.shareFavoritesExport() {
    val name = "barcode-generator-backup-android.zip"
    lifecycleScope.launch(Dispatchers.IO) {
        val exportFile = File(cacheDir, name)
        val exportUri = FileProvider.getUriForFile(this@shareFavoritesExport, "$packageName.fileprovider", exportFile)
        val result = runCatching { FavoritesTransferManager.export(contentResolver, exportUri, dao.loadGroups(), dao.loadGroupItems(), dao.loadItems(), dao.loadFolders()) }
        withContext(Dispatchers.Main) {
            result.onSuccess {
                val share = Intent(Intent.ACTION_SEND).apply {
                    type = "application/zip"
                    putExtra(Intent.EXTRA_STREAM, exportUri)
                    putExtra(Intent.EXTRA_TITLE, name)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    clipData = ClipData.newRawUri("收藏备份", exportUri)
                }
                startActivity(Intent.createChooser(share, "导出收藏到"))
            }.onFailure { toast("收藏导出失败：${it.message ?: "无法生成备份"}") }
        }
    }
}

/** 保留原有的 SAF 文件保存入口，供需要指定位置时使用。 */
private fun MainActivity.createFavoritesDocumentExport() {
    val name = "barcode-generator-backup-android.zip"
    startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply { type = "application/zip"; putExtra(Intent.EXTRA_TITLE, name); addCategory(Intent.CATEGORY_OPENABLE) }, MainActivity.REQUEST_FAVORITES_EXPORT)
}

internal fun MainActivity.restoreFavoritesImport() {
    startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply { type = "application/zip"; addCategory(Intent.CATEGORY_OPENABLE); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }, MainActivity.REQUEST_FAVORITES_IMPORT)
}

internal fun MainActivity.exportFavorites(uri: Uri) {
    lifecycleScope.launch(Dispatchers.IO) {
        val result = runCatching { FavoritesTransferManager.export(contentResolver, uri, dao.loadGroups(), dao.loadGroupItems(), dao.loadItems(), dao.loadFolders()) }
        withContext(Dispatchers.Main) { result.onSuccess { toast("收藏备份已导出") }.onFailure { toast("收藏导出失败：${it.message ?: "无法写入文件"}") } }
    }
}

internal fun MainActivity.confirmImportFavorites(uri: Uri) {
    val activity = this
    lifecycleScope.launch(Dispatchers.IO) {
        val parsed = runCatching { FavoritesTransferManager.restore(contentResolver, uri) }
        withContext(Dispatchers.Main) {
            parsed.onFailure { toast("无法导入收藏：${it.message ?: "文件格式无效"}") }.onSuccess { backup ->
                AlertDialog.Builder(activity).setTitle("导入跨平台收藏？").setMessage("将合并 ${backup.favorites.size} 个收藏，并保留一级文件夹、二级文件夹和收藏文件名。不会删除当前数据。")
                    .setNegativeButton("取消", null).setPositiveButton("导入") { _, _ -> importFavorites(backup) }.create().also { showIos26Dialog(it) }
            }
        }
    }
}

private fun MainActivity.importFavorites(backup: InterchangeBackup) {
    lifecycleScope.launch(Dispatchers.IO) {
        val result = runCatching {
            databaseMutex.withLock {
                val counts = database.withTransaction {
                    val transfer = FavoritesTransferManager.appendEntities(backup, dao.loadItems(), dao.loadGroups(), dao.loadGroupItems())
                    dao.saveItems(transfer.items); dao.saveGroups(transfer.groups); dao.saveGroupItems(transfer.links); dao.saveFolders(transfer.folders)
                    transfer.items.size to transfer.groups.size
                }
                loadItemsOnIo(); loadFavoriteGroupsOnIo(); loadFavoriteFoldersOnIo()
                collapsedFavoriteFolders.addAll(backup.folders.filter { it.isNotBlank() })
                collapsedFavoriteFolders.addAll(backup.favorites.map { it.folder }.filter { it.isNotBlank() })
                counts
            }
        }
        withContext(Dispatchers.Main) { result.onSuccess { (itemCount, groupCount) -> page = "favorites"; render(); toast("已导入 $groupCount 个收藏，$itemCount 条码") }.onFailure { toast("收藏导入失败：${it.message ?: "无法写入数据"}") } }
    }
}

internal fun MainActivity.pickBarcodeImage() { startActivityForResult(Intent(Intent.ACTION_GET_CONTENT).apply { type = "image/*"; addCategory(Intent.CATEGORY_OPENABLE) }, 44) }


internal fun MainActivity.pickTextImage() { startActivityForResult(Intent(Intent.ACTION_GET_CONTENT).apply { type = "image/*"; addCategory(Intent.CATEGORY_OPENABLE) }, 46) }


/**
 * 拍摄电脑屏幕时相机经常把方向写在 EXIF 中，且原图可能大到让 OCR 处理变慢。
 * 先按 EXIF 校正，再限制最长边，保证屏幕文字以正确方向和稳定尺寸交给 ML Kit。
 */
internal fun MainActivity.prepareTextBitmap(bitmap: Bitmap, sourceFile: File?): Bitmap {
    var prepared = bitmap
    val orientation = sourceFile?.let {
        runCatching { ExifInterface(it.absolutePath).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
            .getOrDefault(ExifInterface.ORIENTATION_NORMAL)
    } ?: ExifInterface.ORIENTATION_NORMAL
    val rotation = when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90, ExifInterface.ORIENTATION_TRANSPOSE -> 90f
        ExifInterface.ORIENTATION_ROTATE_180 -> 180f
        ExifInterface.ORIENTATION_ROTATE_270, ExifInterface.ORIENTATION_TRANSVERSE -> 270f
        else -> 0f
    }
    if (rotation != 0f) {
        prepared = runCatching {
            Bitmap.createBitmap(prepared, 0, 0, prepared.width, prepared.height, Matrix().apply { postRotate(rotation) }, true)
        }.getOrDefault(prepared)
    }
    val longest = maxOf(prepared.width, prepared.height)
    if (longest > 2400) {
        val scale = 2400f / longest.toFloat()
        prepared = Bitmap.createScaledBitmap(prepared, (prepared.width * scale).roundToInt(), (prepared.height * scale).roundToInt(), true)
    }
    return prepared
}


internal fun MainActivity.recognizeText(bitmap: Bitmap) {
        val activity = this
        val enhanced = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val matrix = ColorMatrix().apply { setSaturation(0f); val scale = 1.35f; val offset = -44.8f; set(floatArrayOf(scale, 0f, 0f, 0f, offset, 0f, scale, 0f, 0f, offset, 0f, 0f, scale, 0f, offset, 0f, 0f, 0f, 1f, 0f)) }
        Canvas(enhanced).drawBitmap(bitmap, 0f, 0f, Paint(Paint.ANTI_ALIAS_FLAG).apply { colorFilter = ColorMatrixColorFilter(matrix) })
        val image = InputImage.fromBitmap(enhanced, 0)
        val recognizer = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
        recognizer.process(image)
            .addOnSuccessListener { results ->
                val recognizedText = forceOcrConfusionReplacement(results.text, settingsStore.getOcrConfusionReplacementMask())
                val normalizedText = recognizedText.trim()
                if (normalizedText.isEmpty()) toast("未识别到文字，请拍摄清晰、正面的屏幕区域")
                else {
                    importRecognizedText(normalizedText)
                    toast("文字识别成功，已按行添加到输入框")
                }
            }
            .addOnFailureListener { toast("文字识别失败，请重试") }
            .addOnCompleteListener { recognizer.close(); enhanced.recycle() }
    }

/** 可选的数字优先纠错：只在用户主动开启时，将常见 OCR 混淆字符改为数字。 */
private fun forceOcrConfusionReplacement(text: String, mask: Int): String = text.map { char ->
    when {
        char in "Oo" && mask and SettingsStore.OCR_REPLACE_O_ZERO != 0 -> '0'
        char in "Iil" && mask and SettingsStore.OCR_REPLACE_I_ONE != 0 -> '1'
        char in "Ss" && mask and SettingsStore.OCR_REPLACE_S_FIVE != 0 -> '5'
        char in "Bb" && mask and SettingsStore.OCR_REPLACE_B_EIGHT != 0 -> '8'
        else -> char
    }
}.joinToString("")


internal fun MainActivity.importRecognizedText(text: String) {
        val activity = this
        val values = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (values.isEmpty()) return
        while (inputRows.size > 1) removeInputRow(inputRows.last())
        inputRows.firstOrNull()?.setText(values.first()) ?: addInputRow(values.first())
        values.drop(1).forEach { addInputRow(it) }
    }


internal fun MainActivity.decodeBitmap(bitmap: Bitmap): String? = try {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        MultiFormatReader().decode(BinaryBitmap(HybridBinarizer(RGBLuminanceSource(bitmap.width, bitmap.height, pixels)))).text
    } catch (_: Exception) { null }


internal fun MainActivity.showFolderEditor(initial: String = "", onSaved: (String) -> Unit) {
        val input = inputField("文件夹名称", initial)
        val box = LinearLayout(this).apply { setPadding(dp(24), dp(8), dp(24), 0); addView(input, LinearLayout.LayoutParams(-1, dp(50))) }
        val dialog = AlertDialog.Builder(this).setTitle(if (initial.isBlank()) "新建文件夹" else "重命名文件夹").setView(box).setNegativeButton("取消", null).setPositiveButton("保存", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = input.text.toString().trim()
                if (name.isBlank()) { toast("请输入文件夹名称"); return@setOnClickListener }
                if (favoriteFolders.any { it == name && it != initial }) { toast("已存在同名文件夹"); return@setOnClickListener }
                onSaved(name); dialog.dismiss()
            }
        }
        showIos26Dialog(dialog)
    }


internal fun MainActivity.showGroupEditor(group: FavoriteGroup) {
        val activity = this
        val nameInput = inputField("收藏文件名", group.name)
        val folderInput = inputField("文件夹", group.folder)
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(24), dp(8), dp(24), 0); addView(nameInput); addView(folderInput, LinearLayout.LayoutParams(-1, dp(50)).apply { setMargins(0, dp(10), 0, 0) }) }
        val dialog = AlertDialog.Builder(this).setTitle("编辑收藏").setView(box).setNegativeButton("取消", null).setNeutralButton("删除", null).setPositiveButton("保存", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = nameInput.text.toString().trim(); val folder = folderInput.text.toString().trim().ifEmpty { "默认" }
                if (name.isBlank()) { toast("请输入收藏文件名"); return@setOnClickListener }
                group.name = name; group.folder = folder; if (folder !in favoriteFolders) favoriteFolders.add(folder)
                selectedFavoriteGroup = group; saveAllFavorites(); render(); dialog.dismiss()
            }
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                AlertDialog.Builder(this).setTitle("删除收藏").setMessage("确定删除“${group.name}”吗？").setNegativeButton("取消", null).setPositiveButton("删除") { _, _ ->
                    favoriteGroups.removeAll { it.id == group.id }
                    // 删除收藏文件时始终保留其所属文件夹。
                    if (group.folder.isNotBlank() && group.folder !in favoriteFolders) favoriteFolders.add(group.folder)
                    selectedFavoriteGroup = null; saveAllFavorites(); page = "favorites"; render()
                }.create().also { showIos26Dialog(it) }
                dialog.dismiss()
            }
        }
        showIos26Dialog(dialog)
    }


internal fun MainActivity.showItemEditor(item: CodeItem) {
        val activity = this
        val value = inputField("条码内容", item.text)
        val formatsSpinner = Spinner(this).apply {
            adapter = formatSpinnerAdapter()
            setSelection(formats.indexOfFirst { it.first == item.format }.coerceAtLeast(0))
            setOnTouchListener { view, event ->
                if (event.actionMasked == MotionEvent.ACTION_UP) {
                    showMaterialDropdown(view, formats.map { it.first }, popupWidth = view.width, selectedIndex = selectedItemPosition) { index ->
                        setSelection(index)
                    }
                }
                true
            }
        }
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(24), dp(8), dp(24), 0); addView(value); addView(formatsSpinner, LinearLayout.LayoutParams(-1, dp(50)).apply { setMargins(0, dp(10), 0, 0) }) }
        val dialog = AlertDialog.Builder(this).setTitle("编辑条目").setView(box).setNegativeButton("取消", null).setNeutralButton("删除", null).setPositiveButton("保存", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val text = value.text.toString().trim(); if (text.isBlank()) { toast("请输入条码内容"); return@setOnClickListener }
                item.text = text; item.format = formats[formatsSpinner.selectedItemPosition].first; saveItems(); render(); dialog.dismiss()
            }
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                AlertDialog.Builder(this).setTitle("删除条目").setMessage("确定删除此条码吗？").setNegativeButton("取消", null).setPositiveButton("删除") { _, _ ->
                    items.removeAll { it.id == item.id }; favoriteGroups.forEach { it.itemIds.removeAll { id -> id == item.id } }; saveAllFavorites(); render()
                }.create().also { showIos26Dialog(it) }; dialog.dismiss()
            }
        }
        showIos26Dialog(dialog)
    }

