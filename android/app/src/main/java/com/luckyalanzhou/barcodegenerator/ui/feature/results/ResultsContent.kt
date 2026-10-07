package com.luckyalanzhou.barcodegenerator.ui.feature.results

import com.luckyalanzhou.barcodegenerator.presentation.*
import com.luckyalanzhou.barcodegenerator.ui.app.*
import com.luckyalanzhou.barcodegenerator.ui.theme.*

import com.luckyalanzhou.barcodegenerator.presentation.settings.SettingsUiState
import com.luckyalanzhou.barcodegenerator.presentation.ResultUiState
import com.luckyalanzhou.barcodegenerator.presentation.navigation.NavigationRoute
import com.luckyalanzhou.barcodegenerator.domain.CodeItem

import com.luckyalanzhou.barcodegenerator.icons.EditIcon
import com.luckyalanzhou.barcodegenerator.icons.FavoriteIcon
import com.luckyalanzhou.barcodegenerator.icons.FavoriteFilledIcon
import com.luckyalanzhou.barcodegenerator.icons.IosShareIcon
import com.luckyalanzhou.barcodegenerator.icons.DownloadIcon
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.semantics.Role

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.CircleShape
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.collect

@Composable
internal fun ResultsContent(
    exportAction: ResultExportAction?,
    resultState: ResultUiState,
    settings: SettingsUiState,
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
    val items = resultState.items
    val density = LocalDensity.current.density
    val fontScale = LocalDensity.current.fontScale
    val statusBarInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val rowWidth = with(LocalDensity.current) { (maxWidth - 24.dp).roundToPx().coerceAtLeast(1) }
    SideEffect { onImageWidthChanged(rowWidth) }
    val isFavorite = resultState.hasSavedFavoriteFile()

    if (items.isEmpty()) {
        Column(Modifier.fillMaxWidth().statusBarsPadding().padding(top = dimensions.pageTopPadding), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("生成结果", color = primary, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp, bottom = 6.dp))
            Text(
                when {
                    resultState.isRestoring -> "正在恢复上次结果…"
                    resultState.restoreFailed -> "上次结果已不可用，请返回重新打开"
                    else -> "暂无生成结果"
                },
                color = secondary,
                fontSize = 17.sp,
                modifier = Modifier.padding(vertical = 40.dp),
            )
        }
        return@BoxWithConstraints
    }

    // Do not reveal a partially populated result list. This also covers restored results
    // whose image cache may have been cleared while the app was stopped.
    val preparedRows by key(items, settings.style, dark, density, rowWidth, fontScale) {
      produceState<List<Bitmap>?>(
        initialValue = null,
        items,
        settings.style,
        dark,
        density,
        rowWidth,
        fontScale,
    ) {
        value = try {
            withContext(Dispatchers.Default.limitedParallelism(8)) {
                items.chunked(8).flatMap { batch ->
                    coroutineScope {
                        batch.map { item ->
                            async {
                                val raw = checkNotNull(loadBarcodeImage(item, dark, density))
                                composeResultRowImage(raw, item, settings.style, dark, rowWidth, density, fontScale)
                            }
                        }.awaitAll()
                    }
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            emptyList()
        }
    }
    }

    if (preparedRows?.size != items.size) {
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

    val backdrop = LocalGlassBackdrop.current ?: rememberGlassBackdrop()
    val toolbarInitialHeightPx = with(LocalDensity.current) { 72.dp.roundToPx() }
    var toolbarSize by remember(toolbarInitialHeightPx) {
        androidx.compose.runtime.mutableStateOf(IntSize(0, toolbarInitialHeightPx))
    }
    val toolbarHeight = with(LocalDensity.current) { toolbarSize.height.toDp() }
    CompositionLocalProvider(LocalGlassBackdrop provides backdrop) {
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().recordGlassBackdrop(backdrop),
            contentPadding = PaddingValues(top = statusBarInset + toolbarHeight + 8.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(settings.style.margin.coerceIn(0, 10).dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            itemsIndexed(items, key = { _, item -> item.id }) { index, item ->
                Image(preparedRows!![index].asImageBitmap(), resultImageLabel(item, settings.style.showFormat),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)
                        .aspectRatio(preparedRows!![index].width.toFloat() / preparedRows!![index].height))
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

// Grow labels with the system font scale, but keep each two-action group usable on a phone.
internal fun resultToolbarActionWidth(fontScale: Float): Float =
    64f * (if (fontScale.isFinite()) fontScale else 1f).coerceIn(1f, 1.5f)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ResultToolbar(exportAction: ResultExportAction?, isFavorite: Boolean,
    onEdit: () -> Unit, onSaveFavorite: () -> Unit, onShare: () -> Unit, onSave: () -> Unit) {
    val themeColors = LocalAppColorScheme.current
    val actionWidth = resultToolbarActionWidth(LocalDensity.current.fontScale).dp
    val resultActionBlue = themeColors.controls.accent
    val favoriteActionIcon = if (isFavorite) FavoriteFilledIcon else FavoriteIcon
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ResultAction(EditIcon, "编辑", resultActionBlue, onClick = onEdit, actionWidth = actionWidth)
                ResultAction(
                    favoriteActionIcon,
                    "收藏",
                    if (isFavorite) themeColors.content.favoriteActive else resultActionBlue,
                    onSaveFavorite,
                    actionWidth = actionWidth,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                ResultAction(IosShareIcon, if (exportAction == ResultExportAction.Share) "准备中…" else "分享", resultActionBlue,
                    onShare, enabled = exportAction == null, busy = exportAction == ResultExportAction.Share, actionWidth = actionWidth)
                ResultAction(DownloadIcon, if (exportAction == ResultExportAction.Save) "准备中…" else "保存", resultActionBlue,
                    onSave, enabled = exportAction == null, busy = exportAction == ResultExportAction.Save, actionWidth = actionWidth)
            }
    }
}

internal fun ResultUiState.hasSavedFavoriteFile(): Boolean =
    selectedFavoriteGroup != null && returnPage == NavigationRoute.Favorites

@Composable
private fun ResultAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, tint: Color, onClick: () -> Unit,
    enabled: Boolean = true, busy: Boolean = false, actionWidth: androidx.compose.ui.unit.Dp = 64.dp) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    var pressPosition by remember(interaction) { mutableStateOf<Offset?>(null) }
    LaunchedEffect(interaction) {
        interaction.interactions.collect { event ->
            if (event is PressInteraction.Press) pressPosition = event.pressPosition
        }
    }
    val colors = LocalAppColorScheme.current
    val effects = LocalVisualEffectsPolicy.current
    val density = LocalDensity.current.density
    val glassRenderer = rememberGlassBackdropRenderer()
    val backdropAvailable = glassBackdropAvailable(effects, glassRenderer)
    val glassMaterial = resultActionGlassMaterial(colors.surfaces.background).let {
        if (effects.opaqueGlass) it.copy(bodyTintStrength = 0f, accentTint = 0f, surfaceOpacity = 1f) else it
    }
    val glassColor = tabGlassFill(colors.surfaces.background, tint, glassMaterial)
    val opticalActivity by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (pressed && !effects.reduceMotion && !effects.opaqueGlass) 1f else 0f,
        animationSpec = if (effects.reduceMotion) androidx.compose.animation.core.tween(0)
            else ComposeAnimationConfig.pressSpring(),
        label = "result-action-glass-interaction",
    )
    val contentTint = if (enabled || busy) tint else LocalAppColorScheme.current.text.disabled
    Column(
        Modifier.width(actionWidth).heightIn(min = 68.dp)
            .clickable(enabled = enabled, interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.width(48.dp).height(48.dp)
                .graphicsLayer {
                    val lift = opticalActivity
                    scaleX = 1f + .035f * lift
                    scaleY = 1f + .035f * lift
                    translationY = -1.5f * density * lift
                    shadowElevation = (1f + 2f * lift) * density
                    shape = CircleShape
                }
                .clip(CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            val circlePx = 48f * density
            val actionWidthPx = actionWidth.value * density
            val touchPoint = pressPosition?.let {
                Offset(
                    x = (it.x - (actionWidthPx - circlePx) * .5f).coerceIn(0f, circlePx),
                    y = it.y.coerceIn(0f, circlePx),
                )
            }
            val actionFrame = {
                resultActionGlassFrame(
                    width = circlePx,
                    height = circlePx,
                    density = density,
                    activity = opticalActivity,
                    touch = touchPoint,
                )
            }
            GlassBackdropSurface(
                modifier = Modifier.matchParentSize(),
                color = glassColor,
                opacity = glassMaterial.surfaceOpacity,
                cornerDp = 24f,
                blurDp = GlassControlDefaults.RoundActionBlurDp,
                refractionDp = { actionFrame().refractionPx / density },
                capsule = actionFrame,
                drawFallback = true,
                renderer = glassRenderer,
            )
            androidx.compose.foundation.Canvas(Modifier.matchParentSize()) {
                val activity = opticalActivity
                if (!backdropAvailable || effects.highContrast) {
                    drawGlassControlBevel(
                        Offset.Zero, size, size.minDimension * .5f, glassMaterial,
                        colors.surfaces.background, contentTint, density, effects.highContrast,
                    )
                }
                if (!backdropAvailable && activity > .01f) {
                    val center = touchPoint ?: Offset(size.width * .5f, size.height * .5f)
                    val radius = size.minDimension * .68f
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(Color.White.copy(alpha = .13f * activity), Color.Transparent),
                            center = center,
                            radius = radius,
                        ),
                        radius = radius,
                        center = center,
                    )
                }
            }
            if (busy) androidx.compose.material3.CircularProgressIndicator(Modifier.width(22.dp).height(22.dp), color = contentTint, strokeWidth = 2.dp)
            else Icon(icon, contentDescription = null, tint = contentTint, modifier = Modifier.width(24.dp).height(24.dp))
        }
        Text(label, color = contentTint, fontSize = 12.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center,
            maxLines = 1, modifier = Modifier.padding(top = 4.dp))
    }
}

internal fun resultActionGlassFrame(
    width: Float,
    height: Float,
    density: Float,
    activity: Float,
    touch: Offset?,
): TabGlassFrame {
    val safeWidth = width.takeIf(Float::isFinite)?.coerceAtLeast(1f) ?: 1f
    val safeHeight = height.takeIf(Float::isFinite)?.coerceAtLeast(1f) ?: 1f
    val safeDensity = density.takeIf(Float::isFinite)?.coerceAtLeast(.1f) ?: .1f
    val centerX = safeWidth * .5f
    val centerY = safeHeight * .5f
    val strength = activity.takeIf(Float::isFinite)?.coerceIn(0f, 1f) ?: 0f
    return TabGlassFrame(
        width = safeWidth,
        height = safeHeight,
        centerX = centerX,
        centerY = centerY,
        halfWidth = centerX,
        halfHeight = centerY,
        motion = strength,
        refractionPx = (GlassControlDefaults.RoundActionRestRefractionDp +
            GlassControlDefaults.RoundActionPressRefractionDp * strength) * safeDensity,
        density = safeDensity,
        touchX = touch?.x?.takeIf(Float::isFinite)?.coerceIn(0f, safeWidth) ?: centerX,
        touchY = touch?.y?.takeIf(Float::isFinite)?.coerceIn(0f, safeHeight) ?: centerY,
        contactSpread = .72f,
        travelStrength = strength,
    )
}
