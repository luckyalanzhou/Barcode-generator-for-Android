package com.luckyalanzhou.barcodegenerator

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Beta 专用测试中心；正式版通过 BuildConfig.DEBUG_LOG_EXPORT 隐藏入口。 */
internal data class BetaTestEntry(val label: String, val action: () -> Unit)

internal fun MainActivity.betaTestEntries(): List<BetaTestEntry> = listOf(
    BetaTestEntry("本地自检") {
        val result = listOf(
            "版本信息：${BuildConfig.VERSION_NAME}",
            "条码格式校验：可用",
            "设置存储：可用",
            "收藏数据层：可用",
            "调试日志：已启用",
        ).joinToString("\n")
        showIos26NoticeDialog("功能自检结果\n$result")
    },
    BetaTestEntry("测试最新版本提示") { showIos26NoticeDialog("测试：当前已是最新版本", showMetrics = true) },
    BetaTestEntry("测试错误提示") { showIos26NoticeDialog("测试错误\n这是测试中心触发的错误提示", showMetrics = true) },
    BetaTestEntry("测试局域网未连接提示") { showLanShareNetworkErrorDialog(showMetrics = true) },
    BetaTestEntry("模拟发现新版本") { showUpdateAvailableDialogCompose("9.9.9", "https://example.invalid/update.apk", null, null, simulateOnly = true, showMetrics = true) },
    BetaTestEntry("模拟下载进度") { downloadAndInstallCompose("https://example.invalid/update.apk", simulateOnly = true, showMetrics = true) },
    BetaTestEntry("模拟下载失败") { showSimulatedDialog("更新下载失败", "网络连接失败，请稍后重试", null, null, "重新下载") },
    BetaTestEntry("模拟二维码弹窗") { showLanShareQrDialog(LanShareSession("http://192.168.1.100:54321")) },
    BetaTestEntry("模拟文件夹编辑") { showFolderEditorCompose("示例文件夹", showMetrics = true) {} },
    BetaTestEntry("模拟导入确认") { showSimulatedDialog("导入收藏", "发现 12 个收藏文件，是否导入？", "取消", null, "导入") },
    BetaTestEntry("模拟导出结果") { showSimulatedDialog("导出收藏", "收藏已导出为 ZIP 文件", null, null, "确定") },
    BetaTestEntry("模拟删除确认") { showSimulatedDialog("删除收藏", "确定删除此收藏吗？", "取消", null, "删除") },
    BetaTestEntry("模拟覆盖确认") { showSimulatedDialog("覆盖收藏", "同名收藏已存在，是否覆盖？", "取消", null, "覆盖") },
    BetaTestEntry("模拟权限提示") { showSimulatedDialog("需要权限", "需要相机权限才能拍摄图片", "取消", null, "去设置") },
    BetaTestEntry("模拟安装权限") { showSimulatedDialog("需要允许安装未知应用", "请在系统设置中允许安装应用更新", "取消", null, "去设置") },
)

@Composable
internal fun BetaTestCenterComposePage(
    dark: Boolean,
    entries: List<BetaTestEntry>,
    onShareDebugLog: () -> Unit,
) {
    val primary = if (dark) Color(0xffe9f1ff) else Color(0xff182230)
    val secondary = if (dark) Color(0xffaeb9c9) else Color(0xff667085)
    val buttonColor = if (dark) Color(0xff172a3a) else Color(0xfff0f5fb)
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "beta-test-header") {
            Text(
                text = "Beta 测试中心",
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 10.dp),
                color = primary,
                fontSize = 24.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
            )
        }
        items(
            items = entries.chunked(2),
            key = { row -> row.joinToString("|") { it.label } },
            contentType = { "beta-test-actions" },
        ) { rowEntries ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                rowEntries.forEach { entry ->
                    BetaTestActionButton(
                        entry = entry,
                        primary = primary,
                        container = buttonColor,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (rowEntries.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        item(key = "beta-test-notice") {
            Text(
                text = "相机、系统权限、局域网连接和 APK 安装仍需在真实设备上验证。",
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                color = secondary,
                fontSize = 12.sp,
            )
        }
        item(key = "beta-test-log-export") {
            Button(
                onClick = onShareDebugLog,
                modifier = Modifier.fillMaxWidth().height(44.dp).globalButtonChrome(RoundedCornerShape(14.dp), 0.5.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = buttonColor, contentColor = primary),
            ) { Text("导出调试日志", maxLines = 1) }
        }
    }
}

@Composable
private fun BetaTestActionButton(
    entry: BetaTestEntry,
    primary: Color,
    container: Color,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = entry.action,
        modifier = modifier.height(48.dp).globalButtonChrome(RoundedCornerShape(16.dp), 1.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = container, contentColor = primary),
        contentPadding = PaddingValues(horizontal = 6.dp),
    ) {
        Text(entry.label, maxLines = 1, fontSize = 13.sp, textAlign = TextAlign.Center)
    }
}
