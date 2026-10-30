package com.luckyalanzhou.barcodegenerator.domain

import java.security.MessageDigest

/** 将不可信的远程文件 ID 转换为安全、稳定的本地缓存文件名。 */
fun lanSharePreviewCacheKey(id: String): String = MessageDigest.getInstance("SHA-256")
    .digest(id.toByteArray(Charsets.UTF_8))
    .joinToString(separator = "") { byte -> "%02x".format(byte) }
