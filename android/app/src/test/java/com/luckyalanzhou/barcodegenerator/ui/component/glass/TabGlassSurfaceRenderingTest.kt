package com.luckyalanzhou.barcodegenerator.ui.component.glass

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/** Tab 选中胶囊静止时也必须绘制清楚、不过厚的深色玻璃边缘。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TabGlassSurfaceRenderingTest {
    @get:Rule val compose = createComposeRule()

    @Test fun movingCapsuleReusesBackdropRenderEffectWhenBlurIsUnchanged() {
        val renderer = BackdropRenderer.createOrNull()
        assertNotNull("The native graphics test environment should support the backdrop shader", renderer)

        val initial = tabGlassFrame(360f, 64f, 1f, 4, .4f, 1f, 0f, 1f)
        val moved = initial.copy(centerX = initial.centerX + 18f)
        val firstEffect = renderer!!.effect(IntSize(360, 64), 1f, Color.White, .48f, 32f, 4f, 2f, initial)
        val nextEffect = renderer.effect(IntSize(360, 64), 1f, Color.White, .48f, 32f, 4f, 2f, moved)

        assertSame("Capsule coordinates are shader uniforms; a steady blur graph should be reused", firstEffect, nextEffect)
    }

    @Test fun darkStaticSelectionHasVisibleThinTopRim() {
        compose.setContent { DarkStaticTabGlassProbe() }

        val image = compose.onNodeWithTag("dark-tab-glass-probe").captureToImage()
        val pixels = image.toPixelMap()
        val centerX = image.width / 8
        val outsideBackground = pixels[0, image.height / 2].toArgb()
        val visibleTopRows = (0 until minOf(12, image.height / 4)).count { y ->
            colorDistance(pixels[centerX, y].toArgb(), outsideBackground) > 45
        }

        assertTrue(
            "Dark static Tab glass should show a one-to-two-pixel top rim; rows=$visibleTopRows",
            visibleTopRows in 1..2,
        )
    }
}

@Composable
private fun DarkStaticTabGlassProbe() {
    val background = Color.Black
    val density = LocalDensity.current.density
    val frame = tabGlassFrame(
        width = with(LocalDensity.current) { 360.dp.toPx() },
        height = with(LocalDensity.current) { 64.dp.toPx() },
        density = density,
        tabCount = 4,
        progress = 0f,
        motion = 0f,
        impact = 0f,
        direction = 0f,
    )
    Box(
        Modifier
            .size(width = 360.dp, height = 64.dp)
            .background(background)
            .testTag("dark-tab-glass-probe"),
    ) {
        TabGlassSurface(
            frameProvider = { frame },
            material = tabGlassMaterial(background, 64f),
            accent = Color.Blue,
            background = background,
            drawMotionHighlight = false,
        )
    }
}

private fun colorDistance(first: Int, second: Int): Int = listOf(16, 8, 0).sumOf { shift ->
    abs((first ushr shift and 0xff) - (second ushr shift and 0xff))
}
