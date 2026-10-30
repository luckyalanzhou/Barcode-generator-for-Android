package com.luckyalanzhou.barcodegenerator.presentation.favorites

import com.luckyalanzhou.barcodegenerator.domain.CodeItem
import com.luckyalanzhou.barcodegenerator.domain.FavoriteGroup
import com.luckyalanzhou.barcodegenerator.presentation.shared.LibraryStateStore
import com.luckyalanzhou.barcodegenerator.domain.FavoriteGroupContent
import java.util.concurrent.atomic.AtomicLong

/** 读取收藏分组的权威内容，并同步普通列表与搜索快照。 */
internal class FavoriteGroupContentCoordinator(
    private val loadContent: suspend (Long) -> FavoriteGroupContent?,
    private val regularStore: LibraryStateStore,
    private val searchStore: LibraryStateStore,
    private val publishDataState: () -> Unit,
    private val publishSearchState: () -> Unit,
) {
    private val libraryMutationGeneration = AtomicLong(0L)

    suspend fun load(groupId: Long): FavoriteGroupContentLoadResult {
        val expectedMutationGeneration = libraryMutationGeneration.get()
        val cachedGroup = findCachedGroup(groupId)
            ?: return FavoriteGroupContentLoadResult.Rejected(FavoriteGroupContentLoadFailure.GROUP_NOT_CACHED)
        val expectedSavedAt = cachedGroup.savedAt
        val content = loadContent(groupId)
            ?: return FavoriteGroupContentLoadResult.Rejected(FavoriteGroupContentLoadFailure.GROUP_NOT_FOUND)

        val latestCachedGroup = findCachedGroup(groupId)
        if (
            libraryMutationGeneration.get() != expectedMutationGeneration ||
            latestCachedGroup == null ||
            latestCachedGroup.savedAt != expectedSavedAt
        ) {
            return FavoriteGroupContentLoadResult.Rejected(FavoriteGroupContentLoadFailure.GROUP_CHANGED)
        }
        if (content.invalidItemIds.isNotEmpty()) {
            return FavoriteGroupContentLoadResult.Rejected(
                failure = FavoriteGroupContentLoadFailure.INVALID_CONTENT,
                linkedCount = content.group.itemIds.size,
                loadedCount = content.items.size,
                invalidCount = content.invalidItemIds.size,
            )
        }
        if (content.items.isEmpty()) {
            return FavoriteGroupContentLoadResult.Rejected(
                failure = FavoriteGroupContentLoadFailure.EMPTY,
                linkedCount = 0,
            )
        }
        return FavoriteGroupContentLoadResult.Loaded(
            group = content.group,
            items = content.items,
            expectedSavedAt = expectedSavedAt,
            expectedMutationGeneration = expectedMutationGeneration,
        )
    }

    fun cache(loaded: FavoriteGroupContentLoadResult.Loaded): Boolean {
        if (!isCurrent(loaded.group.id, loaded.expectedSavedAt, loaded.expectedMutationGeneration)) return false

        fun LibraryStateStore.updateIfPresent() {
            edit {
                val groupIndex = groups.indexOfFirst { it.id == loaded.group.id }
                if (groupIndex >= 0) {
                    groups[groupIndex] = loaded.group.copy(itemIds = loaded.group.itemIds.toMutableList())
                }
                val loadedIds = loaded.items.mapTo(HashSet()) { it.id }
                items.removeAll { it.id in loadedIds }
                items.addAll(loaded.items.map(CodeItem::copy))
            }
            markGroupLinksLoaded(loaded.group.id)
        }

        regularStore.updateIfPresent()
        searchStore.updateIfPresent()
        publishDataState()
        publishSearchState()
        return true
    }

    fun onLibraryMutation() {
        libraryMutationGeneration.incrementAndGet()
    }

    fun isCurrent(groupId: Long, savedAt: Long, mutationGeneration: Long): Boolean {
        if (mutationGeneration != libraryMutationGeneration.get()) return false
        val cachedGroups = listOf(regularStore, searchStore)
            .flatMap { store -> store.groupsSnapshot().filter { it.id == groupId } }
        return cachedGroups.isNotEmpty() && cachedGroups.all { it.savedAt == savedAt }
    }

    private fun findCachedGroup(groupId: Long): FavoriteGroup? =
        regularStore.groupsSnapshot().firstOrNull { it.id == groupId }
            ?: searchStore.groupsSnapshot().firstOrNull { it.id == groupId }
}

internal enum class FavoriteGroupContentLoadFailure {
    GROUP_NOT_CACHED,
    GROUP_NOT_FOUND,
    GROUP_CHANGED,
    INVALID_CONTENT,
    EMPTY,
}

internal sealed interface FavoriteGroupContentLoadResult {
    data class Loaded(
        val group: FavoriteGroup,
        val items: List<CodeItem>,
        val expectedSavedAt: Long,
        val expectedMutationGeneration: Long,
    ) : FavoriteGroupContentLoadResult

    data class Rejected(
        val failure: FavoriteGroupContentLoadFailure,
        val linkedCount: Int = 0,
        val loadedCount: Int = 0,
        val invalidCount: Int = 0,
    ) : FavoriteGroupContentLoadResult
}
