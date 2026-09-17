package com.luckyalanzhou.barcodegenerator

import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.view.View
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.FileProvider
import kotlin.math.roundToInt

internal fun MainActivity.isDark() =
    settingsViewModel.style.colorScheme == "dark" ||
        (settingsViewModel.style.colorScheme == "system" &&
            (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES)

internal fun MainActivity.appBackground() = if (isDark()) 0xff000000.toInt() else 0xfff2f2f7.toInt()
internal fun MainActivity.primaryText() = if (isDark()) 0xfff2f4f7.toInt() else 0xff172033.toInt()
internal fun MainActivity.secondaryText() = if (isDark()) 0xffc5cedb.toInt() else 0xff667085.toInt()
internal fun MainActivity.applyAppearance() {
    val mode = when (settingsViewModel.style.colorScheme) {
        "dark" -> AppCompatDelegate.MODE_NIGHT_YES
        "light" -> AppCompatDelegate.MODE_NIGHT_NO
        else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
    }
    AppCompatDelegate.setDefaultNightMode(mode)
    syncSystemBars()
}

/** 在主题重建完成后同步系统栏，避免沿用旧颜色。 */
internal fun MainActivity.syncSystemBars() {
    val background = appBackground()
    window.decorView.systemUiVisibility =
        if (isDark()) 0 else View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
    window.statusBarColor = background
    window.navigationBarColor = background
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
    window.statusBarColor = Color.BLACK
    window.navigationBarColor = Color.BLACK
    val lightSystemBars = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
    window.decorView.systemUiVisibility = window.decorView.systemUiVisibility and lightSystemBars.inv()
}

internal fun MainActivity.dismissFireworksEasterEgg() {
    if (!viewModel.fireworksVisible.value) return
    viewModel.dismissFireworks()
    syncSystemBars()
}
