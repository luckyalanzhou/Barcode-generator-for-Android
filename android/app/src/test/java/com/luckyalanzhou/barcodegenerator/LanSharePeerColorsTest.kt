package com.luckyalanzhou.barcodegenerator

import com.luckyalanzhou.barcodegenerator.domain.LanShareFile
import com.luckyalanzhou.barcodegenerator.ui.feature.lanshare.lanSharePeerColorIndices
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LanSharePeerColorsTest {
    @Test
    fun peerColorAssignmentIsStableAndUniqueForBrowserSenders() {
        val files = listOf(
            file("b", "browser:client-b"),
            file("a", "browser:client-a"),
            file("host", "app"),
        )

        assertEquals(
            mapOf("browser:client-a" to 0, "browser:client-b" to 1),
            lanSharePeerColorIndices(files, emptySet()),
        )
    }

    @Test
    fun colorsStayDisabledForOnePeerAndIgnoreOwnUploads() {
        val files = listOf(file("peer", "browser:client-a"), file("own", "browser:host"))

        assertTrue(lanSharePeerColorIndices(files, setOf("own")).isEmpty())
    }

    private fun file(id: String, sender: String) = LanShareFile(id, "$id.txt", 1L, 1L, sender)
}
