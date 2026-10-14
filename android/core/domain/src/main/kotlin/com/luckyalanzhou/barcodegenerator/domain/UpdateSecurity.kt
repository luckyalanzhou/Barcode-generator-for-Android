package com.luckyalanzhou.barcodegenerator.domain

import java.security.MessageDigest

/** 更新版本比较、摘要和标准数字条码校验所需的纯 Kotlin 逻辑。 */
object UpdateSecurity {
    const val MAX_APK_DOWNLOAD_BYTES = 500L * 1024L * 1024L
    private val VERSION_PART_PATTERN = Regex("(?:0|[1-9][0-9]*)")

    fun sha256(value: String): String = sha256(value.toByteArray(Charsets.UTF_8))

    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }

    fun isValidEan13(value: String): Boolean = hasCheckDigit(value, 13)
    fun isValidEan8(value: String): Boolean = hasCheckDigit(value, 8)
    fun isValidUpcA(value: String): Boolean = hasCheckDigit(value, 12)
    fun isValidItf14(value: String): Boolean = hasCheckDigit(value, 14)

    private fun hasCheckDigit(value: String, length: Int): Boolean {
        if (value.length != length || !value.all { it in '0'..'9' }) return false
        val digits = value.map { it - '0' }
        val sum = digits.dropLast(1).reversed().mapIndexed { index, digit -> digit * if (index % 2 == 0) 3 else 1 }.sum()
        return (10 - sum % 10) % 10 == digits.last()
    }

    fun compareVersions(a: String, b: String): Int {
        val left = parseVersion(a) ?: return 0
        val right = parseVersion(b) ?: return 0
        for (index in 0 until maxOf(left.size, right.size)) {
            val leftPart = left.getOrElse(index) { "0" }
            val rightPart = right.getOrElse(index) { "0" }
            val comparison = compareNumericParts(leftPart, rightPart)
            if (comparison != 0) return comparison
        }
        return 0
    }

    /** 按十进制字符串比较任意长度的版本段，避免数值转换溢出。 */
    private fun compareNumericParts(left: String, right: String): Int =
        left.length.compareTo(right.length).takeIf { it != 0 } ?: left.compareTo(right)

    fun parseVersion(value: String): List<String>? {
        val parts = value.trim().split('.')
        if (parts.size !in 2..3 || parts.any { !VERSION_PART_PATTERN.matches(it) }) return null
        return parts
    }
}
