package com.luckyalanzhou.barcodegenerator.data

import com.luckyalanzhou.barcodegenerator.domain.LanShareFile
import com.luckyalanzhou.barcodegenerator.domain.LanShareMessage
import com.luckyalanzhou.barcodegenerator.domain.LanShareRealtimeEvent
import com.luckyalanzhou.barcodegenerator.domain.LanShareRealtimeState
import com.luckyalanzhou.barcodegenerator.domain.LAN_SHARE_MAX_SESSION_MESSAGES
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LanShareRealtimeStateTest {
    @Test
    fun latestConnectionStateReplacesPreviousValue() {
        val connected = LanShareRealtimeState().applying(LanShareRealtimeEvent.ConnectionChanged(true))
        val disconnected = connected.applying(LanShareRealtimeEvent.ConnectionChanged(false))

        assertTrue(connected.browserConnected)
        assertFalse(disconnected.browserConnected)
    }

    @Test
    fun messageStateDeduplicatesByIdAndKeepsOnlyLatestSessionWindow() {
        val messages = (0..LAN_SHARE_MAX_SESSION_MESSAGES).map { index ->
            LanShareMessage("message-$index", "text-$index", "browser", index.toLong())
        }
        val state = messages.fold(LanShareRealtimeState()) { current, message ->
            current.applying(LanShareRealtimeEvent.MessageAdded(message))
        }
        val duplicate = state.applying(LanShareRealtimeEvent.MessageAdded(messages.last()))

        assertEquals(LAN_SHARE_MAX_SESSION_MESSAGES, state.messages.size)
        assertEquals("message-1", state.messages.first().id)
        assertEquals(state.messages, duplicate.messages)
    }

    @Test
    fun fileStateUpsertsExistingIdentityWithoutLosingOtherFiles() {
        val original = LanShareFile("one", "one.txt", 1, 1)
        val other = LanShareFile("two", "two.txt", 1, 2)
        val updated = original.copy(name = "renamed.txt", modifiedAt = 3)
        val state = LanShareRealtimeState()
            .applying(LanShareRealtimeEvent.FileAdded(original))
            .applying(LanShareRealtimeEvent.FileAdded(other))
            .applying(LanShareRealtimeEvent.FileAdded(updated))

        assertEquals(listOf("two", "one"), state.files.map(LanShareFile::id))
        assertEquals("renamed.txt", state.files.last().name)
    }
}
