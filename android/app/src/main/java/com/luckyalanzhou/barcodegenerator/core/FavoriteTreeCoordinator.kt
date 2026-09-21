package com.luckyalanzhou.barcodegenerator

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 管理收藏页树形目录的折叠与搜索展开状态，不参与收藏数据持久化。 */
internal class FavoriteTreeCoordinator {
    private val _state = MutableStateFlow(FavoriteTreeUiState())
    val state: StateFlow<FavoriteTreeUiState> = _state.asStateFlow()

    fun sync(folders: Set<String>) {
        val validFolders = folders.filter { it.isNotBlank() }.toSet()
        val current = _state.value
        val newlySeen = validFolders - current.knownFolders
        val nextCollapsed = if (!current.initialized) validFolders
        else (current.collapsedFolders intersect validFolders) + newlySeen
        val next = current.copy(
            collapsedFolders = nextCollapsed,
            initialized = true,
            knownFolders = validFolders,
        )
        if (next != current) _state.value = next
    }

    fun addCollapsed(paths: Set<String>) {
        if (paths.isEmpty()) return
        val current = _state.value
        _state.value = current.copy(
            collapsedFolders = current.collapsedFolders + paths,
            knownFolders = current.knownFolders + paths,
        )
    }

    fun toggle(path: String, folders: Set<String>) {
        val current = _state.value
        val nextCollapsed = current.collapsedFolders.toMutableSet()
        if (path in nextCollapsed) nextCollapsed.remove(path)
        else nextCollapsed.addAll(folders.filter { it == path || it.startsWith("$path/") })
        _state.value = current.copy(collapsedFolders = nextCollapsed)
    }

    fun updateSearch(expandedPaths: Set<String>, searching: Boolean) {
        val current = _state.value
        if (searching) {
            val before = current.collapsedBeforeSearch ?: current.collapsedFolders
            _state.value = current.copy(
                collapsedFolders = current.collapsedFolders - expandedPaths,
                collapsedBeforeSearch = before,
            )
        } else {
            _state.value = current.copy(
                collapsedFolders = current.collapsedBeforeSearch ?: current.collapsedFolders,
                collapsedBeforeSearch = null,
            )
        }
    }
}
