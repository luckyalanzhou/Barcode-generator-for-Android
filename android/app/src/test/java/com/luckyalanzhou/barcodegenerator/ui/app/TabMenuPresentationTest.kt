package com.luckyalanzhou.barcodegenerator.ui.app

import org.junit.Assert.*
import org.junit.Test

class TabMenuPresentationTest {
    @Test fun closingRetainsMenuAndRunsActionOnce() {
        val controller = TabMenuPresentation<String>()
        var calls = 0
        controller.show("历史")
        controller.measured()
        controller.dismiss { calls++ }
        assertEquals("历史", controller.menu)
        assertFalse(controller.open)
        controller.dismiss { calls += 10 }
        controller.closed()
        controller.closed()
        assertEquals(1, calls)
        assertNull(controller.menu)
    }

    @Test fun earlyDismissDoesNotLeaveInvisibleModal() {
        val controller = TabMenuPresentation<String>()
        controller.show("收藏")
        controller.dismiss()
        assertNull(controller.menu)
    }

    @Test fun reopeningCancelsOldPendingAction() {
        val controller = TabMenuPresentation<String>()
        var calls = 0
        controller.show("历史")
        controller.measured()
        controller.dismiss { calls++ }
        controller.show("设置")
        controller.closed()
        assertEquals("设置", controller.menu)
        assertEquals(0, calls)
        controller.measured()
        controller.dismiss()
        controller.closed()
        assertNull(controller.menu)
        assertEquals(0, calls)
    }
}
