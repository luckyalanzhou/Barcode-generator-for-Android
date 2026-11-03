package com.luckyalanzhou.barcodegenerator.data

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FavoritesTransferFolderValidationTest {
    @Test
    fun restoreRejectsFolderSegmentsThatCannotBeExportedAgain() {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry("favorites/invalid-folder.json"))
            zip.write(
                """{"name":"invalid-folder","rootFolder":"..","subFolder":"","type":"code128","texts":["ABC"]}"""
                    .toByteArray(),
            )
            zip.closeEntry()
        }

        val failure = runCatching { FavoritesTransferManager.restore(output.toByteArray()) }.exceptionOrNull()

        assertTrue(
            "导入应因父目录文件夹段被拒绝，实际错误：${failure?.message}",
            failure is IllegalArgumentException && failure.message.orEmpty().contains("一级文件夹格式无效"),
        )
    }
}
