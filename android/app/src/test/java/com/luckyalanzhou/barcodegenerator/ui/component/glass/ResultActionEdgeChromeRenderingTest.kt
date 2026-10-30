package com.luckyalanzhou.barcodegenerator.ui.component.glass

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.luckyalanzhou.barcodegenerator.ui.theme.AppTheme
import com.luckyalanzhou.barcodegenerator.ui.theme.LocalAppColorScheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/** 两种形状和深浅主题下，共享按钮外框都必须保持为单条细描边。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ResultActionEdgeChromeRenderingTest {
    @get:Rule val compose = createComposeRule()

    @Test fun sharedEdgeChromeUsesOneThinStrokeInLightAndDarkThemes() {
        val appearance = mutableStateOf("light")
        val circle = mutableStateOf(false)
        compose.setContent { EdgeProbe(appearance.value, circle.value) }

        listOf("light", "dark").forEach { scheme ->
            listOf(false, true).forEach { isCircle ->
                compose.runOnIdle {
                    appearance.value = scheme
                    circle.value = isCircle
                }
                val image = compose.onNodeWithTag("edge-probe").captureToImage()
                val pixels = image.toPixelMap()
                val x = image.width / 2
                val background = pixels[x, image.height / 2].toArgb()
                val visibleEdgeRows = (0 until 8).count { y -> colorDistance(pixels[x, y].toArgb(), background) > 10 }

                assertTrue(
                    "$scheme ${if (isCircle) "circle" else "rounded rectangle"} should render one visible thin contour; rows=$visibleEdgeRows",
                    visibleEdgeRows in 1..2,
                )
            }
        }
    }
}

@Composable
private fun EdgeProbe(appearance: String, circle: Boolean) {
    AppTheme(appearance) {
        val background = LocalAppColorScheme.current.surfaces.background
        val shape = if (circle) CircleShape else RoundedCornerShape(12.dp)
        val width = if (circle) 48.dp else 180.dp
        Box(
            Modifier
                .size(width = width, height = 48.dp)
                .background(background)
                .resultActionEdgeChrome(shape, rimBackground = background)
                .testTag("edge-probe"),
        )
    }
}

private fun colorDistance(first: Int, second: Int): Int = listOf(16, 8, 0).sumOf { shift ->
    abs((first ushr shift and 0xff) - (second ushr shift and 0xff))
}
