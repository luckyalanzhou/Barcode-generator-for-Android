package com.luckyalanzhou.barcodegenerator.ui

import com.luckyalanzhou.barcodegenerator.BarcodeViewModel
import com.luckyalanzhou.barcodegenerator.MainActivity
import com.luckyalanzhou.barcodegenerator.domain.FavoriteGroup
import com.luckyalanzhou.barcodegenerator.ui.ComposeAnimationConfig
import com.luckyalanzhou.barcodegenerator.ui.rememberComposeAnimationConfig

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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext

@Composable
internal fun ComposeFavoritesPage(
    viewModel: BarcodeViewModel,
    dark: Boolean,
    onShowSubfolderEditor: (String) -> Unit,
    onShowFolderEditor: (String, (String) -> Unit) -> Unit,
    onShowMoveDialog: (FavoriteGroup) -> Unit,
    onShowRenameDialog: (FavoriteGroup) -> Unit,
    onConfirm: (String, String, String, () -> Unit) -> Unit,
) {
    val favoritesState by viewModel.dataState.collectAsStateWithLifecycle()
    val searchState by viewModel.favoriteSearchState.collectAsStateWithLifecycle()
    val treeState by viewModel.favoriteTreeUiState.collectAsStateWithLifecycle()
    val hapticView = LocalView.current
    var query by remember { mutableStateOf("") }
    var folderMenu by remember { mutableStateOf<Pair<String, Int>?>(null) }
    var fileMenu by remember { mutableStateOf<FavoriteGroup?>(null) }
    val animation = rememberComposeAnimationConfig()
    val themeColors = LocalBarcodeThemeColors.current
    val primary = themeColors.primary
    val secondary = themeColors.secondary
    val rootFolderColor = themeColors.folder
    val childFolderColor = themeColors.childFolder
    val fileColor = themeColors.file
    val listState = rememberLazyListState()
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

    LaunchedEffect(folderPaths, displayState.groups) { viewModel.syncFavoriteTree(folderPaths) }
    LaunchedEffect(normalizedQuery) { viewModel.searchFavoriteContent(normalizedQuery) }
    LaunchedEffect(normalizedQuery, expandedSearchPaths) { viewModel.updateFavoriteSearch(expandedSearchPaths, normalizedQuery.isNotEmpty()) }

    val visibleCollapsedFolders = if (normalizedQuery.isEmpty()) treeState.collapsedFolders else treeState.collapsedFolders - expandedSearchPaths
    val rows by produceState<List<ComposeFavoriteRow>?>(null, displayState, normalizedQuery, visibleCollapsedFolders) {
        value = withContext(Dispatchers.Default) {
            composeFavoriteRows(displayState, normalizedQuery, visibleCollapsedFolders)
        }
    }

    LaunchedEffect(listState, rows, normalizedQuery) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .distinctUntilChanged()
            .collect { lastVisible ->
                if (rows != null && lastVisible >= rows!!.size - 5) {
                    viewModel.loadMoreFavoriteGroups(normalizedQuery)
                }
            }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item(key = "favorite-search") {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                textStyle = TextStyle(color = primary, fontSize = 17.sp),
                placeholder = { Text("搜索名称、文件夹或内容", color = secondary, fontSize = 17.sp) },
                leadingIcon = { Icon(SearchIcon, "搜索", tint = secondary) },
                shape = RoundedCornerShape(14.dp),
            )
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
            item(key = "favorite-tree-loading") {
                Text(
                    "正在整理收藏…",
                    color = secondary,
                    fontSize = 17.sp,
                    modifier = Modifier.fillMaxWidth().padding(top = 40.dp),
                    textAlign = TextAlign.Center,
                )
            }
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
                        onClick = { viewModel.toggleFavoriteFolder(row.path, folderPaths) },
                        onLongClick = {
                            hapticView.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                            folderMenu = row.path to row.level
                        },
                        onShowSubfolderEditor = onShowSubfolderEditor,
                        onShowFolderEditor = onShowFolderEditor,
                        onConfirm = onConfirm,
                        viewModel = viewModel,
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
                            onClick = { viewModel.openFavoriteGroup(group) },
                            onLongClick = {
                                hapticView.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                                fileMenu = group
                            },
                            onShowMoveDialog = onShowMoveDialog,
                            onShowRenameDialog = onShowRenameDialog,
                            onConfirm = onConfirm,
                            viewModel = viewModel,
                        )
                    }
                }
            }
        }
    }
}
