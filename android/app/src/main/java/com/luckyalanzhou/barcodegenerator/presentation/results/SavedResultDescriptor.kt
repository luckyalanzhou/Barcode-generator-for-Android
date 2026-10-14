package com.luckyalanzhou.barcodegenerator.presentation.results

import androidx.lifecycle.SavedStateHandle
import com.luckyalanzhou.barcodegenerator.presentation.ResultUiState
import com.luckyalanzhou.barcodegenerator.presentation.navigation.NavigationRoute
import com.luckyalanzhou.barcodegenerator.domain.CodeItem
import com.luckyalanzhou.barcodegenerator.domain.FavoriteGroup

/** Only stable identifiers go into the Activity's saved-state Bundle. */
internal data class SavedResultDescriptor(
    val itemIds: List<Long>,
    val returnPage: NavigationRoute,
    val showingHistoryResult: Boolean,
    val favoriteGroupId: Long?,
)

/** Rebuilds result content by stable ID without assuming startup paging loaded every item. */
internal suspend fun loadSavedResult(
    descriptor: SavedResultDescriptor,
    loadItems: suspend (List<Long>) -> List<CodeItem>,
    loadGroups: suspend (List<Long>) -> List<FavoriteGroup>,
): ResultUiState? {
    val byId = loadItems(descriptor.itemIds).associateBy(CodeItem::id)
    val items = descriptor.itemIds.mapNotNull(byId::get)
    val group = descriptor.favoriteGroupId?.let { id -> loadGroups(listOf(id)).firstOrNull() }
    if (items.size != descriptor.itemIds.size ||
        (descriptor.favoriteGroupId != null && group == null)
    ) return null
    return ResultUiState(
        items = items,
        showingHistoryResult = descriptor.showingHistoryResult,
        returnPage = descriptor.returnPage,
        selectedFavoriteGroup = group,
    )
}

internal class SavedResultState(private val savedState: SavedStateHandle) {
    fun read(): SavedResultDescriptor? {
        val ids = savedState.get<ArrayList<Long>>(ITEM_IDS)?.toList()?.takeIf { it.isNotEmpty() } ?: return null
        val returnPage = NavigationRoute.fromPage(savedState[RETURN_PAGE] ?: NavigationRoute.Generate.pageName)
        return SavedResultDescriptor(ids, returnPage, savedState[HISTORY] ?: false, savedState[GROUP_ID])
    }

    fun save(result: ResultUiState) {
        if (result.items.isEmpty()) {
            clear()
            return
        }
        savedState[ITEM_IDS] = ArrayList(result.items.map { it.id })
        savedState[RETURN_PAGE] = result.returnPage.pageName
        savedState[HISTORY] = result.showingHistoryResult
        result.selectedFavoriteGroup?.id?.let { savedState[GROUP_ID] = it }
            ?: savedState.remove<Long>(GROUP_ID)
    }

    fun clear() {
        savedState.remove<ArrayList<Long>>(ITEM_IDS)
        savedState.remove<String>(RETURN_PAGE)
        savedState.remove<Boolean>(HISTORY)
        savedState.remove<Long>(GROUP_ID)
    }

    private companion object {
        const val ITEM_IDS = "results.itemIds"
        const val RETURN_PAGE = "results.returnPage"
        const val HISTORY = "results.history"
        const val GROUP_ID = "results.favoriteGroupId"
    }
}
