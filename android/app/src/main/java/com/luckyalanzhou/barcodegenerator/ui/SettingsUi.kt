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

internal fun MainActivity.showSettings() {
        val activity = this
        val draft = style.copy().apply { barHeight = barHeight.coerceIn(30, 150); barWidth = barWidth.coerceIn(120f, 360f); textSize = textSize.coerceIn(10f, 24f); margin = margin.coerceIn(0, 40) }
        content.removeAllViews()
        content.setPadding(dp(8), dp(4), dp(8), dp(18))
        content.setBackgroundColor(appBackground())
        rootLayout.setBackgroundColor(appBackground())
        fun sectionLabel(text: String) = TextView(this).apply {
            this.text = text; textSize = 12f; letterSpacing = 0.055f; includeFontPadding = false
            setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL)); setTextColor(secondaryText())
            setPadding(dp(8), dp(8), dp(8), dp(6))
        }
        fun sliderRow(title: String, seekBar: SeekBar, valueText: (Int) -> String): LinearLayout {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(8), 0, dp(8), 0) }
            val titleView = TextView(this).apply { text = title; textSize = 16f; gravity = Gravity.CENTER_VERTICAL; includeFontPadding = false; setTypeface(Typeface.create("sans-serif", Typeface.NORMAL)); setTextColor(primaryText()) }
            val value = TextView(this).apply { text = valueText(seekBar.progress); textSize = 15f; setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL)); letterSpacing = -0.01f; gravity = Gravity.CENTER_VERTICAL or Gravity.END; includeFontPadding = false; setSingleLine(true); setTextColor(if (isDark()) 0xffb8ccff.toInt() else 0xff2864d7.toInt()) }
            row.addView(titleView, LinearLayout.LayoutParams(dp(88), dp(48)))
            row.addView(seekBar, LinearLayout.LayoutParams(0, dp(40), 1f).apply { setMargins(dp(2), 0, dp(8), 0) })
            row.addView(value, LinearLayout.LayoutParams(dp(72), dp(48)))
            seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar, progress: Int, fromUser: Boolean) { value.text = valueText(progress); if (fromUser) (bar.tag as? ((Int) -> Unit))?.invoke(progress) }
            override fun onStartTrackingTouch(bar: SeekBar) = Unit
            override fun onStopTrackingTouch(bar: SeekBar) = Unit
            })
            return row
        }

        var persistSettingsAction: (() -> Unit)? = null
         val appearance = Spinner(this).apply { adapter = ArrayAdapter(activity, android.R.layout.simple_spinner_dropdown_item, listOf("跟随系统", "浅色", "深色")); setSelection(listOf("system", "light", "dark").indexOf(draft.colorScheme).coerceAtLeast(0)); gravity = Gravity.CENTER; setBackgroundResource(R.drawable.bg_input) }
         val appearanceValue = TextView(activity).apply { text = listOf("跟随系统", "浅色", "深色")[appearance.selectedItemPosition]; gravity = Gravity.CENTER; setTextColor(primaryText()); setBackgroundResource(R.drawable.bg_input); setOnClickListener { view -> showMaterialDropdown(view, listOf("跟随系统", "浅色", "深色"), selectedIndex = appearance.selectedItemPosition) { index -> (view as TextView).text = listOf("跟随系统", "浅色", "深色")[index]; appearance.setSelection(index); persistSettingsAction?.invoke() } } }
        val showFormat = SwitchCompat(this).apply {
            // 保持设置行和卡片尺寸不变，只放大开关本体；SwitchCompat 自带平滑滑块动画。
            showText = false
            isChecked = draft.showFormat
            minWidth = dp(58)
            minimumWidth = dp(58)
            minHeight = dp(34)
            minimumHeight = dp(34)
            // 视觉放大但不改变父布局测量尺寸，避免该设置卡片变高。
            scaleX = 1.12f
            scaleY = 1.12f
            setPadding(0, 0, 0, 0)
            thumbTintList = ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                intArrayOf(Color.WHITE, if (isDark()) 0xffd8dde6.toInt() else 0xfff4f5f7.toInt())
            )
            trackTintList = ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                intArrayOf(0xff34c759.toInt(), if (isDark()) 0xff4b5058.toInt() else 0xffd1d5db.toInt())
            )
        }
        // 为放大后的开关提供独立承载区域，避免视觉缩放超出原测量边界被裁切。
        val showFormatSlot = FrameLayout(activity).apply {
            clipChildren = false
            clipToPadding = false
            addView(showFormat, FrameLayout.LayoutParams(-2, -1, Gravity.END or Gravity.CENTER_VERTICAL))
        }
        val ocrReplacementLabels = arrayOf("O → 0", "I → 1", "S → 5", "B → 8")
        val ocrReplacementBits = intArrayOf(
            SettingsStore.OCR_REPLACE_O_ZERO,
            SettingsStore.OCR_REPLACE_I_ONE,
            SettingsStore.OCR_REPLACE_S_FIVE,
            SettingsStore.OCR_REPLACE_B_EIGHT
        )
        val ocrReplacementValue = TextView(activity).apply {
            gravity = Gravity.CENTER
            setTextColor(primaryText())
            setBackgroundResource(R.drawable.bg_input)
        }
        fun updateOcrReplacementValue(mask: Int) {
            val selected = ocrReplacementLabels.mapIndexedNotNull { index, label -> if (mask and ocrReplacementBits[index] != 0) label else null }
            ocrReplacementValue.text = when (selected.size) {
                0 -> "关闭"
                1 -> selected.first()
                ocrReplacementLabels.size -> "全部启用"
                else -> "已启用 ${selected.size} 项"
            }
            // 视觉上保持紧凑，辅助功能仍能读出完整的替换规则。
            ocrReplacementValue.contentDescription = if (selected.isEmpty()) {
                "OCR 字符纠错：关闭"
            } else {
                "OCR 字符纠错：${selected.joinToString("、")}"
            }
        }
        updateOcrReplacementValue(settingsStore.getOcrConfusionReplacementMask())
        ocrReplacementValue.setOnClickListener {
            val current = settingsStore.getOcrConfusionReplacementMask()
            showMaterialMultiDropdown(
                ocrReplacementValue,
                ocrReplacementLabels.toList(),
                ocrReplacementBits.indices.filter { current and ocrReplacementBits[it] != 0 }.toSet()
            ) { selected ->
                val mask = selected.sumOf { ocrReplacementBits[it] }
                settingsStore.setOcrConfusionReplacementMask(mask)
                updateOcrReplacementValue(mask)
            }
        }
         val textSizeSeekBar = SeekBar(this).apply { max = 14; progress = (draft.textSize.roundToInt() - 10).coerceIn(0, 14) }
        val barHeight = SeekBar(this).apply { max = 120; progress = (draft.barHeight - 30).coerceIn(0, 120) }
        val barWidth = SeekBar(this).apply { max = 240; progress = (draft.barWidth.roundToInt() - 120).coerceIn(0, 240) }
         val margin = SeekBar(this).apply { max = 40; progress = draft.margin.coerceIn(0, 40) }

        fun persistSettings() {
            draft.barColor = Color.BLACK
            draft.bgColor = Color.WHITE
            draft.colorScheme = listOf("system", "light", "dark")[appearance.selectedItemPosition]
            draft.showText = true; draft.textPosition = "bottom"; draft.showFormat = showFormat.isChecked
            draft.textSize = (10 + textSizeSeekBar.progress).toFloat(); draft.barHeight = 30 + barHeight.progress
            draft.barWidth = (120 + barWidth.progress).toFloat(); draft.margin = margin.progress
            style.barColor = draft.barColor; style.bgColor = draft.bgColor; style.colorScheme = draft.colorScheme
            style.showText = draft.showText; style.showFormat = draft.showFormat; style.textPosition = draft.textPosition
            style.textSize = draft.textSize; style.barHeight = draft.barHeight; style.barWidth = draft.barWidth
            style.margin = draft.margin
            saveStyle()
            applyAppearance()
        }
        persistSettingsAction = { persistSettings() }
        var suppressAppearanceCallback = true
        appearance.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
        override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long) {
                val selectedScheme = listOf("system", "light", "dark")[pos]
                appearanceValue.text = listOf("跟随系统", "浅色", "深色")[pos]
                if (!suppressAppearanceCallback) {
                    persistSettings()
                }
            }
        }
        // Spinner 绑定监听器后可能异步触发一次初始回调；必须先放行初始化回调，避免刚进入设置页就 recreate。
        appearance.post { suppressAppearanceCallback = false }
        showFormat.setOnCheckedChangeListener { _, _ -> persistSettings() }
        listOf(textSizeSeekBar, barHeight, barWidth, margin).forEach { seekBar ->
            seekBar.tag = { _: Int -> persistSettings() }
        }
        fun groupCard(rows: List<View>): LinearLayout = contentCard().apply {
             orientation = LinearLayout.VERTICAL
             elevation = 0f
             setPadding(dp(8), dp(4), dp(8), dp(4))
             rows.forEachIndexed { index, row ->
                 addView(row, LinearLayout.LayoutParams(-1, dp(48)))
                 if (index < rows.lastIndex) addView(View(activity).apply { setBackgroundColor(if (isDark()) 0x263b4658 else 0x1a667085) }, LinearLayout.LayoutParams(-1, dp(1)))
             }
         }
         fun textRow(title: String, trailing: View, trailingWidth: Int = dp(132)): LinearLayout = LinearLayout(this).apply {
             gravity = Gravity.CENTER_VERTICAL; setPadding(0, 0, 0, 0)
             addView(TextView(activity).apply { text = title; this.textSize = 16f; gravity = Gravity.START or Gravity.CENTER_VERTICAL; includeFontPadding = false; letterSpacing = -0.01f; setTypeface(Typeface.create("sans-serif", Typeface.NORMAL)); setTextColor(primaryText()) }, LinearLayout.LayoutParams(0, -1, 1f))
             addView(trailing, LinearLayout.LayoutParams(trailingWidth, dp(40)))
         }
        fun compactSliderRow(title: String, seekBar: SeekBar, valueText: (Int) -> String) = sliderRow(title, seekBar, valueText).apply { setPadding(0, 0, 0, 0) }
          addSpaced(sectionLabel("显示"), bottom = 2)
         addSpaced(groupCard(listOf(
            textRow("外观", appearanceValue)
         )), bottom = 12)
          addSpaced(sectionLabel("条码"), bottom = 2)
         addSpaced(groupCard(listOf(
            compactSliderRow("文字大小", textSizeSeekBar) { "${10 + it} sp" }, compactSliderRow("条码高度", barHeight) { "${30 + it} dp" },
            compactSliderRow("条码宽度", barWidth) { "${120 + it} dp" }, compactSliderRow("条码间距", margin) { "$it dp" },
            textRow("条码格式", showFormatSlot),
            textRow("OCR 字符纠错", ocrReplacementValue)
        )), bottom = 12)
        addSpaced(sectionLabel("工具"), bottom = 2)
        val toolRows = mutableListOf<View>()
        fun toolActionButton(label: String, buttonMinHeight: Int = 48, horizontalPadding: Int = 14, action: () -> Unit) = styleButton(Button(activity).apply {
            text = label
            minWidth = 0
            minimumWidth = 0
            minHeight = dp(buttonMinHeight)
            minimumHeight = dp(buttonMinHeight)
            setPadding(dp(horizontalPadding), dp(7), dp(horizontalPadding), dp(7))
            setOnClickListener { action() }
        }).apply {
            // 工具按钮保留玻璃质感，但减少胶囊感并稍微放大外框。
            background = glassButtonBackground().apply { cornerRadius = dp(14).toFloat() }
        }
        toolRows += textRow("局域网文件分享", toolActionButton("启动", buttonMinHeight = 40, horizontalPadding = 16) { enterLanShare() }, trailingWidth = dp(88))
        val versionLine = LinearLayout(this).apply {
             orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
             val info = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
             info.addView(TextView(activity).apply { text = "作者：Alan"; this.textSize = 13f; setTextColor(secondaryText()) })
             val versionRow = LinearLayout(activity).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(8), 0, 0) }
             // 红点使用版本文字右侧的独立槽位，而非覆盖式叠放在同一容器中。
             versionRow.addView(TextView(activity).apply { text = "版本：${BuildConfig.VERSION_NAME}"; this.textSize = 13f; includeFontPadding = false; gravity = Gravity.CENTER_VERTICAL; setTextColor(secondaryText()) }, LinearLayout.LayoutParams(-2, dp(18)))
             val updateBadgeSlot = FrameLayout(activity)
             updateBadgeSlot.addView(View(activity).apply {
                 background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(0xffef4444.toInt()) }
                 visibility = if (availableUpdateUrl != null) View.VISIBLE else View.GONE
             }, FrameLayout.LayoutParams(dp(5), dp(5), Gravity.END or Gravity.TOP).apply { topMargin = dp(2); rightMargin = 0 })
             // 12dp 的紧凑槽位保证红点不压住版本文字，也不会显得游离。
             versionRow.addView(updateBadgeSlot, LinearLayout.LayoutParams(dp(12), dp(18)))
             info.addView(versionRow)
             addView(info, LinearLayout.LayoutParams(0, -2, 1f))
             addView(styleButton(Button(activity).apply { text = "检查更新"; setOnClickListener { checkForUpdates(silent = false) } }), LinearLayout.LayoutParams(-2, dp(38)))
         }
         val about = contentCard().apply {
             orientation = LinearLayout.VERTICAL
             setPadding(dp(12), dp(6), dp(8), dp(6))
             addView(TextView(activity).apply { text = "关于"; this.textSize = 16f; setTypeface(null, Typeface.BOLD); setTextColor(primaryText()) })
             addView(versionLine, LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, dp(8), 0, 0) })
         }
         toolRows += textRow("恢复默认设置", toolActionButton("恢复", buttonMinHeight = 40, horizontalPadding = 16) {
                 textSizeSeekBar.progress = 4
                 barHeight.progress = 25
                 barWidth.progress = 100
                 margin.progress = 4
                 persistSettings()
                 toast("已恢复条码默认设置")
              }, trailingWidth = dp(88))
         val backupActions = LinearLayout(activity).apply {
             orientation = LinearLayout.HORIZONTAL
             gravity = Gravity.CENTER_VERTICAL
             // 给按钮上下留出空间，避免圆角背景和阴影被 48dp 行高裁切；固定宽度让左右边框完整且一致。
             addView(toolActionButton("导入", buttonMinHeight = 40, horizontalPadding = 16) { restoreFavoritesImport() }, LinearLayout.LayoutParams(dp(88), dp(40)))
             addView(toolActionButton("导出", buttonMinHeight = 40, horizontalPadding = 16) { createFavoritesExport() }, LinearLayout.LayoutParams(dp(88), dp(40)).apply { leftMargin = dp(8) })
         }
         toolRows += textRow("收藏备份", backupActions, trailingWidth = -2)
          if (BuildConfig.DEBUG_LOG_EXPORT) {
              toolRows += textRow("功能自检", toolActionButton("打开", buttonMinHeight = 40, horizontalPadding = 16) { showFeatureSelfTestDialog() }, trailingWidth = dp(88))
          }
        addSpaced(groupCard(toolRows), bottom = 12)
          // 整张“关于”卡片是一个安静的入口：在短时间内连点五次才打开彩蛋，日常浏览不会误触。
         var aboutTapCount = 0
         var lastAboutTapAt = 0L
         about.isClickable = true
         about.setOnClickListener {
             val now = System.currentTimeMillis()
             aboutTapCount = if (now - lastAboutTapAt <= 1_500L) aboutTapCount + 1 else 1
             lastAboutTapAt = now
             it.animate().scaleX(0.985f).scaleY(0.985f).setDuration(65).withEndAction {
                 it.animate().scaleX(1f).scaleY(1f).setDuration(130).start()
             }.start()
             if (aboutTapCount >= 5) {
                 aboutTapCount = 0
                showFireworksEasterEgg()
             }
         }
         addSpaced(about, bottom = 10)
    }

