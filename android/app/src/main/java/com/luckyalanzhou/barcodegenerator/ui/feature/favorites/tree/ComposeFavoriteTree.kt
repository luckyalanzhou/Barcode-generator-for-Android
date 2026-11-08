package com.luckyalanzhou.barcodegenerator.ui.feature.favorites.tree

import com.luckyalanzhou.barcodegenerator.domain.CodeItem
import com.luckyalanzhou.barcodegenerator.domain.FavoriteGroup
import java.util.Locale

/** 将收藏层级映射为懒加载列表行时，由收藏功能提供的输入数据。 */
internal data class FavoritesTreeData(
    val items: List<CodeItem>,
    val groups: List<FavoriteGroup>,
    val folders: List<String>,
)

/** 层级映射所需的树交互状态，与 Presentation 状态相互独立。 */
internal data class FavoritesTreeState(
    val collapsedFolders: Set<String> = emptySet(),
    val knownFolders: Set<String> = emptySet(),
    val searchAutoExpandSuppressed: Boolean = false,
)

/** 供收藏功能的 Compose 界面渲染使用的显示行数据。 */
internal data class ComposeFavoriteRow(
    val path: String,
    val label: String,
    val level: Int,
    val count: Int,
    val collapsed: Boolean,
    val folder: Boolean,
    val group: FavoriteGroup? = null,
    val treeBranches: List<Boolean> = emptyList(),
)

/** 缓存最近一次 UI 树投影；Shell 生命周期跨越主 Tab 页面销毁与重建。 */
internal class FavoritesRowsProjectionCache {
    private data class Entry(
        val items: List<CodeItem>,
        val groups: List<FavoriteGroup>,
        val folders: List<String>,
        val query: String,
        val collapsedFolders: Set<String>,
        val rows: List<ComposeFavoriteRow>,
    )

    private var entry: Entry? = null

    @Synchronized
    fun find(
        state: FavoritesTreeData,
        query: String,
        collapsedFolders: Set<String>,
    ): List<ComposeFavoriteRow>? = entry?.takeIf {
        it.items === state.items &&
            it.groups === state.groups &&
            it.folders === state.folders &&
            it.query == query &&
            it.collapsedFolders == collapsedFolders
    }?.rows

    @Synchronized
    fun store(
        state: FavoritesTreeData,
        query: String,
        collapsedFolders: Set<String>,
        rows: List<ComposeFavoriteRow>,
    ) {
        entry = Entry(state.items, state.groups, state.folders, query, collapsedFolders, rows)
    }
}

/** 映射列表时，将尚未同步到 ViewModel 树状态的文件夹视为折叠。 */
internal fun effectiveCollapsedFavoriteFolders(
    treeState: FavoritesTreeState,
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
    state: FavoritesTreeData,
    query: String,
    collapsedFolders: Set<String>,
): List<ComposeFavoriteRow> {
    val folders = (state.folders + state.groups.map { it.folder }).filter { it.isNotBlank() }.distinct()
    val foldersByParent = folders.groupBy { it.substringBeforeLast('/', missingDelimiterValue = "") }
    val roots = foldersByParent[""].orEmpty().sorted()
    // 普通收藏树只显示文件夹与文件，不需要扫描所有条码内容；仅搜索时构建索引。
    val itemsById = if (query.isEmpty()) emptyMap<Long, CodeItem>() else state.items.associateBy { it.id }
    fun matches(group: FavoriteGroup): Boolean = query.isEmpty() ||
        group.folder.lowercase(Locale.ROOT).contains(query) ||
        group.name.lowercase(Locale.ROOT).contains(query) ||
        group.itemIds.any { id -> itemsById[id]?.text?.lowercase(Locale.ROOT)?.contains(query) == true }
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
    fun renderFolder(path: String, level: Int, treeBranches: List<Boolean>) {
        val allChildren = foldersByParent[path].orEmpty().sorted()
        val groups = matchingGroupsByFolder[path].orEmpty()
        if (query.isNotEmpty() && path !in matchingFolders) return
        val collapsed = path in collapsedFolders
        result += ComposeFavoriteRow(path, path.substringAfterLast('/'), level, if (level == 0) allChildren.size else groups.size,
            collapsed, true, treeBranches = treeBranches)
        if (!collapsed) {
            val children = if (query.isEmpty()) allChildren else allChildren.filter { it in matchingFolders }
            val continuingBranch = treeBranches + true
            val endingBranch = treeBranches + false
            groups.forEachIndexed { index, group ->
                val branches = if (index == groups.lastIndex && children.isEmpty()) endingBranch else continuingBranch
                result += ComposeFavoriteRow(group.folder, group.name, level + 1, 0, false, false, group, branches)
            }
            children.forEachIndexed { index, child ->
                renderFolder(child, level + 1, if (index == children.lastIndex) endingBranch else continuingBranch)
            }
        }
    }
    matchingGroupsByFolder[""].orEmpty().forEach { group ->
        result += ComposeFavoriteRow(
            path = "",
            label = group.name,
            level = 0,
            count = 0,
            collapsed = false,
            folder = false,
            group = group,
        )
    }
    roots.forEach { renderFolder(it, 0, emptyList()) }
    return result
}

internal fun favoriteSearchExpandedPaths(state: FavoritesTreeData, query: String): Set<String> {
    if (query.isEmpty()) return emptySet()
    val itemsById = state.items.associateBy { it.id }
    fun matches(group: FavoriteGroup): Boolean =
        group.folder.lowercase(Locale.ROOT).contains(query) ||
            group.name.lowercase(Locale.ROOT).contains(query) ||
            group.itemIds.any { id -> itemsById[id]?.text?.lowercase(Locale.ROOT)?.contains(query) == true }
    return state.groups.filter(::matches).flatMap { group ->
        val parts = group.folder.split('/')
        parts.indices.map { parts.take(it + 1).joinToString("/") }
    }.toSet()
}
