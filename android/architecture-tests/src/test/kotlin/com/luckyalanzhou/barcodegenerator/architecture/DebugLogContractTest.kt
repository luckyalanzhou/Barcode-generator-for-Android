package com.luckyalanzhou.barcodegenerator.architecture

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 防止重新引入按字节截断、主线程打包和正式版日志后端。 */
class DebugLogContractTest {
    private fun source(path: String) = File("app/src/$path").readText()

    @Test
    fun betaAndOfficialSourcesAreDeclaredAsArchitectureTestInputs() {
        val buildScript = File("architecture-tests/build.gradle.kts").readText()
        assertTrue(buildScript.contains("app/src/beta/**/*.kt"))
        assertTrue(buildScript.contains("app/src/official/**/*.kt"))
    }

    @Test
    fun betaExportKeepsWholeRecordsAndIncludesFallback() {
        val backend = source("beta/java/com/luckyalanzhou/barcodegenerator/BetaDebugLogBackend.kt")
        assertTrue(backend.contains("selectRecentLogRecords("))
        assertTrue(backend.contains("buildLogExport("))
        assertTrue(backend.contains("debug.log"))
        assertTrue(backend.contains("sortedByDescending { it.lastModified() }"))
        assertTrue("Beta 导出需原子预留文件并放入专用分享缓存", backend.contains("createUniqueLogExportFile("))
        assertTrue(backend.contains("ensureShareableCacheDirectory(context.cacheDir, SHAREABLE_DEBUG_LOGS_DIRECTORY)"))
        val share = source("beta/java/com/luckyalanzhou/barcodegenerator/BetaDebugLogShare.kt")
        assertTrue(share.contains("withContext(Dispatchers.IO) { DebugLog.snapshot("))
        val log = source("main/java/com/luckyalanzhou/barcodegenerator/ui/support/logging/DebugLog.kt")
        assertTrue("兜底导出需原子预留文件并放入专用分享缓存", log.contains("createUniqueLogExportFile("))
        assertTrue(log.contains("ensureShareableCacheDirectory(context.cacheDir, SHAREABLE_DEBUG_LOGS_DIRECTORY)"))
    }

    @Test
    fun betaLogInitializationSchedulesDiskPreparationOffTheCallingThread() {
        val backend = source("beta/java/com/luckyalanzhou/barcodegenerator/BetaDebugLogBackend.kt")
        val initialization = backend.substringAfter("fun initialize(context: Context)")
            .substringBefore("\n    fun record(")
        val callerThreadSetup = initialization.substringBefore("writer.submit")

        assertTrue("初始化磁盘准备需要交给诊断写入线程", initialization.contains("writer.submit"))
        assertFalse(callerThreadSetup.contains("target.mkdirs()"))
        assertFalse(callerThreadSetup.contains("migrateLegacyFile("))
        assertFalse(callerThreadSetup.contains("cleanup(target)"))
    }

    @Test
    fun betaLogRetentionIsRecheckedWhenWritingOnANewDay() {
        val backend = source("beta/java/com/luckyalanzhou/barcodegenerator/BetaDebugLogBackend.kt")
        val write = backend.substringAfter("private fun writeNow(")
            .substringBefore("private fun dailyFile(")

        assertTrue("新日期的首次写入应触发一次过期日志清理", write.contains("cleanupSchedule.needsCleanup(currentDay)"))
        assertTrue(write.contains("cleanup(targetDirectory, currentDay)"))
        assertTrue(write.contains("cleanupSchedule.markCleaned(currentDay)"))
    }

    @Test
    fun exportedThrowableDiagnosticsUseMessageRedaction() {
        val backend = source("beta/java/com/luckyalanzhou/barcodegenerator/BetaDebugLogBackend.kt")
        val log = source("main/java/com/luckyalanzhou/barcodegenerator/ui/support/logging/DebugLog.kt")

        assertTrue(backend.contains("formatDiagnosticThrowable(it)"))
        assertTrue(log.contains("formatDiagnosticThrowable(error)"))
        assertTrue(log.contains("formatDiagnosticThrowable(it)"))
        assertFalse(backend.contains("Log.getStackTraceString("))
        assertFalse(log.contains("Log.getStackTraceString("))
        assertFalse(log.contains("Log.e(TAG, \"Beta log backend record failed: [\$tag] \$message\", backendError)"))
        assertFalse(log.contains("Log.e(\"BarcodeGenerator.DebugLog\", \"Fallback log write failed\", it)"))
    }

    @Test
    fun legacyThrowableMessagesAreRedactedWhenLogsAreExported() {
        val backend = source("beta/java/com/luckyalanzhou/barcodegenerator/BetaDebugLogBackend.kt")
        val snapshot = backend.substringAfter("fun snapshot(context: Context)")
            .substringBefore("private fun dailyFile(")
        val log = source("main/java/com/luckyalanzhou/barcodegenerator/ui/support/logging/DebugLog.kt")

        assertTrue("历史日志必须在导出前清理", snapshot.contains("sanitizeDiagnosticLogText"))
        assertTrue("兜底日志也必须在导出前清理", log.contains("sanitizeDiagnosticLogText(source.readText(Charsets.UTF_8))"))
    }

    @Test
    fun diagnosticsDoNotPersistRawUpdateUrlsOrUploadedFileNames() {
        val updateDialogs = source("main/java/com/luckyalanzhou/barcodegenerator/ui/app/platform/ComposeUpdateDialogs.kt")
        val updateTrace = updateDialogs.lineSequence().first { it.contains("download dialog shown") }
        assertFalse("更新 URL 可能含签名查询参数，不写入可导出的调试日志", updateTrace.contains("apkUrl"))
        assertTrue(updateTrace.contains("expectedSize"))

        val lanClient = File("core/lan-share/src/main/kotlin/com/luckyalanzhou/barcodegenerator/data/network/client/LanShareClient.kt")
            .readText()
        val uploadTrace = lanClient.lineSequence().first { it.contains("file uploaded") }
        assertFalse("文件名属于用户数据，不写入可导出的调试日志", uploadTrace.contains("name=\$name"))
        assertTrue(uploadTrace.contains("size=\$size"))
    }

    @Test
    fun failedRecognitionImageDecodeIsLoggedAndShownToTheUser() {
        val decoder = source("main/java/com/luckyalanzhou/barcodegenerator/ui/app/platform/ImageDecodeActivityBridge.kt")
        assertTrue("解码异常应记录不含 URI/图片内容的诊断事件", decoder.contains("DebugLog.record(\"image_decode\""))

        val activity = source("main/java/com/luckyalanzhou/barcodegenerator/MainActivity.kt")
        val missingBitmap = activity.substringAfter("val bitmap = withContext(Dispatchers.IO)")
            .substringBefore("when (requestCode)")
        assertTrue("未获得可识别位图时应记录请求类型", missingBitmap.contains("DebugLog.record(\"image_decode\""))
        assertTrue("未获得可识别位图时应给用户明确反馈", missingBitmap.contains("toast("))
    }

    @Test
    fun externalActivityLaunchFailureIsRecordedWithoutIntentData() {
        val activity = source("main/java/com/luckyalanzhou/barcodegenerator/MainActivity.kt")
        val launchFailure = activity.substringAfter("internal fun launchExternalActivity")
            .substringBefore("private fun handleExternalActivityResult")
        assertTrue("系统 Activity 启动失败应进入调试日志", launchFailure.contains("DebugLog.record(") && launchFailure.contains("\"external_activity\""))
        assertTrue("日志应包含静态请求码便于定位入口", launchFailure.contains("requestCode=\$requestCode"))
        assertFalse("不应把含 URI/参数的 Intent 原文写入日志", launchFailure.contains("intent.toUri("))
    }

    @Test
    fun renderDiagnosticsStayOutsideFrameParametersAndBehindBetaGate() {
        val glass = source("main/java/com/luckyalanzhou/barcodegenerator/ui/component/glass/GlassBackdrop.kt")
        assertTrue(glass.contains("if (BuildConfig.DEBUG_LOG_EXPORT)"))
        val details = glass.substringAfter("val diagnostic =").substringBefore("LaunchedEffect(diagnostic)")
        assertTrue(!details.contains("refraction"))
        assertTrue(!details.contains("motionEnergy"))
        val log = source("main/java/com/luckyalanzhou/barcodegenerator/ui/support/logging/DebugLog.kt")
        assertTrue(log.contains("if (!BuildConfig.DEBUG_LOG_EXPORT) return"))
        val official = source("official/java/com/luckyalanzhou/barcodegenerator/DebugLogBackend.kt")
        assertTrue(!official.contains("appendText"))
        assertTrue(!official.contains("writeText"))
    }
}
