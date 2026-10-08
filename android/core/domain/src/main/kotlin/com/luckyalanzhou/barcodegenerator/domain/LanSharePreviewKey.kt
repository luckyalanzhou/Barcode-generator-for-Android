package com.luckyalanzhou.barcodegenerator.domain

import java.security.MessageDigest

/** Maps an untrusted remote file id to a safe, stable local cache filename. */
fun lanSharePreviewCacheKey(id: String): String = MessageDigest.getInstance("SHA-256")
    .digest(id.toByteArray(Charsets.UTF_8))
    .joinToString(separator = "") { byte -> "%02x".format(byte) }
