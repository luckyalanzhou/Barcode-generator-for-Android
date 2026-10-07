package com.luckyalanzhou.barcodegenerator.ui.feature.favorites.content

import com.luckyalanzhou.barcodegenerator.ui.theme.*

import com.luckyalanzhou.barcodegenerator.domain.FavoriteGroup
import com.luckyalanzhou.barcodegenerator.domain.StyleSettings
import com.luckyalanzhou.barcodegenerator.ui.animation.ComposeAnimationConfig
import com.luckyalanzhou.barcodegenerator.ui.feature.favorites.FavoriteFolderRow
import com.luckyalanzhou.barcodegenerator.ui.feature.favorites.FavoriteGroupRow
import com.luckyalanzhou.barcodegenerator.ui.feature.favorites.favoriteRowInitiallyVisible
import com.luckyalanzhou.barcodegenerator.ui.feature.favorites.tree.ComposeFavoriteRow
import com.luckyalanzhou.barcodegenerator.ui.feature.favorites.tree.FavoritesTreeData
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
import androidx.compose.material3.IconButton
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext

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
    onRenameFolder: (String, String) -> Unit,
    onDeleteFolder: (String) -> Unit,
    onDeleteGroup: (FavoriteGroup) -> Unit,
    onClearAll: () -> Unit,
    onShowSubfolderEditor: (String) -> Unit,
    onShowFolderEditor: (String, (String) -> Unit) -> Unit,
    onEdit: (FavoriteGroup) -> Unit,
    onShowMoveDialog: (FavoriteGroup) -> Unit,
    onShowRenameDialog: (FavoriteGroup) -> Unit,
    onConfirm: (String, String, String, () -> Unit) -> Unit,
) {
    val favoritesState = state.favorites
    val searchState = state.searchResults
    val searchStatus = state.search
    val treeState = state.tree
    val query = state.query
    val savedListPosition = state.savedListPosition
    val density = LocalDensity.current.density
    val animation = ComposeAnimationConfig
    val reduceMotion = LocalVisualEffectsPolicy.current.reduceMotion
    val themeColors = LocalAppColorScheme.current
    val primary = themeColors.text.primary
    val secondary = themeColors.text.secondary
    val rootFolderColor = themeColors.content.folder
    val childFolderColor = themeColors.content.childFolder
    val fileColor = themeColors.content.file
    val listState = rememberLazyListState()
    var listPositionRestored by remember { mutableStateOf(false) }
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
    LaunchedEffect(normalizedQuery, favoritesState) { onSearchFavoriteContent(normalizedQuery) }
    LaunchedEffect(normalizedQuery, expandedSearchPaths) { onUpdateFavoriteSearch(expandedSearchPaths, normalizedQuery.isNotEmpty()) }

    LaunchedEffect(listState, listPositionRestored) {
        if (!listPositionRestored) return@LaunchedEffect
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .distinctUntilChanged()
            .collect { (index, offset) -> onRememberListPosition(index, offset) }
    }

    LaunchedEffect(listState, listPositionRestored, scrollToTopEvents, reduceMotion) {
        if (!listPositionRestored) return@LaunchedEffect
        scrollToTopEvents.collect {
            if (reduceMotion) listState.scrollToItem(0) else listState.animateScrollToItem(0)
        }
    }

    val visibleCollapsedFolders = effectiveCollapsedFavoriteFolders(
        treeState = treeState,
        allFolderPaths = allFavoriteFolderPaths,
        query = normalizedQuery,
        expandedSearchPaths = expandedSearchPaths,
    )
    val rows by produceState<List<ComposeFavoriteRow>?>(null, displayTreeData, normalizedQuery, visibleCollapsedFolders) {
        value = withContext(Dispatchers.Default) {
            composeFavoriteRows(displayTreeData, normalizedQuery, visibleCollapsedFolders)
        }
    }
    val rowKey: (ComposeFavoriteRow) -> String = { row ->
        if (row.folder) "folder-${row.path}" else "group-${row.group?.id}"
    }
    var displayedRows by remember { mutableStateOf<List<ComposeFavoriteRow>?>(null) }
    var targetRowKeys by remember { mutableStateOf<Set<String>>(emptySet()) }
    var enteringRowKeys by remember { mutableStateOf<Set<String>>(emptySet()) }
    LaunchedEffect(enteringRowKeys) {
        if (enteringRowKeys.isNotEmpty()) {
            delay(ComposeAnimationConfig.favoriteRowExpandDurationMillis.toLong())
            enteringRowKeys = emptySet()
        }
    }

    LaunchedEffect(rows, listPositionRestored, reduceMotion) {
        val targetRows = rows ?: return@LaunchedEffect
        val targetKeys = targetRows.mapTo(mutableSetOf(), rowKey)
        val previousRows = displayedRows
        val previousKeys = previousRows.orEmpty().mapTo(mutableSetOf(), rowKey)
        val removedKeys = previousKeys - targetKeys
        val addedKeys = targetKeys - previousKeys
        enteringRowKeys = if (previousRows != null && listPositionRestored) addedKeys else emptySet()
        targetRowKeys = targetKeys

        if (reduceMotion || previousRows == null || !listPositionRestored || removedKeys.isEmpty() || addedKeys.isNotEmpty()) {
            displayedRows = targetRows
            return@LaunchedEffect
        }

        // Keep collapsing rows composed until their height transition finishes;
        // LazyColumn remains virtualized, and a rapid re-expand cancels this delay.
        delay(ComposeAnimationConfig.favoriteRowCollapseDurationMillis + ComposeAnimationConfig.favoriteRowRemovalBufferMillis)
        displayedRows = targetRows
    }
    val rowsForDisplay = displayedRows ?: rows

    LaunchedEffect(favoritesState.isReady, rows) {
        val targetRows = rows ?: return@LaunchedEffect
        if (favoritesState.isReady && !listPositionRestored) {
            val lastAvailableIndex = targetRows.lastIndex.coerceAtLeast(0)
            val targetIndex = savedListPosition.first.coerceIn(0, lastAvailableIndex)
            listState.scrollToItem(targetIndex, savedListPosition.second)
            listPositionRestored = true
        }
    }

    LaunchedEffect(listState, rows, normalizedQuery) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .distinctUntilChanged()
            .collect { lastVisible ->
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
                    BasicTextField(
                        value = query,
                        onValueChange = onQueryChange,
                        modifier = Modifier.weight(1f).heightIn(min = 28.dp)
                            .padding(vertical = 8.dp).semantics { contentDescription = "搜索收藏名称、文件夹或条码内容" },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = {
                            keyboardController?.hide()
                            focusManager.clearFocus()
                        }),
                        textStyle = TextStyle(
                            color = primary,
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
                                        "搜索名称、文件夹或内容",
                                        style = TextStyle(
                                            color = themeColors.text.placeholder,
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
                        IconButton(onClick = { onQueryChange("") }, modifier = Modifier.size(48.dp)) {
                            Icon(CloseSmallIcon, contentDescription = "清除搜索文字",
                                tint = themeColors.text.secondary, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
            TextButton(onClick = onClearAll, modifier = Modifier.padding(start = 4.dp)) {
                Text("清空收藏", color = themeColors.text.destructive, fontSize = 14.sp)
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {

        if (!favoritesState.isReady) {
            item(key = "favorite-loading") {
                Text(
                    "正在加载收藏…",
                    color = secondary,
                    fontSize = 17.sp,
                    modifier = Modifier.fillMaxWidth().padding(top = 40.dp),
                    textAlign = TextAlign.Center,
                )
            }
        } else if (normalizedQuery.isNotEmpty() && (searchPending || searchStatus.failed)) {
            item(key = "favorite-search-status") {
                Text(if (searchPending) "正在搜索…" else "搜索失败，请修改搜索词重试",
                    color = secondary, fontSize = 17.sp,
                    modifier = Modifier.fillMaxWidth().padding(top = 40.dp), textAlign = TextAlign.Center)
            }
        } else if (rowsForDisplay == null) {
            // The tree projection is computed off the main thread; keep the page quiet
            // during the short recomposition instead of showing a flashing placeholder.
        } else if (rowsForDisplay.isEmpty()) {
            item(key = "favorite-empty") {
                Text(
                    favoriteEmptyMessage(normalizedQuery),
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
                val key = rowKey(row)
                val targetVisible = !listPositionRestored || key in targetRowKeys
                val visibility = remember(key) {
                    // Off-screen rows returning during scroll are already visible.
                    MutableTransitionState(favoriteRowInitiallyVisible(key, enteringRowKeys))
                }
                LaunchedEffect(targetVisible) {
                    visibility.targetState = targetVisible
                }
                AnimatedVisibility(
                    visibleState = visibility,
                    // One size transition only: do not also animate placement,
                    // which causes following rows to chase their moving layout.
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(themeColors.surfaces.background),
                    enter = if (reduceMotion) androidx.compose.animation.EnterTransition.None else expandVertically(
                        expandFrom = Alignment.Top,
                        animationSpec = tween(
                            durationMillis = ComposeAnimationConfig.favoriteRowExpandDurationMillis,
                            easing = FastOutSlowInEasing,
                        ),
                    ),
                    exit = if (reduceMotion) androidx.compose.animation.ExitTransition.None else shrinkVertically(
                        shrinkTowards = Alignment.Top,
                        animationSpec = tween(
                            durationMillis = ComposeAnimationConfig.favoriteRowCollapseDurationMillis,
                            easing = FastOutLinearInEasing,
                        ),
                    ),
                ) {
                  // Cache the full row display list while its outer height is animated.
                  // Keep placement disabled: neighboring rows must not chase layout changes.
                  Column(Modifier.graphicsLayer().padding(bottom = 6.dp)) {
                    if (row.folder) {
                        FavoriteFolderRow(
                            row = row,
                            dark = dark,
                            secondary = secondary,
                            folderColor = if (row.level == 0) rootFolderColor else childFolderColor,
                            animation = animation,
                            onClick = { onToggleFolder(row.path, folderPaths) },
                            onShowSubfolderEditor = onShowSubfolderEditor,
                            onShowFolderEditor = onShowFolderEditor,
                            onConfirm = onConfirm,
                            onRenameFolder = onRenameFolder,
                            onDeleteFolder = onDeleteFolder,
                        )
                    } else {
                        row.group?.let { group ->
                            FavoriteGroupRow(
                                row = row,
                                group = group,
                                dark = dark,
                                secondary = secondary,
                                fileColor = fileColor,
                                animation = animation,
                                onClick = {
                                    onRememberListPosition(
                                        listState.firstVisibleItemIndex,
                                        listState.firstVisibleItemScrollOffset,
                                    )
                                    onOpenGroup(group, style, dark, density)
                                },
                                onShowMoveDialog = onShowMoveDialog,
                                onShowRenameDialog = onShowRenameDialog,
                                onEdit = onEdit,
                                onConfirm = onConfirm,
                                onDelete = onDeleteGroup,
                            )
                        }
                    }
                  }
                }
            }
        }
    }
}
}
