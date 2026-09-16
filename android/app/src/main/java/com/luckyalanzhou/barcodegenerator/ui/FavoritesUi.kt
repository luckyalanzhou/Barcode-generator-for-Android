package com.luckyalanzhou.barcodegenerator

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 收藏页的状态和兼容入口。
 *
 * 收藏树、搜索、长按菜单和编辑弹窗均由 ComposeFavoritesUi.kt /
 * ComposeFavoriteDialogs.kt 负责；这里保留跨页面业务状态变更。
 */
internal fun MainActivity.openFavoriteForEditing(group: FavoriteGroup) {
    selectedFavoriteGroup = group
    resultItems = group.itemIds.mapNotNull { id -> items.firstOrNull { it.id == id } }
    inputDraft = resultItems.map { it.text }.toMutableList()
    pendingGenerateFormat = resultItems.firstOrNull()?.format
    page = "generate"
    resultsReturnPage = "favorites"
    render()
}

/** 兼容旧导航调用，实际界面由 ComposeAppShell 路由。 */
internal fun MainActivity.showFavoriteGroups() {
    page = "favorites"
    render()
}

internal fun MainActivity.showSubfolderEditor(
    parent: String,
    onCreated: ((String) -> Unit)? = null,
) {
    showSubfolderEditorCompose(parent, onCreated)
}

internal fun MainActivity.showFavoriteRenameDialog(group: FavoriteGroup) {
    showFavoriteRenameDialogCompose(group)
}

internal fun MainActivity.showFavoriteMoveDialog(group: FavoriteGroup) {
    showFavoriteMoveDialogCompose(group)
}

internal fun MainActivity.showFavoriteDetail() {
    page = "favoriteDetail"
    render()
}

internal fun MainActivity.formatSavedTime(time: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(time))

internal fun MainActivity.moveToFolder(item: CodeItem) {
    moveToFolderCompose(item)
}

internal fun MainActivity.confirmClear(favoritesOnly: Boolean) {
    confirmClearCompose(favoritesOnly)
}
