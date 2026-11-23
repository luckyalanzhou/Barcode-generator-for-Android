package com.luckyalanzhou.barcodegenerator.ui.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MenuPanelMotionTest {
    @Test fun genericTabTitleIsOmittedButEditingTitlesRemain() {
        assertFalse(menuShowsTitle(""))
        assertTrue(menuShowsTitle("收藏备份"))
        assertTrue(menuShowsTitle("编辑"))
    }
}
