package com.luckyalanzhou.barcodegenerator.ui.support.logging

import java.io.FileNotFoundException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticThrowableFormatterTest {
    @Test
    fun keepsThrowableTypesAndFramesButOmitsPotentiallySensitiveMessages() {
        val cause = IllegalStateException("barcode-body=PRIVATE-12345")
            .apply { stackTrace = arrayOf(StackTraceElement("sample.Parser", "decode", "Parser.kt", 27)) }
        val error = FileNotFoundException("No content provider: content://private.provider/document/secret?token=abc")
            .apply {
                stackTrace = arrayOf(StackTraceElement("sample.Reader", "open", "Reader.kt", 42))
                initCause(cause)
                addSuppressed(IllegalArgumentException("/storage/emulated/0/secret.pdf"))
            }

        val summary = formatDiagnosticThrowable(error)

        assertTrue(summary.contains("java.io.FileNotFoundException"))
        assertTrue(summary.contains("Reader.kt:42"))
        assertTrue(summary.contains("java.lang.IllegalStateException"))
        assertTrue(summary.contains("Parser.kt:27"))
        assertTrue(summary.contains("java.lang.IllegalArgumentException"))
        assertFalse(summary.contains("content://"))
        assertFalse(summary.contains("secret?token"))
        assertFalse(summary.contains("PRIVATE-12345"))
        assertFalse(summary.contains("/storage/emulated/0"))
    }

    @Test
    fun scrubsSensitiveValuesFromLegacyLogsBeforeExport() {
        val oldRecords = """
            2026-10-20 12:00:00.000 [update] download dialog shown apkUrl=https://updates.example/app.apk?sig=secret
            2026-10-20 12:00:01.000 [lan_share] file uploaded name=private backup.zip size=10
            2026-10-20 12:00:02.000 [favorites_backup_parse] failed | java.io.FileNotFoundException: No content provider: content://private.provider/document/secret?token=abc
            ${'\t'}at sample.Reader.open(Reader.kt:42)
            Caused by: java.lang.SecurityException: denied /storage/emulated/0/private-backup.zip barcode-body=PRIVATE-12345
            ${'\t'}at sample.Reader.check(Reader.kt:51)
        """.trimIndent()

        val sanitized = sanitizeDiagnosticLogText(oldRecords)

        assertFalse(sanitized.contains("https://"))
        assertFalse(sanitized.contains("backup.zip"))
        assertFalse(sanitized.contains("content://"))
        assertFalse(sanitized.contains("token=abc"))
        assertFalse(sanitized.contains("/storage/emulated/0"))
        assertFalse(sanitized.contains("PRIVATE-12345"))
        assertTrue(sanitized.contains("Reader.kt:42"))
        assertTrue(sanitized.contains("SecurityException [message redacted]"))
    }

    @Test
    fun redactsCredentialProtectedAndDeviceEncryptedAppPathsFromLegacyLogs() {
        val oldRecords = listOf(
            "failed /data/data/com.example.app/files/private-cache.zip",
            "failed /data/user_de/0/com.example.app/no_backup/private-state.db",
        ).joinToString("\n")

        val sanitized = sanitizeDiagnosticLogText(oldRecords)

        assertFalse(sanitized.contains("/data/data/com.example.app"))
        assertFalse(sanitized.contains("/data/user_de/0/com.example.app"))
        assertTrue(sanitized.contains("[PATH redacted]"))
    }

    @Test
    fun redactsEntireAndroidFilePathWhenPathContainsSpaces() {
        val privatePath = "/data/user/0/com.example.app/files/private folder/backup secret.zip"

        val sanitized = sanitizeDiagnosticLogText("failed $privatePath operation=read")

        assertFalse(sanitized.contains("private folder"))
        assertFalse(sanitized.contains("backup secret.zip"))
    }
}
