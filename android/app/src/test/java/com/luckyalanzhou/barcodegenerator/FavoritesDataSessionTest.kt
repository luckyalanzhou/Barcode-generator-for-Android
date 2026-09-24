package com.luckyalanzhou.barcodegenerator

import com.luckyalanzhou.barcodegenerator.presentation.favorites.FavoritesDataSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FavoritesDataSessionTest {
    @Test
    fun publishesSnapshotFromSharedStoreAndKeepsReadiness() {
        val session = FavoritesDataSession()
        assertFalse(session.dataState.value.isReady)

        session.store.edit { folders += "项目/子目录" }
        session.publishDataState(isReady = true)

        assertTrue(session.dataState.value.isReady)
        assertEquals(listOf("项目/子目录"), session.dataState.value.folders)
    }
}
