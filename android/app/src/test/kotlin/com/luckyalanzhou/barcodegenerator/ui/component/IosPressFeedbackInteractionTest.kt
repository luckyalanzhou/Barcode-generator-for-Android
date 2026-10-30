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

/** 验证减少动效策略下，共享按压 Modifier 仍即时变暗且不缩小内容。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class IosPressFeedbackInteractionTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun reducedMotionPressDimsImmediatelyWithoutScaling() {
        val interactions = MutableInteractionSource()
        compose.setContent {
            CompositionLocalProvider(
                LocalVisualEffectsPolicy provides VisualEffectsPolicy(reduceMotion = true),
            ) {
                Box(
                    modifier = Modifier.size(120.dp).background(Color.Black).testTag("press-root"),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .iosPressFeedback(interactions)
                            .background(Color.White)
                            .clickable(interactionSource = interactions, indication = null) {},
                    )
                }
            }
        }

        val target = compose.onNodeWithTag("press-root")
        val initialImage = target.captureToImage()
        val initialPixels = initialImage.toPixelMap()
        val edgeX = initialImage.width / 12
        val midY = initialImage.height / 2
        val initialEdge = initialPixels[edgeX, midY]
        val initialCenter = initialPixels[initialImage.width / 2, midY]

        assertTrue("the unpressed target should cover its original edge", initialEdge.red > 0.95f)
        assertTrue("the unpressed target should be fully bright", initialCenter.red > 0.95f)

        target.performTouchInput { down(center) }
        compose.waitForIdle()

        val pressedImage = target.captureToImage()
        val pressedPixels = pressedImage.toPixelMap()
        val pressedEdge = pressedPixels[edgeX, midY]
        val pressedCenter = pressedPixels[pressedImage.width / 2, midY]
        assertTrue("reduced motion must not scale the target away from its edge", pressedEdge.red > 0.65f)
        assertTrue("press must still provide visible dim feedback", pressedCenter.red < initialCenter.red * 0.9f)

        target.performTouchInput { up() }
        compose.waitForIdle()
        val releasedCenter = target.captureToImage().toPixelMap()[initialImage.width / 2, midY]
        assertTrue("the target should return to its resting appearance", releasedCenter.red > 0.95f)
    }
}
