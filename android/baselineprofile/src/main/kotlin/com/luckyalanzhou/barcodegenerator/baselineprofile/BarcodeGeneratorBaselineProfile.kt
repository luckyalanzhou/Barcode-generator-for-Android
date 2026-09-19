package com.luckyalanzhou.barcodegenerator.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import androidx.test.uiautomator.UiDevice
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 覆盖首屏和四个主 Tab 的真实用户路径。
 * 生成的 profile 会随 app 一起打包，减少冷启动与首次切页时的解释执行。
 */
@RunWith(AndroidJUnit4::class)
class BarcodeGeneratorBaselineProfile {
    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun startupAndMainTabs() = rule.collect(
        packageName = "com.luckyalanzhou.barcodegenerator.test",
        includeInStartupProfile = true,
    ) {
        pressHome()
        startActivityAndWait()
        device.waitForIdle()
        clickText("历史")
        clickText("收藏")
        clickText("设置")
        clickText("生成")
    }

    private fun clickText(text: String) {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        device.wait(Until.hasObject(By.text(text)), 3_000)
        device.findObject(By.text(text)).click()
        device.waitForIdle()
    }
}
