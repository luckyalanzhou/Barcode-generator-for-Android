package com.luckyalanzhou.barcodegenerator.ui.feature.lanshare

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import com.luckyalanzhou.barcodegenerator.domain.LanShareSession
import com.luckyalanzhou.barcodegenerator.ui.theme.AppTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 主机可管理分享二维码；客户端状态不显示无效的二维码入口。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class LanShareQrEntryTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun qrEntryIsHiddenForClientAndOpensForHost() {
        val state = mutableStateOf(
            LanShareContentState(
                session = LanShareSession("http://192.168.1.2:8080", pairingCode = "123456"),
                isHost = false,
            ),
        )
        val qrVisibilityChanges = mutableListOf<Boolean>()

        compose.setContent {
            AppTheme("light") {
                LanShareContent(
                    lanState = state.value,
                    message = "",
                    dark = false,
                    onMessageChange = {},
                    onSetQrVisible = { qrVisibilityChanges += it },
                    onSend = {},
                    onCancelUpload = {},
                    onLoadImagePreview = { null },
                    onLoadFullImagePreview = { null },
                    onOpenCamera = {},
                    onOpenGallery = {},
                    onOpenFiles = {},
                    onSaveFile = {},
                    onCopyAddress = {},
                    createQrBitmap = { _, _, _, size -> Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888) },
                )
            }
        }

        compose.onAllNodesWithContentDescription("显示二维码").assertCountEquals(0)
        compose.runOnIdle { state.value = state.value.copy(isHost = true) }
        compose.onAllNodesWithContentDescription("显示二维码").assertCountEquals(1)
        compose.onNodeWithContentDescription("显示二维码").performClick()
        compose.runOnIdle { assertEquals(listOf(true), qrVisibilityChanges) }
    }
}
