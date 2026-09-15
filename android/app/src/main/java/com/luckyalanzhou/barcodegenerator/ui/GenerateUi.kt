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

internal fun MainActivity.showManualAdd(prefill: String = "") {
        val valueInput = EditText(this).apply { hint = "输入一行条码内容"; setSingleLine(true); minLines = 1; gravity = Gravity.CENTER_VERTICAL; includeFontPadding = false; setBackgroundResource(R.drawable.bg_input); setPadding(dp(12), 0, dp(12), 0); setText(prefill); setSelection(text.length) }
        val selector = Spinner(this).apply {
            adapter = formatSpinnerAdapter()
            setBackgroundResource(R.drawable.bg_input)
            setPadding(dp(12), 0, dp(12), 0)
            setSelection(formats.indexOfFirst { it.second == BarcodeFormat.CODE_128 }.coerceAtLeast(0))
            setOnTouchListener { view, event ->
                if (event.actionMasked == MotionEvent.ACTION_UP) {
                    showMaterialDropdown(view, formats.map { it.first }, popupWidth = view.width, selectedIndex = selectedItemPosition) { index ->
                        setSelection(index)
                    }
                }
                true
            }
        }
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(24), dp(8), dp(24), 0); addView(valueInput); addView(selector) }
         AlertDialog.Builder(this).setTitle("添加一行条码").setView(box).setNegativeButton("取消", null).setPositiveButton("添加") { _, _ ->
            val value = valueInput.text.toString().trim()
            if (value.isEmpty()) { toast("请输入条码内容"); return@setPositiveButton }
            val selected = formats[selector.selectedItemPosition]
            items.add(0, CodeItem(nextItemId(), value, selected.first))
            saveItems(); page = "history"; showList(false); toast("已添加条码")
        }.create().also { showIos26Dialog(it) }
    }



internal fun MainActivity.showGenerate() {
         val background = appBackground()
         content.setBackgroundColor(background)
         rootLayout.setBackgroundColor(background)
         window.statusBarColor = background
         window.navigationBarColor = background
         window.decorView.systemUiVisibility = if (isDark()) 0 else View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        val activity = this
        saveInputDraft()
        content.removeAllViews()
        content.setPadding(0, 0, 0, 0)
        inputRows.clear()
        inputContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        val visibleInput = ScrollView(this).apply {
             isVerticalScrollBarEnabled = true
             overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            isFillViewport = true
            isNestedScrollingEnabled = false
            setBackgroundDrawable(liquidGlassCard())
            setPadding(dp(14), dp(10), dp(14), dp(10))
            addView(inputContainer)
            setOnTouchListener { view, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> view.parent?.requestDisallowInterceptTouchEvent(true)
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> view.parent?.requestDisallowInterceptTouchEvent(false)
                }
                false
            }
        }
        inputScroll = visibleInput
        content.addView(visibleInput, LinearLayout.LayoutParams(-1, dp(56 + 16)).apply { setMargins(0, 0, 0, dp(12)) })
        if (inputDraft.isEmpty()) addInputRow() else inputDraft.toList().forEach { addInputRow(it) }
         val actionRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
         val addButton = styleButton(Button(this).apply {
             text = "+ 添加一行"
             textSize = 15f
             setOnClickListener {
                 if (inputRows.size >= 100) { toast("最多保留 100 行输入框"); return@setOnClickListener }
                 val currentInput = inputRows.firstOrNull { it.hasFocus() }
                 addInputRow(focus = true, after = currentInput)
             }
         }).apply {
             setBackgroundDrawable(glassButtonBackground().apply { cornerRadius = dp(18).toFloat() })
             setPadding(dp(12), 0, dp(12), 0)
         }
         actionRow.addView(addButton, LinearLayout.LayoutParams(0, dp(52), 1f).apply { setMargins(0, 0, dp(4), 0) })
         val cameraAction = LinearLayout(this).apply {
             gravity = Gravity.CENTER
             setBackgroundDrawable(glassButtonBackground().apply { cornerRadius = dp(18).toFloat() })
             setPadding(dp(12), 0, dp(12), 0)
             isClickable = true; isFocusable = true
             setOnClickListener { captureText() }
             addView(ImageView(activity).apply {
                 setImageResource(R.drawable.ic_camera)
                 imageTintList = ColorStateList.valueOf(if (isDark()) 0xffa9caff.toInt() else 0xff2453a6.toInt())
                 contentDescription = "拍照取字"
             }, LinearLayout.LayoutParams(dp(24), dp(24)))
             addView(TextView(activity).apply { text = "拍照取字"; textSize = 15f; gravity = Gravity.CENTER_VERTICAL; includeFontPadding = false; setTextColor(if (isDark()) 0xffd7e3f5.toInt() else 0xff2453a6.toInt()) }, LinearLayout.LayoutParams(-2, dp(52)).apply { setMargins(dp(6), 0, 0, 0) })
         }
         actionRow.addView(cameraAction, LinearLayout.LayoutParams(0, dp(52), 1f).apply { setMargins(dp(4), 0, 0, 0) })
         addSpaced(actionRow, bottom = 10)
         formatSpinner = Spinner(this).apply { adapter = formatSpinnerAdapter(); setBackgroundResource(R.drawable.bg_input); setPadding(dp(8), 0, dp(8), 0) }
          formatSpinner.setSelection(formats.indexOfFirst { it.first == (pendingGenerateFormat ?: "Code 128-B") }.coerceAtLeast(0))
          pendingGenerateFormat = null
          val formatCard = LinearLayout(this).apply {
              orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
              setPadding(dp(14), dp(4), dp(14), dp(4)); setBackgroundDrawable(liquidGlassCard())
          }
          formatCard.addView(TextView(activity).apply { text = "条码类型"; textSize = 16f; gravity = Gravity.CENTER_VERTICAL; setTypeface(null, Typeface.BOLD); setTextColor(primaryText()); setPadding(dp(18), 0, 0, 0) }, LinearLayout.LayoutParams(0, dp(52), 1f))
           formatCard.addView(formatSpinner, LinearLayout.LayoutParams(dp(150), dp(44)))
           formatSpinner.setOnTouchListener { _, event ->
               if (event.actionMasked == MotionEvent.ACTION_UP) showFormatPopup(formatSpinner)
               true
          }
          addSpaced(formatCard, bottom = 12)
          batchGenerateButton = styleButton(Button(this).apply { isEnabled = false; setOnClickListener { if (isEnabled) generateAll() } }, primary = true)
         updateBatchGenerateButton()
         addSpaced(batchGenerateButton!!, bottom = 14)
    }


internal fun MainActivity.updateBatchGenerateButton() {
         val count = inputRows.count { it.text.toString().trim().isNotEmpty() }
                   batchGenerateButton?.apply {
              text = "生成 ${count} 个条码"
              isEnabled = count > 0
              setTextColor(if (isEnabled) Color.WHITE else 0xff98a2b3.toInt())
              setBackgroundResource(if (isEnabled) R.drawable.bg_button_primary else R.drawable.bg_button_disabled)
          }
}


internal fun MainActivity.updateInputScrollHeight() {
         val scroll = inputScroll ?: return
         val height = dp((inputRows.size.coerceIn(1, 5)) * 56 + 16)
         scroll.layoutParams = (scroll.layoutParams ?: LinearLayout.LayoutParams(-1, height)).apply { this.height = height }
         scroll.requestLayout()
     }

internal fun MainActivity.addInputRow(value: String = "", focus: Boolean = false, after: EditText? = null) {
        val container = runCatching { inputContainer }.getOrNull() ?: return
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(-1, -2)
        }
        val edit = EditText(this).apply {
            hint = "输入一行条码内容"
            textSize = 16f
            setSingleLine(true)
            setTextColor(primaryText())
            setHintTextColor(secondaryText())
            setBackgroundResource(R.drawable.bg_input)
            setPadding(dp(12), 0, dp(12), 0)
            setText(value)
            setSelection(text.length)
        }
        row.addView(edit, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(inputActionButton("↑") { moveInputRow(edit, -1) })
        row.addView(inputActionButton("↓") { moveInputRow(edit, 1) })
        row.addView(deleteInputButton(onClick = { removeInputRow(edit) }, onLongClick = { confirmClearAllInputRows() }))
        val insertAt = after?.let { inputRows.indexOf(it) + 1 }?.takeIf { it > 0 } ?: inputRows.size
        inputRows.add(insertAt, edit)
        edit.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                inputDraft = inputRows.map { it.text.toString() }.toMutableList()
                updateBatchGenerateButton()
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })
        inputDraft = inputRows.map { it.text.toString() }.toMutableList()
        container.addView(row, insertAt, LinearLayout.LayoutParams(-1, dp(48)).apply { setMargins(0, 0, 0, dp(6)) })
        container.requestLayout()
        updateInputScrollHeight()
        updateBatchGenerateButton()
        refreshInputActions()
        if (focus) edit.post {
            edit.requestFocus()
            edit.setSelection(edit.text.length)
            inputScroll?.smoothScrollTo(0, row.top)
        }
    }


internal fun MainActivity.deleteInputButton(onClick: () -> Unit, onLongClick: () -> Unit): ImageButton {
        val activity = this
        val size = (36 * resources.displayMetrics.density).toInt()
        return ImageButton(this).apply {
            setImageResource(R.drawable.ic_delete_light)
            contentDescription = "删除此行"
            background = null
            setPadding(dp(6), dp(6), dp(6), dp(6))
            layoutParams = LinearLayout.LayoutParams(size, size)
            setOnClickListener { onClick() }
            setOnLongClickListener {
                onLongClick()
                true
            }
        }
    }


internal fun MainActivity.inputActionButton(label: String, color: Int = secondaryText(), onClick: () -> Unit): Button {
        val size = (36 * resources.displayMetrics.density).toInt()
        return Button(this).apply {
            text = label
            setBackgroundResource(R.drawable.bg_sort_button)
            textSize = 14f
            setTextColor(color)
            minWidth = 0
            minimumWidth = 0
            minimumHeight = 0
            setPadding(0, 0, 0, 0)
            layoutParams = LinearLayout.LayoutParams(size, size)
            setOnClickListener { onClick() }
        }
    }


internal fun MainActivity.moveInputRow(edit: EditText, direction: Int) {
        val activity = this
        val from = inputRows.indexOf(edit)
        val to = from + direction
        if (from < 0 || to !in inputRows.indices) return
        val item = inputRows.removeAt(from)
        inputRows.add(to, item)
        inputContainer.removeAllViews()
        inputRows.forEach { current ->
            val row = (current.parent as? LinearLayout)
            if (row != null) row.removeAllViews()
            val rebuilt = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
            rebuilt.addView(current, LinearLayout.LayoutParams(0, -2, 1f))
            rebuilt.addView(inputActionButton("↑") { moveInputRow(current, -1) })
            rebuilt.addView(inputActionButton("↓") { moveInputRow(current, 1) })
            rebuilt.addView(deleteInputButton(onClick = { removeInputRow(current) }, onLongClick = { confirmClearAllInputRows() }))
            inputContainer.addView(rebuilt, LinearLayout.LayoutParams(-1, dp(48)).apply { setMargins(0, 0, 0, dp(6)) })
        }
        inputDraft = inputRows.map { it.text.toString() }.toMutableList()
        updateBatchGenerateButton()
        refreshInputActions()
    }


internal fun MainActivity.removeInputRow(edit: EditText) {
        val activity = this
        if (inputRows.size <= 1) {
            edit.setText("")
            inputDraft = mutableListOf("")
            updateBatchGenerateButton()
            return
        }
        inputRows.remove(edit)
        inputDraft = inputRows.map { it.text.toString() }.toMutableList()
        (edit.parent as? View)?.let { (it.parent as? LinearLayout)?.removeView(it) }
        updateInputScrollHeight()
        updateBatchGenerateButton()
        refreshInputActions()
    }


internal fun MainActivity.clearAllInputRows() {
        inputContainer.removeAllViews()
        inputRows.clear()
        inputDraft = mutableListOf("")
        addInputRow()
        inputScroll?.post { inputScroll?.fullScroll(View.FOCUS_UP) }
        toast("已清空输入框，仅保留一个")
    }

/** 长按垃圾桶是批量操作，先确认以避免误触清空所有输入。 */
internal fun MainActivity.confirmClearAllInputRows() {
    if (inputRows.size <= 1 && inputRows.firstOrNull()?.text.isNullOrBlank()) return
    val dialog = AlertDialog.Builder(this)
        .setTitle("清空所有输入？")
        .setMessage("将删除当前所有输入内容，并保留一个空白输入框。")
        .setNegativeButton("取消", null)
        .setPositiveButton("清空") { _, _ -> clearAllInputRows() }
        .create()
    showIos26Dialog(dialog, compact = true)
}


internal fun MainActivity.refreshInputActions() {
        val activity = this
        for (i in 0 until inputContainer.childCount) {
            val row = inputContainer.getChildAt(i) as? LinearLayout ?: continue
            if (row.childCount >= 4) {
                val showActions = inputRows.size > 1
                row.getChildAt(1).visibility = if (showActions) View.VISIBLE else View.GONE
                row.getChildAt(2).visibility = if (showActions) View.VISIBLE else View.GONE
                row.getChildAt(3).visibility = if (showActions) View.VISIBLE else View.GONE
                row.getChildAt(1).isEnabled = showActions && i > 0
                row.getChildAt(2).isEnabled = showActions && i < inputContainer.childCount - 1
                row.getChildAt(3).isEnabled = showActions
            }
        }
    }


internal fun MainActivity.generateAll() {
        val activity = this
        saveInputDraft()
        val values = inputDraft.map { it.trim() }.filter { it.isNotEmpty() }
        if (values.isEmpty()) { toast("请输入内容"); return }
        val selected = formats[formatSpinner.selectedItemPosition]
        val invalid = values.indexOfFirst { !BarcodeValidator.validate(it, selected.first).valid }
        if (invalid >= 0) { toast("第 ${invalid + 1} 行：${BarcodeValidator.validate(values[invalid], selected.first).message}"); return }
        // 仅从收藏文件的“编辑”路径重新生成时才更新原收藏。
        // 其他入口可能保留了旧的选中状态，不能让它影响新建收藏。
        val editingFavorite = selectedFavoriteGroup?.takeIf { resultsReturnPage == "favorites" }
        if (editingFavorite == null) selectedFavoriteGroup = null
         val generated = mutableListOf<CodeItem>()
        val batchTime = System.currentTimeMillis()
        values.forEach { value ->
            CodeItem(nextItemId(), value, selected.first, batchTime).also {
                items.add(0, it)
                generated.add(it)
            }
        }
        saveItems()
        resultItems = generated
        showingHistoryResult = false
        resultsReturnPage = if (editingFavorite != null) "favorites" else "generate"
        page = "results"
        showResults()
    }

