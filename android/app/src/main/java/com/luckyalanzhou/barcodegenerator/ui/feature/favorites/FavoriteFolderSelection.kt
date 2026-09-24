package com.luckyalanzhou.barcodegenerator.ui.feature.favorites

internal const val FAVORITE_ROOT_ONLY_OPTION = "仅一级文件夹"

internal fun favoriteFolderRoots(paths: Collection<String>): List<String> =
    paths.filter(::isValidFavoriteFolderPath)
        .map { it.substringBefore('/') }
        .distinct()
        .sorted()

internal fun favoriteFolderChildren(paths: Collection<String>, root: String): List<String> {
    if (root.isBlank()) return emptyList()
    return paths.asSequence()
        .filter(::isValidFavoriteFolderPath)
        .filter { it.startsWith("$root/") }
        .map { it.removePrefix("$root/") }
        .filter { it.isNotBlank() && '/' !in it }
        .distinct()
        .sorted()
        .toList()
}

internal fun favoriteMoveDestination(root: String, child: String): String = when {
    root.isBlank() -> ""
    child.isBlank() -> root
    else -> "$root/$child"
}
