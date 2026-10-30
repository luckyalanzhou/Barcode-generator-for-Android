package com.luckyalanzhou.barcodegenerator.ui

import com.luckyalanzhou.barcodegenerator.ui.feature.favorites.favoriteFolderChildren
import com.luckyalanzhou.barcodegenerator.ui.feature.favorites.favoriteFolderRoots
import com.luckyalanzhou.barcodegenerator.ui.feature.favorites.favoriteFolderDestination
import org.junit.Assert.assertEquals
import org.junit.Test

class FavoriteFolderSelectionTest {
    private val folders = listOf(
        "旅行",
        "旅行/日本",
        "旅行/欧洲",
        "工作",
        "工作/项目A",
        "工作/项目A/无效深层级",
    )

    @Test
    fun listsRootFoldersAndOnlyTheirDirectChildren() {
        assertEquals(listOf("工作", "旅行"), favoriteFolderRoots(folders))
        assertEquals(listOf("项目A"), favoriteFolderChildren(folders, "工作"))
        assertEquals(listOf("日本", "欧洲"), favoriteFolderChildren(folders, "旅行"))
    }

    @Test
    fun selectedFolderCanBeRootOrSelectedChild() {
        assertEquals("工作", favoriteFolderDestination("工作", ""))
        assertEquals("工作/项目A", favoriteFolderDestination("工作", "项目A"))
        assertEquals("", favoriteFolderDestination("", "项目A"))
    }
}
