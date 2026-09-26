package com.luckyalanzhou.barcodegenerator.presentation.lanshare

import com.luckyalanzhou.barcodegenerator.domain.LanShareFile
import com.luckyalanzhou.barcodegenerator.domain.LanShareSession
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LanShareRefreshGuardTest {
    @Test
    fun ignoresDelayedRefreshAfterSwitchingRooms() {
        val first = session("http://192.168.1.2:8080")
        val second = session("http://192.168.1.3:8080")
        val state = MutableStateFlow(LanShareUiState(session = first))
        val guard = LanShareRefreshGuard(state)
        val delayedTicket = guard.currentGeneration()

        guard.invalidate()
        state.value = LanShareUiState(session = second)
        guard.update(first, delayedTicket) { it.copy(files = listOf(file("old"))) }

        assertEquals(second, state.value.session)
        assertTrue(state.value.files.isEmpty())
        assertFalse(guard.isCurrent(first, delayedTicket))
    }

    @Test
    fun invalidationRejectsDelayedRefreshEvenWhenTheSameRoomIsRejoined() {
        val room = session()
        val state = MutableStateFlow(LanShareUiState(session = room))
        val guard = LanShareRefreshGuard(state)
        val delayedTicket = guard.currentGeneration()

        guard.invalidate()
        state.value = LanShareUiState(session = room)
        guard.update(room, delayedTicket) { it.copy(files = listOf(file("old"))) }

        assertTrue(state.value.files.isEmpty())
        assertTrue(guard.isCurrent(room, guard.currentGeneration()))
    }

    @Test
    fun ignoresDelayedRefreshAfterRoomIsClosed() {
        val room = session()
        val state = MutableStateFlow(LanShareUiState(session = room))
        val guard = LanShareRefreshGuard(state)
        val delayedTicket = guard.currentGeneration()

        guard.invalidate()
        state.value = LanShareUiState()
        guard.update(room, delayedTicket) { it.copy(files = listOf(file("old"))) }

        assertEquals(LanShareUiState(), state.value)
    }

    private fun session(baseUrl: String = "http://192.168.1.2:8080") = LanShareSession(baseUrl)

    private fun file(id: String) = LanShareFile(id, "$id.txt", 1L, 1L)
}
