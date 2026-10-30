package com.luckyalanzhou.barcodegenerator.ui.feature.lanshare

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.luckyalanzhou.barcodegenerator.domain.LanShareFile
import com.luckyalanzhou.barcodegenerator.ui.theme.AppTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class LanShareMessageBubbleAccessibilityTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun imagePreviewExposesButtonRoleAndPreservesActivation() {
        val bitmap = Bitmap.createBitmap(16, 8, Bitmap.Config.ARGB_8888)
        var previewCount = 0
        compose.setContent {
            AppTheme("light") {
                LanShareMessageBubble(
                    ownFileIds = emptySet(),
                    file = LanShareFile("image-1", "sample.png", 128, 1, mimeType = "image/png"),
                    previewReady = true,
                    loadPreview = { bitmap },
                    dark = false,
                    primary = Color.Black,
                    secondary = Color.Gray,
                    onSaveFile = {},
                    isDownloading = false,
                    peerColorIndex = null,
                    onPreviewImage = { previewCount++ },
                )
            }
        }

        val previewAction = compose.onNodeWithContentDescription("sample.png")
        previewAction.assert(
            SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button),
        )
        val clickAction = previewAction.fetchSemanticsNode().config[SemanticsActions.OnClick]
        assertEquals("预览图片", clickAction.label)
        previewAction.assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp).performClick()
        compose.runOnIdle { assertEquals(1, previewCount) }
    }
}
