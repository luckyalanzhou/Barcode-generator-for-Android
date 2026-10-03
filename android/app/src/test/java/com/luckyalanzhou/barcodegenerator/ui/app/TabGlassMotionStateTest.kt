package com.luckyalanzhou.barcodegenerator.ui.app

import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.ui.geometry.Offset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TabGlassMotionStateTest {
    @Test fun reducedMotionPreservesNavigationAndDragWithoutSpringOrPulse() = runBlocking {
        val job = SupervisorJob()
        val state = TabGlassMotionState(CoroutineScope(coroutineContext.minusKey(Job) + job), 0, 4)
        try {
            state.setReducedMotion(true)
            yield()
            state.select(3)
            state.pulse(.03f)
            state.press(Offset.Zero)
            yield()
            assertEquals(3f, state.progress, 0f)
            assertEquals(0f, state.impact.value, 0f)
            assertFalse(state.settling)
            state.beginDrag(Offset.Zero, 100)
            state.drag(-.6f, Offset.Zero, 116)
            assertEquals(2.4f, state.progress, .001f)
            state.release(2)
            yield()
            assertEquals(2f, state.progress, 0f)
            assertFalse(state.dragging)
            assertFalse(state.settling)
        } finally { job.cancel() }
    }
    @Test
    fun releaseHandsOverAtTheRenderedFingerPosition() = runBlocking {
        val clock = BroadcastFrameClock()
        val job = SupervisorJob()
        val state = TabGlassMotionState(CoroutineScope(coroutineContext.minusKey(Job) + job + clock), 0, 4)
        try {
            state.beginDrag(Offset.Zero, 100)
            state.drag(.72f, Offset(72f, 20f), 116)
            state.release(1)
            yield()
            assertFalse(state.dragging)
            assertEquals(.72f, state.progress, .0001f)
            repeat(120) { clock.sendFrame((it + 1) * 16_666_667L); yield() }
            assertEquals(1f, state.progress, .001f)
            assertFalse(state.settling)
        } finally {
            job.cancel()
        }
    }

    @Test
    fun grabbingAMovingCapsuleDoesNotJumpToTheTouchLocation() = runBlocking {
        val clock = BroadcastFrameClock()
        val job = SupervisorJob()
        val state = TabGlassMotionState(CoroutineScope(coroutineContext.minusKey(Job) + job + clock), 0, 4)
        try {
            state.select(3)
            yield()
            repeat(8) { clock.sendFrame((it + 1) * 16_666_667L); yield() }
            val current = state.progress
            assertTrue(current > 0f && current < 3f)
            state.beginDrag(Offset(280f, 25f), 200)
            assertEquals(current, state.progress, .0001f)
            state.drag(-.2f, Offset(260f, 25f), 216)
            assertEquals(current - .2f, state.progress, .0001f)
            assertEquals(-1f, state.direction, 0f)
            yield()
            assertTrue(state.dragging)
            assertFalse(state.settling)
        } finally {
            job.cancel()
        }
    }

    @Test
    fun rapidRetargetingFinishesAtTheLatestTab() = runBlocking {
        val clock = BroadcastFrameClock()
        val job = SupervisorJob()
        val state = TabGlassMotionState(CoroutineScope(coroutineContext.minusKey(Job) + job + clock), 0, 4)
        try {
            state.select(3)
            yield()
            repeat(8) { clock.sendFrame((it + 1) * 16_666_667L); yield() }
            val previous = state.progress
            state.select(1)
            yield()
            assertEquals(previous, state.progress, .0001f)
            repeat(120) { clock.sendFrame((it + 9) * 16_666_667L); yield() }
            assertEquals(1f, state.progress, .001f)
            assertFalse(state.settling)
        } finally {
            job.cancel()
        }
    }

    @Test
    fun equalPhysicalVelocityIsIndependentOfPointerEventFrequency() {
        for (interval in listOf(4L, 6L, 8L, 11L, 16L, 33L)) {
            assertEquals(4f, tabDragVelocity(4f * interval / 1000f, interval), .0001f)
        }
        assertEquals(0f, tabDragVelocity(.1f, 0), 0f)
        assertEquals(10f, tabDragVelocity(10f, 1), 0f)
    }

    @Test
    fun springSettlesUsingFrameTimeAtDifferentRefreshRates() = runBlocking {
        for (refreshRate in listOf(60, 90, 120, 144, 165)) {
            val clock = BroadcastFrameClock()
            val job = SupervisorJob()
            val state = TabGlassMotionState(CoroutineScope(coroutineContext.minusKey(Job) + job + clock), 0, 4)
            try {
                state.select(3)
                yield()
                repeat(refreshRate * 2) {
                    clock.sendFrame((it + 1) * 1_000_000_000L / refreshRate)
                    yield()
                }
                assertEquals("refreshRate=$refreshRate", 3f, state.progress, .001f)
                assertFalse("refreshRate=$refreshRate", state.settling)
            } finally {
                job.cancel()
            }
        }
    }
}
