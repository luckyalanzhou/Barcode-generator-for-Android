package com.luckyalanzhou.barcodegenerator.ui.feature.lanshare

import java.util.Locale

internal fun formatLanShareSize(bytes: Long): String =
    when {
        bytes < 0L -> "大小未知"
        bytes < 1024L -> "$bytes B"
        bytes >= 1024L * 1024L ->
            String.format(Locale.getDefault(), "%.1f MB", bytes / 1024.0 / 1024.0)
        else -> "${bytes / 1024} KB"
    }
