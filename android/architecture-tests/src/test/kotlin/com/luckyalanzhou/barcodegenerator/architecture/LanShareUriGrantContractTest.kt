package com.luckyalanzhou.barcodegenerator.architecture

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** SAF 文件仅供当前局域网传输，不应留下跨设备重启仍有效的文档读取授权。 */
class LanShareUriGrantContractTest {
    @Test
    fun attachmentTransferUsesOnlyTemporaryUriAccess() {
        val bridge = File("app/src/main/java/com/luckyalanzhou/barcodegenerator/ui/app/lanshare/LanShareActivityBridge.kt")
            .readText()

        assertFalse(
            "局域网附件只在当前上传任务中使用，不应持久化 URI 授权",
            bridge.contains("takePersistableUriPermission"),
        )
        assertFalse(
            "普通附件选择不应申请跨重启持久 URI 授权",
            bridge.contains("FLAG_GRANT_PERSISTABLE_URI_PERMISSION"),
        )

        val application = File("app/src/main/java/com/luckyalanzhou/barcodegenerator/BarcodeGeneratorApplication.kt")
            .readText()
        assertTrue(application.contains("persistedUriPermissions"))
        assertTrue(application.contains("releasePersistableUriPermission"))
        assertTrue(application.contains("URI_GRANT_MIGRATION_COMPLETED"))
        assertTrue(application.contains("getBoolean(URI_GRANT_MIGRATION_COMPLETED, false)"))
    }
}
