package com.luckyalanzhou.barcodegenerator.ui.component

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.luckyalanzhou.barcodegenerator.ui.theme.LocalVisualEffectsPolicy
import com.luckyalanzhou.barcodegenerator.ui.theme.VisualEffectsPolicy
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** 通过真实触摸输入确认无框按钮按下可见、松开恢复，且不依赖设备验收。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class UnframedActionPressFeedbackInteractionTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun pressProvidesVisibleFeedbackAndReleaseRestoresTheTarget() {
        val interactions = MutableInteractionSource()
        compose.setContent {
            CompositionLocalProvider(
                LocalVisualEffectsPolicy provides VisualEffectsPolicy(reduceMotion = false),
            ) {
                Box(
                    modifier = Modifier.size(120.dp).background(Color.Black).testTag("press-root"),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .unframedActionPressFeedback(interactions)
                            .background(Color.White)
                            .clickable(interactionSource = interactions, indication = null) {},
                    )
                }
            }
        }

        val target = compose.onNodeWithTag("press-root")
        val initialImage = target.captureToImage()
        val initial = initialImage.toPixelMap()
        val edgeX = (initialImage.width * 0.125f).toInt()
        val centerX = initialImage.width / 2
        val centerY = initialImage.height / 2
        assertTrue("the resting target should cover its original edge", initial[edgeX, centerY].red > 0.95f)
        assertTrue("the resting target should be bright", initial[centerX, centerY].red > 0.95f)

        target.performTouchInput { down(center) }
        compose.waitForIdle()

        val pressed = target.captureToImage().toPixelMap()
        assertTrue("press scale should visibly uncover the original edge", pressed[edgeX, centerY].red < 0.2f)
        assertTrue("press should visibly dim the target", pressed[centerX, centerY].red < 0.75f)

        target.performTouchInput { up() }
        compose.waitForIdle()
        // Release feedback deliberately remains visible for up to 80 ms; advance that delay
        // and the spring restoration before asserting the resting pixels.
        compose.mainClock.advanceTimeBy(300)
        compose.waitForIdle()

        val released = target.captureToImage().toPixelMap()
        assertTrue("release should restore the original edge", released[edgeX, centerY].red > 0.95f)
        assertTrue("release should restore target brightness", released[centerX, centerY].red > 0.95f)
    }
}
