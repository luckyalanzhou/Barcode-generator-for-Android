package com.luckyalanzhou.barcodegenerator.ui.app

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import org.junit.Assert.*
import org.junit.Test

class MenuPanelMotionTest {
    @Test fun tabDragIsVisibleBoundedAndRestoresAtZero() {
        val size = androidx.compose.ui.unit.IntSize(200, 160)
        val rest = tabMenuDragTransform(Offset.Zero, 1f, .25f, 1f, size, false)
        assertEquals(1f, rest.scale, 0f)
        assertEquals(Offset.Zero, rest.translation)
        for (motion in listOf(Offset(-.5f, 0f), Offset(.5f, 0f), Offset(0f, .5f))) {
            assertEquals(.95f, tabMenuDragTransform(motion, 1f, .25f, 1f, size, false).scale, .0001f)
        }
        assertEquals(.90f, tabMenuDragTransform(Offset(100f, 100f), 1f, .25f, 1f, size, false).scale, .0001f)
        assertEquals(1f, tabMenuDragTransform(Offset(Float.NaN, Float.POSITIVE_INFINITY), 1f, .25f, 1f, size, false).scale, 0f)
    }

    @Test fun tabDragKeepsItsOwnBottomCornerFixedDuringRevealAndReturn() {
        val size = androidx.compose.ui.unit.IntSize(200, 160)
        for (right in listOf(false, true)) {
            val pivot = if (right) .75f else .25f
            val corner = if (right) 200f else 0f
            for (presentation in listOf(.94f, 1f, 1.004f)) {
                for (amount in listOf(0f, .2f, .5f, 1f)) {
                    val drag = tabMenuDragTransform(Offset(amount, 0f), presentation, pivot, 1f, size, right)
                    val composite = pivot * 200f + (corner - pivot * 200f) * presentation * drag.scale + drag.translation.x
                    val revealOnly = pivot * 200f + (corner - pivot * 200f) * presentation
                    assertEquals(revealOnly, composite, .0001f)
                    assertEquals(0f, drag.translation.y, 0f)
                }
            }
        }
    }

    @Test fun genericTabTitleIsOmittedButEditingTitlesRemain() {
        assertFalse(menuShowsTitle(true, "操作"))
        assertFalse(menuShowsTitle(true, ""))
        assertTrue(menuShowsTitle(true, "收藏备份"))
        assertTrue(menuShowsTitle(false, "编辑"))
    }
    private val panel = Rect(20f, 100f, 220f, 300f)

    @Test fun tabSourceHasSmallerTravelAndLowerDragGainThanRowSources() {
        for (distance in listOf(8f, 32f, 64f, 192f, 10000f)) {
            val point = Offset(distance, distance)
            val row = menuAnchorMotion(point, Offset.Zero, 64f)
            val tab = tabMenuIconMotion(row)
            val expected = menuAnchorMotion(point, Offset.Zero, TabMenuSourceMotion.dragRangeDp)
            assertEquals(expected.x, tab.x, .0001f)
            assertEquals(expected.y, tab.y, .0001f)
            assertTrue(tab.x * TabMenuSourceMotion.horizontalLimitDp < row.x * 28f)
            assertTrue(tab.y * TabMenuSourceMotion.verticalLimitDp < row.y * 32f)
            assertTrue(tab.x * TabMenuSourceMotion.horizontalLimitDp < 20f)
            assertTrue(tab.y * TabMenuSourceMotion.verticalLimitDp < 22f)
        }
        assertTrue(TabMenuSourceMotion.returnStiffness < 700f)
        assertTrue(TabMenuSourceMotion.returnDamping in .9f..1f)
    }

    @Test fun menuDragFeedbackIsSubtleAndCannotGrowPanel() {
        assertEquals(1f, menuDragScale(Offset.Zero), 0f)
        for (motion in listOf(Offset(-1f, 0f), Offset(1f, 0f), Offset(0f, 1f))) {
            assertEquals(.97f, menuDragScale(motion), .0001f)
        }
    }

    @Test fun upwardSelectionKeepsMenuStationaryButSideAndDownDragsStillFollow() {
        val origin = Offset(100f, 400f)
        assertEquals(Offset.Zero, menuSourceInteractionMotion(Offset(105f, 300f), origin, 64f, true))
        assertEquals(Offset.Zero, menuSourceInteractionMotion(Offset(100f, 100f), origin, 64f, true))
        assertTrue(menuSourceInteractionMotion(Offset(164f, 395f), origin, 64f, true).x > 0f)
        assertTrue(menuSourceInteractionMotion(Offset(100f, 464f), origin, 64f, true).y > 0f)
        assertEquals(Offset.Zero, menuSourceInteractionMotion(Offset(105f, 500f), origin, 64f, false))
        assertTrue(menuSourceInteractionMotion(Offset(100f, 336f), origin, 64f, false).y < 0f)
    }

    @Test fun draggingUsesLatestPointerWithoutReadingSpringState() {
        var reads = 0
        val pointer = Offset(.7f, -.4f)
        assertEquals(pointer, menuMotionForDrawing(pointer, false) { reads++; Offset.Zero })
        assertEquals(0, reads)
        assertEquals(Offset(-.2f, .1f), menuMotionForDrawing(null, false) { reads++; Offset(-.2f, .1f) })
        assertEquals(1, reads)
        assertEquals(Offset.Zero, menuMotionForDrawing(pointer, true) { reads++; pointer })
        assertEquals(1, reads)
    }

    @Test fun sourceDragStartsAtRestAndFollowsAllFourDirections() {
        val origin = Offset(100f, 400f)
        assertEquals(Offset.Zero, menuAnchorMotion(origin, origin, 64f))
        assertEquals(Offset(1f / 3f, -1f / 3f), menuAnchorMotion(origin + Offset(32f, -32f), origin, 64f))
        assertEquals(Offset(-1f / 3f, 1f / 3f), menuAnchorMotion(origin + Offset(-32f, 32f), origin, 64f))
        assertEquals(Offset(1000f / 1064f, -1000f / 1064f), menuAnchorMotion(origin + Offset(1000f, -1000f), origin, 64f))
        assertEquals(Offset.Zero, menuAnchorMotion(null, origin, 64f))
        assertEquals(Offset.Zero, menuAnchorMotion(origin, origin, 0f))
    }

    @Test fun largerSourceDragsKeepRespondingAndShrinkInsteadOfSwelling() {
        val short = menuAnchorMotion(Offset(64f, 0f), Offset.Zero, 64f)
        val long = menuAnchorMotion(Offset(192f, 0f), Offset.Zero, 64f)
        assertTrue(long.x > short.x)
        assertTrue(long.x < 1f)
        assertEquals(1f, menuDragScale(Offset.Zero), .0001f)
        assertTrue(menuDragScale(long) < menuDragScale(short))
        assertEquals(.97f, menuDragScale(Offset(1f, 1f)), .0001f)
    }

    @Test fun noTouchAndPanelCenterAreAtRest() {
        assertEquals(Offset.Zero, menuPanelMotion(null, panel))
        assertEquals(Offset.Zero, menuPanelMotion(panel.center, panel))
    }

    @Test fun followsDirectionAndDistanceWithinPanel() {
        assertEquals(Offset(.5f, -.5f), menuPanelMotion(Offset(170f, 150f), panel))
    }

    @Test fun outsideTouchesCannotCauseUnboundedMotion() {
        assertEquals(Offset(1f, -1f), menuPanelMotion(Offset(10000f, -10000f), panel))
        assertEquals(Offset(-1f, 1f), menuPanelMotion(Offset(-10000f, 10000f), panel))
    }

    @Test fun zeroSizedPanelNeverProducesInvalidValues() {
        assertEquals(Offset.Zero, menuPanelMotion(Offset(1f, 2f), Rect.Zero))
    }

    @Test fun windowTranslationDoesNotChangeRelativeMotion() {
        val offset = Offset(24f, 38f)
        val point = Offset(170f, 150f)
        assertEquals(menuPanelMotion(point, panel), menuPanelMotion(point + offset, panel.translate(offset)))
    }
}
