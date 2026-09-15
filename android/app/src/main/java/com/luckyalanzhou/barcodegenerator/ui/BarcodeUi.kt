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

private val bottomTabIcons = intArrayOf(
    R.drawable.ic_tab_barcode, R.drawable.ic_tab_history, R.drawable.ic_tab_favorite, R.drawable.ic_tab_settings
)
private val bottomTabSelectedIcons = intArrayOf(
    R.drawable.ic_tab_barcode_selected, R.drawable.ic_tab_history_selected,
    R.drawable.ic_tab_favorite_selected, R.drawable.ic_tab_settings_selected
)

/** 使用真实弹簧驱动缩放，不使用 Bounce/OvershootInterpolator。 */
private fun springScale(view: View, start: Float, peak: Float, settle: Float = 1f) {
    val x = SpringAnimation(view, DynamicAnimation.SCALE_X)
    val y = SpringAnimation(view, DynamicAnimation.SCALE_Y)
    fun force(finalPosition: Float) = SpringForce(finalPosition).apply {
        // 低阻尼 + 中低刚度，产生轻微果冻回弹，但不会长时间晃动。
        dampingRatio = SpringForce.DAMPING_RATIO_LOW_BOUNCY
        // AndroidX 没有 MEDIUM_LOW 常量，600f 是 LOW(200) 与 MEDIUM(1500) 之间的中低刚度。
        stiffness = 600f
    }
    x.spring = force(peak)
    y.spring = force(peak)
    x.addEndListener { _, canceled, _, _ ->
        if (!canceled && settle != peak) {
            SpringAnimation(view, DynamicAnimation.SCALE_X).apply { spring = force(settle); start() }
            SpringAnimation(view, DynamicAnimation.SCALE_Y).apply { spring = force(settle); start() }
        }
    }
    x.cancel(); y.cancel()
    view.scaleX = start
    view.scaleY = start
    x.start(); y.start()
}


internal fun MainActivity.updateTopTabSelection() {
    val selected = tabPageIndex()
    // 浅色模式使用清晰的蓝色强调色，而不是深灰色；选中后不应显得更暗。
    val selectedColor = if (isDark()) 0xfff4f7ff.toInt() else 0xff246fc4.toInt()
    val unselectedColor = if (isDark()) 0xffc4cada.toInt() else 0xff64748b.toInt()
    topTabButtons.forEach { tab ->
        val isSelected = tab.tag == selected
        tab.findViewWithTag<TextView>("tabLabel")?.setTextColor(if (isSelected) selectedColor else unselectedColor)
        tab.findViewWithTag<SettingsTabIconView>("settingsTabIcon")?.apply {
            setTint(if (isSelected) selectedColor else unselectedColor)
            setSelectedState(isSelected)
            alpha = if (isSelected) 1f else 0.82f
            springScale(this, start = if (isSelected) 0.86f else scaleX, peak = if (isSelected) 1.12f else 1f, settle = if (isSelected) 1.06f else 1f)
            translationY = if (isSelected) -dp(1).toFloat() else 0f
        } ?: tab.findViewWithTag<HistoryTabIconView>("historyTabIcon")?.apply {
            setTint(if (isSelected) selectedColor else unselectedColor)
            setSelectedState(isSelected)
            alpha = if (isSelected) 1f else 0.82f
            springScale(this, start = if (isSelected) 0.86f else scaleX, peak = if (isSelected) 1.12f else 1f, settle = if (isSelected) 1.06f else 1f)
            translationY = if (isSelected) -dp(1).toFloat() else 0f
        } ?: tab.findViewWithTag<ImageView>("tabIcon")?.apply {
            val iconResource = if (isSelected) bottomTabSelectedIcons[tab.tag as Int] else bottomTabIcons[tab.tag as Int]
            setImageResource(iconResource)
            setColorFilter(if (isSelected) selectedColor else unselectedColor)
            // 图标切换采用“收缩-注入-回弹”：线性图标切换为面性图标时不会闪现。
            if (isSelected) {
                alpha = 1f
                springScale(this, start = 0.86f, peak = 1.12f, settle = 1.06f)
            } else {
                alpha = 0.82f
                springScale(this, start = scaleX, peak = 1f)
            }
            translationY = if (isSelected) -dp(1).toFloat() else 0f
        }
        // 选中项自身抬升，玻璃表面在图文下方绘制，不会遮挡图标或文字。
        tab.setBackgroundResource(if (isSelected && !tabGlassDragActive) R.drawable.bg_tab_selected else R.drawable.bg_tab)
        // 保持很轻的悬浮距离，避免变成厚重的实体按钮。
        tab.elevation = if (isSelected && !tabGlassDragActive) dp(1).toFloat() else 0f
        tab.translationZ = 0f
        if (isSelected && !tabGlassDragActive) {
            // 激活背景轻微压缩后拉伸，模拟果冻吸附到当前 Tab 的回弹。
            springScale(tab, start = 0.97f, peak = 1.035f)
        }
    }
}

internal fun MainActivity.isDark() = style.colorScheme == "dark" || (style.colorScheme == "system" && (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES)



// iOS 18 风格使用清晰的系统分组背景，避免全局过度透明。
internal fun MainActivity.appBackground() = if (isDark()) 0xff000000.toInt() else 0xfff2f2f7.toInt()
internal fun MainActivity.primaryText() = if (isDark()) 0xfff2f4f7.toInt() else 0xff172033.toInt()


internal fun MainActivity.secondaryText() = if (isDark()) 0xffc5cedb.toInt() else 0xff667085.toInt()


internal fun MainActivity.nextItemId(): Long = (items.maxOfOrNull { it.id } ?: 0L) + 1L


internal fun MainActivity.nextGroupId(): Long = (favoriteGroups.maxOfOrNull { it.id } ?: 0L) + 1L


internal fun MainActivity.inputField(hint: String, value: String = "") = EditText(this).apply {
        this.hint = hint; setText(value); setSingleLine(true); minHeight = dp(48)
        typeface = Typeface.create("sans-serif", Typeface.NORMAL); textSize = 16f; includeFontPadding = false
        gravity = Gravity.CENTER_VERTICAL
        setTextColor(primaryText()); setHintTextColor(secondaryText())
        setBackgroundResource(R.drawable.bg_input); setPadding(dp(14), 0, dp(14), 0)
    }


internal fun MainActivity.openSettings() {
        val activity = this
        // 已在设置页时只同步视觉状态；不能再次 render，否则某些导航实现会回调选中事件形成递归。
        if (page == "settings") {
            updateTopTabSelection()
            return
        }
        // 分享房间已在离页时关闭，不能把它作为返回页；否则返回设置会自动新建房间。
        settingsReturnPage = if (page == "lanShare") "generate" else page
        if (page == "lanShare") closeLanShare()
        page = "settings"
        render()
    }


internal fun MainActivity.switchTopTabBySwipe(deltaX: Float) {
        val activity = this
        if (kotlin.math.abs(deltaX) < dp(42).toFloat()) return
        val current = tabPageIndex()
        val next = (current + if (deltaX < 0) 1 else -1).coerceIn(0, 3)
        val target = listOf("generate", "history", "favorites", "settings")[next]
        if (next != current) pendingPageTransitionDirection = if (next > current) 1 else -1
        if (target == "settings") settingsReturnPage = if (page == "lanShare") "generate" else page
        if (page == "lanShare" && target != "lanShare") closeLanShare()
        page = target
        render()
    }



internal fun MainActivity.styleButton(button: Button, primary: Boolean = false) = button.apply {
        background = if (primary) glassPrimaryButtonBackground() else glassButtonBackground()
        // 以 Android 系统字实现接近 iOS 的清晰、略带强调的按钮文字，不嵌入受限字体。
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        textSize = 15f
        letterSpacing = -0.01f
        includeFontPadding = false
        gravity = Gravity.CENTER
        isAllCaps = false
        // 约 2mm 的文字外边距（8dp）；避免按钮边框远大于文字。
        minHeight = dp(40)
        minimumHeight = dp(40)
        setPadding(dp(8), dp(6), dp(8), dp(6))
        setTextColor(if (primary) Color.WHITE else if (isDark()) 0xffd7e3f5.toInt() else 0xff2453a6.toInt())
        stateListAnimator = null
        elevation = 0f
        // 所有通用按钮共享轻微压下与回弹，模拟玻璃受触时的柔软反馈。
        setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> view.animate().scaleX(0.975f).scaleY(0.975f).setDuration(90).start()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> view.animate().scaleX(1f).scaleY(1f).setDuration(180).setInterpolator(OvershootInterpolator(0.7f)).start()
            }
            false
        }
    }

internal fun MainActivity.glassButtonBackground() = GradientDrawable().apply {
    shape = GradientDrawable.RECTANGLE
    cornerRadius = dp(14).toFloat()
    setColor(if (isDark()) 0xff2c2c2e.toInt() else 0xffffffff.toInt())
    setStroke(dp(1), if (isDark()) 0xff3a3a3c.toInt() else 0xffd8d8dc.toInt())
}

internal fun MainActivity.glassPrimaryButtonBackground() = GradientDrawable().apply {
    shape = GradientDrawable.RECTANGLE
    cornerRadius = dp(14).toFloat()
    setColor(if (isDark()) 0xff0a84ff.toInt() else 0xff007aff.toInt())
    setStroke(dp(1), if (isDark()) 0xff4da3ff.toInt() else 0xff007aff.toInt())
}

internal fun MainActivity.applyIos26DialogStyle(dialog: AlertDialog) {
    dialog.window?.setDimAmount(if (isDark()) 0.48f else 0.34f)
    dialog.window?.setBackgroundDrawable(GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dp(20).toFloat()
        setColor(if (isDark()) 0xff1c1c1e.toInt() else 0xffffffff.toInt())
        setStroke(dp(1), if (isDark()) 0xff3a3a3c.toInt() else 0xffd8d8dc.toInt())
    })
    dialog.window?.decorView?.elevation = dp(6).toFloat()
    val actionColor = if (isDark()) 0xffa9c4ff.toInt() else 0xff2166d1.toInt()
    val dialogTitleId = resources.getIdentifier("alertTitle", "id", "android")
    dialog.findViewById<TextView>(dialogTitleId)?.apply {
        textSize = 20f
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        letterSpacing = -0.015f
        includeFontPadding = false
    }
    dialog.findViewById<TextView>(android.R.id.message)?.apply {
        textSize = 15f
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        setLineSpacing(dp(2).toFloat(), 1f)
        includeFontPadding = false
    }
    dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(actionColor)
    dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(if (isDark()) 0xffc4cada.toInt() else 0xff667085.toInt())
    dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.setTextColor(actionColor)
    listOf(AlertDialog.BUTTON_POSITIVE, AlertDialog.BUTTON_NEGATIVE, AlertDialog.BUTTON_NEUTRAL).forEach { which ->
        dialog.getButton(which)?.apply {
            textSize = 15f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            letterSpacing = -0.01f
            isAllCaps = false
            // Android 默认按钮有较大的最小宽度；清除后让可见边框贴近文字，
            // 同时保留整块按钮的点击区域。
            minWidth = 0
            minimumWidth = 0
            minHeight = dp(36)
            minimumHeight = dp(36)
            setPadding(dp(8), dp(4), dp(8), dp(4))
            // 标准弹窗按钮也使用统一的轻量玻璃外框；可见边框与整块点击区域一致。
            background = glassButtonBackground().apply { cornerRadius = dp(12).toFloat() }
            stateListAnimator = null
            elevation = 0f
        }
    }
    // 系统按钮栏默认会把相邻按钮贴得过近；只在同一弹窗存在多个按钮时增加间距。
    val dialogButtons = listOf(AlertDialog.BUTTON_NEGATIVE, AlertDialog.BUTTON_NEUTRAL, AlertDialog.BUTTON_POSITIVE)
        .mapNotNull { dialog.getButton(it)?.takeIf { button -> button.visibility == View.VISIBLE } }
    if (dialogButtons.size > 1) {
        dialogButtons.forEachIndexed { index, button ->
            (button.layoutParams as? ViewGroup.MarginLayoutParams)?.apply {
                marginStart = if (index == 0) 0 else dp(6)
                marginEnd = if (index == dialogButtons.lastIndex) 0 else dp(6)
                button.layoutParams = this
            }
        }
    }
    // 统一给按钮栏增加底部留白，让按钮不要贴近弹窗下边框；按钮本身的完整点击区域保持不变。
    val buttonPanelId = resources.getIdentifier("buttonPanel", "id", "android")
    dialog.findViewById<View>(buttonPanelId)?.let { panel ->
        val bottomInset = dp(8)
        panel.setPadding(panel.paddingLeft, panel.paddingTop, panel.paddingRight, bottomInset)
    }
}

internal fun MainActivity.showIos26Dialog(dialog: AlertDialog, compact: Boolean = false): AlertDialog {
    dialog.show()
    applyIos26DialogStyle(dialog)
    // 统一限制弹窗宽度：手机上保持适度留白，大屏上不铺满；同时保留输入和长文本所需的最小宽度。
    val screenWidth = resources.displayMetrics.widthPixels
    val preferredWidth = (screenWidth * if (compact) 0.82f else 0.88f).roundToInt()
    val availableWidth = (screenWidth - dp(24)).coerceAtLeast(1)
    val minWidth = dp(280).coerceAtMost(availableWidth)
    val maxWidth = dp(420).coerceAtMost(availableWidth)
    val dialogMaxWidth = if (compact) dp(360) else dp(400)
    val upperWidth = dialogMaxWidth.coerceAtMost(maxWidth)
    val width = preferredWidth.coerceIn(minWidth.coerceAtMost(upperWidth), upperWidth)
    dialog.window?.setLayout(width, WindowManager.LayoutParams.WRAP_CONTENT)
    dialog.window?.setGravity(Gravity.CENTER)
    return dialog
}


internal fun MainActivity.sectionTitle(text: String, subtitle: String? = null): LinearLayout {
        val activity = this
        return LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(activity.dp(4), activity.dp(10), activity.dp(4), activity.dp(12))
            addView(TextView(activity).apply {
                this.text = text; textSize = 22f; typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                letterSpacing = -0.02f; includeFontPadding = false; setLineSpacing(activity.dp(2).toFloat(), 1f); setTextColor(activity.primaryText())
            })
            subtitle?.let { addView(TextView(activity).apply { this.text = it; textSize = 13f; includeFontPadding = false; setLineSpacing(activity.dp(2).toFloat(), 1f); setTextColor(activity.secondaryText()); setPadding(0, activity.dp(5), 0, 0) }) }
        }
    }


internal fun MainActivity.contentCard(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(14), dp(10), dp(14), dp(10))
        // 所有页面卡片统一使用动态液态玻璃，避免设置页仍显示固定浅色卡片。
        background = liquidGlassCard()
        elevation = 0f
        clipToOutline = true
}


internal fun MainActivity.addSpaced(view: View, top: Int = 0, bottom: Int = 10) {
        content.addView(view, LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, dp(top), 0, dp(bottom)) })
    }


internal fun MainActivity.buildShell() {
        val activity = this
        val d = resources.displayMetrics.density
        val p = (18 * d).toInt()
        val statusBarId = resources.getIdentifier("status_bar_height", "dimen", "android")
        val statusBar = if (statusBarId > 0) resources.getDimensionPixelSize(statusBarId) else 0
        // 自绘界面没有自动处理系统栏；底部也要避开三键/手势导航区域，不能让 Tab 压在系统导航栏上。
        val navigationBarId = resources.getIdentifier("navigation_bar_height", "dimen", "android")
        val navigationBar = if (navigationBarId > 0) resources.getDimensionPixelSize(navigationBarId) else 0
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(p, p + statusBar, p, dp(10) + navigationBar); gravity = Gravity.CENTER_HORIZONTAL }
        rootLayout = root
        root.setBackgroundColor(appBackground())
        val title = TextView(this).apply { text = "条码生成器"; textSize = 25f; gravity = Gravity.CENTER_VERTICAL; typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL); letterSpacing = -0.025f; includeFontPadding = false; setTextColor(primaryText()) }
        appTitle = title
        val header = LinearLayout(this).apply {
            // 图标和标题作为一个整体居中，而不是让标题从容器左侧起排。
            gravity = Gravity.CENTER
            setPadding(dp(4), dp(2), dp(4), dp(9))
            addView(ImageView(this@buildShell).apply {
                setImageResource(R.drawable.ic_tab_barcode)
                setColorFilter(if (isDark()) 0xffd9e6ff.toInt() else 0xff2166d1.toInt())
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                // 顶部标识仅保留符号本身，不再使用圆形玻璃底座。
                setPadding(dp(1), dp(1), dp(1), dp(1))
                contentDescription = "条码生成器"
            }, LinearLayout.LayoutParams(dp(42), dp(42)).apply { setMargins(0, 0, dp(9), 0) })
            addView(title, LinearLayout.LayoutParams(-2, dp(46)))
        }
        appHeader = header
        root.addView(header, LinearLayout.LayoutParams(-1, dp(60)))
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val pageScrollView = object : ScrollView(activity) {
            private fun settingsNeedsScroll(): Boolean {
                val viewportHeight = height - paddingTop - paddingBottom
                return viewportHeight > 0 && content.measuredHeight > viewportHeight
            }
            override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
                // 设置页内容完整时保持固定；只有超出可视区域才接管手势允许滚动。
                return if (page == "generate" || (page == "settings" && !settingsNeedsScroll())) false else super.onInterceptTouchEvent(event)
            }
            override fun onTouchEvent(event: MotionEvent): Boolean {
                return if (page == "settings" && !settingsNeedsScroll()) false else super.onTouchEvent(event)
            }
        }.also { pageScroll = it }.apply {
            addView(content); isFillViewport = true; clipToPadding = false
            setPadding(0, 0, 0, dp(16))
        }
        root.addView(pageScrollView, LinearLayout.LayoutParams(-1, 0, 1f))
        lanShareComposer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            visibility = View.GONE
        }
        root.addView(lanShareComposer, LinearLayout.LayoutParams(-1, dp(72)).apply { setMargins(0, dp(4), 0, dp(4)) })
        val nav = FrameLayout(this).apply {
            setPadding(dp(4), dp(4), dp(4), dp(4))
            // 不再绘制底部 Tab 的整体大外框，视觉重点仅留给选中项的玻璃胶囊。
            background = null
            elevation = 0f
            clipChildren = false
        }
        topNav = nav
        val tabStrip = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            elevation = dp(1).toFloat()
            clipChildren = false
            clipToPadding = false
        }
        // 拖动时使用这一层连续跟随手指的玻璃，而非在各 Tab 之间跳换背景。
        val dragGlass = View(this).apply {
            setBackgroundResource(R.drawable.bg_tab_selected)
            visibility = View.GONE
            isClickable = false
            isFocusable = false
            elevation = 0f
        }
        tabGlassDragOverlay = dragGlass
        nav.addView(dragGlass, FrameLayout.LayoutParams(0, 0))
        val tabPages = listOf("generate", "history", "favorites", "settings")
        val tabData = listOf(
            Triple("生成", R.drawable.ic_tab_barcode, "生成条码"),
            Triple("历史", R.drawable.ic_tab_history, "历史记录"),
            Triple("收藏", R.drawable.ic_tab_favorite, "收藏夹"),
            Triple("设置", R.drawable.ic_tab_settings, "设置")
        )
        fun activateTab(index: Int) {
            if (index !in tabPages.indices) return
            val current = tabPageIndex()
            if (index != current) pendingPageTransitionDirection = if (index > current) 1 else -1
            if (index == 3) openSettings()
            else if (page != tabPages[index]) {
                if (page == "lanShare") closeLanShare()
                if (index == 0) {
                    // 从收藏/收藏结果页切回普通生成页时清除编辑收藏上下文，避免新结果返回收藏页。
                    selectedFavoriteGroup = null
                    resultsReturnPage = "generate"
                    showingHistoryResult = false
                }
                page = tabPages[index]
                render()
            }
            else updateTopTabSelection()
        }
        var touchDownX = 0f
        var isDraggingTab = false
        var lastDraggedTab = -1
        // 玻璃经过图标与文字时，内容本身也以很小的比例被“折射放大”。
        // 直接缩放实际 Tab 内容，比只移动底层背景更接近液态玻璃的局部透镜效果。
        fun updateTabGlassMagnification(rawX: Float) {
            val stripLocation = IntArray(2)
            tabStrip.getLocationOnScreen(stripLocation)
            val pointerX = rawX - stripLocation[0]
            topTabButtons.forEach { tab ->
                if (tab.width <= 0) return@forEach
                val tabCenter = tab.left + tab.width / 2f
                val proximity = (1f - kotlin.math.abs(pointerX - tabCenter) / tab.width)
                    .coerceIn(0f, 1f)
                val lensStrength = proximity * proximity
                tab.scaleX = 1f + 0.075f * lensStrength
                tab.scaleY = 1f + 0.075f * lensStrength
                tab.translationY = -dp(1).toFloat() * lensStrength
                tab.elevation = dp(2).toFloat() * lensStrength
            }
        }
        fun clearTabGlassMagnification(animated: Boolean) {
            topTabButtons.forEach { tab ->
                tab.animate().cancel()
                if (animated) {
                    tab.animate()
                        .scaleX(1f).scaleY(1f).translationY(0f)
                        .setDuration(160)
                        .setInterpolator(OvershootInterpolator(0.55f))
                        .start()
                } else {
                    tab.scaleX = 1f
                    tab.scaleY = 1f
                    tab.translationY = 0f
                }
            }
        }
        fun moveDragGlass(rawX: Float) {
            val overlay = tabGlassDragOverlay ?: return
            val tabWidth = topTabButtons.firstOrNull()?.width ?: return
            if (tabWidth <= 0 || tabStrip.height <= 0) return
            val params = overlay.layoutParams as FrameLayout.LayoutParams
            if (params.width != tabWidth || params.height != tabStrip.height) {
                params.width = tabWidth
                params.height = tabStrip.height
                overlay.layoutParams = params
            }
            val navLocation = IntArray(2)
            nav.getLocationOnScreen(navLocation)
            val desiredLeft = (rawX - navLocation[0] - tabWidth / 2f)
                .coerceIn(tabStrip.left.toFloat(), (tabStrip.right - tabWidth).toFloat())
            overlay.translationX = desiredLeft - overlay.left
            // overlay 高度与 tabStrip 相同；直接对齐顶部，避免首次 layout 前高度为 0 时发生纵向偏移。
            overlay.translationY = tabStrip.top.toFloat() - overlay.top
            // 高光在每个 Tab 的中心最亮、跨越边界时略微变柔，模拟玻璃随内容流动的反射变化。
            val normalizedCenter = (desiredLeft + tabWidth / 2f) / tabWidth
            val distanceToCenter = kotlin.math.abs(normalizedCenter - normalizedCenter.roundToInt())
            overlay.alpha = 0.84f + 0.16f * (1f - (distanceToCenter * 2f).coerceIn(0f, 1f))
            overlay.visibility = View.VISIBLE
            updateTabGlassMagnification(rawX)
        }
        fun finishDragGlass(withBounce: Boolean) {
            val overlay = tabGlassDragOverlay
            val selectedTab = topTabButtons.getOrNull(tabPageIndex())
            if (withBounce && overlay?.visibility == View.VISIBLE && selectedTab != null && overlay.width > 0) {
                // 松手时吸附到目标项，使用轻微过冲的果冻回弹；结束后再固化为静态选中态。
                val targetX = (tabStrip.left + selectedTab.left - overlay.left).toFloat()
                overlay.animate().cancel()
                overlay.animate()
                    .translationX(targetX)
                    .alpha(1f)
                    .setDuration(240)
                    .setInterpolator(OvershootInterpolator(1.15f))
                    .withEndAction {
                        tabGlassDragActive = false
                        overlay.visibility = View.GONE
                        clearTabGlassMagnification(animated = true)
                        updateTopTabSelection()
                    }
                    .start()
            } else {
                tabGlassDragActive = false
                overlay?.visibility = View.GONE
                clearTabGlassMagnification(animated = false)
                updateTopTabSelection()
            }
        }
        topTabButtons.clear()
        tabData.forEachIndexed { index, (label, icon, description) ->
            val button = LinearLayout(this).apply {
                tag = index; orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
                setPadding(0, dp(3), 0, dp(3))
                isClickable = true; isFocusable = true; contentDescription = description
                // 原生 Ripple 只作为轻触反馈，范围裁剪在当前 Tab 内，不改变底部布局。
                foreground = RippleDrawable(
                    ColorStateList.valueOf(if (isDark()) 0x336f9fff else 0x244080c8),
                    null,
                    GradientDrawable().apply { shape = GradientDrawable.RECTANGLE; cornerRadius = dp(23).toFloat(); setColor(Color.WHITE) }
                )
                setOnClickListener { activateTab(index) }
                setOnTouchListener { view, event ->
                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN -> {
                            touchDownX = event.rawX
                            isDraggingTab = false
                            lastDraggedTab = index
                            tabGlassDragActive = true
                            updateTopTabSelection()
                            moveDragGlass(event.rawX)
                            true
                        }
                        MotionEvent.ACTION_MOVE -> {
                            moveDragGlass(event.rawX)
                            if (kotlin.math.abs(event.rawX - touchDownX) >= dp(8)) isDraggingTab = true
                            if (isDraggingTab) {
                                val stripLocation = IntArray(2)
                                tabStrip.getLocationOnScreen(stripLocation)
                                val localX = event.rawX - stripLocation[0]
                                val target = topTabButtons.indexOfFirst { localX >= it.left && localX < it.right }
                                if (target >= 0 && target != lastDraggedTab) {
                                    lastDraggedTab = target
                                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                    activateTab(target)
                                }
                            }
                            true
                        }
                        MotionEvent.ACTION_UP -> {
                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            if (isDraggingTab) view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            finishDragGlass(isDraggingTab)
                            if (!isDraggingTab) view.performClick()
                            true
                        }
                        MotionEvent.ACTION_CANCEL -> { finishDragGlass(false); true }
                        else -> true
                    }
                }
            }
            button.addView(if (index == 1) {
                HistoryTabIconView(this)
            } else if (index == 3) {
                SettingsTabIconView(this)
            } else {
                ImageView(this).apply {
                    tag = "tabIcon"; setImageResource(icon); scaleType = ImageView.ScaleType.CENTER_INSIDE
                    setColorFilter(if (isDark()) 0xffc4cada.toInt() else 0xff64748b.toInt())
                }
            }, LinearLayout.LayoutParams(-1, dp(23)))
            button.addView(TextView(this).apply {
                tag = "tabLabel"; text = label; textSize = 12f; gravity = Gravity.CENTER
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL); letterSpacing = -0.01f
                includeFontPadding = false; setTextColor(if (isDark()) 0xffc4cada.toInt() else 0xff64748b.toInt())
            }, LinearLayout.LayoutParams(-1, dp(19)))
            topTabButtons.add(button)
            tabStrip.addView(button, LinearLayout.LayoutParams(0, -1, 1f))
        }
        nav.addView(tabStrip, FrameLayout.LayoutParams(-1, -1))
        root.addView(nav, LinearLayout.LayoutParams(-1, dp(72)).apply { setMargins(dp(12), 0, dp(12), 0) })
        updateTopTabSelection()
        setContentView(root)
    }


internal fun MainActivity.render() {
        // setContentView / Tab 选中同步期间可能触发同一监听器；忽略嵌套刷新以断开递归链。
        if (isRenderingUi) return
        isRenderingUi = true
        try {
        val activity = this
        syncBarcodeDisplaySettings(page == "results")
        updateTopTabSelection()
        // 顶部标题随当前 Tab 同步更新，并参与下方统一的页面过渡动画。
        appTitle.text = when (page) {
            "history" -> "历史记录"
            "favorites", "favoriteDetail" -> "收藏"
            "settings" -> "设置"
            "betaTestCenter" -> "Beta 测试中心"
            "lanShare" -> "局域网分享"
            else -> "条码生成器"
        }
        // 所有页面统一使用纯文字居中标题，不显示标题前的图标。
        appHeader.getChildAt(0)?.visibility = View.GONE
        showAppChrome(page !in listOf("results", "favoriteDetail", "lanShare", "betaTestCenter"))
        when (page) { "history" -> content.post { showList(false) }; "favorites" -> showFavoriteGroups(); "favoriteDetail" -> showFavoriteDetail(); "results" -> showResults(); "settings" -> showSettings(); "lanShare" -> showLanShare(); "betaTestCenter" -> renderBetaTestCenterPage(); else -> showGenerate() }
        content.clearAnimation()
        content.alpha = 1f
        // 设置项触发重绘时可能打断上一次上弹动画；先清除残留的属性动画状态，避免整页持续下移。
        content.translationX = 0f
        content.translationY = 0f
        content.scaleX = 1f
        content.scaleY = 1f
        runCatching {
            appHeader.animate().cancel()
            appHeader.alpha = 1f
            appHeader.translationX = 0f
            appHeader.translationY = 0f
            appHeader.scaleX = 1f
            appHeader.scaleY = 1f
        }
        val transitionDirection = pendingPageTransitionDirection
        pendingPageTransitionDirection = 0
        if (transitionDirection != 0 && page in listOf("generate", "history", "favorites", "settings")) {
            // 页面从底部 Tab 上方短距离浮起，像玻璃面板被轻轻托起，避免整屏横向飞入。
            content.animate().cancel()
            content.translationX = 0f
            content.translationY = dp(26).toFloat()
            content.scaleX = 0.985f
            content.scaleY = 0.985f
            content.alpha = 0.78f
            content.animate()
                .translationX(0f).translationY(0f)
                .scaleX(1f).scaleY(1f)
                .alpha(1f)
                .setDuration(240)
                .setInterpolator(android.view.animation.DecelerateInterpolator(1.65f))
                .start()
            // 顶部标题与页面内容同步上弹，避免标题停在原位产生割裂感。
            appHeader.animate().cancel()
            appHeader.translationY = dp(14).toFloat()
            appHeader.scaleX = 0.99f
            appHeader.scaleY = 0.99f
            appHeader.alpha = 0.82f
            appHeader.animate()
                .translationY(0f).scaleX(1f).scaleY(1f).alpha(1f)
                .setDuration(240)
                .setInterpolator(android.view.animation.DecelerateInterpolator(1.65f))
                .start()
        }
        } finally {
            isRenderingUi = false
        }
    }


internal fun MainActivity.showAppChrome(visible: Boolean) {
        val activity = this
        runCatching { appHeader.visibility = if (visible) View.VISIBLE else View.GONE }
        runCatching { topNav.visibility = if (visible) View.VISIBLE else View.GONE }
        runCatching { pageScroll?.isVerticalScrollBarEnabled = false; pageScroll?.overScrollMode = View.OVER_SCROLL_NEVER; pageScroll?.isEnabled = visible || page == "lanShare" }
        runCatching { lanShareComposer?.visibility = if (!visible && page == "lanShare") View.VISIBLE else View.GONE }
        if (!visible) runCatching { rootLayout.setBackgroundColor(appBackground()) }
        if (visible) runCatching {
            content.setPadding(0, 0, 0, 0)
            content.setBackgroundColor(Color.TRANSPARENT)
        }
    }


internal fun MainActivity.showMaterialDropdown(
    anchor: View,
    options: List<String>,
    popupWidth: Int? = null,
    selectedIndex: Int = -1,
    // 所有下拉菜单均从触发控件下方展开；空间不足时由菜单自身滚动，
    // 不将锚点上移，也不翻转到控件上方。
    forceBelowAnchor: Boolean = true,
    onSelected: (Int) -> Unit
) {
    val menu = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(2), dp(2), dp(2), dp(2))
    }
    var popup: PopupWindow? = null
    options.forEachIndexed { index, label ->
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), 0, dp(16), 0)
            setOnClickListener {
                onSelected(index)
                popup?.dismiss()
            }
        }
        row.addView(TextView(this).apply {
            text = label
            textSize = 15f
            gravity = Gravity.CENTER_VERTICAL
            setSingleLine(true)
            setTextColor(primaryText())
        }, LinearLayout.LayoutParams(0, dp(40), 1f))
        row.addView(TextView(this).apply {
            text = if (index == selectedIndex) "✓" else ""
            textSize = 18f
            gravity = Gravity.CENTER
            setTextColor(if (isDark()) 0xffb8c9ff.toInt() else 0xff367be8.toInt())
            setPadding(dp(8), 0, 0, 0)
        }, LinearLayout.LayoutParams(dp(28), dp(40)))
        // 所有选项使用完全一致的 40dp 行高，不再通过行上下外边距制造不一致的空隙。
        menu.addView(row, LinearLayout.LayoutParams(-1, dp(40)))
        if (index < options.lastIndex) menu.addView(View(this).apply { setBackgroundColor(if (isDark()) 0x33ffffff else 0x33475b7a) }, LinearLayout.LayoutParams(-1, dp(1)).apply { setMargins(dp(16), 0, dp(16), 0) })
    }
    val frame = Rect()
    anchor.getWindowVisibleDisplayFrame(frame)
    val metrics = resources.displayMetrics
    val maxWidth = (metrics.widthPixels - dp(32)).coerceAtLeast(dp(1))
    val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 15f * resources.displayMetrics.scaledDensity }
    val measuredContentWidth = (options.maxOfOrNull { labelPaint.measureText(it) } ?: 0f).roundToInt() + dp(16) * 2 + dp(28) + dp(4)
    val width = maxOf(anchor.width, popupWidth ?: 0, measuredContentWidth, dp(150)).coerceAtMost(maxWidth)
    // 高度同时包含选项之间的分割线；最后一项下方不增加分割线高度，避免被窗口裁切。
    val contentHeight = options.size * dp(40) + (options.size - 1).coerceAtLeast(0) * dp(1) + dp(4)
    val location = IntArray(2)
    anchor.getLocationOnScreen(location)
    val below = frame.bottom - (location[1] + anchor.height) - dp(6)
    val above = location[1] - frame.top - dp(6)
    // 右边界与触发控件右边界对齐；宽度不足时才按屏幕边距收缩。
    val desiredRight = location[0] + anchor.width
    val desiredLeft = (desiredRight - width).coerceIn(frame.left + dp(16), frame.right - width - dp(16))
    val opensBelow = forceBelowAnchor || below >= dp(48) || below >= above
    // 文件夹菜单必须保持在控件下方：空间不足时限制窗口高度并让选项在窗口内滚动，
    // 绝不能为了完整显示选项而把锚点抬高或翻转到控件上方。
    val height = if (forceBelowAnchor) contentHeight.coerceAtMost(below.coerceAtLeast(dp(48))) else contentHeight
    val popupContent: View = if (height < contentHeight) {
        ScrollView(this).apply {
            isFillViewport = false
            isVerticalScrollBarEnabled = true
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            addView(menu, FrameLayout.LayoutParams(-1, -2))
        }
    } else menu
    popup = PopupWindow(popupContent, width, height, true).apply {
        setBackgroundDrawable(getDrawable(R.drawable.bg_popup))
        isOutsideTouchable = true
        isFocusable = true
        isClippingEnabled = true
        elevation = dp(6).toFloat()
    }
    if (forceBelowAnchor) {
        // 弹窗内的 View 使用屏幕坐标会产生偏差；由系统直接相对控件定位，确保紧贴“选择文件夹”项的下边缘。
        popup.showAsDropDown(anchor, desiredLeft - location[0], dp(6))
    } else if (opensBelow) {
        popup.showAtLocation(anchor, Gravity.TOP or Gravity.START, desiredLeft, location[1] + anchor.height + dp(6))
    } else {
        popup.showAtLocation(anchor, Gravity.TOP or Gravity.START, desiredLeft, location[1] - height - dp(6))
    }
    // 所有下拉菜单统一从锚点附近轻微放大展开，保持玻璃面板的连续感。
    popupContent.apply {
        alpha = 0f
        scaleX = 0.94f
        scaleY = 0.94f
        translationY = -dp(4).toFloat()
        post {
            pivotX = width / 2f
            pivotY = 0f
            animate().alpha(1f).scaleX(1f).scaleY(1f).translationY(0f)
                .setDuration(180)
                .setInterpolator(android.view.animation.DecelerateInterpolator(1.45f))
                .start()
        }
    }
}

internal fun MainActivity.showFormatPopup(anchor: View) = showMaterialDropdown(anchor, formats.map { it.first }, popupWidth = anchor.width, selectedIndex = formatSpinner.selectedItemPosition) { index ->
    formatSpinner.setSelection(index)
}

/** 锚定在设置项下方的多选下拉菜单；每项独立切换，点击外部关闭。 */
internal fun MainActivity.showMaterialMultiDropdown(
    anchor: View,
    options: List<String>,
    selected: Set<Int>,
    onChanged: (Set<Int>) -> Unit
) {
    val menu = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(2), dp(2), dp(2), dp(2))
    }
    var popup: PopupWindow? = null
    val selectedItems = selected.toMutableSet()
    options.forEachIndexed { index, label ->
        val check = TextView(this).apply {
            text = if (index in selectedItems) "✓" else ""
            textSize = 18f
            gravity = Gravity.CENTER
            setTextColor(if (isDark()) 0xffb8c9ff.toInt() else 0xff367be8.toInt())
            setPadding(dp(8), 0, 0, 0)
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), 0, dp(16), 0)
            setOnClickListener {
                if (!selectedItems.add(index)) selectedItems.remove(index)
                check.text = if (index in selectedItems) "✓" else ""
                onChanged(selectedItems.toSet())
            }
        }
        row.addView(TextView(this).apply {
            text = label
            textSize = 15f
            gravity = Gravity.CENTER_VERTICAL
            setSingleLine(true)
            setTextColor(primaryText())
        }, LinearLayout.LayoutParams(0, dp(40), 1f))
        row.addView(check, LinearLayout.LayoutParams(dp(28), dp(40)))
        menu.addView(row)
        if (index < options.lastIndex) menu.addView(View(this).apply { setBackgroundColor(if (isDark()) 0x33ffffff else 0x33475b7a) }, LinearLayout.LayoutParams(-1, dp(1)).apply { setMargins(dp(16), 0, dp(16), 0) })
    }
    val frame = Rect()
    anchor.getWindowVisibleDisplayFrame(frame)
    val metrics = resources.displayMetrics
    val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 15f * resources.displayMetrics.scaledDensity }
    val longestLabel = options.maxOfOrNull { labelPaint.measureText(it) } ?: 0f
    val contentWidth = longestLabel.roundToInt() + dp(16) * 2 + dp(28) + dp(4)
    val maxWidth = (metrics.widthPixels - dp(32)).coerceAtLeast(dp(1))
    val width = maxOf(anchor.width, contentWidth, dp(150)).coerceAtMost(maxWidth)
    val height = options.size * dp(40) + (options.size - 1).coerceAtLeast(0) * dp(1) + dp(4)
    val location = IntArray(2)
    anchor.getLocationOnScreen(location)
    // 多选菜单同样让右边界贴齐设置按钮，避免菜单向右错开。
    val left = (location[0] + anchor.width - width).coerceIn(frame.left + dp(16), frame.right - width - dp(16))
    popup = PopupWindow(menu, width, height, true).apply {
        setBackgroundDrawable(getDrawable(R.drawable.bg_popup))
        isOutsideTouchable = true
        isFocusable = true
        isClippingEnabled = true
        elevation = dp(6).toFloat()
    }
    popup.showAsDropDown(anchor, left - location[0], dp(6))
    menu.alpha = 0f
    menu.scaleX = 0.94f
    menu.scaleY = 0.94f
    menu.translationY = -dp(4).toFloat()
    menu.post {
        menu.pivotX = width / 2f
        menu.pivotY = 0f
        menu.animate().alpha(1f).scaleX(1f).scaleY(1f).translationY(0f)
            .setDuration(180)
            .setInterpolator(android.view.animation.DecelerateInterpolator(1.45f))
            .start()
    }
}

internal fun MainActivity.formatSpinnerAdapter(): ArrayAdapter<String> {
    val activity = this
    return object : ArrayAdapter<String>(activity, android.R.layout.simple_spinner_item, formats.map { it.first }) {
    fun style(view: View, dropdown: Boolean): View = (view as? TextView)?.apply {
            textSize = 15f
            gravity = Gravity.CENTER_VERTICAL
            setTextColor(activity.primaryText())
            setPadding(activity.dp(if (dropdown) 16 else 14), 0, activity.dp(if (dropdown) 16 else 14), 0)
            if (dropdown) {
                minimumHeight = activity.dp(48)
                setBackgroundColor(if (activity.isDark()) 0xff20242e.toInt() else Color.WHITE)
            }
        } ?: view

    override fun getView(position: Int, convertView: View?, parent: android.view.ViewGroup): View = style(super.getView(position, convertView, parent), false)
    override fun getDropDownView(position: Int, convertView: View?, parent: android.view.ViewGroup): View = style(super.getDropDownView(position, convertView, parent), true)
    }
}

internal fun MainActivity.showIos26NoticeDialog(message: String, showMetrics: Boolean = false) {
    val dialog = AlertDialog.Builder(this).create()
    val box = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        setPadding(dp(18), dp(18), dp(18), dp(10))
        addView(TextView(this@showIos26NoticeDialog).apply {
            text = message
            textSize = 16f
            gravity = Gravity.CENTER
            includeFontPadding = false
            setTextColor(primaryText())
        }, LinearLayout.LayoutParams(-1, -2))
         addView(styleButton(Button(this@showIos26NoticeDialog).apply {
             text = "确定"
             textSize = 15f
             minWidth = 0; minimumWidth = 0
             // 保持上下 44dp 不变，仅扩大左右可见外框。
             minWidth = dp(76); minimumWidth = dp(76)
             isAllCaps = false
             // 保持与其他弹窗一致的紧凑按钮边距。
             setPadding(dp(10), dp(7), dp(10), dp(7))
             setTextColor(primaryText())
             setBackgroundDrawable(glassButtonBackground().apply { cornerRadius = dp(14).toFloat() })
             setOnClickListener { dialog.dismiss() }
         }), LinearLayout.LayoutParams(dp(76), dp(44)).apply { gravity = Gravity.END; topMargin = dp(10) })
    }
    dialog.setView(box)
    showIos26Dialog(dialog, compact = true)
    // 以实际弹窗 decorView 为锚点，保证检查数据永远显示在弹窗完整底部。
    val metricsPopup = if (showMetrics) showSimulationMetrics(dialog.window?.decorView ?: box, "提示弹窗") else null
    dialog.setOnDismissListener { metricsPopup?.dismiss() }
}

/** 显示弹窗目录中的模拟状态；模拟弹窗和真实弹窗共用统一样式及数据检查器。 */
internal fun MainActivity.showSimulatedDialog(title: String, message: String, negative: String?, neutral: String?, positive: String?, showMetrics: Boolean = true) {
    val builder = AlertDialog.Builder(this).setTitle(title).setMessage(message)
    negative?.let { builder.setNegativeButton(it, null) }
    neutral?.let { builder.setNeutralButton(it, null) }
    positive?.let { builder.setPositiveButton(it, null) }
    // 通用确认类弹窗与正式弹窗一样使用标准宽度；紧凑弹窗由各自的真实实现决定。
    val dialog = showIos26Dialog(builder.create())
    val metricsPopup = if (showMetrics) showSimulationMetrics(dialog.window?.decorView ?: return, title) else null
    dialog.setOnDismissListener { metricsPopup?.dismiss() }
}

private fun Float.formatOneDecimal() = "%.1f".format(this)

private fun View.maxWidthOrUnset() = (this as? TextView)?.maxWidth ?: -1
private fun View.maxHeightOrUnset() = (this as? TextView)?.maxHeight ?: -1

/** Beta 测试中心的八方向尺寸控制层；仅覆盖选中的弹窗元素，不进入正式版界面。 */
private fun MainActivity.installSimulationInspector(root: View, metrics: TextView, label: String, selection: GradientDrawable): () -> Unit {
    var selected: View? = null
    fun describe(view: View): String {
        val density = resources.displayMetrics.density
        val location = IntArray(2)
        view.getLocationInWindow(location)
        val text = (view as? TextView)?.text?.toString()?.takeIf { it.isNotBlank() }
        val id = if (view.id != View.NO_ID) resources.getResourceEntryName(view.id) else "无"
        val params = view.layoutParams
        val margins = (params as? ViewGroup.MarginLayoutParams)?.let { "${it.leftMargin},${it.topMargin},${it.rightMargin},${it.bottomMargin}" } ?: "无"
        return listOf(
            "弹窗：$label",
            "元素：${view.javaClass.simpleName}，id=$id",
            "文字：${text ?: "无"}",
            "位置：x=${location[0]}px/${(location[0] / density).formatOneDecimal()}dp y=${location[1]}px/${(location[1] / density).formatOneDecimal()}dp",
            "尺寸：w=${view.width}px/${(view.width / density).formatOneDecimal()}dp h=${view.height}px/${(view.height / density).formatOneDecimal()}dp",
            "内边距：${view.paddingLeft},${view.paddingTop},${view.paddingRight},${view.paddingBottom}px",
            "外边距：$margins，字号 ${(view as? TextView)?.textSize?.div(resources.displayMetrics.scaledDensity)?.formatOneDecimal() ?: "无"}sp",
            "外观：背景=${view.background?.javaClass?.simpleName ?: "无"} 前景=${view.foreground?.javaClass?.simpleName ?: "无"} 透明度=${view.alpha.formatOneDecimal()}",
            "变换：旋转=${view.rotation.formatOneDecimal()}° 缩放=${view.scaleX.formatOneDecimal()}×${view.scaleY.formatOneDecimal()}× 阴影=${(view.elevation / density).formatOneDecimal()}dp",
            "约束：最小=${view.minimumWidth}px×${view.minimumHeight}px，最大=${view.maxWidthOrUnset()}×${view.maxHeightOrUnset()}",
            "状态：可见=${view.visibility == View.VISIBLE} 可点击=${view.isClickable} 可用=${view.isEnabled} 可聚焦=${view.isFocusable}"
        ).joinToString("\n")
    }
    fun select(view: View) {
        selected?.overlay?.remove(selection)
        selected = view
        view.overlay.add(selection)
        selection.setBounds(0, 0, view.width, view.height)
        metrics.text = describe(view)
    }
    fun visit(view: View) {
        // 普通文本元素开启系统选择能力；按钮仍由检查器拦截，避免触发原操作。
        val blockClick = view is Button || view.isClickable
        if (view is TextView && view !is Button) view.setTextIsSelectable(true)
        view.setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_DOWN || event.actionMasked == MotionEvent.ACTION_UP) select(view)
            // 模拟检查期间拦截弹窗内部可点击元素，避免点击按钮执行真实操作或关闭弹窗。
            // 点击弹窗外部仍交给 Dialog/PopupWindow 处理，因此仍可退出。
            if (blockClick && event.actionMasked != MotionEvent.ACTION_CANCEL) true else false
        }
        if (view is ViewGroup) for (index in 0 until view.childCount) visit(view.getChildAt(index))
    }
    root.post {
        if (!root.isShown) return@post
        visit(root)
        select(root)
    }
    return {
        selected?.overlay?.remove(selection)
        fun clear(view: View) {
            view.setOnTouchListener(null)
            if (view is ViewGroup) for (index in 0 until view.childCount) clear(view.getChildAt(index))
        }
        clear(root)
    }
}

/** Beta 模拟专用参数面板：紧贴真实弹窗或菜单下方，不参与真实功能。 */
internal fun MainActivity.showSimulationMetrics(anchor: View, label: String, onDismiss: (() -> Unit)? = null): PopupWindow {
    val metrics = TextView(this).apply {
        textSize = 11f; includeFontPadding = false; setTextColor(secondaryText()); setPadding(dp(12), dp(9), dp(12), dp(9))
        background = liquidGlassCard(); setTextIsSelectable(true)
    }
    val panel = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; setPadding(0, 0, 0, dp(4))
        addView(ScrollView(this@showSimulationMetrics).apply { isFillViewport = true; addView(metrics) }, LinearLayout.LayoutParams(-1, -1))
    }
    val popup = PopupWindow(panel, dp(320), dp(224), false).apply {
        setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        elevation = dp(4).toFloat()
        isOutsideTouchable = false
    }
    val selection = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(Color.TRANSPARENT)
        setStroke(dp(2), if (isDark()) 0xff64a9ff.toInt() else 0xff1677ff.toInt())
        cornerRadius = dp(12).toFloat()
    }
    var cleanupInspector: (() -> Unit)? = null
    popup.setOnDismissListener { cleanupInspector?.invoke(); onDismiss?.invoke() }
    anchor.post {
        if (!anchor.isShown) return@post
        // 以真实模拟弹窗的底部为起点强制向下显示，避免 PopupWindow 因空间判断翻到弹窗上方。
        cleanupInspector = installSimulationInspector(anchor, metrics, label, selection)
        val location = IntArray(2); anchor.getLocationOnScreen(location)
        popup.showAtLocation(anchor, Gravity.TOP or Gravity.START, ((resources.displayMetrics.widthPixels - dp(320)) / 2).coerceAtLeast(dp(8)), location[1] + anchor.height + dp(8))
    }
    return popup
}

internal fun MainActivity.parseColor(value: String, fallback: Int): Int = try {
        val normalized = value.trim().let { if (it.startsWith("#")) it else "#$it" }
        Color.parseColor(normalized)
    } catch (_: Exception) { fallback }


internal fun MainActivity.applyAppearance() {
        val mode = when (style.colorScheme) { "dark" -> AppCompatDelegate.MODE_NIGHT_YES; "light" -> AppCompatDelegate.MODE_NIGHT_NO; else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM }
        AppCompatDelegate.setDefaultNightMode(mode)
        syncSystemBars()
        runCatching { rootLayout.setBackgroundColor(appBackground()) }
    }

/** 在主题重建完成后再次同步系统栏，避免切换模式时短暂沿用旧颜色或旧图标明暗。 */
internal fun MainActivity.syncSystemBars() {
        val background = appBackground()
        window.decorView.systemUiVisibility = if (isDark()) 0 else View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        window.statusBarColor = background
        window.navigationBarColor = background
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) window.isNavigationBarContrastEnforced = false
}


internal fun MainActivity.loadStyle(): StyleSettings = StyleSettings(
        barColor = Color.BLACK, bgColor = Color.WHITE,
        showText = true, textPosition = "bottom",
        textSize = settingsStore.get(SettingsStore.TEXT_SIZE, 14f).coerceIn(10f, 24f), barHeight = settingsStore.get(SettingsStore.BAR_HEIGHT, 55).coerceIn(30, 150), barWidth = settingsStore.get(SettingsStore.BAR_WIDTH, 220f).coerceIn(120f, 360f),
        margin = settingsStore.get(SettingsStore.MARGIN, 4).coerceIn(0, 40), showFormat = settingsStore.get(SettingsStore.SHOW_FORMAT, false), colorScheme = settingsStore.get(SettingsStore.COLOR_SCHEME, "system")
    )


internal fun MainActivity.saveStyle() = settingsStore.saveStyle(style)

internal fun MainActivity.saveInputDraft() {
        val activity = this
        if (inputRows.isNotEmpty()) inputDraft = inputRows.map { it.text.toString() }.toMutableList()
    }


internal fun MainActivity.preview(item: CodeItem) {
        val activity = this
        val bmp = encode(item.text, formats.first { it.first == item.format }.second) ?: run { toast("内容不符合该格式"); return }
        val image = ImageView(this).apply { setImageBitmap(bmp); adjustViewBounds = true }
        AlertDialog.Builder(this).setTitle(item.format).setMessage(item.text).setView(image).setPositiveButton("关闭", null).setNeutralButton("分享图片") { _, _ -> shareBitmap(bmp, item.text) }.setNegativeButton("保存图片") { _, _ -> saveBitmap(bmp, item.text) }.create().also { showIos26Dialog(it) }
    }


internal fun MainActivity.shareText(text: String) { startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) }, "分享条码内容")) }

internal fun MainActivity.saveBitmap(bitmap: Bitmap, label: String) {
        val activity = this
        val values = android.content.ContentValues().apply { put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, label.replace(Regex("[^A-Za-z0-9._-]+"), "_").take(80).ifBlank { "barcode" } + ".png"); put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/png"); if (android.os.Build.VERSION.SDK_INT >= 29) put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, "Pictures/BarcodeGenerator") }
        val uri = contentResolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        if (uri == null) { toast("保存失败"); return }
        contentResolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        toast("已保存到相册")
    }


internal fun MainActivity.shareBitmap(bitmap: Bitmap, label: String) {
        val activity = this
        val values = android.content.ContentValues().apply { put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, label.replace(Regex("[^A-Za-z0-9._-]+"), "_").take(80).ifBlank { "barcode" } + ".png"); put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/png"); if (android.os.Build.VERSION.SDK_INT >= 29) put(android.provider.MediaStore.Images.Media.IS_PENDING, 1) }
        val uri = contentResolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        if (uri == null) { toast("分享失败"); return }
        contentResolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        if (android.os.Build.VERSION.SDK_INT >= 29) contentResolver.update(uri, android.content.ContentValues().apply { put(android.provider.MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "image/png"; putExtra(Intent.EXTRA_STREAM, uri); putExtra(Intent.EXTRA_TEXT, label); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }, "分享条码图片"))
    }


internal fun MainActivity.encode(text: String, format: BarcodeFormat): Bitmap? = try {
        val activity = this
        val code128 = format == BarcodeFormat.CODE_128
        val width = if (code128) dp(activity.style.barWidth.roundToInt().coerceIn(120, 360)).coerceAtLeast(1) else 500
        val barcodeHeight = if (code128) dp(activity.style.barHeight.coerceIn(30, 150).coerceAtLeast(1)) else if (format == BarcodeFormat.QR_CODE) 500 else 200
        val matrix = MultiFormatWriter().encode(text, format, width, barcodeHeight, mapOf(EncodeHintType.MARGIN to 0))
        // 编码函数只返回纯条码 Bitmap；人类可读文字由结果页的独立 TextView 绘制。
        // 这里不能把文字画进 Bitmap，否则历史、预览、分享等路径会再次出现条码内嵌文字。
        // 不使用抗锯齿绘制条纹，避免相邻黑白模块出现灰边。
        val paint = Paint().apply {
            setColor(if (activity.isDark()) Color.BLACK else activity.style.barColor)
        }
        val bitmap = Bitmap.createBitmap(width, barcodeHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        if (activity.isDark()) canvas.drawColor(Color.WHITE)
         else canvas.drawColor(activity.style.bgColor)
        for (x in 0 until matrix.width) {
            for (y in 0 until matrix.height) {
                if (matrix[x, y]) canvas.drawRect(x.toFloat(), y.toFloat(), (x + 1).toFloat(), (y + 1).toFloat(), paint)
            }
        }
    // 所有 Code 128 输出路径（结果页、预览、保存、分享）统一保留白色静区。
    if (code128) addBarcodeQuietZone(bitmap) else bitmap
} catch (_: Exception) { null }

/** 裁掉纯条码 Bitmap 的左右空白；仅供 Code 128-B 结果页使用。 */
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

/** 为屏幕和分享图片保留白色静区，满足外部扫描器对 Code 128 quiet zone 的要求。 */
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


internal fun MainActivity.toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

/** 与业务完全分离的全屏烟花；不写入任何设置或条码数据。 */
internal fun MainActivity.showFireworksEasterEgg() {
    val host = findViewById<ViewGroup>(android.R.id.content) ?: return
    // 在当前 Activity 叠加特效，而非启动新页面，避免改变设置页导航栈。
    // Android 15 默认边到边显示：让黑色夜空延伸到状态栏后方，但保持状态栏图标可读。
    val overlay = FrameLayout(this).apply {
        setBackgroundColor(Color.BLACK)
        isClickable = true
        contentDescription = "烟花彩蛋"
    }
    val fireworks = InlineFireworksView(this)
    overlay.addView(fireworks, FrameLayout.LayoutParams(-1, -1))
    dismissFireworksEasterEgg()
    fireworksPreviousStatusBarColor = window.statusBarColor
    fireworksPreviousNavigationBarColor = window.navigationBarColor
    fireworksPreviousSystemUiVisibility = window.decorView.systemUiVisibility
    window.statusBarColor = Color.BLACK
    window.navigationBarColor = Color.BLACK
    val lightSystemBars = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
    window.decorView.systemUiVisibility = window.decorView.systemUiVisibility and lightSystemBars.inv()
    fireworksOverlay = overlay
    // Android 15 会透明化导航栏颜色；必须让夜空内容本身延伸到导航栏后方，才能避免露出原页面浅色背景。
    host.addView(overlay, FrameLayout.LayoutParams(-1, -1))
}

/** 关闭彩蛋时恢复进入前的系统状态栏，避免影响当前页面的深浅色显示。 */
internal fun MainActivity.dismissFireworksEasterEgg() {
    fireworksOverlay?.let { overlay -> (overlay.parent as? ViewGroup)?.removeView(overlay) }
    fireworksOverlay = null
    fireworksPreviousStatusBarColor?.let { window.statusBarColor = it }
    fireworksPreviousNavigationBarColor?.let { window.navigationBarColor = it }
    fireworksPreviousSystemUiVisibility?.let { window.decorView.systemUiVisibility = it }
    fireworksPreviousStatusBarColor = null
    fireworksPreviousNavigationBarColor = null
    fireworksPreviousSystemUiVisibility = null
}

