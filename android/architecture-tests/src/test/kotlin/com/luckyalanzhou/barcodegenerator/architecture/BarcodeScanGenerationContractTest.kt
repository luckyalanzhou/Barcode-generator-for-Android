package com.luckyalanzhou.barcodegenerator.architecture

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class BarcodeScanGenerationContractTest {
    @Test
    fun barcodeResultsAreCommittedOnlyForTheLatestScan() {
        val activity = File("app/src/main/java/com/luckyalanzhou/barcodegenerator/MainActivity.kt").readText()
        val barcodeResult = activity
            .substringAfter("when (requestCode) {\n                    43, 44 ->")
            .substringBefore("                    51 ->")

        assertTrue("每次条码解码应取得当前请求代次", activity.contains("cameraOcrViewModel.beginBarcodeDecode()"))
        val currentCheck = "isCurrentBarcodeDecode(checkNotNull(barcodeDecodeRequestId))"
        assertTrue("过期解码不得覆盖生成草稿", barcodeResult.contains(currentCheck))
        assertTrue(
            "只有检查请求仍是最新后才允许回填生成草稿",
            barcodeResult.indexOf(currentCheck) in
                0 until barcodeResult.indexOf("generateViewModel.updateDraft(listOf(decoded))"),
        )

        val lanQrResult = activity
            .substringAfter("                    51 -> {")
            .substringBefore("                    45, 46 ->")
        assertTrue("旧 LAN 二维码解码不得加入过期房间", lanQrResult.contains(currentCheck))
        assertTrue(
            "LAN 房间加入前必须先验证解码请求仍是最新",
            lanQrResult.indexOf(currentCheck) in 0 until lanQrResult.indexOf("joinSessionFromAddress(decoded)"),
        )
    }
}
