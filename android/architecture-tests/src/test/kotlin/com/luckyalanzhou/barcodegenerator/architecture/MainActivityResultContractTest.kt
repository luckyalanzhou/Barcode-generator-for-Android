package com.luckyalanzhou.barcodegenerator.architecture

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Activity 结果和路由桥接必须保留可恢复状态，并对异常回调明确处理。 */
class MainActivityResultContractTest {
    @Test
    fun enteringLanSharePreservesTheExistingSettingsReturnRoute() {
        val bridge = File("app/src/main/java/com/luckyalanzhou/barcodegenerator/ui/app/lanshare/LanShareActivityBridge.kt")
            .readText()
        val enter = bridge
            .substringAfter("internal fun MainActivity.enterLanShare()")
            .substringBefore("internal fun MainActivity.showLanShareNetworkErrorDialog")

        assertTrue(enter.contains("navigateTo(AppRoute.LanShare)"))
        assertFalse(
            "嵌套进入局域网分享不能把设置页返回目标改成设置页本身",
            enter.contains("updateSettingsReturnPage(AppRoute.Settings)"),
        )
    }

    @Test
    fun returningWithoutInstallPermissionReopensThePromptForThePendingApk() {
        val activity = File("app/src/main/java/com/luckyalanzhou/barcodegenerator/MainActivity.kt").readText()
        val resume = activity
            .substringAfter("override fun onResume()")
            .substringBefore("override fun onSaveInstanceState")

        assertTrue(resume.contains("pendingInstallPath"))
        assertTrue(resume.contains("else if (!installPermissionDialogShowing)"))
        assertTrue(resume.contains("installApkCompose(File(pendingInstallPath))"))

        val updateDialogs = File("app/src/main/java/com/luckyalanzhou/barcodegenerator/ui/app/platform/ComposeUpdateDialogs.kt")
            .readText()
        assertTrue(updateDialogs.contains("if (installPermissionDialogShowing) return"))
        assertTrue(updateDialogs.contains("installPermissionDialogShowing = false"))
    }

    @Test
    fun expiredUpdatePackagesAreCleanedOnResumeWithoutDeletingPendingInstallApk() {
        val activity = File("app/src/main/java/com/luckyalanzhou/barcodegenerator/MainActivity.kt").readText()
        val resume = activity
            .substringAfter("override fun onResume()")
            .substringBefore("override fun onSaveInstanceState")

        assertTrue(resume.contains("cleanupExpiredUpdateApkFiles()"))
        assertTrue(
            "过期 APK 清理必须在读取待安装路径前启动，且清理器需保护该路径",
            resume.indexOf("cleanupExpiredUpdateApkFiles()") in 0 until resume.indexOf("val pendingInstallPath"),
        )
        val cleanup = activity
            .substringAfter("private fun cleanupExpiredUpdateApkFiles()")
            .substringBefore("private fun cleanupInterruptedCameraCaptures()")
        assertTrue(cleanup.contains("val protectedApkPaths = updateViewModel.uiState.value.pendingInstallPath"))
        assertTrue(cleanup.contains("cleanupExpiredApkDownloadFiles(cacheDirectory, protectedApkPaths, nowMillis)"))
        assertTrue(cleanup.contains("Dispatchers.IO"))
    }

    @Test
    fun rejectedDownloadedApkIsCleanedBeforeShowingValidationFailure() {
        val updateDialogs = File("app/src/main/java/com/luckyalanzhou/barcodegenerator/ui/app/platform/ComposeUpdateDialogs.kt")
            .readText()
        val downloadReady = updateDialogs
            .substringAfter("is UpdateEvent.DownloadReady")
            .substringBefore("is UpdateEvent.DownloadFailed")

        assertTrue(downloadReady.contains("validateDownloadedApkForInstall"))
        assertTrue(downloadReady.contains("showDownloadFailedCompose"))
        assertTrue(downloadReady.indexOf("validateDownloadedApkForInstall") < downloadReady.indexOf("installApkCompose(file)"))
        assertTrue(downloadReady.contains("catch (cancelled: CancellationException)"))
    }

    @Test
    fun barcodeEncodingOnlyConvertsRecoverableExceptionsToMissingImage() {
        val renderer = File("app/src/main/java/com/luckyalanzhou/barcodegenerator/infrastructure/barcode/ZxingBarcodeImageRenderer.kt")
            .readText()
        val encode = renderer
            .substringAfter("private fun encode(")
            .substringBefore("private fun trim(")

        assertTrue(encode.contains("captureRecoverableBarcodeEncoding"))
        assertFalse(encode.contains("runCatching"))
    }

    @Test
    fun recognitionImageDecodeOnlyConvertsRecoverableExceptionsToNoImage() {
        val decoder = File("app/src/main/java/com/luckyalanzhou/barcodegenerator/ui/app/platform/ImageDecodeActivityBridge.kt")
            .readText()
        val decode = decoder
            .substringAfter("internal fun MainActivity.decodeRecognitionBitmap")
            .substringBefore("internal fun MainActivity.recognizeText")

        assertTrue(decode.contains("captureRecoverableRecognitionImageDecode"))
        assertFalse(decode.contains("runCatching"))
    }

    @Test
    fun lanShareHostStartPreservesFatalErrorsAndKeepsFailureRollback() {
        val bridge = File("app/src/main/java/com/luckyalanzhou/barcodegenerator/ui/app/lanshare/LanShareActivityBridge.kt")
            .readText()
        val enter = bridge
            .substringAfter("internal fun MainActivity.enterLanShare()")
            .substringBefore("internal fun MainActivity.showLanShareNetworkErrorDialog")

        assertTrue(enter.contains("startHostSessionAsync"))
        assertFalse(enter.contains("runCatching"))
        val viewModel = File("app/src/main/java/com/luckyalanzhou/barcodegenerator/presentation/lanshare/LanShareViewModel.kt")
            .readText()
        val startup = viewModel
            .substringAfter("fun startHostSessionAsync(")
            .substringBefore("fun joinSession(")
        assertTrue(startup.contains("captureRecoverableLanShareHostStart"))
        assertTrue(startup.contains("rollback = { lanShareGateway.stop(clearSharedFiles = true) }"))
        assertTrue(startup.contains("onFailure = { recoverableFailure = it }"))
        assertTrue(enter.contains("navigateTo(AppRoute.Settings)"))
    }

    @Test
    fun favoriteBackupChooserOnlyConvertsRecoverableLaunchFailures() {
        val bridge = File("app/src/main/java/com/luckyalanzhou/barcodegenerator/ui/app/platform/FavoriteBackupActivityBridge.kt")
            .readText()
        val share = bridge
            .substringAfter("internal fun MainActivity.shareFavoritesExportForCompose()")
            .substringBefore("internal suspend fun <T> prepareFavoriteBackupShareArtifact")

        assertTrue(share.contains("launchFavoriteBackupChooser"))
        assertFalse(share.contains("runCatching"))
    }

    @Test
    fun favoriteBackupDocumentExportRollsBackTheCreatedDocumentOnFailure() {
        val bridge = File("app/src/main/java/com/luckyalanzhou/barcodegenerator/ui/app/platform/FavoriteBackupActivityBridge.kt")
            .readText()
        val export = bridge
            .substringAfter("internal fun MainActivity.exportFavorites(uri: Uri)")
            .substringBefore("private fun formatFavoritesExportError")

        assertTrue(export.contains("writeCreatedDocumentWithRollbackSuspending("))
        assertTrue(export.contains("contentResolver.delete(uri, null, null) > 0"))
        assertTrue(export.contains("temporary.outputStream().use"))
        assertTrue(export.contains("temporary.inputStream().use"))
    }

    @Test
    fun externalActivityLaunchOnlyTreatsOrdinaryExceptionsAsRecoverable() {
        val activity = File("app/src/main/java/com/luckyalanzhou/barcodegenerator/MainActivity.kt").readText()
        val launch = activity
            .substringAfter("internal fun launchExternalActivity")
            .substringBefore("private fun handleExternalActivityResult")

        assertFalse("致命 JVM Error 不应伪装成系统选择器启动失败", launch.contains("runCatching"))
        assertTrue("普通 Activity 启动异常仍需反馈并清理暂存请求", launch.contains("catch (error: Exception)"))
        assertTrue(launch.contains("cameraOcrViewModel.consumeExternalActivityRequest()"))
    }

    @Test
    fun resultImageSaveMissingUriIsNotSilent() {
        val activity = File("app/src/main/java/com/luckyalanzhou/barcodegenerator/MainActivity.kt").readText()
        val resultImageResult = activity
            .substringAfter("if (requestCode == REQUEST_RESULT_IMAGE_FILE)")
            .substringBefore("if (requestCode == REQUEST_LAN_SHARE_UPLOAD || requestCode == REQUEST_LAN_SHARE_DOWNLOAD)")

        assertTrue(resultImageResult.contains("result_image_save_returned_without_uri"))
        assertTrue(resultImageResult.contains("未能获取保存位置，请重试"))
        assertTrue(resultImageResult.contains("finishPendingResultImageDocument(imageFile)"))
    }

    @Test
    fun resultImageSaveFailureIsDiagnosedWithoutProviderDetails() {
        val activity = File("app/src/main/java/com/luckyalanzhou/barcodegenerator/MainActivity.kt").readText()
        val resultImageResult = activity
            .substringAfter("if (requestCode == REQUEST_RESULT_IMAGE_FILE)")
            .substringBefore("if (requestCode == REQUEST_LAN_SHARE_UPLOAD || requestCode == REQUEST_LAN_SHARE_DOWNLOAD)")

        assertTrue(resultImageResult.contains("result_image_save_failed"))
        assertTrue(resultImageResult.contains("error.javaClass.simpleName"))
        assertTrue(resultImageResult.contains("catch (cancel: CancellationException)"))
        assertTrue(!resultImageResult.contains("error.message"))
        assertTrue(!resultImageResult.contains("targetUri.toString()"))
    }

    @Test
    fun lanSharePickerMissingUriDoesNotLeaveSilentOrStaleState() {
        val activity = File("app/src/main/java/com/luckyalanzhou/barcodegenerator/MainActivity.kt").readText()
        val pickerResults = activity
            .substringAfter("if (requestCode == REQUEST_LAN_SHARE_UPLOAD || requestCode == REQUEST_LAN_SHARE_DOWNLOAD)")
            .substringBefore("if (requestCode == REQUEST_LAN_SHARE_CAPTURE)")
        val downloadResult = pickerResults.substringAfter("val downloadId =")

        assertTrue(pickerResults.contains("upload_picker_returned_without_uri"))
        assertTrue(downloadResult.contains("download_picker_returned_without_uri"))
        assertTrue(
            "下载 ID 必须在判断成功/取消和目标 URI 之前清理",
            downloadResult.indexOf("setPendingDownloadId(null)") in 0 until downloadResult.indexOf("resultCode"),
        )
    }

    @Test
    fun favoriteBackupPickerMissingUriIsDiagnosedAndRetryable() {
        val activity = File("app/src/main/java/com/luckyalanzhou/barcodegenerator/MainActivity.kt").readText()
        val pickerResult = activity
            .substringAfter("if (requestCode == REQUEST_FAVORITES_EXPORT || requestCode == REQUEST_FAVORITES_IMPORT)")
            .substringBefore("if (resultCode != RESULT_OK)")

        assertTrue(pickerResult.contains("if (resultCode == RESULT_OK)"))
        assertTrue(pickerResult.contains("backup_picker_returned_without_uri"))
        assertTrue(pickerResult.contains("未能获取备份文件，请重试"))
        assertTrue(pickerResult.contains("REQUEST_FAVORITES_EXPORT -> exportFavorites(uri)"))
        assertTrue(pickerResult.contains("REQUEST_FAVORITES_IMPORT -> confirmImportFavorites(uri)"))
    }

    @Test
    fun lanShareDownloadWithoutActiveTransferSchedulesCreatedDocumentCleanup() {
        val activity = File("app/src/main/java/com/luckyalanzhou/barcodegenerator/MainActivity.kt").readText()
        val pickerResults = activity
            .substringAfter("if (requestCode == REQUEST_LAN_SHARE_UPLOAD || requestCode == REQUEST_LAN_SHARE_DOWNLOAD)")
            .substringBefore("if (requestCode == REQUEST_LAN_SHARE_CAPTURE)")
        val inactiveTransferBranch = pickerResults
            .substringAfter("if (downloadId == null || session == null)")
            .substringBefore("lanShareViewModel.downloadFile(session, downloadId, uri)")

        assertTrue(inactiveTransferBranch.contains("lifecycleScope.launch"))
        assertTrue(inactiveTransferBranch.contains("runDocumentProviderCleanup"))
        assertTrue(inactiveTransferBranch.contains("contentResolver.delete(uri, null, null)"))
    }
}
