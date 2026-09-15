package com.luckyalanzhou.barcodegenerator

/**
 * 生成页的业务入口。
 *
 * 页面布局、输入行状态和格式选择已经由 ComposeGenerateUi.kt 负责；
 * 这里仅保留生成校验、持久化和结果路由，避免旧 View 页面再次接管界面。
 */
internal fun MainActivity.generateAll() {
    saveInputDraft()
    val values = inputDraft.map { it.trim() }.filter { it.isNotEmpty() }
    if (values.isEmpty()) {
        toast("请输入内容")
        return
    }

    val selected = formats.firstOrNull { it.first == generateFormatName }
        ?: formats.first()
    val invalid = values.indexOfFirst { !BarcodeValidator.validate(it, selected.first).valid }
    if (invalid >= 0) {
        val message = BarcodeValidator.validate(values[invalid], selected.first).message
        toast("第 ${invalid + 1} 行：$message")
        return
    }

    // 仅从收藏编辑路径重新生成时更新原收藏；普通生成始终创建新的结果批次。
    val editingFavorite = selectedFavoriteGroup?.takeIf { resultsReturnPage == "favorites" }
    if (editingFavorite == null) selectedFavoriteGroup = null

    val generated = mutableListOf<CodeItem>()
    val batchTime = System.currentTimeMillis()
    values.forEach { value ->
        CodeItem(nextItemId(), value, selected.first, batchTime).also {
            items.add(0, it)
            generated.add(it)
        }
    }
    saveItems()
    resultItems = generated
    showingHistoryResult = false
    resultsReturnPage = if (editingFavorite != null) "favorites" else "generate"
    page = "results"
    showResults()
}
