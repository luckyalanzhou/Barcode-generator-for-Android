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

internal fun MainActivity.showResults() {
        val activity = this
        content.removeAllViews()
        content.setPadding(0, dp(if (showingHistoryResult) 16 else 0), 0, dp(if (showingHistoryResult) 24 else 0))
        // 浅色结果页与固定白色条码画布使用同一底色，避免每个条码周围出现矩形白边。
        val resultBackground = if (isDark()) appBackground() else Color.WHITE
        content.setBackgroundColor(resultBackground)
        rootLayout.setBackgroundColor(resultBackground)
        window.statusBarColor = appBackground()
        window.navigationBarColor = appBackground()
        if (resultItems.isEmpty()) {
            addSpaced(sectionTitle("生成结果"), bottom = 6)
            addSpaced(TextView(this).apply { text = "暂无生成结果"; textSize = 17f; gravity = Gravity.CENTER; setTextColor(secondaryText()); setPadding(0, dp(40), 0, dp(40)) }, bottom = 0)
            return
        }

        if (!showingHistoryResult) {
            val toolbar = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(10), dp(8), dp(10), dp(8))
                setBackgroundColor(resultBackground)
            }
            fun toolButton(iconRes: Int, description: String, action: () -> Unit) = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(6), 0, dp(6), 0)
                isClickable = true
                isFocusable = true
                contentDescription = description
                addView(ImageView(activity).apply {
                    setImageResource(iconRes)
                    setColorFilter(if (isDark()) 0xffb8ccff.toInt() else 0xff2166d1.toInt())
                    scaleType = ImageView.ScaleType.CENTER_INSIDE
                }, LinearLayout.LayoutParams(dp(25), dp(27)))
                addView(TextView(activity).apply { text = description; textSize = 12f; gravity = Gravity.CENTER; typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL); letterSpacing = -0.01f; includeFontPadding = false; setTextColor(if (isDark()) 0xffd7e3f5.toInt() else 0xff2453a6.toInt()); setPadding(0, dp(3), 0, 0) })
                setOnClickListener { action() }
            }
            toolbar.addView(Space(this), LinearLayout.LayoutParams(0, 1, 1f))
            toolbar.addView(toolButton(R.drawable.ic_action_edit, "编辑") {
                if (selectedFavoriteGroup != null && resultsReturnPage == "favorites") {
                    // 从收藏文件的结果页编辑时，以当前结果页数据回填生成页，并保留收藏组以便保存时更新原文件。
                    inputDraft = resultItems.map { it.text }.toMutableList()
                    pendingGenerateFormat = resultItems.firstOrNull()?.format
                    // showGenerate() 会先保存现有输入框草稿；清除旧页面引用，避免其覆盖刚回填的结果数据。
                    inputRows.clear()
                    page = "generate"
                    render()
                }
                else {
                    inputDraft = resultItems.map { it.text }.toMutableList()
                    inputRows.clear()
                    page = "generate"
                    render()
                }
            }, LinearLayout.LayoutParams(dp(64), dp(64)))
            toolbar.addView(toolButton(R.drawable.ic_action_favorite, "收藏") { saveResultAsFavorite() }, LinearLayout.LayoutParams(dp(64), dp(64)))
            toolbar.addView(toolButton(R.drawable.ic_action_share, "分享") { shareResultPage() }, LinearLayout.LayoutParams(dp(64), dp(64)))
            addSpaced(toolbar, bottom = 0)
        }

        resultItems.forEachIndexed { index, item ->
            val itemBox = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setBackgroundColor(Color.TRANSPARENT)
                setPadding(dp(12), 0, dp(12), 0)
            }
            val barcode = encode(item.text, formats.firstOrNull { it.first == item.format }?.second ?: BarcodeFormat.CODE_128)
            if (barcode != null) {
                val isCode128 = item.format == "Code 128-B"
                if (isCode128) {
                    val barHeightPx = dp(activity.style.barHeight.coerceIn(30, 150).coerceAtLeast(1)).coerceAtMost(barcode.height)
                    // 仅 Code 128-B 使用设置中的固定宽度；其他条码格式保持原有自适应布局。
                    val desiredWidth = dp(activity.style.barWidth.roundToInt().coerceIn(120, 360)).coerceAtLeast(1)
                    val textEnabled = true
                    val label = if (activity.style.showFormat) "${item.text} · ${item.format}" else item.text
                    // encode() 的 Bitmap 可能还包含文字。这里只取纯条码区域，文字交给独立 TextView，
                    // 从根上避免文字和条码共享同一个 Canvas 而发生重叠。
                    val sourceTop = 0
                    // ZXing 会根据内容长度留下不同的左右空白；只裁剪 Code 128-B 的有效条纹区域，
                    // 再由固定宽度的 ImageView 统一显示，避免同一结果页中出现宽窄不一。
                    // 裁剪后补回白色静区；深色页面也必须使用黑条白底，供外部扫描器读取。
                    val barOnly = addBarcodeQuietZone(trimBarcodeHorizontal(Bitmap.createBitmap(barcode, 0, sourceTop, barcode.width, barHeightPx)))
                    val labelView = TextView(activity).apply {
                        text = label
                        textSize = activity.style.textSize.coerceIn(10f, 24f)
                        gravity = Gravity.CENTER
                        includeFontPadding = true
                        setTextColor(if (activity.isDark()) Color.WHITE else activity.style.barColor)
                        setPadding(0, dp(8), 0, dp(4))
                        contentDescription = "条码文字"
                    }
                    itemBox.addView(ImageView(activity).apply {
                        setImageBitmap(barOnly)
                        scaleType = ImageView.ScaleType.FIT_XY
                        setPadding(0, 0, 0, 0)
                        contentDescription = "${item.format} 条码"
                    }, LinearLayout.LayoutParams(desiredWidth, barHeightPx).apply {
                        gravity = Gravity.CENTER_HORIZONTAL
                    })
                    itemBox.addView(labelView, LinearLayout.LayoutParams(-1, -2))
                } else {
                    itemBox.addView(ImageView(activity).apply {
                        setImageBitmap(barcode)
                        adjustViewBounds = true
                        setPadding(0, 0, 0, 0)
                        contentDescription = "${item.format} 条码"
                    }, LinearLayout.LayoutParams(-1, -2))
                }
            }
            content.addView(itemBox, LinearLayout.LayoutParams(-1, -2))
            if (index < resultItems.lastIndex && item.format == "Code 128-B" && resultItems[index + 1].format == "Code 128-B" && style.margin > 0) {
                content.addView(Space(this), LinearLayout.LayoutParams(1, dp(style.margin)))
            }
        }
    }


internal fun MainActivity.shareResultPage() {
        val activity = this
        val images = resultItems.mapNotNull { item ->
            encode(item.text, formats.firstOrNull { it.first == item.format }?.second ?: BarcodeFormat.CODE_128)
        }
        if (images.isEmpty()) { toast("没有可分享的条码"); return }
        val width = images.maxOf { it.width }
        val spacing = if (resultItems.all { it.format == "Code 128-B" }) dp(style.margin).coerceAtLeast(0) else 0
        val height = images.sumOf { it.height } + spacing * (images.size - 1)
        val pageImage = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(pageImage)
        canvas.drawColor(style.bgColor)
        var top = 0
        images.forEach { image ->
            canvas.drawBitmap(image, (width - image.width) / 2f, top.toFloat(), null)
            top += image.height + spacing
        }
        shareBitmap(pageImage, "本页生成的 ${images.size} 个条码")
    }


internal fun MainActivity.showList(favoritesOnly: Boolean) {
        content.removeAllViews()
        val background = appBackground()
        content.setBackgroundColor(background)
        rootLayout.setBackgroundColor(background)
        window.statusBarColor = background
        window.navigationBarColor = background
        window.decorView.systemUiVisibility = if (isDark()) 0 else View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        val tools = LinearLayout(this).apply { gravity = Gravity.END }
        tools.addView(Button(this).apply {
             text = "清空"; minHeight = dp(36); minimumHeight = dp(36); minWidth = 0; minimumWidth = 0
             setPadding(dp(10), 0, dp(10), 0); setBackgroundColor(Color.TRANSPARENT); stateListAnimator = null; elevation = 0f
             setTextColor(if (isDark()) 0xffff9b9b.toInt() else 0xffc85c5c.toInt())
             setOnClickListener { confirmClear(favoritesOnly) }
         })
        addSpaced(tools, bottom = 12)
        val list = if (favoritesOnly) items.filter { it.favorite } else items.filter { it.inHistory }
        if (list.isEmpty()) { addSpaced(TextView(this).apply { text = if (favoritesOnly) "还没有收藏" else "暂无历史记录"; textSize = 17f; gravity = Gravity.CENTER; setTextColor(secondaryText()); setPadding(0, dp(40), 0, dp(40)) }, bottom = 0); return }
        if (favoritesOnly) list.forEach { addCard(it) }
        else list.groupBy { it.createdAt }.toList().sortedByDescending { it.first }.forEach { (time, batch) -> addHistoryRow(batch, time) }
    }


internal fun MainActivity.addHistoryRow(batch: List<CodeItem>, time: Long) {
    val activity = this
    val orderedBatch = batch.sortedBy { it.id }
    val row = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(10), dp(2), dp(6), dp(2))
        setBackgroundResource(R.drawable.bg_history_card)
        setOnClickListener {
            resultItems = orderedBatch.toMutableList()
            showingHistoryResult = true
            resultsReturnPage = "history"
            page = "results"
            showResults()
        }
        setOnLongClickListener {
            if (orderedBatch.size == 1) showItemEditor(orderedBatch.first())
            else AlertDialog.Builder(activity)
                .setTitle("本次生成的 ${orderedBatch.size} 个条码")
                .setItems(orderedBatch.map { it.text }.toTypedArray()) { _, which -> showItemEditor(orderedBatch[which]) }
                .create().also { showIos26Dialog(it) }
            true
        }
    }

    val firstCodePreview = orderedBatch.firstOrNull()?.text?.let { value ->
        if (value.length > 8) value.take(8) + "..." else value
    }.orEmpty()
    row.addView(TextView(this).apply {
        text = "${orderedBatch.size}条：$firstCodePreview"
        textSize = 16f
        gravity = Gravity.CENTER_VERTICAL
        maxLines = 1
        ellipsize = android.text.TextUtils.TruncateAt.END
        includeFontPadding = false
        setTextColor(primaryText())
    }, LinearLayout.LayoutParams(0, dp(40), 1f))

    val right = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL or Gravity.END
        addView(TextView(activity).apply {
            text = formatHistoryTime(time)
            textSize = 12f
            setTextColor(secondaryText())
            gravity = Gravity.END
        }, LinearLayout.LayoutParams(0, dp(18), 1f))
        addView(ImageButton(activity).apply {
            setImageResource(R.drawable.ic_delete_light)
            contentDescription = "删除这条历史记录"
            background = null
            setColorFilter(0xffd98787.toInt())
            setPadding(dp(4), dp(2), 0, dp(2))
            elevation = 0f
            setOnClickListener {
                orderedBatch.forEach { it.inHistory = false }
                saveItems()
                showList(false)
            }
        }, LinearLayout.LayoutParams(dp(32), dp(28)))
    }
    row.addView(right, LinearLayout.LayoutParams(dp(100), dp(40)))
    content.addView(row, LinearLayout.LayoutParams(-1, -2).apply {
        setMargins(dp(6), 0, dp(6), dp(7))
    })
}

internal fun MainActivity.formatHistoryTime(time: Long): String {
    val date = Date(time)
    val now = Date()
    val sameDay = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(date) == SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(now)
    return if (sameDay) SimpleDateFormat("HH:mm", Locale.getDefault()).format(date)
    else SimpleDateFormat("M/d HH:mm", Locale.getDefault()).format(date)
}

