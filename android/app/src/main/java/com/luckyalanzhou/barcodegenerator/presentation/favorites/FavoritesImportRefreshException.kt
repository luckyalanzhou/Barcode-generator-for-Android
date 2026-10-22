package com.luckyalanzhou.barcodegenerator.presentation.favorites

/** Import has committed, but the post-import library snapshot could not be reloaded. */
internal class FavoritesImportRefreshException(
    val importedItemCount: Int,
    val importedGroupCount: Int,
    cause: Throwable,
) : Exception("收藏备份已写入，但收藏列表刷新失败", cause)
