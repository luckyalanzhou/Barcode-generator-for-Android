package com.luckyalanzhou.barcodegenerator

import android.Manifest
import android.app.AlertDialog
import android.app.Dialog
import android.content.*
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.*
import android.media.ExifInterface
import android.net.Uri
import android.os.Bundle
import android.text.*
import android.view.*
import android.view.animation.OvershootInterpolator
import android.widget.*
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.RippleDrawable
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.SwitchCompat
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import android.widget.PopupWindow
import androidx.lifecycle.lifecycleScope
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
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

internal fun MainActivity.enterLanShare() {
    if (!lanShareManager.isOnLocalNetwork()) {
        showLanShareNetworkErrorDialog()
        return
    }
    settingsReturnPage = "settings"
    page = "lanShare"
    runCatching {
        lanShareSession = lanShareManager.start()
        lanShareIsHost = true
        lanShareQrVisible = true
        lanShareBrowserConnected = false
        lanShareOwnFileIds.clear()
        lanShareFiles = lanShareManager.localFiles()
        startLanShareAutoRefresh()
        render()
        content.post { if (page == "lanShare" && lanShareQrVisible) showLanShareQrDialog() }
    }.onFailure {
        stopLanShareAutoRefresh()
        lanShareSession = null
        lanShareIsHost = false
        page = "settings"
        render()
        if (it.message == "Error 当前不处于局域网") showLanShareNetworkErrorDialog() else toast(it.message ?: "无法创建房间")
    }
}

/** 局域网不可用时使用独立的紧凑玻璃提示，避免被普通 Toast 忽略。 */
internal fun MainActivity.showLanShareNetworkErrorDialog(showMetrics: Boolean = false) {
    val dialog = AlertDialog.Builder(this).create()
    val box = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        setPadding(dp(20), dp(16), dp(20), dp(8))
        addView(TextView(this@showLanShareNetworkErrorDialog).apply {
            text = "Error: 当前不处于局域网"
            textSize = 16f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            gravity = Gravity.CENTER
            includeFontPadding = false
            setTextColor(primaryText())
            setPadding(0, dp(2), 0, dp(2))
        }, LinearLayout.LayoutParams(-1, dp(44)))
        addView(styleButton(Button(this@showLanShareNetworkErrorDialog).apply {
            text = "确定"
            textSize = 15f
            minWidth = 0
            minimumWidth = 0
            minHeight = dp(40)
            minimumHeight = dp(40)
            setPadding(dp(10), dp(6), dp(10), dp(6))
            setTextColor(primaryText())
            background = glassButtonBackground().apply { cornerRadius = dp(14).toFloat() }
            setOnClickListener { dialog.dismiss() }
        }), LinearLayout.LayoutParams(dp(76), dp(40)).apply { gravity = Gravity.END; topMargin = dp(8) })
    }
    dialog.setView(box)
    showIos26Dialog(dialog, compact = true)
    // 必须在真实弹窗完成 show 并确定最终尺寸后定位数据面板，避免数据面板覆盖弹窗。
    val metricsPopup = if (showMetrics) showSimulationMetrics(dialog.window?.decorView ?: box, "局域网错误弹窗") else null
    dialog.setOnDismissListener { metricsPopup?.dismiss() }
}

/** 用于短提示的紧凑居中 Liquid Glass 弹窗。 */

internal fun MainActivity.showLanShare() {
    val messageDraft = lanShareMessageInput?.text?.toString().orEmpty()
    val messageHadFocus = lanShareMessageInput?.hasFocus() == true
    content.removeAllViews()
    content.setPadding(0, 0, 0, dp(24))
    // 文件传输页跟随应用深浅色，保持原生传输面板的简洁层次。
    val shareBackground = if (isDark()) Color.BLACK else 0xfff4f6fb.toInt()
    val shareTitle = if (isDark()) Color.WHITE else primaryText()
    content.setBackgroundColor(shareBackground); rootLayout.setBackgroundColor(shareBackground)
    val toggleQr: () -> Unit = {
        if (!lanShareQrVisible && lanShareIsHost) {
            runCatching { lanShareSession = lanShareManager.restart(); lanShareBrowserConnected = false; lanShareQrVisible = true; lanShareFiles = lanShareManager.localFiles(); showLanShareQrDialog() }
                .onFailure { toast(it.message ?: "无法刷新分享端口") }
        } else if (lanShareQrVisible) {
            lanShareQrVisible = false
            render()
        }
    }
    content.addView(LinearLayout(this).apply {
        isClickable = true; isFocusable = true; setOnClickListener { toggleQr() }
        gravity = Gravity.CENTER_VERTICAL; setPadding(dp(8), dp(8), dp(8), dp(8)); background = liquidGlassCard(); elevation = 0f; clipToOutline = true
        addView(Space(this@showLanShare), LinearLayout.LayoutParams(dp(64), dp(64)))
        addView(TextView(this@showLanShare).apply { text = "文件传输"; textSize = 20f; gravity = Gravity.CENTER; setTypeface(null, Typeface.BOLD); setTextColor(shareTitle) }, LinearLayout.LayoutParams(0, dp(64), 1f))
        addView(ImageButton(this@showLanShare).apply {
            setImageResource(R.drawable.ic_qr_code)
            imageTintList = ColorStateList.valueOf(if (isDark()) 0xff8fc1ff.toInt() else 0xff0a84ff.toInt())
            // 保留 60dp 点击区域，收紧可见外框和图标比例，二维码图形更清晰。
            background = glassButtonBackground().apply { cornerRadius = dp(16).toFloat() }
            elevation = 0f; clipToOutline = true
            isClickable = true; isFocusable = true; contentDescription = "显示二维码"
            // 60dp 的玻璃外框避免深色模式下被标题卡片边缘和阴影裁切；标题整块仍可点击。
            minimumWidth = dp(60); minimumHeight = dp(60)
            setPadding(dp(15), dp(15), dp(15), dp(15))
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setOnClickListener { toggleQr() }
        }, LinearLayout.LayoutParams(dp(60), dp(60)))
    }, LinearLayout.LayoutParams(-1, dp(80)))
    val sessionState = lanShareSession
    content.addView(TextView(this).apply { tag = "lanShareStatus"; textSize = 15f; gravity = Gravity.CENTER; setPadding(0, dp(12), 0, dp(14)); updateLanShareConnectionStatus(this) })
    if (lanShareSession == null) {
        content.post { enterLanShare() }
        return
    }
    val session = sessionState ?: return
    // 二维码通过独立弹窗展示；消息列表可独立刷新，避免重建输入框与键盘。
    content.addView(LinearLayout(this).apply { tag = "lanShareFileList"; orientation = LinearLayout.VERTICAL; renderLanShareFileList(this) }, LinearLayout.LayoutParams(-1, -2))
    val actions = lanShareComposer ?: return
    actions.removeAllViews()
    actions.visibility = View.VISIBLE
    actions.apply {
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(8), dp(8), dp(8), dp(8))
        background = liquidGlassCard()
        elevation = 0f
        clipToOutline = true
        addView(ImageButton(this@showLanShare).apply {
            setImageResource(R.drawable.ic_attachment)
            setColorFilter(if (isDark()) Color.WHITE else 0xff344054.toInt())
            setBackgroundColor(Color.TRANSPARENT)
            contentDescription = "选择附件"
            setOnClickListener { showLanShareAttachmentSheet(this) }
        }, LinearLayout.LayoutParams(dp(48), dp(48)))
        lanShareMessageInput = EditText(this@showLanShare).apply {
            hint = pendingLanUploadName?.let { "已选择：$it" } ?: "输入文字"
            textSize = 15f
            setSingleLine(true)
            setPadding(dp(14), 0, dp(14), 0)
            setTextColor(primaryText())
            setHintTextColor(secondaryText())
            setText(messageDraft)
            setSelection(text.length)
            background = GradientDrawable().apply { shape = GradientDrawable.RECTANGLE; cornerRadius = dp(24).toFloat(); setColor(if (isDark()) 0xff2c2c2e.toInt() else 0xfff0f2f5.toInt()) }
        }.also { addView(it, LinearLayout.LayoutParams(0, dp(44), 1f).apply { setMargins(0, 0, dp(8), 0) }) }
        addView(ImageButton(this@showLanShare).apply {
            setImageResource(R.drawable.ic_action_share)
            setColorFilter(Color.WHITE)
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(0xff0a84ff.toInt()) }
            contentDescription = "发送文字或上传附件"
            setOnClickListener {
                if (pendingLanUploadUri != null) uploadSelectedLanShareFile()
                else {
                    val text = lanShareMessageInput?.text?.toString()?.trim().orEmpty()
                    if (text.isNotEmpty()) uploadLanShareMessage(text)
                }
            }
        }, LinearLayout.LayoutParams(dp(48), dp(48)))
    }
    if (messageHadFocus) lanShareMessageInput?.post {
        lanShareMessageInput?.requestFocus()
        lanShareMessageInput?.setSelection(lanShareMessageInput?.text?.length ?: 0)
    }
}

private fun MainActivity.renderLanShareFileList(list: LinearLayout) {
    val expectedIds = lanShareFiles.map { it.id }
    val currentIds = (0 until list.childCount).mapNotNull { list.getChildAt(it).tag as? String }
    if (currentIds == expectedIds) return
    list.removeAllViews()
    lanShareFiles.forEach { file ->
        val mine = file.id in lanShareOwnFileIds
        val imageFile = (lanShareManager.localFile(file.id) ?: lanSharePreviewFiles[file.id])?.takeIf { isLanShareImageName(file.name) }
        list.addView(LinearLayout(this).apply { tag = file.id
            gravity = if (mine) Gravity.END else Gravity.START; setPadding(0, dp(4), 0, dp(4))
            val bubble = LinearLayout(this@renderLanShareFileList).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(dp(if (imageFile == null) 12 else 6), dp(if (imageFile == null) 8 else 6), dp(if (imageFile == null) 10 else 6), dp(if (imageFile == null) 8 else 6)); background = liquidGlassCard().apply { setColor(if (mine) (if (isDark()) 0x7a0a84ff else 0x660a84ff) else if (isDark()) 0x662c2c2e else 0xcfffffff.toInt()) }; elevation = 0f; clipToOutline = true
                if (imageFile == null) addView(ImageView(this@renderLanShareFileList).apply { setImageResource(R.drawable.ic_attachment); setColorFilter(if (mine) Color.WHITE else if (isDark()) 0xffd0d6e4.toInt() else 0xff52627a.toInt()); contentDescription = "文件附件" }, LinearLayout.LayoutParams(dp(26), dp(26)).apply { rightMargin = dp(10) })
                val details = LinearLayout(this@renderLanShareFileList).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; setPadding(0, 0, dp(6), 0) }
                imageFile?.let { source -> decodeLanSharePreview(source)?.let { bitmap ->
                    val scale = minOf(dp(220).toFloat() / bitmap.width.coerceAtLeast(1), dp(180).toFloat() / bitmap.height.coerceAtLeast(1), 1f)
                    details.addView(ImageView(this@renderLanShareFileList).apply { setImageBitmap(bitmap); scaleType = ImageView.ScaleType.CENTER_CROP; setBackgroundColor(0x22000000); contentDescription = file.name }, LinearLayout.LayoutParams((bitmap.width * scale).roundToInt().coerceAtLeast(dp(80)), (bitmap.height * scale).roundToInt().coerceAtLeast(dp(80))).apply { bottomMargin = dp(6) })
                } }
                details.addView(TextView(this@renderLanShareFileList).apply { text = file.name; textSize = 14f; maxLines = 4; maxWidth = dp(220); ellipsize = null; setHorizontallyScrolling(false); gravity = Gravity.CENTER_HORIZONTAL; setTextColor(if (mine) Color.WHITE else primaryText()) })
                details.addView(TextView(this@renderLanShareFileList).apply { text = formatLanShareSize(file.size); textSize = 12f; gravity = Gravity.CENTER_HORIZONTAL; setTextColor(if (mine) 0xffdbeafe.toInt() else secondaryText()) })
                addView(details, LinearLayout.LayoutParams(-2, -2))
            }
            bubble.setOnClickListener { saveLanShareFile(file) }
            addView(bubble, LinearLayout.LayoutParams(-2, -2))
        }, LinearLayout.LayoutParams(-1, -2))
    }
}

private fun MainActivity.updateLanShareConnectionStatus(view: TextView? = content.findViewWithTag("lanShareStatus")) {
    val connected = lanShareSession != null && lanShareManager.browserConnected()
    lanShareBrowserConnected = connected
    view?.apply {
        text = if (connected) "●  浏览器已连接" else "○  等待浏览器连接..."
        setTextColor(if (connected) 0xff22c55e.toInt() else secondaryText())
    }
}

internal fun MainActivity.showLanShareAttachmentSheet(anchor: View) {
    showLanSharePopup(anchor, listOf(
        "拍摄图片" to { openLanShareCamera() },
        "照片图库" to { openLanShareGallery() },
        "选择文件" to { openLanShareFiles() }
    ))
}

internal fun MainActivity.openLanShareCamera() {
    pendingCameraRequest = MainActivity.REQUEST_LAN_SHARE_CAPTURE
    if (android.os.Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
        requestPermissions(arrayOf(Manifest.permission.CAMERA), MainActivity.REQUEST_CAMERA_PERMISSION)
        return
    }
    val photoFile = File.createTempFile("lan_share_photo_", ".jpg", cacheDir)
    val photoUri = FileProvider.getUriForFile(this, "$packageName.fileprovider", photoFile)
    pendingCameraUri = photoUri
    pendingCameraFile = photoFile
    val intent = Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE).apply {
        putExtra(android.provider.MediaStore.EXTRA_OUTPUT, photoUri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        clipData = android.content.ClipData.newRawUri("output", photoUri)
    }
    try { startActivityForResult(intent, MainActivity.REQUEST_LAN_SHARE_CAPTURE) } catch (_: Exception) { pendingCameraUri = null; pendingCameraFile = null; photoFile.delete(); toast("当前设备没有可用的系统相机") }
}

internal fun MainActivity.findRecentLanCameraMedia(): Uri? {
    val threshold = (pendingLanCameraStartedAt - 2_000L).coerceAtLeast(0L) / 1_000L
    val collection = android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    return contentResolver.query(collection, arrayOf(android.provider.MediaStore.MediaColumns._ID, android.provider.MediaStore.MediaColumns.DATE_ADDED), null, null, "${android.provider.MediaStore.MediaColumns.DATE_ADDED} DESC")?.use { cursor ->
        if (cursor.moveToFirst() && cursor.getLong(1) >= threshold) android.content.ContentUris.withAppendedId(collection, cursor.getLong(0)) else null
    }
}

internal fun MainActivity.openLanShareGallery() {
    if (android.os.Build.VERSION.SDK_INT >= 33) {
        openLanShareGalleryPicker()
        return
    }
    val permission = Manifest.permission.READ_EXTERNAL_STORAGE
    if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
        requestPermissions(arrayOf(permission), MainActivity.REQUEST_LAN_SHARE_GALLERY_PERMISSION)
    } else openLanShareGalleryPicker()
}

internal fun MainActivity.openLanShareGalleryPicker() {
    val intent = if (android.os.Build.VERSION.SDK_INT >= 33) {
        Intent(android.provider.MediaStore.ACTION_PICK_IMAGES)
    } else {
        Intent(Intent.ACTION_PICK).setDataAndType(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "image/*")
    }
    startActivityForResult(intent.apply { addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }, MainActivity.REQUEST_LAN_SHARE_UPLOAD)
}

internal fun MainActivity.openLanShareFiles() {
    if (android.os.Build.VERSION.SDK_INT <= 32 && checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
        requestPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), MainActivity.REQUEST_LAN_SHARE_FILE_PERMISSION)
    } else openLanShareFilePicker()
}

internal fun MainActivity.openLanShareFilePicker() {
    startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply { type = "*/*"; addCategory(Intent.CATEGORY_OPENABLE); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION) }, MainActivity.REQUEST_LAN_SHARE_UPLOAD)
}

internal fun MainActivity.selectLanShareAttachment(uri: Uri, temporaryFile: File? = null, autoUpload: Boolean = false) {
    pendingLanUploadTempFile?.takeIf { it != temporaryFile }?.delete()
    pendingLanUploadUri = uri
    pendingLanUploadTempFile = temporaryFile
    pendingLanUploadName = runCatching { contentResolver.query(uri, null, null, null, null)?.use { cursor -> cursor.moveToFirst(); cursor.getString(cursor.getColumnIndexOrThrow(android.provider.OpenableColumns.DISPLAY_NAME)) } }.getOrNull() ?: "附件"
    runCatching { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
    if (autoUpload) uploadSelectedLanShareFile() else { render(); toast("已选择附件，点击上传按钮发送") }
}

internal fun MainActivity.uploadSelectedLanShareFile() {
    val uri = pendingLanUploadUri ?: return
    val temporaryFile = pendingLanUploadTempFile
    pendingLanUploadUri = null
    pendingLanUploadTempFile = null
    pendingLanUploadName = null
    uploadLanShareFile(uri, temporaryFile)
}

internal fun MainActivity.showLanSharePopup(anchor: View, options: List<Pair<String, () -> Unit>>, showMetrics: Boolean = false) {
    lateinit var popup: PopupWindow
    val popupWidth = dp(128)
    val panel = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(7), dp(7), dp(7), dp(7))
        background = liquidGlassCard()
         elevation = dp(6).toFloat()
        options.forEachIndexed { index, (label, action) ->
            addView(TextView(this@showLanSharePopup).apply { text = label; textSize = 15f; gravity = Gravity.CENTER; setTextColor(primaryText()); setBackgroundColor(Color.TRANSPARENT); isClickable = true; setOnClickListener { action(); popup.dismiss() } }, LinearLayout.LayoutParams(dp(114), dp(36)).apply { setMargins(0, dp(1), 0, dp(1)) })
            // 只在选项之间绘制分割线，最后一项下方不绘制；每个选项本身保持相同宽度。
            if (index < options.lastIndex) addView(View(this@showLanSharePopup).apply { setBackgroundColor(if (isDark()) 0x33ffffff else 0x33475b7a) }, LinearLayout.LayoutParams(dp(102), dp(1)).apply { setMargins(dp(6), 0, dp(6), 0) })
        }
    }
    popup = PopupWindow(panel, popupWidth, WindowManager.LayoutParams.WRAP_CONTENT, true).apply {
        setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        isOutsideTouchable = true
         elevation = dp(6).toFloat()
    }
    panel.measure(View.MeasureSpec.makeMeasureSpec(popupWidth, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
    val location = IntArray(2); anchor.getLocationOnScreen(location)
    popup.showAtLocation(anchor, Gravity.TOP or Gravity.START, (location[0] - dp(8)).coerceAtLeast(dp(4)), (location[1] - panel.measuredHeight - dp(16)).coerceAtLeast(dp(8)))
    if (showMetrics) {
        val metricsPopup = showSimulationMetrics(panel, "附件选项")
        popup.setOnDismissListener { metricsPopup.dismiss() }
    }
}

internal fun MainActivity.liquidGlassCard() = GradientDrawable().apply {
    shape = GradientDrawable.RECTANGLE
    cornerRadius = dp(18).toFloat()
    setColor(if (isDark()) 0xff1c1c1e.toInt() else 0xffffffff.toInt())
    setStroke(dp(1), if (isDark()) 0xff3a3a3c.toInt() else 0xffd8d8dc.toInt())
}

private fun formatLanShareSize(bytes: Long): String = if (bytes >= 1024L * 1024L) {
    String.format(Locale.getDefault(), "%.1f MB", bytes / 1024.0 / 1024.0)
} else {
    "${bytes / 1024} KB"
}

internal fun MainActivity.joinLanShareSession(value: String) {
    if (!value.startsWith("http://")) { toast("这不是局域网分享地址"); return }
    val uri = Uri.parse(value)
    if (uri.host.isNullOrBlank() || uri.query != null || !lanShareManager.isRouterLanHost(uri.host)) { toast("这不是局域网分享地址"); return }
    stopLanShareAutoRefresh(); lanShareManager.stop(); lanShareIsHost = false
    lanShareSession = LanShareSession("${uri.scheme}://${uri.host}:${if (uri.port > 0) uri.port else 80}")
    startLanShareAutoRefresh()
    refreshLanShareFiles()
}

internal fun MainActivity.showLanShareQrDialog(simulatedSession: LanShareSession? = null) {
    val simulated = simulatedSession != null
    val session = simulatedSession ?: lanShareSession
    if ((!lanShareIsHost && !simulated) || session == null) { toast("请先创建分享房间"); return }
    // 缩小二维码内部默认静区，保留可可靠识别所需的最小留白，避免白色方块过大。
    val matrix = MultiFormatWriter().encode(session.baseUrl, BarcodeFormat.QR_CODE, dp(240), dp(240), mapOf(EncodeHintType.MARGIN to 1))
    val qrForeground = if (isDark()) 0xff111318.toInt() else Color.BLACK
    val qrBackground = if (isDark()) 0xfff1f3f6.toInt() else Color.WHITE
    val bitmap = Bitmap.createBitmap(matrix.width, matrix.height, Bitmap.Config.ARGB_8888).also { image -> for (x in 0 until matrix.width) for (y in 0 until matrix.height) image.setPixel(x, y, if (matrix[x, y]) qrForeground else qrBackground) }
    val box = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        // 二维码弹窗只保留必要的安全留白，减少左右白边；弹窗本身仍保留圆角和系统最小宽度。
        gravity = Gravity.CENTER_HORIZONTAL
        setPadding(0, dp(14), 0, dp(10))
        addView(ImageView(this@showLanShareQrDialog).apply {
            setImageBitmap(bitmap)
            contentDescription = "局域网分享二维码"
            setBackgroundColor(qrBackground)
            scaleType = ImageView.ScaleType.CENTER
        }, LinearLayout.LayoutParams(dp(240), dp(240)))
        addView(LinearLayout(this@showLanShareQrDialog).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(TextView(this@showLanShareQrDialog).apply {
                text = session.baseUrl
                gravity = Gravity.CENTER
                setTextColor(secondaryText())
                setTextIsSelectable(true)
                setSingleLine(true)
                ellipsize = android.text.TextUtils.TruncateAt.MIDDLE
            }, LinearLayout.LayoutParams(0, dp(42), 1f))
            addView(ImageButton(this@showLanShareQrDialog).apply {
                setImageResource(R.drawable.ic_copy)
                setColorFilter(if (isDark()) Color.WHITE else 0xff334155.toInt())
                setBackgroundColor(Color.TRANSPARENT)
                contentDescription = "复制局域网传输地址"
                setOnClickListener {
                    (getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager).setPrimaryClip(android.content.ClipData.newPlainText("局域网传输地址", session.baseUrl))
                    toast("已复制局域网传输地址")
                }
            }, LinearLayout.LayoutParams(dp(42), dp(42)))
        }, LinearLayout.LayoutParams(dp(240), dp(42)))
    }
    val dialog = AlertDialog.Builder(this).setView(box).create()
    dialog.setCanceledOnTouchOutside(true)
    dialog.setOnCancelListener { if (!simulated) lanShareQrVisible = false }
    showIos26Dialog(dialog)
    val metricsPopup = if (simulated) showSimulationMetrics(dialog.window?.decorView ?: box, "二维码弹窗") else null
    dialog.setOnDismissListener { metricsPopup?.dismiss(); if (!simulated) lanShareQrVisible = false }
}

internal fun MainActivity.startLanShareAutoRefresh() {
    stopLanShareAutoRefresh()
    val task = object : Runnable {
        override fun run() {
            if (page != "lanShare" || lanShareSession == null) return
            refreshLanShareFiles(showError = false)
            lanShareRefreshHandler.postDelayed(this, 1_500L)
        }
    }
    lanShareRefreshRunnable = task
    lanShareRefreshHandler.postDelayed(task, 1_500L)
}

internal fun MainActivity.stopLanShareAutoRefresh() {
    lanShareRefreshRunnable?.let(lanShareRefreshHandler::removeCallbacks)
    lanShareRefreshRunnable = null
}

internal fun MainActivity.refreshLanShareFiles(showError: Boolean = true) {
    val session = lanShareSession ?: return
    if (lanShareRefreshInFlight) return
    lanShareRefreshInFlight = true
    lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
        val result = runCatching {
            val files = lanShareManager.list(session)
            files to fetchLanSharePreviews(session, files)
        }
        runOnUiThread {
            lanShareRefreshInFlight = false
            updateLanShareConnectionStatus()
            result.onSuccess {
                (files, previews) ->
                lanShareFiles = files
                lanSharePreviewFiles.putAll(previews)
                if (page == "lanShare") {
                    val messageList = content.findViewWithTag<LinearLayout>("lanShareFileList")
                    if (messageList != null) renderLanShareFileList(messageList) else render()
                }
            }
            result.onFailure { if (showError) toast("无法连接到分享房间") }
        }
    }
}

internal fun MainActivity.closeLanShare() {
    stopLanShareAutoRefresh()
    lanShareManager.stop(clearSharedFiles = true)
    lanShareSession = null
    lanShareFiles = emptyList()
    lanShareOwnFileIds.clear()
    lanSharePreviewFiles.clear()
    File(cacheDir, "lan-share-preview").listFiles().orEmpty().forEach { it.delete() }
}

private fun MainActivity.fetchLanSharePreviews(session: LanShareSession, files: List<LanShareFile>): Map<String, File> {
    val previewFolder = File(cacheDir, "lan-share-preview").apply { mkdirs() }
    val imageIds = files.filter { isLanShareImageName(it.name) }.map { it.id }.toSet()
    previewFolder.listFiles().orEmpty().filter { it.name !in imageIds }.forEach { it.delete() }
    var cachedBytes = previewFolder.listFiles().orEmpty().filter { it.isFile }.sumOf { it.length() }
    return buildMap {
        files.filter { isLanShareImageName(it.name) && lanShareManager.localFile(it.id) == null }.forEach { file ->
            val preview = File(previewFolder, file.id)
            if (!preview.isFile && file.size <= 16L * 1024L * 1024L && cachedBytes + file.size <= 64L * 1024L * 1024L) {
                runCatching { lanShareManager.downloadPreview(session, file.id, preview) }
                cachedBytes += preview.length()
            }
            if (preview.isFile) put(file.id, preview)
        }
    }
}
internal fun MainActivity.uploadLanShareFile(uri: Uri, temporaryFile: File? = null) { val session = lanShareSession ?: return; lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) { runCatching { val id = lanShareManager.upload(session, uri); id to lanShareManager.list(session) }.onSuccess { (id, files) -> temporaryFile?.delete(); runOnUiThread { lanShareOwnFileIds.add(id); lanShareFiles = files; render() } }.onFailure { temporaryFile?.delete(); runOnUiThread { toast("上传失败") } } } }
internal fun MainActivity.uploadLanShareMessage(text: String) { val session = lanShareSession ?: return; lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) { runCatching { val id = lanShareManager.uploadText(session, text); id to lanShareManager.list(session) }.onSuccess { (id, files) -> runOnUiThread { lanShareMessageInput?.setText(""); lanShareOwnFileIds.add(id); lanShareFiles = files; render(); toast("发送成功") } }.onFailure { runOnUiThread { toast("发送失败") } } } }
internal fun MainActivity.downloadLanShareFile(id: String, uri: Uri) { val session = lanShareSession ?: return; lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) { runCatching { lanShareManager.download(session, id, uri) }.onSuccess { runOnUiThread { toast("下载完成") } }.onFailure { runOnUiThread { toast("下载失败") } } } }

internal fun MainActivity.saveLanShareFile(file: LanShareFile) {
    val mime = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.name.substringAfterLast('.', "").lowercase()) ?: "application/octet-stream"
    pendingLanDownloadId = file.id
    startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
        type = mime
        putExtra(Intent.EXTRA_TITLE, file.name)
        addCategory(Intent.CATEGORY_OPENABLE)
    }, MainActivity.REQUEST_LAN_SHARE_DOWNLOAD)
}

private fun isLanShareImageName(name: String) = name.substringAfterLast('.', "").lowercase() in setOf("jpg", "jpeg", "png", "gif", "webp", "heic", "heif")

/** 相机照片常将方向保存在 EXIF；BitmapFactory 不会自动应用，故在气泡预览前校正。 */
private fun decodeLanSharePreview(file: File): Bitmap? {
    val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return null
    val orientation = runCatching { ExifInterface(file.absolutePath).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
    val rotation = when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90, ExifInterface.ORIENTATION_TRANSPOSE -> 90f
        ExifInterface.ORIENTATION_ROTATE_180 -> 180f
        ExifInterface.ORIENTATION_ROTATE_270, ExifInterface.ORIENTATION_TRANSVERSE -> 270f
        else -> 0f
    }
    if (rotation == 0f) return bitmap
    return runCatching { Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, Matrix().apply { postRotate(rotation) }, true) }.getOrDefault(bitmap)
}
