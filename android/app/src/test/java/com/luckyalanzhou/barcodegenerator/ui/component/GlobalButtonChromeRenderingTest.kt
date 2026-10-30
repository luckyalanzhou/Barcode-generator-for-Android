package com.luckyalanzhou.barcodegenerator.ui.component

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
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
import com.luckyalanzhou.barcodegenerator.ui.component.glass.resultActionEdgeChrome
import com.luckyalanzhou.barcodegenerator.ui.theme.AppTheme
import com.luckyalanzhou.barcodegenerator.ui.theme.LocalAppColorScheme
import kotlin.math.abs
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** 有框普通按钮和结果工具按钮必须使用相同的静态边缘材质。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GlobalButtonChromeRenderingTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun ordinaryAndResultButtonsShareTheSameRimInBothThemes() {
        val appearance = mutableStateOf("light")
        compose.setContent {
            AppTheme(appearance.value) {
                val background = LocalAppColorScheme.current.surfaces.background
                Row {
                    RimProbe(background, resultRim = false, tag = "ordinary-rim")
                    RimProbe(background, resultRim = true, tag = "result-rim")
                }
            }
        }

        for (scheme in listOf("light", "dark")) {
            compose.runOnIdle { appearance.value = scheme }
            val ordinary = compose.onNodeWithTag("ordinary-rim").captureToImage().toPixelMap()
            val result = compose.onNodeWithTag("result-rim").captureToImage().toPixelMap()
            val x = ordinary.width / 2
            val edgeDistance = colorDistance(ordinary[x, 1].toArgb(), result[x, 1].toArgb())

            assertTrue(
                "$scheme 普通按钮与结果按钮的上缘应使用同一材质，像素差=$edgeDistance",
                edgeDistance <= 3,
            )
        }
    }
}

@Composable
private fun RimProbe(
    background: androidx.compose.ui.graphics.Color,
    resultRim: Boolean,
    tag: String,
) {
    val shape = RoundedCornerShape(12.dp)
    val modifier = Modifier
        .size(width = 180.dp, height = 48.dp)
        .background(background)
        .then(
            if (resultRim) Modifier.resultActionEdgeChrome(shape, rimBackground = background)
            else Modifier.globalButtonChrome(shape),
        )
        .testTag(tag)
    Box(modifier)
}

private fun colorDistance(first: Int, second: Int): Int = listOf(16, 8, 0).sumOf { shift ->
    abs((first ushr shift and 0xff) - (second ushr shift and 0xff))
}
