package com.luckyalanzhou.barcodegenerator.presentation.favorites

/** 导入已提交，但提交后的收藏库快照重新加载失败。 */
internal class FavoritesImportRefreshException(
    val importedItemCount: Int,
    val importedGroupCount: Int,
    cause: Throwable,
) : Exception("收藏备份已写入，但收藏列表刷新失败", cause)
