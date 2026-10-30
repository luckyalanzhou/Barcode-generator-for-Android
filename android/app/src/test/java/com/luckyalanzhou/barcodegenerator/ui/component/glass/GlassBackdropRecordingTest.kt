package com.luckyalanzhou.barcodegenerator.ui.component.glass

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** 使用原生 Compose 图形后端验证 RenderNode 重入保护。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GlassBackdropRecordingTest {
    @get:Rule val compose = createComposeRule()

    @Test fun nestedRecordersSharingOneLayerDoNotBeginRecordingTwice() {
        compose.setContent {
            val source = rememberTestBackdropSource()
            Box(Modifier.fillMaxSize().recordGlassBackdrop(source).background(Color.White)) {
                Box(Modifier.size(120.dp).recordGlassBackdrop(source).background(Color.Blue))
            }
        }
        val rendered = compose.onRoot().captureToImage()
        assertTrue(rendered.width > 0)
    }
}

@Composable
private fun rememberTestBackdropSource(): GlassBackdropSource {
    val layer = rememberGraphicsLayer()
    return remember(layer) { GlassBackdropSource(layer) }
}
