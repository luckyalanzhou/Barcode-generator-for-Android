package com.luckyalanzhou.barcodegenerator

/**
 * 收藏相关弹窗的兼容入口。
 *
 * 所有可见界面和交互均由 ComposeFavoriteDialogs.kt 实现；
 * 这些小门面只让旧业务回调继续使用原函数名，不再创建 XML/View 弹窗。
 */
internal fun MainActivity.saveResultAsFavorite() {
    saveResultAsFavoriteCompose()
}

internal fun MainActivity.showFolderEditor(
    initial: String = "",
    showMetrics: Boolean = false,
    onSaved: (String) -> Unit,
) {
    showFolderEditorCompose(initial, showMetrics, onSaved)
}

internal fun MainActivity.showGroupEditor(group: FavoriteGroup) {
    showGroupEditorCompose(group)
}

internal fun MainActivity.showItemEditor(item: CodeItem) {
    showItemEditorCompose(item)
}
