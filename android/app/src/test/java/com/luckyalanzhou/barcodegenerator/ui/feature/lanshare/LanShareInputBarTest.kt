package com.luckyalanzhou.barcodegenerator.ui.feature.lanshare

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.junit4.StateRestorationTester
import com.luckyalanzhou.barcodegenerator.domain.LanShareSession
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.unit.dp
import com.luckyalanzhou.barcodegenerator.domain.LanShareFile
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertTrue
import com.luckyalanzhou.barcodegenerator.ui.theme.AppTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class LanShareInputBarTest {
    @get:Rule val compose = createComposeRule()

    @Test fun latestImageSaveActionRemainsVisibleAfterAsyncPreviewGrows() {
        val preview = CompletableDeferred<Bitmap?>()
        compose.setContent {
            AppTheme("light") {
                Box(Modifier.height(300.dp).testTag("transfer-host")) {
                    LanShareContent(
                        lanState = LanShareContentState(
                            session = LanShareSession("http://127.0.0.1:8080"),
                            files = listOf(LanShareFile("image", "portrait.png", 800, 1, mimeType = "image/png")),
                        ),
                        message = "", dark = false, onMessageChange = {}, onSetQrVisible = {}, onSend = {},
                        onCancelUpload = {}, onLoadImagePreview = { preview.await() }, onLoadFullImagePreview = { null },
                        onOpenCamera = {}, onOpenGallery = {}, onOpenFiles = {}, onSaveFile = {}, onCopyAddress = {},
                        createQrBitmap = { _, _, _, size -> Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888) },
                    )
                }
            }
        }
        compose.waitForIdle()
        preview.complete(Bitmap.createBitmap(400, 800, Bitmap.Config.ARGB_8888))
        compose.waitForIdle()
        val save = compose.onNodeWithText("保存").assertIsDisplayed().getUnclippedBoundsInRoot()
        val viewport = compose.onNode(hasScrollAction()).getUnclippedBoundsInRoot()
        assertTrue("save action clipped after async image growth: save=$save viewport=$viewport", save.top >= viewport.top && save.bottom <= viewport.bottom)
    }

    @Test fun unsentDraftSurvivesSavedStateRestoration() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            AppTheme("light") {
                LanShareScreen(
                    lanState = LanShareContentState(session = LanShareSession("http://127.0.0.1:8080")),
                    dark = false, clearInputGeneration = 0,
                    onOpenCamera = {}, onOpenGallery = {}, onOpenFiles = {}, onSaveFile = {}, onCopyAddress = {},
                    createQrBitmap = { _, _, _, size -> Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888) },
                    onSetQrVisible = {}, onCancelUpload = {}, onLoadImagePreview = { null },
                    onLoadFullImagePreview = { null }, onSend = {},
                )
            }
        }
        compose.onNodeWithContentDescription("消息内容").performTextInput("unsent draft")
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("unsent draft").assertIsDisplayed()
    }

    @Test fun attachmentStaysVisibleWithDraftAndCanBeRemovedWithoutSending() {
        val pending = mutableStateOf<String?>("report.pdf")
        var sent = 0
        compose.setContent {
            AppTheme("light") {
                LanShareInputBar(
                    dark = false, panel = Color.White, inputPanel = Color.LightGray,
                    primary = Color.Black, secondary = Color.DarkGray, accent = Color.Blue,
                    pendingUploadName = pending.value, message = "保留的草稿",
                    onMessageChange = {}, onSend = { sent++ },
                    onOpenAttachmentMenu = {}, attachmentMenu = false,
                    onDismissAttachmentMenu = {}, onOpenCamera = {}, onOpenGallery = {}, onOpenFiles = {},
                    onRemovePendingUpload = { pending.value = null },
                )
            }
        }
        compose.onNodeWithText("report.pdf").assertIsDisplayed()
        compose.onNodeWithContentDescription("消息内容").assertIsNotEnabled()
        compose.onNodeWithContentDescription("移除待发送附件").performClick()
        compose.onNodeWithContentDescription("消息内容").assertIsEnabled()
        compose.onNodeWithText("保留的草稿").assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, sent) }
    }
}
