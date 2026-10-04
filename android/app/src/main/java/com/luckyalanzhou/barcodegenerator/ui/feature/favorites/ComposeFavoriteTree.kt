package com.luckyalanzhou.barcodegenerator.ui.feature.favorites

import com.luckyalanzhou.barcodegenerator.presentation.*
import com.luckyalanzhou.barcodegenerator.presentation.BarcodeDataState
import com.luckyalanzhou.barcodegenerator.domain.FavoriteGroup
import java.util.Locale

/** Favorites tree projection kept separate from row rendering and dialogs. */
internal data class ComposeFavoriteRow(
    val path: String,
    val label: String,
    val level: Int,
    val count: Int,
    val collapsed: Boolean,
    val folder: Boolean,
    val group: FavoriteGroup? = null,
)

/** Existing lazy rows must not replay expansion when recomposed after scrolling. */
internal fun favoriteRowInitiallyVisible(key: String, enteringKeys: Set<String>): Boolean =
    key !in enteringKeys

/** Treat folders not yet synchronized into the view-model tree as collapsed during projection. */
internal fun effectiveCollapsedFavoriteFolders(
    treeState: FavoriteTreeUiState,
    allFolderPaths: Set<String>,
    query: String,
    expandedSearchPaths: Set<String>,
): Set<String> {
    val collapsed = treeState.collapsedFolders + (allFolderPaths - treeState.knownFolders)
    return if (query.isEmpty() || treeState.searchAutoExpandSuppressed) {
        collapsed
    } else {
        collapsed - expandedSearchPaths
    }
}

internal fun composeFavoriteRows(
    state: BarcodeDataState,
    query: String,
    collapsedFolders: Set<String>,
): List<ComposeFavoriteRow> {
    val folders = (state.folders + state.groups.map { it.folder }).filter { it.isNotBlank() }.distinct()
    val foldersByParent = folders.groupBy { it.substringBeforeLast('/', missingDelimiterValue = "") }
    val roots = foldersByParent[""].orEmpty().sorted()
    val itemsById = state.items.associateBy { it.id }
    fun matches(group: FavoriteGroup): Boolean = query.isEmpty() ||
        group.folder.lowercase(Locale.getDefault()).contains(query) ||
        group.name.lowercase(Locale.getDefault()).contains(query) ||
        group.itemIds.any { id -> itemsById[id]?.text?.lowercase(Locale.getDefault())?.contains(query) == true }
    val groupsByFolder = state.groups.groupBy { it.folder }
    val matchingGroupsByFolder = groupsByFolder.mapValues { (_, groups) -> groups.filter(::matches) }
    val matchingFolders = if (query.isEmpty()) emptySet() else matchingGroupsByFolder
        .filterValues { it.isNotEmpty() }
        .keys
        .flatMap { path ->
            val parts = path.split('/')
            parts.indices.map { parts.take(it + 1).joinToString("/") }
        }
        .toSet()
    val result = mutableListOf<ComposeFavoriteRow>()
    fun renderFolder(path: String, level: Int) {
        val children = foldersByParent[path].orEmpty().sorted()
        val groups = matchingGroupsByFolder[path].orEmpty()
        if (query.isNotEmpty() && path !in matchingFolders) return
        val collapsed = path in collapsedFolders
        result += ComposeFavoriteRow(path, path.substringAfterLast('/'), level, if (level == 0) children.size else groups.size, collapsed, true)
        if (!collapsed) {
            groups.forEach { group -> result += ComposeFavoriteRow(group.folder, group.name, level + 1, 0, false, false, group) }
            children.forEach { child -> renderFolder(child, level + 1) }
        }
    }
    roots.forEach { renderFolder(it, 0) }
    return result
}

internal fun favoriteSearchExpandedPaths(state: BarcodeDataState, query: String): Set<String> {
    if (query.isEmpty()) return emptySet()
    val itemsById = state.items.associateBy { it.id }
    fun matches(group: FavoriteGroup): Boolean =
        group.folder.lowercase(Locale.getDefault()).contains(query) ||
            group.name.lowercase(Locale.getDefault()).contains(query) ||
            group.itemIds.any { id -> itemsById[id]?.text?.lowercase(Locale.getDefault())?.contains(query) == true }
    return state.groups.filter(::matches).flatMap { group ->
        val parts = group.folder.split('/')
        parts.indices.map { parts.take(it + 1).joinToString("/") }
    }.toSet()
}
