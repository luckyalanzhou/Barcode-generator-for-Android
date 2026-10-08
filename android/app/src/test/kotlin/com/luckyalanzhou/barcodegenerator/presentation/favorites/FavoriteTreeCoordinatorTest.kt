package com.luckyalanzhou.barcodegenerator.presentation.favorites

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FavoriteTreeCoordinatorTest {
    @Test
    fun collapseAllMarksEveryKnownFolderCollapsed() {
        val coordinator = FavoriteTreeCoordinator()
        val folders = setOf("root", "root/child", "other")
        coordinator.sync(folders)
        coordinator.toggle("root", folders)

        coordinator.collapseAll(folders)

        assertEquals(folders, coordinator.state.value.collapsedFolders)
    }

    @Test
    fun collapseAllDuringSearchKeepsMatchesCollapsedAfterSearchUpdatesAndExit() {
        val coordinator = FavoriteTreeCoordinator()
        val folders = setOf("root", "root/child")
        coordinator.sync(folders)
        coordinator.updateSearch(expandedPaths = setOf("root"), searching = true)

        coordinator.collapseAll(folders)
        coordinator.updateSearch(expandedPaths = setOf("root"), searching = true)

        assertEquals(folders, coordinator.state.value.collapsedFolders)
        assertTrue(coordinator.state.value.searchAutoExpandSuppressed)

        coordinator.updateSearch(expandedPaths = emptySet(), searching = false)

        assertEquals(folders, coordinator.state.value.collapsedFolders)
        assertFalse(coordinator.state.value.searchAutoExpandSuppressed)
    }
}
