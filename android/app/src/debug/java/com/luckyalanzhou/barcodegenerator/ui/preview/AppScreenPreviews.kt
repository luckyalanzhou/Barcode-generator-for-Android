package com.luckyalanzhou.barcodegenerator.ui.preview

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.content.res.Configuration
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.luckyalanzhou.barcodegenerator.domain.CodeItem
import com.luckyalanzhou.barcodegenerator.domain.FavoriteGroup
import com.luckyalanzhou.barcodegenerator.domain.LanShareFile
import com.luckyalanzhou.barcodegenerator.domain.LanShareMessage
import com.luckyalanzhou.barcodegenerator.domain.LanShareSession
import com.luckyalanzhou.barcodegenerator.domain.StyleSettings
import com.luckyalanzhou.barcodegenerator.ui.dialogs.UpdateAvailableDialogContent
import com.luckyalanzhou.barcodegenerator.ui.feature.favorites.content.FavoritesContent
import com.luckyalanzhou.barcodegenerator.ui.feature.favorites.content.FavoritesContentState
import com.luckyalanzhou.barcodegenerator.ui.feature.favorites.content.FavoritesListContentState
import com.luckyalanzhou.barcodegenerator.ui.feature.favorites.tree.FavoritesTreeState
import com.luckyalanzhou.barcodegenerator.ui.feature.generate.GenerateContent
import com.luckyalanzhou.barcodegenerator.ui.feature.history.HistoryComposePage
import com.luckyalanzhou.barcodegenerator.ui.feature.history.HistoryBatchEntry
import com.luckyalanzhou.barcodegenerator.ui.feature.lanshare.LanShareContent
import com.luckyalanzhou.barcodegenerator.ui.feature.lanshare.LanShareContentState
import com.luckyalanzhou.barcodegenerator.ui.feature.results.PreparedResultRow
import com.luckyalanzhou.barcodegenerator.ui.feature.results.PreparedResultRowView
import com.luckyalanzhou.barcodegenerator.ui.feature.results.ResultToolbar
import com.luckyalanzhou.barcodegenerator.ui.feature.results.resultImageRowLayout
import com.luckyalanzhou.barcodegenerator.ui.feature.settings.SettingsContent
import com.luckyalanzhou.barcodegenerator.ui.feature.settings.SettingsContentState
import com.luckyalanzhou.barcodegenerator.ui.theme.AppTheme
import kotlinx.coroutines.flow.emptyFlow

/** 调试预览统一使用确定的演示数据；渲染的 PNG 放在 .local-validation/ui-previews。 */
private val previewItems = listOf(
    CodeItem(1, "UX-001-PREVIEW", "Code 128-B", 1L),
    CodeItem(2, "BARCODE-2026-002", "Code 128-B", 2L),
    CodeItem(3, "1234567890", "Code 128-B", 3L),
)

@Preview(name = "生成页 · 浅色", showBackground = true, widthDp = 360, heightDp = 800, uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "生成页 · 深色", showBackground = true, widthDp = 360, heightDp = 800, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun GenerateScreenPreview() = PreviewTheme { dark ->
    GenerateContent(
        inputDraft = listOf("UX-001-PREVIEW", "BARCODE-2026-002"),
        initialFormat = "Code 128-B",
        dark = dark,
        isPreparingResult = false,
        isDataReady = true,
        onDraftChanged = {}, onFormatChanged = {}, onGenerate = { _, _ -> },
        onCaptureText = {}, onNotice = {},
    )
}

@Preview(name = "结果页 · 浅色", showBackground = true, widthDp = 360, heightDp = 800, uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "结果页 · 深色", showBackground = true, widthDp = 360, heightDp = 800, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ResultsScreenPreview() = PreviewTheme { dark ->
    val style = StyleSettings()
    val bitmap = previewBarcodeBitmap()
    Column(Modifier.fillMaxSize()) {
        ResultToolbar(null, false, onEdit = {}, onSaveFavorite = {}, onShare = {}, onSave = {})
        previewItems.forEach { item ->
            PreparedResultRowView(
                row = PreparedResultRow(
                    item = item,
                    barcode = bitmap,
                    layout = resultImageRowLayout(bitmap, item, style, dark, 336, 1f, 1f),
                ),
                dark = dark,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            )
        }
    }
}

@Preview(name = "历史页 · 浅色", showBackground = true, widthDp = 360, heightDp = 800, uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "历史页 · 深色", showBackground = true, widthDp = 360, heightDp = 800, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun HistoryScreenPreview() = PreviewTheme { dark ->
    HistoryComposePage(
        entries = previewItems.mapIndexed { index, item ->
            HistoryBatchEntry(key = "preview-$index", time = item.createdAt, items = listOf(item))
        },
        refreshGeneration = 0L, dark = dark,
        onOpen = {}, onEdit = {}, onDelete = {}, timeText = { "12:34" },
    )
}

@Preview(name = "收藏页 · 浅色", showBackground = true, widthDp = 360, heightDp = 800, uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "收藏页 · 深色", showBackground = true, widthDp = 360, heightDp = 800, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun FavoritesScreenPreview() = PreviewTheme { dark ->
    val group = FavoriteGroup(1L, "默认", "示例收藏", 1L, previewItems.map { it.id }.toMutableList())
    FavoritesContent(
        state = FavoritesContentState(
            favorites = FavoritesListContentState(
                items = previewItems,
                groups = listOf(group),
                folders = listOf("默认", "工作"),
                isReady = true,
            ),
            tree = FavoritesTreeState(knownFolders = setOf("默认", "工作")),
        ),
        scrollToTopEvents = emptyFlow(), dark = dark, style = StyleSettings(),
        onQueryChange = {}, onSearchFavoriteContent = {}, onSyncFavoriteTree = {},
        onUpdateFavoriteSearch = { _, _ -> }, onRememberListPosition = { _, _ -> },
        onLoadMoreGroups = {}, onToggleFolder = { _, _ -> }, onOpenGroup = { _, _, _, _ -> },
        onRenameFolder = { _, _ -> true }, onDeleteFolder = {}, onDeleteGroup = {},
        onShowSubfolderEditor = {}, onShowFolderEditor = { _, _ -> }, onEdit = {},
        onShowMoveDialog = {}, onShowRenameDialog = {}, onConfirm = { _, _, _, _ -> },
    )
}

@Preview(name = "设置页 · 浅色", showBackground = true, widthDp = 360, heightDp = 800, uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "设置页 · 深色", showBackground = true, widthDp = 360, heightDp = 800, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SettingsScreenPreview() = PreviewTheme { dark ->
    SettingsContent(
        checkingForUpdates = false,
        settings = SettingsContentState(style = StyleSettings(), ocrMask = 0),
        dark = dark, onStyleChange = {}, onOcrMaskChange = {}, onEnterLanShare = {},
        onShareDebugLog = {}, onCheckForUpdates = {}, onNotice = {},
    )
}

@Preview(name = "局域网分享 · 浅色", showBackground = true, widthDp = 360, heightDp = 800, uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "局域网分享 · 深色", showBackground = true, widthDp = 360, heightDp = 800, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun LanShareScreenPreview() = PreviewTheme { dark ->
    LanShareContent(
        lanState = LanShareContentState(
            session = LanShareSession("http://192.168.1.20:8080"),
            isHost = true,
            browserConnected = true,
            files = listOf(LanShareFile("file-1", "sample.png", 128_000L, 1L, sender = "我")),
            messages = listOf(LanShareMessage("message-1", "已连接，可以发送消息或文件。", "访客", 1L)),
        ),
        message = "", dark = dark, onMessageChange = {}, onSetQrVisible = {}, onSend = {},
        onCancelUpload = {}, onLoadImagePreview = { null }, onLoadFullImagePreview = { null },
        onOpenCamera = {}, onOpenGallery = {}, onOpenFiles = {}, onSaveFile = {}, onCopyAddress = {},
        createQrBitmap = { _, _, _, _ -> previewBarcodeBitmap() },
    )
}

@Preview(name = "更新提示 · 浅色", showBackground = true, widthDp = 360, heightDp = 240, uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "更新提示 · 深色", showBackground = true, widthDp = 360, heightDp = 240, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun UpdateDialogPreview() = PreviewTheme { dark ->
    UpdateAvailableDialogContent(latest = "1.0.11", dark = dark, onIgnore = {}, onLater = {}, onUpdate = {})
}

@Composable
private fun PreviewTheme(content: @Composable (dark: Boolean) -> Unit) {
    AppTheme("system") {
        val dark = isSystemInDarkTheme()
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = com.luckyalanzhou.barcodegenerator.ui.theme.LocalAppColorScheme.current.surfaces.background,
        ) { content(dark) }
    }
}

/** 结果页和二维码预览共用轻量静态条纹图，避免 Preview 触发业务渲染或网络逻辑。 */
private fun previewBarcodeBitmap(): Bitmap = Bitmap.createBitmap(420, 120, Bitmap.Config.ARGB_8888).apply {
    val canvas = Canvas(this)
    canvas.drawColor(android.graphics.Color.WHITE)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.BLACK }
    var x = 8f
    val widths = intArrayOf(4, 2, 7, 3, 2, 5, 3, 8, 2, 4, 6, 3, 2, 7, 4, 2, 5)
    widths.forEachIndexed { index, width ->
        if (index % 2 == 0) canvas.drawRect(x, 8f, x + width, 112f, paint)
        x += width + 3
    }
}
