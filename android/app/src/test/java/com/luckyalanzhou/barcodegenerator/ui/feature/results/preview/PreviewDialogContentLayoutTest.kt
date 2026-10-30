package com.luckyalanzhou.barcodegenerator.ui.feature.results.preview

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.luckyalanzhou.barcodegenerator.ui.theme.AppTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** 预览弹窗的大字号窄屏布局仍需保留全部媒体操作。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PreviewDialogContentLayoutTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun largeTextAndNarrowWidthKeepAllActionsVisible() {
        val bitmap = Bitmap.createBitmap(24, 24, Bitmap.Config.ARGB_8888)
        compose.setContent {
            AppTheme("light") {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, 1.8f)) {
                    Box(Modifier.requiredSize(width = 280.dp, height = 640.dp)) {
                        PreviewDialogContent(
                            format = "Code 128-B",
                            text = "SAMPLE-123",
                            bitmap = bitmap,
                            dark = false,
                            onSave = {},
                            onShare = {},
                            onDismiss = {},
                        )
                    }
                }
            }
        }

        compose.onNodeWithText("保存图片").assertIsDisplayed()
        compose.onNodeWithText("分享图片").assertIsDisplayed()
        compose.onNodeWithText("关闭").assertIsDisplayed()
    }
}
