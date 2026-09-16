package com.luckyalanzhou.barcodegenerator

/**
 * 生成页的业务入口。
 *
 * 页面布局、输入行状态和格式选择已经由 ComposeGenerateUi.kt 负责；
 * 这里仅保留生成校验、持久化和结果路由，避免旧 View 页面再次接管界面。
 */
internal fun MainActivity.generateAll() {
    saveInputDraft()
    val selected = formats.firstOrNull { it.first == generateFormatName }
        ?: formats.first()
    val generatedResult = generateBarcodesUseCase.execute(inputDraft, selected.first, items)
    if (!generatedResult.isValid) {
        val message = generatedResult.errorMessage
        if (message == "请输入内容") toast(message) else toast("第 ${generatedResult.errorIndex + 1} 行：$message")
        return
    }

    // 仅从收藏编辑路径重新生成时更新原收藏；普通生成始终创建新的结果批次。
    val editingFavorite = selectedFavoriteGroup?.takeIf { resultsReturnPage == "favorites" }
    if (editingFavorite == null) selectedFavoriteGroup = null

    val generated = generatedResult.items
    items.addAll(0, generated)
    saveItems()
    resultItems = generated
    showingHistoryResult = false
    resultsReturnPage = if (editingFavorite != null) "favorites" else "generate"
    page = "results"
    showResults()
}
