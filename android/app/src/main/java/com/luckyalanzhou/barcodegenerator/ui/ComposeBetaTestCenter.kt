package com.luckyalanzhou.barcodegenerator.ui

import com.luckyalanzhou.barcodegenerator.*

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

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

private data class BetaChecklistItem(val title: String, val detail: String, val page: String)

private val betaChecklist = listOf(
    BetaChecklistItem("生成条码", "输入内容、排序、删除、生成结果", "generate"),
    BetaChecklistItem("历史记录", "查看、删除、长按和结果详情", "history"),
    BetaChecklistItem("收藏管理", "文件夹、编辑、移动、导入导出", "favorites"),
    BetaChecklistItem("设置与主题", "外观、条码格式、OCR 和更新检查", "settings"),
    BetaChecklistItem("文件传输", "二维码、附件发送和接收保存", "lanShare"),
)

@Composable
internal fun BetaTestCenterComposePage(
    dark: Boolean,
    entries: List<BetaTestEntry>,
    viewModel: BarcodeViewModel,
    onNavigate: (String) -> Unit,
    onShareDebugLog: () -> Unit,
) {
    val dataState by viewModel.dataState.collectAsStateWithLifecycle()
    val editorState by viewModel.generateEditorState.collectAsStateWithLifecycle()
    var scenariosExpanded by remember { mutableStateOf(false) }
    val themeColors = LocalBarcodeThemeColors.current
    val primary = themeColors.primary
    val secondary = themeColors.secondary
    val card = themeColors.card
    val button = themeColors.button
    val accent = themeColors.accent

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "beta-test-header") {
            Column(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 2.dp)) {
                Text("Beta 测试中心", color = primary, fontSize = 24.sp, fontWeight = FontWeight.Medium)
                Text("验证功能、记录问题、导出完整反馈信息", color = secondary, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
            }
        }
        item(key = "beta-build-info") {
            BetaSectionCard(card) {
                SectionTitle("当前构建", primary)
                InfoRow("版本", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", primary, secondary)
                InfoRow("设备", "${Build.MANUFACTURER} ${Build.MODEL}", primary, secondary)
                InfoRow("系统", "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})", primary, secondary)
                InfoRow("包名", BuildConfig.APPLICATION_ID, primary, secondary)
            }
        }
        item(key = "beta-checklist") {
            BetaSectionCard(card) {
                SectionTitle("核心功能测试", primary)
                Text("点击功能名称进入页面，测试完成后由测试人员手动确认。", color = secondary, fontSize = 12.sp, modifier = Modifier.padding(bottom = 6.dp))
                betaChecklist.forEachIndexed { index, item ->
                    if (index > 0) HorizontalDivider(color = secondary.copy(alpha = .12f))
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(item.title, color = primary, fontSize = 15.sp)
                            Text(item.detail, color = secondary, fontSize = 12.sp, maxLines = 1)
                        }
                        SmallBetaButton("进入", button, accent) { onNavigate(item.page) }
                    }
                }
            }
        }
        item(key = "beta-data-status") {
            BetaSectionCard(card) {
                SectionTitle("数据状态", primary)
                InfoRow("历史/条码记录", "${dataState.items.count { it.inHistory }} 条", primary, secondary)
                InfoRow("收藏条目", "${dataState.items.count { it.favorite }} 条", primary, secondary)
                InfoRow("收藏文件夹", "${dataState.folders.size} 个", primary, secondary)
                InfoRow("输入框", "${editorState.inputDraft.size} 个", primary, secondary)
                Text("以上为当前内存快照，用于发现升级后数据丢失或数量异常。", color = secondary, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
            }
        }
        item(key = "beta-feedback") {
            BetaSectionCard(card) {
                SectionTitle("问题反馈", primary)
                Text("导出前会附带版本、设备和运行日志。请先复现问题，再导出并分享文件。日志不会主动上传。", color = secondary, fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp))
                Button(
                    onClick = onShareDebugLog,
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = button, contentColor = primary),
                ) { Text("导出调试日志", maxLines = 1) }
            }
        }
        item(key = "beta-scenarios") {
            BetaSectionCard(card) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        SectionTitle("交互场景模拟", primary)
                        Text("用于复现弹窗、错误提示、更新和权限场景", color = secondary, fontSize = 12.sp)
                    }
                    SmallBetaButton(if (scenariosExpanded) "收起" else "展开", button, accent) { scenariosExpanded = !scenariosExpanded }
                }
                if (scenariosExpanded) {
                    Spacer(Modifier.height(8.dp))
                    entries.chunked(2).forEach { rowEntries ->
                        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            rowEntries.forEach { entry ->
                                BetaTestActionButton(entry, primary, button, Modifier.weight(1f))
                            }
                            if (rowEntries.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
        item(key = "beta-test-notice") {
            Text("相机、系统权限、局域网连接和 APK 安装必须在真实设备上验证。", color = secondary, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

@Composable
private fun BetaSectionCard(color: Color, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = color,
        shape = RoundedCornerShape(18.dp),
        shadowElevation = 0.5.dp,
        content = { Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), content = content) },
    )
}

@Composable
private fun SectionTitle(title: String, primary: Color) {
    Text(title, color = primary, fontSize = 17.sp, fontWeight = FontWeight.Medium)
}

@Composable
private fun InfoRow(label: String, value: String, primary: Color, secondary: Color) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = secondary, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(value, color = primary, fontSize = 13.sp, textAlign = TextAlign.End, maxLines = 1)
    }
}

@Composable
private fun SmallBetaButton(text: String, container: Color, accent: Color, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.height(36.dp),
        shape = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(horizontal = 12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = container, contentColor = accent),
    ) { Text(text, fontSize = 12.sp, maxLines = 1) }
}

@Composable
private fun BetaTestActionButton(entry: BetaTestEntry, primary: Color, container: Color, modifier: Modifier = Modifier) {
    Button(
        onClick = entry.action,
        modifier = modifier.height(44.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = container, contentColor = primary),
        contentPadding = PaddingValues(horizontal = 6.dp),
    ) { Text(entry.label, maxLines = 1, fontSize = 12.sp, textAlign = TextAlign.Center) }
}
