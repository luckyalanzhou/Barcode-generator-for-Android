package com.luckyalanzhou.barcodegenerator.ui.feature.lanshare

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.luckyalanzhou.barcodegenerator.ui.theme.AppTheme
import com.luckyalanzhou.barcodegenerator.ui.theme.LocalAppColorScheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** LAN 主机的明文传输提示应完整暴露给界面与辅助技术。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class LanShareSecurityNoticeTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun securityNoticeExplainsTransportRiskAndSafeNetworkAction() {
        compose.setContent {
            AppTheme("light") {
                LanShareSecurityNotice(secondary = LocalAppColorScheme.current.text.secondary)
            }
        }

        compose.onNodeWithText(
            "配对令牌用于限制访问，但 HTTP/WebSocket 仍未加密；请仅在可信 Wi-Fi 下使用。",
        ).assertIsDisplayed()
    }
}
