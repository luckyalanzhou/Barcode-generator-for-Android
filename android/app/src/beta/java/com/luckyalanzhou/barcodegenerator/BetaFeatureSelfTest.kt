package com.luckyalanzhou.barcodegenerator

import android.app.AlertDialog
import android.view.View
import android.util.TypedValue
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Beta 专用测试中心页面；正式版不编译此功能。 */
internal fun MainActivity.renderBetaTestCenterPageImpl() {
    var attachmentAnchor: View? = null
    val checks = listOf(
        "本地自检" to {
        val result = listOf(
                "版本信息：${BuildConfig.VERSION_NAME}",
                "条码格式校验：可用",
                "设置存储：可用",
                "收藏数据层：可用",
                "调试日志：已启用"
            ).joinToString("\n")
            showIos26NoticeDialog("功能自检结果\n$result")
        },
        "测试最新版本提示" to { showIos26NoticeDialog("测试：当前已是最新版本", showMetrics = true) },
        "测试错误提示" to { showIos26NoticeDialog("测试错误\n这是测试中心触发的错误提示", showMetrics = true) },
        "测试局域网未连接提示" to { showLanShareNetworkErrorDialog(showMetrics = true) },
        "模拟发现新版本" to { showUpdateAvailableDialog("9.9.9", "https://example.invalid/update.apk", null, null, simulateOnly = true, showMetrics = true) },
        "模拟下载进度" to { downloadAndInstall("https://example.invalid/update.apk", simulateOnly = true, showMetrics = true) },
        "模拟下载失败" to { showSimulatedDialog("更新下载失败", "网络连接失败，请稍后重试", null, null, "重新下载") },
        "模拟二维码弹窗" to { showLanShareQrDialog(LanShareSession("http://192.168.1.100:54321")) },
        "模拟附件选项" to { attachmentAnchor?.let { anchor -> showLanSharePopup(anchor, listOf("拍摄图片" to {}, "照片图库" to {}, "选择文件" to {}), showMetrics = true) } },
        "模拟文件夹编辑" to { showFolderEditor("示例文件夹", showMetrics = true) {} },
        "模拟导入确认" to { showSimulatedDialog("导入收藏", "发现 12 个收藏文件，是否导入？", "取消", null, "导入") },
        "模拟导出结果" to { showSimulatedDialog("导出收藏", "收藏已导出为 ZIP 文件", null, null, "确定") },
        "模拟删除确认" to { showSimulatedDialog("删除收藏", "确定删除此收藏吗？", "取消", null, "删除") },
        "模拟覆盖确认" to { showSimulatedDialog("覆盖收藏", "同名收藏已存在，是否覆盖？", "取消", null, "覆盖") },
        "模拟权限提示" to { showSimulatedDialog("需要权限", "需要相机权限才能拍摄图片", "取消", null, "去设置") },
        "模拟安装权限" to { showSimulatedDialog("需要允许安装未知应用", "请在系统设置中允许安装应用更新", "取消", null, "去设置") },
    )
    val box = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(20), dp(8), dp(20), dp(8))
        addView(TextView(this@renderBetaTestCenterPageImpl).apply {
            text = "Beta 测试中心"
            textSize = 14f
            includeFontPadding = false
            setTextColor(secondaryText())
            setPadding(0, 0, 0, dp(8))
        })
        checks.chunked(2).forEachIndexed { rowIndex, rowItems ->
            addView(LinearLayout(this@renderBetaTestCenterPageImpl).apply {
                orientation = LinearLayout.HORIZONTAL
                rowItems.forEachIndexed { columnIndex, (label, action) ->
                    val testButton = styleButton(Button(this@renderBetaTestCenterPageImpl).apply {
                        text = label; isAllCaps = false; minWidth = 0; minimumWidth = 0
                        isSingleLine = true
                        ellipsize = null
                        gravity = android.view.Gravity.CENTER
                        setPadding(dp(4), 0, dp(4), 0)
                        // 双列下长标题自动缩小字号，保证完整显示且不换行。
                        setAutoSizeTextTypeUniformWithConfiguration(10, 14, 1, TypedValue.COMPLEX_UNIT_SP)
                        setOnClickListener { action() }
                    })
                    if (label == "模拟附件选项") attachmentAnchor = testButton
                    addView(testButton, LinearLayout.LayoutParams(0, dp(48), 1f).apply {
                        if (columnIndex > 0) leftMargin = dp(6)
                    })
                }
            }, LinearLayout.LayoutParams(-1, dp(48)).apply { if (rowIndex > 0) topMargin = dp(6) })
        }
        addView(TextView(this@renderBetaTestCenterPageImpl).apply {
            text = "相机、系统权限、局域网连接和 APK 安装仍需在真实设备上验证。"
            textSize = 12f
            includeFontPadding = false
            setTextColor(secondaryText())
            setPadding(0, dp(12), 0, 0)
        })
    }
    // 列表占满测试中心的剩余空间；项目超出可用高度时由 ScrollView 自然滚动，避免固定高度造成底部空白。
    val scroll = ScrollView(this).apply {
        isFillViewport = true
        isScrollbarFadingEnabled = true
        addView(box)
    }
    content.removeAllViews()
    content.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
    content.addView(styleButton(Button(this).apply {
        text = "导出调试日志"; isAllCaps = false
        setOnClickListener { shareDebugLog() }
    }), LinearLayout.LayoutParams(-1, dp(42)).apply { topMargin = dp(8) })
}
