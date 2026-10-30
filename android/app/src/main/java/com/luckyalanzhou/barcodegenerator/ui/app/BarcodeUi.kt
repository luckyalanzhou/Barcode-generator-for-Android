package com.luckyalanzhou.barcodegenerator.ui.app

import com.luckyalanzhou.barcodegenerator.ui.theme.*
import com.luckyalanzhou.barcodegenerator.ui.dialogs.*
import com.luckyalanzhou.barcodegenerator.ui.app.platform.showIos26NoticeDialogCompose
import com.luckyalanzhou.barcodegenerator.ui.app.platform.runDocumentProviderCleanupResult
import com.luckyalanzhou.barcodegenerator.ui.app.platform.ensureShareableCacheDirectory
import com.luckyalanzhou.barcodegenerator.ui.app.platform.SHAREABLE_IMAGES_DIRECTORY

import com.luckyalanzhou.barcodegenerator.MainActivity
import com.luckyalanzhou.barcodegenerator.domain.CodeItem
import com.luckyalanzhou.barcodegenerator.ui.support.logging.DebugLog

import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.FileProvider
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.ui.graphics.toArgb
import kotlin.math.roundToInt
import android.content.ClipData
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import java.io.File

internal fun MainActivity.isDark() =
    resolveDarkAppearance(
        settingsViewModel.style.colorScheme,
        (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES,
    )

internal fun MainActivity.appBackground() = appColorScheme(isDark()).surfaces.background.toArgb()
internal fun MainActivity.primaryText() = appColorScheme(isDark()).text.primary.toArgb()
internal fun MainActivity.secondaryText() = appColorScheme(isDark()).text.secondary.toArgb()
internal fun MainActivity.applyAppearance() {
    // ComposeAppShell 根据 SettingsUiState 实时选择浅色/深色主题；
    // 这里只同步系统栏，避免 AppCompatDelegate 重建 Activity 造成画面闪烁。
    syncSystemBars()
}

/** 在主题重建完成后同步系统栏，避免沿用旧颜色。 */
internal fun MainActivity.syncSystemBars() {
    val background = appBackground()
    WindowInsetsControllerCompat(window, window.decorView).apply {
        isAppearanceLightStatusBars = !isDark()
        isAppearanceLightNavigationBars = !isDark()
    }
    // MainActivity 使用 edge-to-edge；在较旧的 Android 版本中，窗口背景会作为 Compose 根布局后方的回退底色，覆盖两个系统栏区域。
    window.decorView.setBackgroundColor(background)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) window.isNavigationBarContrastEnforced = false
}

internal fun MainActivity.saveStyle() = settingsViewModel.save()

internal fun MainActivity.preview(item: CodeItem) {
    previewCompose(item)
}

internal fun MainActivity.shareText(text: String) {
    runCatching {
        startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, text)
                },
                "分享条码内容",
            ),
        )
    }.onSuccess { DebugLog.record("image_export", "text_share_chooser_opened") }
        .onFailure {
            DebugLog.record("image_export", "text_share_failed", it)
            toast("分享失败，请重试")
        }
}

internal fun MainActivity.saveBitmap(bitmap: Bitmap, label: String, onFinished: () -> Unit = {}) {
    lifecycleScope.launch {
        val operation = System.nanoTime()
        DebugLog.record("image_export", "gallery_save_started operation=$operation")
        var createdUri: Uri? = null
        try {
            withContext(Dispatchers.IO) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, shareImageFileName(label))
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    if (Build.VERSION.SDK_INT >= 29) {
                        put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/BarcodeGenerator")
                        put(MediaStore.Images.Media.IS_PENDING, 1)
                    }
                }
                val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    ?: error("无法创建图片")
                createdUri = uri
                contentResolver.openOutputStream(uri)?.use {
                    check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
                } ?: error("无法写入图片")
                if (Build.VERSION.SDK_INT >= 29) {
                    check(contentResolver.update(uri,
                        ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null) > 0)
                }
            }
            toast("已保存到相册")
            DebugLog.record("image_export", "gallery_save_succeeded operation=$operation")
        } catch (error: CancellationException) {
            DebugLog.record("image_export", "gallery_save_cancelled operation=$operation")
            createdUri?.let { rollbackGalleryImage(it, operation) }
            throw error
        } catch (error: Exception) {
            DebugLog.record("image_export", "gallery_save_failed operation=$operation", error)
            createdUri?.let { rollbackGalleryImage(it, operation) }
            toast("保存失败，请重试")
        } finally {
            onFinished()
        }
    }
}

private suspend fun MainActivity.rollbackGalleryImage(uri: Uri, operation: Long) {
    val cleanup = runDocumentProviderCleanupResult { contentResolver.delete(uri, null, null) }
    if ((cleanup.getOrNull() ?: 0) <= 0) {
        val failureType = cleanup.exceptionOrNull()?.javaClass?.simpleName ?: "no_document_deleted"
        DebugLog.record("image_export", "gallery_save_rollback_failed operation=$operation type=$failureType")
    }
}

internal fun MainActivity.writeBitmapToUri(bitmap: Bitmap, uri: Uri): Boolean = runCatching {
    contentResolver.openOutputStream(uri)?.use { output ->
        check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) { "图片写入失败" }
    } ?: error("无法打开文件")
}.onFailure { DebugLog.record("image_export", "document_write_failed", it) }
    .onSuccess { DebugLog.record("image_export", "document_write_succeeded") }.isSuccess

internal fun MainActivity.shareBitmap(bitmap: Bitmap, label: String, onStarted: () -> Unit = {}, onFinished: () -> Unit = {}) {
    // 分享不等于保存；不要改动用户原有的相册内容。
    lifecycleScope.launch {
        val operation = System.nanoTime()
        DebugLog.record("image_export", "image_share_started operation=$operation")
        onStarted()
        var shareFile: File? = null
        try {
            val uri = withContext(Dispatchers.IO) {
                val directory = ensureShareableCacheDirectory(cacheDir, SHAREABLE_IMAGES_DIRECTORY)
                val file = File.createTempFile(shareImageFileName(label).removeSuffix(".png") + "-", ".png", directory)
                shareFile = file
                file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
                FileProvider.getUriForFile(this@shareBitmap, "$packageName.fileprovider", file)
            }
            startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TITLE, label)
                clipData = ClipData.newUri(contentResolver, label, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }, "分享条码图片"))
            // 此处不要删除文件：系统分享面板关闭后，接收应用仍可能继续读取它。
            DebugLog.record("image_export", "image_share_chooser_opened operation=$operation")
        } catch (error: CancellationException) {
            DebugLog.record("image_export", "image_share_cancelled operation=$operation")
            shareFile?.delete()
            throw error
        } catch (error: Exception) {
            DebugLog.record("image_export", "image_share_failed operation=$operation", error)
            shareFile?.delete()
            toast("分享失败，请重试")
        } finally {
            onFinished()
        }
    }
}

internal fun MainActivity.toast(s: String) = android.widget.Toast.makeText(this, s, android.widget.Toast.LENGTH_SHORT).show()
internal fun MainActivity.dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

internal fun MainActivity.showIos26NoticeDialog(message: String) {
    showIos26NoticeDialogCompose(message)
}
