package com.luckyalanzhou.barcodegenerator.ui

import com.luckyalanzhou.barcodegenerator.*
import com.luckyalanzhou.barcodegenerator.domain.CodeItem

import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.FileProvider
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.ui.graphics.toArgb
import kotlin.math.roundToInt

internal fun MainActivity.isDark() =
    settingsViewModel.style.colorScheme == "dark" ||
        (settingsViewModel.style.colorScheme == "system" &&
            (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES)

internal fun MainActivity.appBackground() = barcodeThemeColors(isDark()).background.toArgb()
internal fun MainActivity.primaryText() = barcodeThemeColors(isDark()).primary.toArgb()
internal fun MainActivity.secondaryText() = barcodeThemeColors(isDark()).secondary.toArgb()
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
    // MainActivity uses edge-to-edge; the decor background is the fallback behind
    // the Compose root for both system-bar regions on older Android versions.
    window.decorView.setBackgroundColor(background)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) window.isNavigationBarContrastEnforced = false
}

internal fun MainActivity.saveStyle() = settingsViewModel.save()

internal fun MainActivity.preview(item: CodeItem) {
    previewCompose(item)
}

internal fun MainActivity.shareText(text: String) {
    startActivity(
        Intent.createChooser(
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
            },
            "分享条码内容",
        ),
    )
}

internal fun MainActivity.saveBitmap(bitmap: Bitmap, label: String) {
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, label.replace(Regex("[^A-Za-z0-9._-]+"), "_").take(80).ifBlank { "barcode" } + ".png")
        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        if (Build.VERSION.SDK_INT >= 29) put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/BarcodeGenerator")
    }
    val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
    if (uri == null) {
        toast("保存失败")
        return
    }
    contentResolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    toast("已保存到相册")
}

internal fun MainActivity.shareBitmap(bitmap: Bitmap, label: String) {
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, label.replace(Regex("[^A-Za-z0-9._-]+"), "_").take(80).ifBlank { "barcode" } + ".png")
        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        if (Build.VERSION.SDK_INT >= 29) put(MediaStore.Images.Media.IS_PENDING, 1)
    }
    val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
    if (uri == null) {
        toast("分享失败")
        return
    }
    contentResolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    if (Build.VERSION.SDK_INT >= 29) {
        contentResolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
    }
    startActivity(
        Intent.createChooser(
            Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, label)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            },
            "分享条码图片",
        ),
    )
}

internal fun MainActivity.toast(s: String) = android.widget.Toast.makeText(this, s, android.widget.Toast.LENGTH_SHORT).show()
internal fun MainActivity.dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

internal fun MainActivity.showIos26NoticeDialog(message: String, showMetrics: Boolean = false) {
    showIos26NoticeDialogCompose(message, showMetrics)
}

internal fun MainActivity.showSimulatedDialog(
    title: String,
    message: String,
    negative: String?,
    neutral: String?,
    positive: String?,
    showMetrics: Boolean = true,
) {
    showSimulatedDialogCompose(title, message, negative, neutral, positive, showMetrics)
}

/** 与业务完全分离的 Compose Canvas 烟花彩蛋；不写入设置或条码数据。 */
internal fun MainActivity.showFireworksEasterEgg() {
    viewModel.showFireworks()
    window.decorView.setBackgroundColor(Color.BLACK)
    WindowInsetsControllerCompat(window, window.decorView).apply {
        isAppearanceLightStatusBars = false
        isAppearanceLightNavigationBars = false
    }
}

internal fun MainActivity.dismissFireworksEasterEgg() {
    if (!viewModel.fireworksVisible.value) return
    viewModel.dismissFireworks()
    syncSystemBars()
}
