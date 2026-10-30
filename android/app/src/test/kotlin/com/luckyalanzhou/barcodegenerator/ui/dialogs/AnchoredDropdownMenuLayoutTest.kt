package com.luckyalanzhou.barcodegenerator.ui.dialogs

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Text
import com.luckyalanzhou.barcodegenerator.ui.theme.AppTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** 来源形变菜单仍需保留调用方宽度/高度边界，并允许长列表滚动。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AnchoredDropdownMenuLayoutTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun cornerRevealAppliesRequestedWidthAndHeightLimitToScrollablePanel() {
        var expanded by mutableStateOf(false)

        compose.setContent {
            AppTheme("light") {
                Box(Modifier.fillMaxSize()) {
                    Box(Modifier.width(160.dp).height(1.dp).testTag("width-reference"))
                    Box(Modifier.width(10.dp).height(120.dp).testTag("height-reference"))
                    AnchoredDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = {},
                        dark = false,
                        modifier = Modifier.testTag("morph-menu").heightIn(max = 120.dp),
                        menuWidth = 160.dp,
                        anchorHeight = 48.dp,
                        cornerReveal = true,
                    ) {
                        repeat(12) { index ->
                            Text("Option $index", Modifier.fillMaxWidth().height(48.dp))
                        }
                    }
                }
            }
        }

        compose.runOnIdle { expanded = true }
        compose.waitUntil(5_000) {
            compose.onAllNodesWithTag("morph-menu")
                .fetchSemanticsNodes(atLeastOneRootRequired = false)
                .any { it.boundsInRoot.width > 0f }
        }
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()

        val menuBounds = compose.onNodeWithTag("morph-menu").fetchSemanticsNode().boundsInRoot
        val widthReference = compose.onNodeWithTag("width-reference").fetchSemanticsNode().boundsInRoot
        val heightReference = compose.onNodeWithTag("height-reference").fetchSemanticsNode().boundsInRoot

        assertEquals("下拉菜单应遵循调用方的明确宽度", widthReference.width, menuBounds.width, 1f)
        assertTrue("长菜单应遵循调用方的最大高度", menuBounds.height <= heightReference.height + 1f)
        val scrollNodes = compose.onAllNodes(hasScrollAction(), useUnmergedTree = true)
            .fetchSemanticsNodes(atLeastOneRootRequired = false)
        assertTrue("长菜单应暴露滚动动作", scrollNodes.isNotEmpty())
        repeat(12) {
            compose.onNodeWithTag("morph-menu").performTouchInput { swipeUp() }
        }
        compose.waitForIdle()
        val lastOption = compose.onNodeWithText("Option 11").fetchSemanticsNode().boundsInRoot
        assertTrue("滚动后末项应落入菜单视口：option=$lastOption menu=$menuBounds",
            lastOption.top >= menuBounds.top && lastOption.bottom <= menuBounds.bottom)
    }
}
