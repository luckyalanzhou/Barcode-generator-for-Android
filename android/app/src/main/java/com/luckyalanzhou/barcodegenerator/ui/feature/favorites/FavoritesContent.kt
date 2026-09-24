package com.luckyalanzhou.barcodegenerator.ui.feature.favorites

import com.luckyalanzhou.barcodegenerator.presentation.*
import com.luckyalanzhou.barcodegenerator.presentation.favorites.*
import com.luckyalanzhou.barcodegenerator.ui.theme.*

import com.luckyalanzhou.barcodegenerator.MainActivity
import com.luckyalanzhou.barcodegenerator.presentation.BarcodeDataState
import com.luckyalanzhou.barcodegenerator.presentation.FavoriteTreeUiState
import com.luckyalanzhou.barcodegenerator.domain.FavoriteGroup
import com.luckyalanzhou.barcodegenerator.domain.StyleSettings
import com.luckyalanzhou.barcodegenerator.ui.app.ComposeAnimationConfig
import com.luckyalanzhou.barcodegenerator.ui.app.rememberComposeAnimationConfig

import com.luckyalanzhou.barcodegenerator.icons.CreateNewFolderIcon
import com.luckyalanzhou.barcodegenerator.icons.DeleteIcon
import com.luckyalanzhou.barcodegenerator.icons.DriveFileMoveIcon
import com.luckyalanzhou.barcodegenerator.icons.EditIcon
import com.luckyalanzhou.barcodegenerator.icons.AttachFileIcon
import com.luckyalanzhou.barcodegenerator.icons.FolderIcon
import com.luckyalanzhou.barcodegenerator.icons.KeyboardArrowDownIcon
import com.luckyalanzhou.barcodegenerator.icons.KeyboardArrowRightIcon
import com.luckyalanzhou.barcodegenerator.icons.SearchIcon

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext

@Composable
internal fun FavoritesContent(
    favoritesState: BarcodeDataState,
    searchState: BarcodeDataState,
    treeState: FavoriteTreeUiState,
    query: String,
    savedListPosition: Pair<Int, Int>,
    dark: Boolean,
    style: StyleSettings,
    onQueryChange: (String) -> Unit,
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
    val hapticView = LocalView.current
    val density = LocalDensity.current.density
    var folderMenu by remember { mutableStateOf<Pair<String, Int>?>(null) }
    var fileMenu by remember { mutableStateOf<FavoriteGroup?>(null) }
    val animation = rememberComposeAnimationConfig()
    val themeColors = LocalAppColorScheme.current
    val primary = themeColors.text.primary
    val secondary = themeColors.text.secondary
    val rootFolderColor = themeColors.content.folder
    val childFolderColor = themeColors.content.childFolder
    val fileColor = themeColors.content.file
    val listState = rememberLazyListState()
    var listPositionRestored by remember { mutableStateOf(false) }
    val normalizedQuery = query.trim().lowercase(Locale.ROOT)
    val displayState = if (normalizedQuery.isEmpty()) favoritesState else searchState
    val folderPaths = remember(displayState.folders, displayState.groups) {
        (displayState.folders + displayState.groups.map { it.folder })
            .filter { it.isNotBlank() }.distinct().toSet()
    }
    val expandedSearchPaths by produceState<Set<String>>(emptySet(), displayState, normalizedQuery) {
        value = withContext(Dispatchers.Default) {
            favoriteSearchExpandedPaths(displayState, normalizedQuery)
        }
    }

    LaunchedEffect(folderPaths, displayState.groups) { onSyncFavoriteTree(folderPaths) }
    LaunchedEffect(normalizedQuery, expandedSearchPaths) { onUpdateFavoriteSearch(expandedSearchPaths, normalizedQuery.isNotEmpty()) }

    LaunchedEffect(listState, listPositionRestored) {
        if (!listPositionRestored) return@LaunchedEffect
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .distinctUntilChanged()
            .collect { (index, offset) -> onRememberListPosition(index, offset) }
    }

    val visibleCollapsedFolders = if (normalizedQuery.isEmpty()) treeState.collapsedFolders else treeState.collapsedFolders - expandedSearchPaths
    val rows by produceState<List<ComposeFavoriteRow>?>(null, displayState, normalizedQuery, visibleCollapsedFolders) {
        value = withContext(Dispatchers.Default) {
            composeFavoriteRows(displayState, normalizedQuery, visibleCollapsedFolders)
        }
    }

    LaunchedEffect(favoritesState.isReady, rows) {
        if (favoritesState.isReady && rows != null && !listPositionRestored) {
            val lastAvailableIndex = rows!!.size
            val targetIndex = savedListPosition.first.coerceIn(0, lastAvailableIndex)
            listState.scrollToItem(targetIndex, savedListPosition.second)
            listPositionRestored = true
        }
    }

    LaunchedEffect(listState, rows, normalizedQuery) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .distinctUntilChanged()
            .collect { lastVisible ->
                if (rows != null && lastVisible >= rows!!.size - 5) onLoadMoreGroups(normalizedQuery)
            }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item(key = "favorite-search") {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(45.dp)
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
                            contentDescription = "搜索",
                            tint = primary,
                            modifier = Modifier.size(24.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        BasicTextField(
                            value = query,
                            onValueChange = onQueryChange,
                            modifier = Modifier.weight(1f).height(28.dp),
                            singleLine = true,
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
                                    modifier = Modifier.fillMaxWidth().height(28.dp),
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
                    }
                }
                TextButton(onClick = onClearAll, modifier = Modifier.padding(start = 4.dp)) {
                    Text("清空", color = themeColors.text.destructive, fontSize = 14.sp)
                }
            }
        }

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
        } else if (rows == null) {
            // The tree projection is computed off the main thread; keep the page quiet
            // during the short recomposition instead of showing a flashing placeholder.
        } else if (rows!!.isEmpty()) {
            item(key = "favorite-empty") {
                Text(
                    if (displayState.groups.isEmpty()) "还没有收藏" else "没有匹配的收藏",
                    color = secondary,
                    fontSize = 17.sp,
                    modifier = Modifier.fillMaxWidth().padding(top = 40.dp),
                    textAlign = TextAlign.Center,
                )
            }
        } else {
                items(
                items = rows!!,
                key = { row -> if (row.folder) "folder-${row.path}" else "group-${row.group?.id}" },
                contentType = { row -> if (row.folder) "folder" else "favorite-group" },
            ) { row ->
                if (row.folder) {
                    FavoriteFolderRow(
                        row = row,
                        dark = dark,
                        secondary = secondary,
                        folderColor = if (row.level == 0) rootFolderColor else childFolderColor,
                        animation = animation,
                        menuExpanded = folderMenu?.first == row.path,
                        onMenuDismiss = { folderMenu = null },
                        onClick = { onToggleFolder(row.path, folderPaths) },
                        onLongClick = {
                            hapticView.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                            folderMenu = row.path to row.level
                        },
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
                            hapticView = hapticView,
                            menuExpanded = fileMenu?.id == group.id,
                            onMenuDismiss = { fileMenu = null },
                            onClick = {
                                onRememberListPosition(
                                    listState.firstVisibleItemIndex,
                                    listState.firstVisibleItemScrollOffset,
                                )
                                onOpenGroup(group, style, dark, density)
                            },
                            onLongClick = {
                                hapticView.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                                fileMenu = group
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
