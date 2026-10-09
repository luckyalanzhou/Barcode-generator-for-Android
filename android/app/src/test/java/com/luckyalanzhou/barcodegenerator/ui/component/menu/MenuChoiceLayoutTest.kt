package com.luckyalanzhou.barcodegenerator.ui.component.menu

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luckyalanzhou.barcodegenerator.ui.theme.AppTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** 实际 Compose 测量：勾号不撑高行，大字体保持自然高度。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MenuChoiceLayoutTest {
    @get:Rule val compose = createComposeRule()

    @Test fun checkedAndUncheckedRowsHaveEqualNaturalHeight() = verifyHeight(1f)
    @Test fun largerFontGrowsBothRowsEqually() = verifyHeight(1.5f)

    @Test fun iconsRemainTwoDpLargerThanOptionTextAcrossFontScales() {
        for (fontScale in listOf(.85f, 1f, 1.5f, 2f)) {
            val density = Density(2f, fontScale)
            val textSize = with(density) { 16.sp.toDp() }
            val iconSize = menuOptionIconSize(density)
            assertEquals(4f, iconSize.value - textSize.value, .001f)
        }
    }

    private fun verifyHeight(fontScale: Float) {
        var density = 1f
        compose.setContent {
            density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, fontScale)) {
                AppTheme("light") {
                    Column(Modifier.width(200.dp)) {
                        MenuChoiceItem("已选中", true) { }
                        MenuChoiceItem("未选中", false) { }
                    }
                }
            }
        }
        val checked = compose.onNodeWithText("已选中").fetchSemanticsNode().boundsInRoot.height
        val unchecked = compose.onNodeWithText("未选中").fetchSemanticsNode().boundsInRoot.height
        assertEquals(checked, unchecked, .5f)
        // 字体回退的真实字形可能高于声明行高；测量真实文字行框，而非假定字形为 20dp。
        val textHeight = compose.onNodeWithText("未选中", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot.height
        assertEquals(textHeight + 16f * density, unchecked, 2f)
    }
}
