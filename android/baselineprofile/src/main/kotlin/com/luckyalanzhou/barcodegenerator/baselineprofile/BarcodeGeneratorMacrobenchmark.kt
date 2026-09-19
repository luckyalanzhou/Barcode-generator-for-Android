package com.luckyalanzhou.barcodegenerator.baselineprofile

import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.benchmark.macro.StartupMode
import androidx.test.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import androidx.test.uiautomator.UiDevice
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** 冷启动和主 Tab 切换的可重复性能回归基线。 */
@RunWith(AndroidJUnit4::class)
class BarcodeGeneratorMacrobenchmark {
    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun coldStartup() = rule.measureRepeated(
        packageName = "com.luckyalanzhou.barcodegenerator.test",
        metrics = listOf(StartupTimingMetric()),
        iterations = 5,
        startupMode = StartupMode.COLD,
        setupBlock = {
            pressHome()
        },
        measureBlock = {
            startActivityAndWait()
        },
    )

    @Test
    fun mainTabSwitching() = rule.measureRepeated(
        packageName = "com.luckyalanzhou.barcodegenerator.test",
        metrics = listOf(FrameTimingMetric()),
        iterations = 5,
        startupMode = StartupMode.COLD,
        setupBlock = {
            pressHome()
        },
        measureBlock = {
            startActivityAndWait()
            clickText("历史")
            clickText("收藏")
            clickText("设置")
            clickText("生成")
        },
    )

    private fun clickText(text: String) {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        device.wait(Until.hasObject(By.text(text)), 3_000)
        device.findObject(By.text(text)).click()
        device.waitForIdle()
    }
}
