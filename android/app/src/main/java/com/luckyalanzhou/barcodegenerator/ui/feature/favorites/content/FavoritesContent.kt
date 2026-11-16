package com.luckyalanzhou.barcodegenerator.ui.feature.favorites.content

import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import com.luckyalanzhou.barcodegenerator.R
import com.luckyalanzhou.barcodegenerator.ui.component.iosPressFeedback
import com.luckyalanzhou.barcodegenerator.ui.dialogs.DialogAction
import com.luckyalanzhou.barcodegenerator.ui.theme.*

import com.luckyalanzhou.barcodegenerator.domain.FavoriteGroup
import com.luckyalanzhou.barcodegenerator.domain.StyleSettings
import com.luckyalanzhou.barcodegenerator.ui.animation.ComposeAnimationConfig
import com.luckyalanzhou.barcodegenerator.ui.feature.favorites.FavoriteFolderRow
import com.luckyalanzhou.barcodegenerator.ui.feature.favorites.FavoriteGroupRow
import com.luckyalanzhou.barcodegenerator.ui.feature.favorites.shouldAnimateFavoriteRowChanges
import com.luckyalanzhou.barcodegenerator.ui.feature.favorites.tree.ComposeFavoriteRow
import com.luckyalanzhou.barcodegenerator.ui.feature.favorites.tree.FavoritesTreeData
import com.luckyalanzhou.barcodegenerator.ui.feature.favorites.tree.FavoritesRowsProjectionCache
import com.luckyalanzhou.barcodegenerator.ui.feature.favorites.tree.composeFavoriteRows
import com.luckyalanzhou.barcodegenerator.ui.feature.favorites.tree.effectiveCollapsedFavoriteFolders
import com.luckyalanzhou.barcodegenerator.ui.feature.favorites.tree.favoriteSearchExpandedPaths

import com.luckyalanzhou.barcodegenerator.icons.CreateNewFolderIcon
import com.luckyalanzhou.barcodegenerator.icons.DeleteIcon
import com.luckyalanzhou.barcodegenerator.icons.DriveFileMoveIcon
import com.luckyalanzhou.barcodegenerator.icons.EditIcon
import com.luckyalanzhou.barcodegenerator.icons.AttachFileIcon
import com.luckyalanzhou.barcodegenerator.icons.FolderIcon
import com.luckyalanzhou.barcodegenerator.icons.SearchIcon
import com.luckyalanzhou.barcodegenerator.icons.CloseSmallIcon
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion

import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext

/**
 * 收藏页界面协调器：按查询条件展示收藏文件与文件夹树，并处理展开折叠、滚动恢复和分页加载。
 * 文件/文件夹的重命名、移动、删除及条目编辑均通过回调交由上层执行，UI 不直接写持久化数据。
 */
@Composable
internal fun FavoritesContent(
    state: FavoritesContentState,
    scrollToTopEvents: Flow<Unit>,
    dark: Boolean,
    style: StyleSettings,
    onQueryChange: (String) -> Unit,
    onSearchFavoriteContent: (String) -> Unit,
    onSyncFavoriteTree: (Set<String>) -> Unit,
    onUpdateFavoriteSearch: (Set<String>, Boolean) -> Unit,
    onRememberListPosition: (Int, Int) -> Unit,
    onLoadMoreGroups: (String) -> Unit,
    onToggleFolder: (String, Set<String>) -> Unit,
    onOpenGroup: (FavoriteGroup, StyleSettings, Boolean, Float) -> Unit,
    onRenameFolder: suspend (String, String) -> Boolean,
    onDeleteFolder: suspend (String) -> Unit,
    onDeleteGroup: suspend (FavoriteGroup) -> Unit,
    onShowSubfolderEditor: (String) -> Unit,
    onShowFolderEditor: (String, suspend (String) -> Boolean) -> Unit,
    onEdit: (FavoriteGroup) -> Unit,
    onShowMoveDialog: (FavoriteGroup) -> Unit,
    onShowRenameDialog: (FavoriteGroup) -> Unit,
    onConfirm: (String, String, String, suspend () -> Unit) -> Unit,
    onRetryLoad: () -> Unit = {},
    rowsProjectionCache: FavoritesRowsProjectionCache? = null,
    fileCounts: Map<String, Int>? = null,
    folderDeletionCount: (String) -> Int? = { null },
    listBottomPadding: Dp = 8.dp,
) {
    val localizedResources = LocalResources.current
    val favoritesState = state.favorites
    val searchState = state.searchResults
    val searchStatus = state.search
    val treeState = state.tree
    val query = state.query
    val savedListPosition = state.savedListPosition
    val searchRefreshGeneration = state.searchRefreshGeneration
    val density = LocalDensity.current.density
    val animation = ComposeAnimationConfig
    val reduceMotion = LocalVisualEffectsPolicy.current.reduceMotion
    val themeColors = LocalAppColorScheme.current
    val primary = themeColors.text.primary
    val secondary = themeColors.text.secondary
    val rootFolderColor = themeColors.content.folder
    val childFolderColor = themeColors.content.childFolder
    val fileColor = themeColors.content.file
    val favoriteListState = rememberLazyListState()
    val searchListState = rememberLazyListState()
    val listState = if (query.trim().isEmpty()) favoriteListState else searchListState
    var listPositionRestored by rememberSaveable { mutableStateOf(false) }
    var rowAnimationActive by remember { mutableStateOf(false) }
    var rowAnimationGeneration by remember { mutableIntStateOf(0) }
    val normalizedQuery = query.trim().lowercase(Locale.ROOT)
    val searchPending = searchStatus.isPending(normalizedQuery)
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val displayState = if (normalizedQuery.isEmpty()) favoritesState else searchState
    val displayTreeData = remember(displayState.items, displayState.groups, displayState.folders) {
        FavoritesTreeData(
            items = displayState.items,
            groups = displayState.groups,
            folders = displayState.folders,
        )
    }
    val allFavoriteFolderPaths = remember(favoritesState.folders, favoritesState.groups) {
        (favoritesState.folders + favoritesState.groups.map { it.folder })
            .filter { it.isNotBlank() }.toSet()
    }
    val folderPaths = remember(displayState.folders, displayState.groups) {
        (displayState.folders + displayState.groups.map { it.folder })
            .filter { it.isNotBlank() }.distinct().toSet()
    }
    val expandedSearchPaths by produceState<Set<String>>(emptySet(), displayTreeData, normalizedQuery) {
        value = withContext(Dispatchers.Default) {
            favoriteSearchExpandedPaths(displayTreeData, normalizedQuery)
        }
    }

    LaunchedEffect(allFavoriteFolderPaths) { onSyncFavoriteTree(allFavoriteFolderPaths) }
    LaunchedEffect(normalizedQuery, favoritesState, searchRefreshGeneration) {
        onSearchFavoriteContent(normalizedQuery)
    }
    LaunchedEffect(normalizedQuery, expandedSearchPaths) { onUpdateFavoriteSearch(expandedSearchPaths, normalizedQuery.isNotEmpty()) }

    LaunchedEffect(favoriteListState, listPositionRestored) {
        if (!listPositionRestored) return@LaunchedEffect
        snapshotFlow { favoriteListState.firstVisibleItemIndex to favoriteListState.firstVisibleItemScrollOffset }
            .distinctUntilChanged()
            .collect { (index, offset) -> onRememberListPosition(index, offset) }
    }

    LaunchedEffect(favoriteListState, listPositionRestored, scrollToTopEvents, reduceMotion) {
        if (!listPositionRestored) return@LaunchedEffect
        scrollToTopEvents.collect {
            if (reduceMotion) favoriteListState.scrollToItem(0) else favoriteListState.animateScrollToItem(0)
        }
    }

    LaunchedEffect(rowAnimationGeneration) {
        if (rowAnimationGeneration == 0) return@LaunchedEffect
        delay(maxOf(
            ComposeAnimationConfig.favoriteRowFadeDurationMillis,
            ComposeAnimationConfig.favoriteRowExpandDurationMillis,
            ComposeAnimationConfig.favoriteRowCollapseDurationMillis,
        ).toLong() + 32L)
        rowAnimationActive = false
    }

    val visibleCollapsedFolders = effectiveCollapsedFavoriteFolders(
        treeState = treeState,
        allFolderPaths = allFavoriteFolderPaths,
        query = normalizedQuery,
        expandedSearchPaths = expandedSearchPaths,
    )
    val cachedRows = rowsProjectionCache?.find(displayTreeData, normalizedQuery, visibleCollapsedFolders)
    val rows by produceState<List<ComposeFavoriteRow>?>(
        cachedRows,
        displayTreeData,
        normalizedQuery,
        visibleCollapsedFolders,
        rowsProjectionCache,
    ) {
        rowsProjectionCache?.find(displayTreeData, normalizedQuery, visibleCollapsedFolders)?.let {
            value = it
            return@produceState
        }
        val projectedRows = withContext(Dispatchers.Default) {
            composeFavoriteRows(displayTreeData, normalizedQuery, visibleCollapsedFolders)
        }
        rowsProjectionCache?.store(displayTreeData, normalizedQuery, visibleCollapsedFolders, projectedRows)
        value = projectedRows
    }
    val rowKey: (ComposeFavoriteRow) -> String = { row ->
        if (row.folder) "folder-${row.path}" else "group-${row.group?.id}"
    }
    var displayedRows by remember { mutableStateOf<List<ComposeFavoriteRow>?>(null) }
    LaunchedEffect(rows, searchPending, searchStatus.failed) {
        // 查询或失败反馈期间保留最近一份可用列表，避免搜索状态把页面内容整体替换掉。
        if (normalizedQuery.isNotEmpty() && (searchPending || searchStatus.failed)) return@LaunchedEffect
        val targetRows = rows ?: return@LaunchedEffect
        displayedRows = targetRows
    }
    val rowsForDisplay = displayedRows ?: rows

    LaunchedEffect(favoritesState.isReady, rows, normalizedQuery) {
        if (!favoritesState.isReady) return@LaunchedEffect
        if (listPositionRestored || normalizedQuery.isNotEmpty()) return@LaunchedEffect
        val targetRows = rows ?: return@LaunchedEffect
        val lastAvailableIndex = targetRows.lastIndex.coerceAtLeast(0)
        val targetIndex = savedListPosition.first.coerceIn(0, lastAvailableIndex)
        favoriteListState.scrollToItem(targetIndex, savedListPosition.second)
        listPositionRestored = true
    }

    LaunchedEffect(listState, rows, normalizedQuery, searchPending, searchStatus.failed) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .distinctUntilChanged()
            .collect { lastVisible ->
                if (searchPending || searchStatus.failed) return@collect
                val currentRows = rows ?: return@collect
                if (lastVisible >= currentRows.size - 5) onLoadMoreGroups(normalizedQuery)
            }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .border(1.dp, themeColors.borders.input, RoundedCornerShape(14.dp))
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = SearchIcon,
                        contentDescription = null,
                        tint = themeColors.text.secondary,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    // 搜索覆盖收藏文件名、文件夹路径和条码内容；提交键只收起键盘，不创建第二份搜索状态。
                    BasicTextField(
                        value = query,
                        onValueChange = onQueryChange,
                        modifier = Modifier.weight(1f).heightIn(min = 28.dp)
                            .padding(vertical = 8.dp).semantics { contentDescription = localizedResources.getString(R.string.favorite_search_description) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = {
                            keyboardController?.hide()
                            focusManager.clearFocus()
                        }),
                        textStyle = TextStyle(
                            color = primary,
                            fontFamily = MiSansFontFamily,
                            fontSize = 18.sp,
                            lineHeight = 24.sp,
                            platformStyle = PlatformTextStyle(includeFontPadding = false),
                            lineHeightStyle = LineHeightStyle(
                                alignment = LineHeightStyle.Alignment.Center,
                                trim = LineHeightStyle.Trim.Both,
                            ),
                        ),
                        cursorBrush = SolidColor(primary),
                        decorationBox = { field ->
                            Box(
                                modifier = Modifier.fillMaxWidth().heightIn(min = 28.dp),
                                contentAlignment = Alignment.CenterStart,
                            ) {
                                if (query.isEmpty()) {
                                    Text(
                                        stringResource(R.string.favorite_search_placeholder),
                                        style = TextStyle(
                                            color = themeColors.text.secondary,
                                            fontFamily = MiSansFontFamily,
                                            fontSize = 14.sp,
                                            lineHeight = 20.sp,
                                            platformStyle = PlatformTextStyle(includeFontPadding = false),
                                            lineHeightStyle = LineHeightStyle(
                                                alignment = LineHeightStyle.Alignment.Center,
                                                trim = LineHeightStyle.Trim.Both,
                                            ),
                                        ),
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Clip,
                                    )
                                }
                                field()
                            }
                        },
                    )
                    if (query.isNotEmpty()) {
                        val clearSearchInteractions = remember { MutableInteractionSource() }
                        IconButton(
                            onClick = { onQueryChange("") },
                            interactionSource = clearSearchInteractions,
                            modifier = Modifier.size(48.dp).iosPressFeedback(clearSearchInteractions),
                        ) {
                            Box(
                                Modifier.size(24.dp).background(
                                    themeColors.text.secondary.copy(alpha = .20f),
                                    androidx.compose.foundation.shape.CircleShape,
                                ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = CloseSmallIcon,
                                    contentDescription = stringResource(R.string.clear_search),
                                    tint = themeColors.text.primary,
                                    modifier = Modifier.requiredSize(28.dp),
                                )
                            }
                        }
                    }
                }
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = listBottomPadding),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {

        if (!favoritesState.isReady) {
            item(key = "favorite-loading") {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = 40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        if (favoritesState.loadFailed) stringResource(R.string.favorites_load_failed) else stringResource(R.string.favorites_loading),
                        color = if (favoritesState.loadFailed) primary else secondary,
                        fontSize = 17.sp,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        textAlign = TextAlign.Center,
                    )
                    if (favoritesState.loadFailed) {
                        DialogAction(stringResource(R.string.action_retry), dark, onRetryLoad, modifier = Modifier.padding(top = 12.dp), primary = true)
                    }
                }
            }
        } else if (normalizedQuery.isNotEmpty() && (searchPending || searchStatus.failed)) {
            item(key = "favorite-search-status") {
                Text(if (searchPending) stringResource(R.string.searching) else stringResource(R.string.favorite_search_failed),
                    color = secondary, fontSize = 17.sp,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite },
                    textAlign = TextAlign.Center)
            }
        }
        if (rowsForDisplay == null) {
            // 文件夹树投影在后台线程计算；短暂重组期间保持页面稳定，避免闪现占位内容。
        } else if (rowsForDisplay.isEmpty() && favoritesState.isReady && !(normalizedQuery.isNotEmpty() && (searchPending || searchStatus.failed))) {
            item(key = "favorite-empty") {
                Text(
                    stringResource(favoriteEmptyMessage(normalizedQuery)),
                    color = secondary,
                    fontSize = 17.sp,
                    modifier = Modifier.fillMaxWidth().padding(top = 40.dp),
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            items(
                items = rowsForDisplay,
                key = rowKey,
                contentType = { row -> if (row.folder) "folder" else "favorite-group" },
            ) { row ->
                val animateRowChanges = shouldAnimateFavoriteRowChanges(rowAnimationActive, reduceMotion)
                val rowModifier = Modifier
                    .fillMaxWidth()
                    .animateItem(
                        fadeInSpec = if (animateRowChanges) tween(
                            durationMillis = ComposeAnimationConfig.favoriteRowFadeDurationMillis,
                            easing = FastOutSlowInEasing,
                        ) else null,
                        placementSpec = if (animateRowChanges) tween(
                            durationMillis = ComposeAnimationConfig.favoriteRowExpandDurationMillis,
                            easing = FastOutSlowInEasing,
                        ) else null,
                        fadeOutSpec = if (animateRowChanges) tween(
                            durationMillis = ComposeAnimationConfig.favoriteRowCollapseDurationMillis,
                        ) else null,
                    )
                    .background(themeColors.surfaces.background)
                    .then(if (normalizedQuery.isEmpty() && row.treeBranches.isNotEmpty()) Modifier.drawBehind {
                        val lineColor = secondary.copy(alpha = .28f)
                        val middle = (size.height - 6.dp.toPx()) / 2
                        val stroke = 1.dp.toPx()
                        row.treeBranches.forEachIndexed { depth, continues ->
                            val current = depth == row.treeBranches.lastIndex
                            if (current || continues) {
                                val start = (24.dp + 20.dp * depth).toPx()
                                val rtl = layoutDirection == LayoutDirection.Rtl
                                val x = if (rtl) size.width - start else start
                                drawLine(lineColor, Offset(x, 0f), Offset(x, if (continues) size.height else middle), stroke)
                                if (current) {
                                    val end = if (rtl) x - 7.dp.toPx() else x + 7.dp.toPx()
                                    drawLine(lineColor, Offset(x, middle), Offset(end, middle), stroke)
                                }
                            }
                        }
                    } else Modifier)
                    .padding(bottom = 6.dp)
                // 点击文件夹只切换展开状态；打开文件前先记住滚动位置，返回收藏树时恢复原位置。
                if (row.folder) {
                    FavoriteFolderRow(
                        row = row,
                        modifier = rowModifier,
                        dark = dark,
                        secondary = secondary,
                        folderColor = if (row.level == 0) rootFolderColor else childFolderColor,
                        animation = animation,
                        animateTreeChanges = animateRowChanges,
                        onClick = {
                            if (!reduceMotion) {
                                rowAnimationActive = true
                                rowAnimationGeneration++
                            }
                            onToggleFolder(row.path, folderPaths)
                        },
                        onShowSubfolderEditor = onShowSubfolderEditor,
                        onShowFolderEditor = onShowFolderEditor,
                        onConfirm = onConfirm,
                        onRenameFolder = onRenameFolder,
                        onDeleteFolder = onDeleteFolder,
                        fileCount = fileCounts?.getOrDefault(row.path, 0),
                        folderDeletionCount = folderDeletionCount,
                    )
                } else {
                    row.group?.let { group ->
                        FavoriteGroupRow(
                            row = row,
                            modifier = rowModifier,
                            group = group,
                            dark = dark,
                            secondary = secondary,
                            fileColor = fileColor,
                            animation = animation,
                            onClick = {
                                if (normalizedQuery.isEmpty()) {
                                    onRememberListPosition(
                                        listState.firstVisibleItemIndex,
                                        listState.firstVisibleItemScrollOffset,
                                    )
                                }
                                onOpenGroup(group, style, dark, density)
                            },
                            onShowMoveDialog = onShowMoveDialog,
                            onShowRenameDialog = onShowRenameDialog,
                            onEdit = onEdit,
                            onConfirm = onConfirm,
                            onDelete = onDeleteGroup,
                            searchPath = group.folder.replace("/", " / ").takeIf { normalizedQuery.isNotEmpty() },
                        )
                    }
                        }
                    }
                }
        if (searchStatus.paginationFailed) {
            item(key = "favorite-pagination-retry") {
                TextButton(
                    onClick = { onLoadMoreGroups(normalizedQuery) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                ) {
                    Text(stringResource(R.string.load_failed_retry), color = secondary, fontSize = 14.sp)
                }
            }
        }
    }
}
}
