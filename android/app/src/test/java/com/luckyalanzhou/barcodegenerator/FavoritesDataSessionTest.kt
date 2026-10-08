package com.luckyalanzhou.barcodegenerator

import com.luckyalanzhou.barcodegenerator.domain.FavoriteGroupPageCursor
import com.luckyalanzhou.barcodegenerator.presentation.shared.LibraryDataSession
import com.luckyalanzhou.barcodegenerator.presentation.shared.LibraryLoadMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryDataSessionTest {
    @Test
    fun publishesSnapshotFromSharedStoreAndKeepsReadiness() {
        val session = LibraryDataSession()
        assertFalse(session.dataState.value.isReady)

        session.store.edit { folders += "项目/子目录" }
        session.publishDataState(isReady = true)

        assertTrue(session.dataState.value.isReady)
        assertEquals(listOf("项目/子目录"), session.dataState.value.folders)
    }

    @Test
    fun publishesNewPagingMetadataForEveryDurableReload() {
        val session = LibraryDataSession()
        val cursor = FavoriteGroupPageCursor(savedAt = 42L, id = 7L)

        session.publishLoadedSnapshot(cursor, hasMoreGroups = true)
        assertEquals(LibraryLoadMetadata(1L, cursor, true), session.loadMetadata.value)

        session.publishLoadedSnapshot(null, hasMoreGroups = false)
        assertEquals(LibraryLoadMetadata(2L, null, false), session.loadMetadata.value)
    }
}
