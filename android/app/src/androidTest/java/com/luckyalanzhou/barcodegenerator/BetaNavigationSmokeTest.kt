package com.luckyalanzhou.barcodegenerator

import android.app.Activity
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Beta runtime smoke test: verifies startup and the four bottom-tab destinations. */
@RunWith(AndroidJUnit4::class)
class BetaNavigationSmokeTest {
    @Test
    fun launchAndSwitchBottomTabs() {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        ActivityScenario.launch(MainActivity::class.java).use {
            assertText(device, "条码生成器")

            clickTab(device, "历史")
            assertText(device, "暂无历史记录")

            clickTab(device, "收藏")
            assertText(device, "还没有收藏")

            clickTab(device, "设置")
            assertText(device, "外观")

            clickTab(device, "生成")
            assertText(device, "条码生成器")
        }
    }

    private fun clickTab(device: UiDevice, label: String) {
        val nodes = device.findObjects(By.text(label))
        assertTrue("Tab not found: $label", nodes.isNotEmpty())
        nodes.last().click()
    }

    private fun assertText(device: UiDevice, text: String) {
        assertTrue(
            "Text not found: $text",
            device.wait(Until.hasObject(By.text(text)), 10_000),
        )
    }
}
