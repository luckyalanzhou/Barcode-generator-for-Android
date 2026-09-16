package com.luckyalanzhou.barcodegenerator

import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.view.View
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.FileProvider
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import java.util.Locale
import kotlin.math.roundToInt

internal fun MainActivity.tabPageIndex(): Int = when (page) {
    "history" -> 1
    "favorites", "favoriteDetail" -> 2
    "settings" -> 3
    "results" -> when (resultsReturnPage) {
        "history" -> 1
        "favorites" -> 2
        "settings" -> 3
        else -> 0
    }
    else -> 0
}

internal fun MainActivity.updateTopTabSelection() {
    // Tab 的可见状态由 ComposeBottomTabBar 绘制，选中索引与页面路由一起进入 ViewModel。
    viewModel.updateSelectedTab(tabPageIndex())
}

internal fun MainActivity.isDark() =
    style.colorScheme == "dark" ||
        (style.colorScheme == "system" &&
            (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES)

internal fun MainActivity.appBackground() = if (isDark()) 0xff000000.toInt() else 0xfff2f2f7.toInt()
internal fun MainActivity.primaryText() = if (isDark()) 0xfff2f4f7.toInt() else 0xff172033.toInt()
internal fun MainActivity.secondaryText() = if (isDark()) 0xffc5cedb.toInt() else 0xff667085.toInt()
internal fun MainActivity.nextItemId(): Long = (items.maxOfOrNull { it.id } ?: 0L) + 1L
internal fun MainActivity.nextGroupId(): Long = (favoriteGroups.maxOfOrNull { it.id } ?: 0L) + 1L

internal fun MainActivity.openSettings() {
    if (page == "settings") {
        updateTopTabSelection()
        return
    }
    // 设置是独立页面，但返回必须回到当前所属的主 Tab，不能回到结果页或详情页。
    settingsReturnPage = mainTabPageForCurrentPage()
    if (page == "lanShare") closeLanShare()
    page = "settings"
    render()
}

internal fun MainActivity.mainTabPageForCurrentPage(): String = when (page) {
    "history" -> "history"
    "favorites", "favoriteDetail" -> "favorites"
    "settings", "betaTestCenter" -> "settings"
    "results" -> when (resultsReturnPage) {
        "history" -> "history"
        "favorites" -> "favorites"
        "settings" -> "settings"
        else -> "generate"
    }
    // 局域网分享由设置工具进入，返回设置页；它本身不是底部 Tab 页面。
    "lanShare" -> "settings"
    else -> "generate"
}

internal fun MainActivity.applyAppearance() {
    val mode = when (style.colorScheme) {
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

internal fun MainActivity.loadStyle(): StyleSettings = StyleSettings(
    barColor = Color.BLACK,
    bgColor = Color.WHITE,
    showText = true,
    textPosition = "bottom",
    textSize = settingsStore.get(SettingsStore.TEXT_SIZE, 14f).coerceIn(10f, 24f),
    barHeight = settingsStore.get(SettingsStore.BAR_HEIGHT, 55).coerceIn(30, 150),
    barWidth = settingsStore.get(SettingsStore.BAR_WIDTH, 220f).coerceIn(120f, 360f),
    margin = settingsStore.get(SettingsStore.MARGIN, 4).coerceIn(0, 40),
    showFormat = settingsStore.get(SettingsStore.SHOW_FORMAT, false),
    colorScheme = settingsStore.get(SettingsStore.COLOR_SCHEME, "system"),
)

internal fun MainActivity.saveStyle() = settingsStore.saveStyle(style)

internal fun MainActivity.saveInputDraft() {
    // Compose 输入行直接写入 inputDraft；这里保留统一业务入口供生成流程调用。
}

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

internal fun MainActivity.encode(text: String, format: BarcodeFormat): Bitmap? = try {
    val code128 = format == BarcodeFormat.CODE_128
    val width = if (code128) dp(style.barWidth.roundToInt().coerceIn(120, 360)).coerceAtLeast(1) else 500
    val barcodeHeight = if (code128) dp(style.barHeight.coerceIn(30, 150).coerceAtLeast(1))
    else if (format == BarcodeFormat.QR_CODE) 500 else 200
    val matrix = MultiFormatWriter().encode(text, format, width, barcodeHeight, mapOf(EncodeHintType.MARGIN to 0))
    val paint = Paint().apply { color = if (this@encode.isDark()) Color.BLACK else this@encode.style.barColor }
    val bitmap = Bitmap.createBitmap(width, barcodeHeight, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    if (isDark()) canvas.drawColor(Color.WHITE) else canvas.drawColor(style.bgColor)
    for (x in 0 until matrix.width) {
        for (y in 0 until matrix.height) {
            if (matrix[x, y]) canvas.drawRect(x.toFloat(), y.toFloat(), (x + 1).toFloat(), (y + 1).toFloat(), paint)
        }
    }
    if (code128) addBarcodeQuietZone(bitmap) else bitmap
} catch (_: Exception) {
    null
}

internal fun trimBarcodeHorizontal(source: Bitmap): Bitmap {
    var left = source.width
    var right = -1
    for (x in 0 until source.width) {
        var hasBar = false
        for (y in 0 until source.height) {
            val pixel = source.getPixel(x, y)
            val luminance = (Color.red(pixel) * 299 + Color.green(pixel) * 587 + Color.blue(pixel) * 114) / 1000
            if (Color.alpha(pixel) > 0 && luminance < 200) {
                hasBar = true
                break
            }
        }
        if (hasBar) {
            left = minOf(left, x)
            right = maxOf(right, x)
        }
    }
    return if (right >= left) Bitmap.createBitmap(source, left, 0, right - left + 1, source.height) else source
}

internal fun addBarcodeQuietZone(source: Bitmap): Bitmap {
    val quiet = maxOf(8, source.height / 4)
    val result = Bitmap.createBitmap(source.width + quiet * 2, source.height, Bitmap.Config.ARGB_8888)
    Canvas(result).apply {
        drawColor(Color.WHITE)
        drawBitmap(source, quiet.toFloat(), 0f, Paint())
    }
    return result
}

internal fun MainActivity.dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()
internal fun MainActivity.toast(s: String) = android.widget.Toast.makeText(this, s, android.widget.Toast.LENGTH_SHORT).show()

/** render() 只更新 Compose 状态，不再重建任何旧 View 页面。 */
internal fun MainActivity.render() {
    if (isRenderingUi) return
    isRenderingUi = true
    try {
        syncBarcodeDisplaySettings(page == "results")
        updateTopTabSelection()
        // 标题和 Chrome 可见性由 AppRoute 派生，避免 render() 再维护第二套判断。
        viewModel.updateAppUi(page)
        viewModel.publishDataState()
        lanShareViewModel.sync(lanShareSession, lanShareIsHost, lanShareQrVisible, lanShareBrowserConnected, lanShareFiles, lanShareOwnFileIds.toSet(), lanSharePreviewFiles.toMap())
        if (page == "lanShare" && lanShareSession == null) window.decorView.post { enterLanShare() }
        if (page == "favoriteDetail" && selectedFavoriteGroup == null) page = "favorites"
        composeShellRevision.intValue++
    } finally {
        isRenderingUi = false
    }
}

internal fun MainActivity.showAppChrome(visible: Boolean) {
    // 页面 Chrome 由 AppRoute 决定；保留入口供旧调用方编译通过。
    if (visible != viewModel.uiState.value.chromeVisible) composeShellRevision.intValue++
}

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
    composeFireworksVisible.value = true
    window.statusBarColor = Color.BLACK
    window.navigationBarColor = Color.BLACK
    val lightSystemBars = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
    window.decorView.systemUiVisibility = window.decorView.systemUiVisibility and lightSystemBars.inv()
    composeShellRevision.intValue++
}

internal fun MainActivity.dismissFireworksEasterEgg() {
    if (!composeFireworksVisible.value) return
    composeFireworksVisible.value = false
    syncSystemBars()
    composeShellRevision.intValue++
}
