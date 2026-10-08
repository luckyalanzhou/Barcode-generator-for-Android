package com.luckyalanzhou.barcodegenerator.ui.feature.results

import com.luckyalanzhou.barcodegenerator.ui.component.glass.*
import com.luckyalanzhou.barcodegenerator.ui.theme.*

import com.luckyalanzhou.barcodegenerator.domain.CodeItem
import com.luckyalanzhou.barcodegenerator.domain.StyleSettings

import com.luckyalanzhou.barcodegenerator.icons.EditIcon
import com.luckyalanzhou.barcodegenerator.icons.FavoriteIcon
import com.luckyalanzhou.barcodegenerator.icons.FavoriteFilledIcon
import com.luckyalanzhou.barcodegenerator.icons.IosShareIcon
import com.luckyalanzhou.barcodegenerator.icons.DownloadIcon
import androidx.compose.runtime.remember

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.key
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.collect

/**
 * 结果页：先在后台准备本批全部条码图片，全部就绪后才显示列表；顶部固定提供编辑、收藏、分享和保存。
 * 四个按钮使用独立回调，分享与保存共用互斥导出状态，编辑和收藏不受导出状态影响。
 */
@Composable
internal fun ResultsContent(
    exportAction: ResultExportAction?,
    state: ResultsContentState,
    style: StyleSettings,
    dark: Boolean,
    onEdit: () -> Unit,
    onSaveFavorite: () -> Unit,
    onShare: () -> Unit,
    onSave: () -> Unit,
    onImageWidthChanged: (Int) -> Unit,
    loadBarcodeImage: suspend (CodeItem, Boolean, Float) -> Bitmap?,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
    val themeColors = LocalAppColorScheme.current
    val dimensions = LocalAppDimensions.current
    val primary = themeColors.text.primary
    val secondary = themeColors.text.secondary
    val items = state.items
    val density = LocalDensity.current.density
    val fontScale = LocalDensity.current.fontScale
    val statusBarInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val rowWidth = with(LocalDensity.current) { (maxWidth - 24.dp).roundToPx().coerceAtLeast(1) }
    SideEffect { onImageWidthChanged(rowWidth) }
    val isFavorite = state.hasSavedFavoriteFile

    if (items.isEmpty()) {
        Column(Modifier.fillMaxWidth().statusBarsPadding().padding(top = dimensions.pageTopPadding), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("生成结果", color = primary, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp, bottom = 6.dp))
            Text(
                when {
                    state.isRestoring -> "正在恢复上次结果…"
                    state.restoreFailed -> "上次结果已不可用，请返回重新打开"
                    else -> "暂无生成结果"
                },
                color = secondary,
                fontSize = 17.sp,
                modifier = Modifier.padding(vertical = 40.dp),
            )
        }
        return@BoxWithConstraints
    }

    // 不显示只准备了一部分的结果；进程停止后图片缓存被清理时，恢复结果也会走此准备流程。
    val preparedRows by key(items, style, dark, density, rowWidth, fontScale) {
        produceState<List<Bitmap>?>(null, items, style, dark, density, rowWidth, fontScale) {
            value = try {
                prepareResultRows(items, style, dark, density, fontScale, rowWidth, loadBarcodeImage)
            } catch (error: CancellationException) {
                throw error
            } catch (_: OutOfMemoryError) {
                emptyList()
            } catch (_: Exception) {
                emptyList()
            }
        }
    }

    androidx.compose.runtime.DisposableEffect(preparedRows) {
        val ownedRows = preparedRows
        onDispose {
            ownedRows.orEmpty().forEach { if (!it.isRecycled) it.recycle() }
        }
    }

    val prepared = preparedRows
    if (prepared == null || prepared.size != items.size) {
        Column(
            Modifier.fillMaxWidth().statusBarsPadding().padding(top = dimensions.pageTopPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "生成结果",
                color = primary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp, bottom = 6.dp),
            )
            Text(
                if (preparedRows != null) "条码准备失败，请返回后重试" else "正在准备全部条码…",
                color = secondary,
                fontSize = 17.sp,
                modifier = Modifier.padding(vertical = 40.dp),
            )
        }
        return@BoxWithConstraints
    }

    // 页面切换动画期间，结果页与主界面可能短暂同时绘制，因此结果页必须使用独立的背景录制层，
    // 避免两个路由共享 GraphicsLayer 时同时写入同一个 RenderNode。
    val backdrop = rememberGlassBackdrop()
    val toolbarInitialHeightPx = with(LocalDensity.current) { 64.dp.roundToPx() }
    var toolbarSize by remember(toolbarInitialHeightPx) {
        androidx.compose.runtime.mutableStateOf(IntSize(0, toolbarInitialHeightPx))
    }
    val toolbarHeight = with(LocalDensity.current) { toolbarSize.height.toDp() }
    CompositionLocalProvider(LocalGlassBackdrop provides backdrop) {
    Box(Modifier.fillMaxSize()) {
        // 条码列表位于固定工具栏下方并独立滚动，顶部预留状态栏和工具栏高度以避免遮挡。
        LazyColumn(
            modifier = Modifier.fillMaxSize().recordGlassBackdrop(backdrop),
            contentPadding = PaddingValues(top = statusBarInset + toolbarHeight + 8.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(style.margin.coerceIn(0, 10).dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            itemsIndexed(items, key = { _, item -> item.id }) { index, item ->
                val bitmap = prepared.getOrNull(index)
                if (bitmap != null) Image(bitmap.asImageBitmap(), resultImageLabel(item, style.showFormat),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)
                        .aspectRatio(bitmap.width.toFloat() / bitmap.height))
            }
        }
        Box(
            Modifier.fillMaxWidth().align(Alignment.TopCenter).padding(top = statusBarInset)
                .onSizeChanged { toolbarSize = it },
        ) {
            ResultToolbar(
                exportAction = exportAction,
                isFavorite = isFavorite,
                onEdit = onEdit,
                onSaveFavorite = onSaveFavorite,
                onShare = onShare,
                onSave = onSave,
            )
        }
    }
    }
}
}

private suspend fun prepareResultRows(
    items: List<CodeItem>,
    style: StyleSettings,
    dark: Boolean,
    density: Float,
    fontScale: Float,
    rowWidth: Int,
    loadBarcodeImage: suspend (CodeItem, Boolean, Float) -> Bitmap?,
): List<Bitmap> {
    val createdRows = java.util.Collections.synchronizedList(mutableListOf<Bitmap>())
    return try {
        withContext(Dispatchers.Default.limitedParallelism(8)) {
            items.chunked(8).flatMap { batch ->
                coroutineScope {
                    batch.map { item ->
                        async {
                            val raw = checkNotNull(loadBarcodeImage(item, dark, density))
                            composeResultRowImage(raw, item, style, dark, rowWidth, density, fontScale)
                                .also(createdRows::add)
                        }
                    }.awaitAll()
                }
            }
        }
    } catch (error: Throwable) {
        val partiallyCreatedRows = synchronized(createdRows) { createdRows.toList() }
        partiallyCreatedRows.forEach { if (!it.isRecycled) it.recycle() }
        throw error
    }
}

// 按系统字体缩放标签宽度，同时限制最大值，保证手机上每组两个操作仍可使用。
internal fun resultToolbarActionWidth(fontScale: Float): Float =
    64f * (if (fontScale.isFinite()) fontScale else 1f).coerceIn(1f, 1.5f)

/** 结果页四个操作入口保持各自回调和按压状态，仅分享与保存互斥执行导出。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ResultToolbar(exportAction: ResultExportAction?, isFavorite: Boolean,
    onEdit: () -> Unit, onSaveFavorite: () -> Unit, onShare: () -> Unit, onSave: () -> Unit) {
    val themeColors = LocalAppColorScheme.current
    val actionWidth = resultToolbarActionWidth(LocalDensity.current.fontScale).dp
    val resultActionBlue = themeColors.controls.accent
    val favoriteActionIcon = if (isFavorite) FavoriteFilledIcon else FavoriteIcon
    // 仅分享和保存共用单次导出互斥状态；四个操作仍各自保留回调、组合身份和按压状态。
    val exportBusy = exportAction != null
    // 编辑把结果带回生成页，收藏进入文件夹/文件名选择，分享与保存分别触发对应导出方式。
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(0.dp, Alignment.End),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                key(ResultActionId.Edit) {
                    ResultAction(EditIcon, "编辑", resultActionBlue, onEdit, actionWidth = actionWidth)
                }
                key(ResultActionId.Favorite) {
                    ResultAction(
                        favoriteActionIcon,
                        "收藏",
                        if (isFavorite) themeColors.content.favoriteActive else resultActionBlue,
                        onSaveFavorite,
                        actionWidth = actionWidth,
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                key(ResultActionId.Share) {
                    ResultAction(
                        IosShareIcon,
                        if (exportAction == ResultExportAction.Share) "准备中…" else "分享",
                        resultActionBlue,
                        onShare,
                        enabled = !exportBusy,
                        busy = exportAction == ResultExportAction.Share,
                        actionWidth = actionWidth,
                    )
                }
                key(ResultActionId.Save) {
                    ResultAction(
                        DownloadIcon,
                        if (exportAction == ResultExportAction.Save) "准备中…" else "保存",
                        resultActionBlue,
                        onSave,
                        enabled = !exportBusy,
                        busy = exportAction == ResultExportAction.Save,
                        actionWidth = actionWidth,
                    )
                }
            }
    }
}

private enum class ResultActionId { Edit, Favorite, Share, Save }

@Composable
private fun ResultAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, tint: Color, onClick: () -> Unit,
    enabled: Boolean = true, busy: Boolean = false, actionWidth: androidx.compose.ui.unit.Dp = 64.dp) {
    GlassRoundActionButton(
        contentDescription = label,
        tint = tint,
        onClick = onClick,
        enabled = enabled,
        busy = busy,
        actionWidth = actionWidth,
    ) { contentTint ->
        if (busy) {
            androidx.compose.material3.CircularProgressIndicator(
                Modifier.width(22.dp).height(22.dp),
                color = contentTint,
                strokeWidth = 2.dp,
            )
        } else {
            Icon(icon, contentDescription = null, tint = contentTint, modifier = Modifier.width(24.dp).height(24.dp))
        }
    }
}
