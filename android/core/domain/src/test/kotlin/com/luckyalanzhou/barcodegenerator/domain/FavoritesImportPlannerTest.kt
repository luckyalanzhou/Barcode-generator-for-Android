package com.luckyalanzhou.barcodegenerator.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FavoritesImportPlannerTest {
    private val planner = FavoritesImportPlanner()

    @Test
    fun conflictInspectionNormalizesFolderSeparatorsAndNames() {
        val existing = listOf(FavoriteGroup(7L, "一级/", " 文件 ", 1L, mutableListOf()))
        val incoming = listOf(
            favorite("旧内容", "一级", "文件"),
            favorite("重复冲突", "一级/", " 文件 "),
        )

        assertEquals(listOf("一级\u0000文件"), planner.inspectConflicts(existing, incoming).fileKeys)
        assertEquals(listOf("一级\u0000文件"), planner.inspectConflicts(existing, incoming).duplicateBackupKeys)
    }

    @Test
    fun nonOverwriteImportSkipsExistingAndAmbiguousBackupDuplicates() {
        val incoming = listOf(
            favorite("A-old", "", "A"),
            favorite("B", "", "B"),
            favorite("A-new", "", "A"),
            favorite("existing", "folder", "taken"),
        )
        val existing = listOf(FavoriteGroup(8L, "folder", "taken", 1L, mutableListOf()))

        val plan = planner.plan(existing, incoming, overwriteConflicts = false)

        assertTrue(plan.replacedGroupIds.isEmpty())
        assertEquals(listOf("B"), plan.favoritesToImport.map { it.texts.single() })
        assertEquals(listOf("folder\u0000taken", "\u0000A"), plan.conflictingFileKeys)
    }

    @Test
    fun conflictInspectionReportsDuplicateNamesInsideBackup() {
        val incoming = listOf(favorite("old", "一级", "文件"), favorite("new", "一级", "文件"))

        val conflicts = planner.inspectConflicts(emptyList(), incoming)

        assertEquals(listOf("一级\u0000文件"), conflicts.fileKeys)
        assertEquals(listOf("一级\u0000文件"), conflicts.duplicateBackupKeys)
    }

    @Test
    fun overwritePlanReturnsConflictingGroupIdsAndLastDuplicate() {
        val existing = listOf(
            FavoriteGroup(8L, "folder", "taken", 1L, mutableListOf()),
            FavoriteGroup(9L, "folder", "taken", 2L, mutableListOf()),
        )
        val incoming = listOf(favorite("old", "folder", "taken"), favorite("new", "folder", "taken"))

        val plan = planner.plan(existing, incoming, overwriteConflicts = true)

        assertEquals(setOf(8L, 9L), plan.replacedGroupIds)
        assertEquals(listOf("new"), plan.favoritesToImport.map { it.texts.single() })
        assertTrue(plan.conflictingFileKeys.isNotEmpty())
    }

    @Test
    fun overwritePlanKeepsLastDuplicateFromBackupAfterExplicitChoice() {
        val incoming = listOf(favorite("old", "一级", "文件"), favorite("new", "一级", "文件"))

        val plan = planner.plan(emptyList(), incoming, overwriteConflicts = true)

        assertEquals(listOf("new"), plan.favoritesToImport.map { it.texts.single() })
        assertEquals(listOf("一级\u0000文件"), plan.conflictingFileKeys)
    }

    private fun favorite(text: String, root: String, name: String) =
        InterchangeFavorite(null, name, root, "", "code128", 1L, listOf(text))
}
