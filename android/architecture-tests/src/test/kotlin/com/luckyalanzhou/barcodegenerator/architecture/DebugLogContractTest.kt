package com.luckyalanzhou.barcodegenerator.architecture

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/** 防止重新引入按字节截断、主线程打包和正式版日志后端。 */
class DebugLogContractTest {
    private fun source(path: String) = File("app/src/$path").readText()

    @Test
    fun betaExportKeepsWholeRecordsAndIncludesFallback() {
        val backend = source("beta/java/com/luckyalanzhou/barcodegenerator/BetaDebugLogBackend.kt")
        assertTrue(backend.contains("selectRecentLogRecords("))
        assertTrue(backend.contains("buildLogExport("))
        assertTrue(backend.contains("debug.log"))
        assertTrue(backend.contains("sortedByDescending { it.lastModified() }"))
        val share = source("beta/java/com/luckyalanzhou/barcodegenerator/BetaDebugLogShare.kt")
        assertTrue(share.contains("withContext(Dispatchers.IO) { DebugLog.snapshot("))
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
