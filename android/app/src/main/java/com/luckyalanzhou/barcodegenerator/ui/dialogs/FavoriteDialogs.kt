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



internal fun MainActivity.showFolderEditor(initial: String = "", showMetrics: Boolean = false, onSaved: (String) -> Unit) {
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
        var metricsPopup: android.widget.PopupWindow? = null
        dialog.setOnDismissListener { metricsPopup?.dismiss() }
        // 先显示真实弹窗，再以弹窗窗口整体作为锚点，确保数据面板出现在完整弹窗下方。
        showIos26Dialog(dialog)
        if (showMetrics) {
            metricsPopup = showSimulationMetrics(dialog.window?.decorView ?: box, "文件夹编辑弹窗")
        }
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




