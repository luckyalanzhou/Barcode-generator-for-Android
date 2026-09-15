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

internal fun MainActivity.openFavoriteForEditing(group: FavoriteGroup) {
    selectedFavoriteGroup = group
    resultItems = group.itemIds.mapNotNull { id -> items.firstOrNull { it.id == id } }
    inputDraft = resultItems.map { it.text }.toMutableList()
    pendingGenerateFormat = resultItems.firstOrNull()?.format
    page = "generate"
    resultsReturnPage = "favorites"
    render()
}

internal fun MainActivity.addCard(item: CodeItem, editable: Boolean = true) {
        val box = contentCard()
        val barcode = encode(item.text, formats.firstOrNull { it.first == item.format }?.second ?: BarcodeFormat.CODE_128)
        if (barcode != null) box.addView(ImageView(this).apply { setImageBitmap(barcode); adjustViewBounds = true; setPadding(0, dp(4), 0, dp(8)); contentDescription = "${item.format} 条码" }, LinearLayout.LayoutParams(-1, -2))
        val detail = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        detail.addView(TextView(this).apply { text = "${item.text}\n${item.format}"; textSize = 16f; setTypeface(null, Typeface.BOLD); setTextColor(primaryText()); setPadding(0, 0, 0, dp(4)) }, LinearLayout.LayoutParams(0, -2, 1f))
        if (editable) detail.addView(styleButton(Button(this).apply { text = "编辑"; setOnClickListener { selectedFavoriteGroup?.let { openFavoriteForEditing(it) } } }), LinearLayout.LayoutParams(-2, dp(38)))
        box.addView(detail)
        content.addView(box, LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 0, 0, dp(12)) })
    }


internal fun MainActivity.showFavoriteGroups() {
        val activity = this
        content.removeAllViews()
        content.setPadding(0, 0, 0, dp(20))
        content.setBackgroundColor(appBackground())
        rootLayout.setBackgroundColor(appBackground())
        window.statusBarColor = appBackground()
        window.navigationBarColor = appBackground()

        search = EditText(this).apply {
            hint = "搜索名称、文件夹或内容"; textSize = 17f; setSingleLine(true)
            gravity = Gravity.CENTER_VERTICAL or Gravity.START; includeFontPadding = true
            minHeight = dp(56); minimumHeight = dp(56)
            setBackgroundResource(R.drawable.bg_input)
            // 图标单独放在容器中，避免复合 Drawable 与字体共用基线导致占位文字上下偏移。
            setPadding(dp(48), 0, dp(16), 0)
        }
        val searchBox = FrameLayout(this).apply {
            addView(search, FrameLayout.LayoutParams(-1, -1))
            addView(ImageView(this@showFavoriteGroups).apply {
                setImageResource(R.drawable.ic_search)
                imageTintList = ColorStateList.valueOf(secondaryText())
                contentDescription = "搜索"
            }, FrameLayout.LayoutParams(dp(24), dp(24), Gravity.START or Gravity.CENTER_VERTICAL).apply {
                marginStart = dp(16)
            })
        }
        addSpaced(searchBox, bottom = 16)
        favoriteTreeContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(favoriteTreeContainer, LinearLayout.LayoutParams(-1, -2))
        renderFavoriteTree("")
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                renderFavoriteTree(s?.toString()?.trim()?.lowercase(Locale.getDefault()).orEmpty())
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })
    }



internal fun MainActivity.renderFavoriteTree(query: String) {
    val tree = favoriteTreeContainer ?: return
    tree.removeAllViews()
    val folders = (favoriteFolders + favoriteGroups.map { it.folder }).filter { it.isNotBlank() }.distinct()
    val roots = folders.map { it.substringBefore('/') }.distinct().sorted()
    // 固定的三级语义色：蓝色一级目录、琥珀色二级目录、绿色收藏文件。
    val rootFolderColor = 0xff527ca8.toInt()
    val childFolderColor = 0xff9b7a57.toInt()
    val favoriteFileColor = 0xff5c8c7b.toInt()
    fun matches(g: FavoriteGroup) = query.isEmpty() || g.folder.lowercase(Locale.getDefault()).contains(query) || g.name.lowercase(Locale.getDefault()).contains(query) || g.itemIds.any { id -> items.firstOrNull { it.id == id }?.text?.lowercase(Locale.getDefault())?.contains(query) == true }
    if (!favoriteTreeInitialized) {
        // 仅首次进入收藏页时默认全折叠；后续重绘必须保留用户的展开状态。
        collapsedFavoriteFolders.addAll(folders)
        favoriteTreeInitialized = true
    } else {
        // 已删除的文件夹不再保留折叠状态，避免状态集合无限增长。
        collapsedFavoriteFolders.retainAll(folders)
    }
    if (query.isNotEmpty()) {
        if (favoriteCollapsedBeforeSearch == null) favoriteCollapsedBeforeSearch = collapsedFavoriteFolders.toSet()
        // 搜索命中的文件及其所有父级路径自动展开；用户清除搜索后会恢复原状态。
        favoriteGroups.filter(::matches).flatMap { group ->
            group.folder.split('/').indices.map { index -> group.folder.split('/').take(index + 1).joinToString("/") }
        }.forEach { collapsedFavoriteFolders.remove(it) }
    } else {
        favoriteCollapsedBeforeSearch?.let { previous ->
            collapsedFavoriteFolders.clear()
            collapsedFavoriteFolders.addAll(previous.filter { it in folders })
            favoriteCollapsedBeforeSearch = null
        }
    }
    roots.forEach { root ->
        fun renderFolder(path: String, level: Int) {
            val prefix = "$path/"
            val children = folders.filter { it.startsWith(prefix) && !it.removePrefix(prefix).contains("/") }.map { it.removePrefix(prefix) }.distinct().sorted()
            val groups = favoriteGroups.filter { it.folder == path && matches(it) }
            val matchingDescendants = favoriteGroups.filter { it.folder.startsWith(prefix) && matches(it) }
            if (query.isNotEmpty() && groups.isEmpty() && matchingDescendants.isEmpty()) return
            val collapsed = path in collapsedFavoriteFolders
            val count = if (level == 0) children.size else groups.size
            addTreeHeader(tree, path.substringAfterLast('/'), path, count, collapsed, if (level == 0) rootFolderColor else childFolderColor, level, query)
            if (!collapsed) {
                groups.forEach { addTreeFile(tree, it, level + 1, favoriteFileColor) }
                children.forEach { child -> renderFolder("$path/$child", level + 1) }
            }
        }
        renderFolder(root, 0)
    }
}

private fun MainActivity.addTreeHeader(container: LinearLayout, label: String, folder: String, count: Int, collapsed: Boolean, color: Int, level: Int, query: String) {
    val isRoot = level == 0
    val header = LinearLayout(this).apply {
        gravity = Gravity.CENTER_VERTICAL
        // 文件夹标题保持轻量的树状内容行，避免每一层都像按钮。
        setPadding(dp(if (isRoot) 11 else 26), 0, dp(5), 0)
        // 文件夹本身保持无底色，仅用缩进、颜色和分隔线表达层级；玻璃材质只留给操作按钮。
        setBackgroundColor(Color.TRANSPARENT)
        elevation = 0f
        setOnClickListener {
            val folders = (favoriteFolders + favoriteGroups.map { it.folder }).filter { it.isNotBlank() }.distinct()
            if (collapsed) collapsedFavoriteFolders.remove(folder)
            else collapsedFavoriteFolders.addAll(folders.filter { it == folder || it.startsWith("$folder/") })
            findViewWithTag<TextView>("folderArrow")?.animate()?.rotation(if (collapsed) 90f else 0f)?.setDuration(170)?.start()
            postDelayed({ renderFavoriteTree(query) }, 150)
        }
    }
    val rowHeight = dp(if (isRoot) 48 else 43)
    header.addView(ImageView(this).apply { setImageResource(R.drawable.ic_folder); setColorFilter(if (isRoot) color else secondaryText()); scaleType = ImageView.ScaleType.CENTER_INSIDE }, LinearLayout.LayoutParams(dp(if (isRoot) 27 else 21), rowHeight).apply { setMargins(0, 0, dp(if (isRoot) 8 else 7), 0) })
    // 名称使用固定的层级语义色，数量、箭头和操作入口继续保持弱化。
    val nameView = TextView(this).apply { text = label; textSize = if (isRoot) 18f else 17f; typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL); letterSpacing = -0.01f; gravity = Gravity.CENTER_VERTICAL; includeFontPadding = false; setTextColor(color) }
    header.addView(nameView, LinearLayout.LayoutParams(0, rowHeight, 1f))
    header.setOnLongClickListener {
        performRowLongPressFeedback(header)
        showTreeFolderMenu(nameView, folder, level)
        true
    }
    header.addView(TextView(this).apply { text = "$count"; textSize = 13f; gravity = Gravity.CENTER; includeFontPadding = false; setTextColor(secondaryText()) }, LinearLayout.LayoutParams(dp(28), rowHeight))
    header.addView(TextView(this).apply { tag = "folderArrow"; text = "›"; textSize = 22f; gravity = Gravity.CENTER; includeFontPadding = false; rotation = if (collapsed) 0f else 90f; setTextColor(secondaryText()) }, LinearLayout.LayoutParams(dp(25), rowHeight))
    container.addView(header, LinearLayout.LayoutParams(-1, dp(if (isRoot) 50 else 43)).apply { setMargins(dp(if (isRoot) 0 else 10), 0, dp(if (isRoot) 0 else 4), dp(if (isRoot) 7 else 1)) })
}

private fun MainActivity.showTreeFolderMenu(anchor: View, folder: String, level: Int) {
    val actions = if (level == 0) listOf("新建文件夹", "重命名", "删除") else listOf("重命名", "删除")
    showMaterialDropdown(anchor, actions) { which ->
        when {
            level == 0 && which == 0 -> showSubfolderEditor(folder)
            which == if (level == 0) 1 else 0 -> showFolderEditor(folder) { renamed ->
                favoriteGroups.filter { it.folder == folder || it.folder.startsWith("$folder/") }.forEach { it.folder = if (it.folder == folder) renamed else renamed + it.folder.removePrefix(folder) }
                favoriteFolders.filter { it == folder || it.startsWith("$folder/") }.toList().forEach { old -> favoriteFolders.remove(old); favoriteFolders.add(if (old == folder) renamed else renamed + old.removePrefix(folder)) }
                saveAllFavorites(); render()
            }
            which == if (level == 0) 2 else 1 -> AlertDialog.Builder(this).setTitle("删除文件夹").setMessage("将删除文件夹内的所有收藏，确定继续吗？").setNegativeButton("取消", null).setPositiveButton("删除") { _, _ ->
                favoriteGroups.removeAll { it.folder == folder || it.folder.startsWith("$folder/") }
                favoriteFolders.removeAll { it == folder || it.startsWith("$folder/") }
                saveAllFavorites(); render()
            }.create().also { showIos26Dialog(it) }
        }
    }
}

private fun MainActivity.addTreeFile(container: LinearLayout, group: FavoriteGroup, level: Int, color: Int) {
    val groupItems = group.itemIds.mapNotNull { id -> items.firstOrNull { it.id == id } }
    val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setBackgroundColor(Color.TRANSPARENT); setPadding(dp(if (level == 1) 20 else 28), dp(2), dp(4), dp(2)); setOnClickListener { resultItems = groupItems; showingHistoryResult = false; resultsReturnPage = "favorites"; selectedFavoriteGroup = group; page = "results"; render() } }
    // 收藏文件名固定为绿色，与两级文件夹形成稳定的三级视觉关系。
    val nameView = TextView(this).apply { text = group.name; textSize = 17f; typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL); letterSpacing = -0.01f; gravity = Gravity.CENTER_VERTICAL; includeFontPadding = false; setTextColor(color) }
    row.addView(nameView, LinearLayout.LayoutParams(0, dp(44), 1f))
    row.setOnLongClickListener {
        performRowLongPressFeedback(row)
        showFavoriteFileMenu(nameView, group, groupItems)
        true
    }
    row.addView(TextView(this).apply { text = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(group.savedAt)); textSize = 11f; gravity = Gravity.CENTER_VERTICAL; setTextColor(secondaryText()) }, LinearLayout.LayoutParams(dp(78), dp(44)))
    // 文件为内容层，沿父文件夹缩进并保留平整材质，不再与文件夹头部争夺玻璃层级。
    container.addView(row, LinearLayout.LayoutParams(-1, -2).apply { setMargins(dp(if (level <= 1) 16 else 38), 0, dp(4), dp(6)) })
}

/** 行级长按反馈：短暂压缩后弹性回弹，避免只按编辑图标才能触发。 */
private fun MainActivity.performRowLongPressFeedback(view: View) {
    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    view.animate().cancel()
    view.animate().scaleX(0.985f).scaleY(0.985f).setDuration(70).withEndAction {
        view.animate().scaleX(1f).scaleY(1f)
            .setInterpolator(android.view.animation.OvershootInterpolator(2.2f))
            .setDuration(220).start()
    }.start()
}

private fun MainActivity.showFavoriteFileMenu(anchor: View, group: FavoriteGroup, groupItems: List<CodeItem>) {
    showMaterialDropdown(anchor, listOf("移动", "重命名", "删除")) { which ->
        when (which) {
            0 -> showFavoriteMoveDialog(group)
            1 -> showFavoriteRenameDialog(group)
            2 -> AlertDialog.Builder(this).setTitle("删除收藏").setMessage("确定删除“${group.name}”吗？").setNegativeButton("取消", null).setPositiveButton("删除") { _, _ -> favoriteGroups.removeAll { it.id == group.id }; groupItems.forEach { item -> if (favoriteGroups.none { it.itemIds.contains(item.id) }) item.favorite = false }; if (group.folder !in favoriteFolders) favoriteFolders.add(group.folder); saveAllFavorites(); showFavoriteGroups() }.create().also { showIos26Dialog(it) }
        }
    }
}

internal fun MainActivity.showSubfolderEditor(parent: String, onCreated: ((String) -> Unit)? = null) {
    val input = inputField("文件夹名称")
    val box = LinearLayout(this).apply { setPadding(dp(24), dp(8), dp(24), 0); addView(input, LinearLayout.LayoutParams(-1, dp(50))) }
    val dialog = AlertDialog.Builder(this).setTitle("新建文件夹").setView(box).setNegativeButton("取消", null).setPositiveButton("保存", null).create()
    dialog.setOnShowListener {
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val child = input.text.toString().trim()
            val path = "$parent/$child"
            if (child.isBlank()) toast("请输入文件夹名称")
            else if (child.contains('/')) toast("名称不能包含斜杠")
            else if (path in favoriteFolders) toast("已存在同名文件夹")
            else { favoriteFolders.add(path); saveFavoriteFolders(); dialog.dismiss(); onCreated?.invoke(child) ?: render() }
        }
    }
    showIos26Dialog(dialog)
}

internal fun MainActivity.showFavoriteRenameDialog(group: FavoriteGroup) {
    val input = inputField("收藏文件名", group.name)
    val box = LinearLayout(this).apply { setPadding(dp(24), dp(8), dp(24), 0); addView(input, LinearLayout.LayoutParams(-1, dp(50))) }
    val dialog = AlertDialog.Builder(this).setTitle("重命名收藏").setView(box).setNegativeButton("取消", null).setPositiveButton("保存", null).create()
    dialog.setOnShowListener { dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
        val name = input.text.toString().trim()
        if (name.isBlank()) toast("请输入收藏文件名") else { group.name = name; saveFavoriteGroups(); dialog.dismiss(); showFavoriteGroups() }
    } }
    showIos26Dialog(dialog)
}

internal fun MainActivity.showFavoriteMoveDialog(group: FavoriteGroup) {
    val folders = favoriteFolders.filter { it.isNotBlank() }
    if (folders.isEmpty()) { AlertDialog.Builder(this).setTitle("移动收藏").setMessage("请先创建文件夹").setPositiveButton("确定", null).create().also { showIos26Dialog(it) }; return }
    val spinner = Spinner(this).apply {
        adapter = ArrayAdapter(this@showFavoriteMoveDialog, android.R.layout.simple_spinner_dropdown_item, folders)
        setSelection(folders.indexOf(group.folder).coerceAtLeast(0))
        setBackgroundResource(R.drawable.bg_input)
        setOnTouchListener { view, event ->
            if (event.actionMasked == MotionEvent.ACTION_UP) {
                showMaterialDropdown(view, folders, popupWidth = view.width, selectedIndex = selectedItemPosition) { index ->
                    setSelection(index)
                }
            }
            true
        }
    }
    val box = LinearLayout(this).apply { setPadding(dp(24), dp(8), dp(24), 0); addView(spinner, LinearLayout.LayoutParams(-1, dp(50))) }
    AlertDialog.Builder(this).setTitle("移动收藏").setView(box).setNegativeButton("取消", null).setPositiveButton("移动") { _, _ ->
        group.folder = spinner.selectedItem?.toString() ?: ""
        if (group.folder.isNotBlank() && group.folder !in favoriteFolders) favoriteFolders.add(group.folder)
        saveAllFavorites(); showFavoriteGroups()
    }.create().also { showIos26Dialog(it) }
}

internal fun MainActivity.showFavoriteDetail() {
        val activity = this
        val group = selectedFavoriteGroup ?: run { page = "favorites"; showFavoriteGroups(); return }
        content.removeAllViews()
        addSpaced(sectionTitle(group.name, "${group.folder} · 保存于 ${formatSavedTime(group.savedAt)}"), bottom = 6)
        addSpaced(styleButton(Button(this).apply { text = "返回收藏"; setOnClickListener { page = "favorites"; showFavoriteGroups() } }), bottom = 10)
        val groupItems = group.itemIds.mapNotNull { id -> items.firstOrNull { it.id == id } }
        groupItems.forEach { addCard(it, editable = true) }
        if (groupItems.isEmpty()) { addSpaced(TextView(this).apply { text = "此收藏暂无条码"; textSize = 16f; gravity = Gravity.CENTER; setTextColor(secondaryText()); setPadding(0, dp(32), 0, dp(32)) }, bottom = 0) }
    }


internal fun MainActivity.formatSavedTime(time: Long): String = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(time))


internal fun MainActivity.moveToFolder(item: CodeItem) {
        val activity = this
        val input = EditText(this).apply { hint = "例如：工作、商品、旅行"; setSingleLine(true); setText(item.folder) }
        AlertDialog.Builder(this).setTitle("移动到文件夹").setView(input).setNegativeButton("取消", null).setPositiveButton("保存") { _, _ -> item.folder = input.text.toString().trim().ifEmpty { "默认" }; saveItems(); render() }.create().also { showIos26Dialog(it) }
    }


internal fun MainActivity.confirmClear(favoritesOnly: Boolean) {
        val activity = this
        AlertDialog.Builder(this).setTitle(if (favoritesOnly) "清空收藏" else "清空历史").setMessage(if (favoritesOnly) "确定删除全部收藏吗？" else "仅清空历史记录，收藏内容不会删除。") .setNegativeButton("取消", null).setPositiveButton("删除") { _, _ ->
            if (favoritesOnly) {
                favoriteGroups.clear()
                items.forEach { it.favorite = false; it.folder = "默认" }
                saveAllFavorites()
            } else {
                items.forEach { it.inHistory = false }
                saveItems()
            }
            render()
        }.create().also { showIos26Dialog(it, compact = !favoritesOnly) }
    }


