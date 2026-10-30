package com.luckyalanzhou.barcodegenerator.ui.feature.lanshare

import android.app.Application
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.luckyalanzhou.barcodegenerator.ui.theme.AppTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class LanShareUploadingBubbleTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun cancelButtonInvokesOnlyTheActiveUploadCallback() {
        var cancelCount = 0

        compose.setContent {
            AppTheme("light") {
                LanShareUploadingBubble(
                    upload = LanShareUploadingContent("upload-1", "large.bin", 1024, 512, 0),
                    onCancel = { cancelCount++ },
                )
            }
        }

        compose.onNodeWithText("取消").performClick()

        compose.runOnIdle { assertEquals(1, cancelCount) }
    }

    @Test
    fun uploadProgressExposesAccessibleNameAndRange() {
        compose.setContent {
            AppTheme("light") {
                LanShareUploadingBubble(
                    upload = LanShareUploadingContent("upload-1", "large.bin", 100, 50, 0),
                    onCancel = {},
                )
            }
        }

        compose.onNodeWithContentDescription("正在上传 large.bin").assert(
            SemanticsMatcher.expectValue(
                SemanticsProperties.ProgressBarRangeInfo,
                ProgressBarRangeInfo(0.5f, 0f..1f),
            ),
        )
    }
}
