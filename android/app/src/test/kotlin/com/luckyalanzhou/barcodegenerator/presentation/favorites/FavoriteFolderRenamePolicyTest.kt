package com.luckyalanzhou.barcodegenerator.presentation.favorites

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FavoriteFolderRenamePolicyTest {
    @Test
    fun rootRenameCannotTurnItsNewNameIntoAParentPath() {
        assertFalse(isSafeFavoriteFolderRename("old", "new/sub", listOf("old", "old/child")))
    }

    @Test
    fun validRootRenameKeepsAllChildrenWithinTwoLevels() {
        assertTrue(isSafeFavoriteFolderRename("old", "new", listOf("old", "old/child")))
    }

    @Test
    fun childRenameMustRemainUnderTheSameRoot() {
        assertTrue(isSafeFavoriteFolderRename("root/old", "root/new", listOf("root/old")))
        assertFalse(isSafeFavoriteFolderRename("root/old", "other/new", listOf("root/old")))
        assertFalse(isSafeFavoriteFolderRename("root/old", "root/new/deeper", listOf("root/old")))
    }

    @Test
    fun folderNameMustBeOneValidSegment() {
        assertTrue(isValidFavoriteFolderName("project_1"))
        assertFalse(isValidFavoriteFolderName("new/sub"))
        assertFalse(isValidFavoriteFolderName("new\\sub"))
    }
}
