package com.luckyalanzhou.barcodegenerator.ui.feature.lanshare

import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalResources
import com.luckyalanzhou.barcodegenerator.R
import com.luckyalanzhou.barcodegenerator.ui.support.localization.rememberApplicationFormattingLocale
import java.text.NumberFormat
import java.util.Locale

@Composable
internal fun formatLanShareSize(bytes: Long): String {
    val locale = rememberApplicationFormattingLocale()
    return LocalResources.current.formatLanShareFileSize(bytes, locale)
}

internal fun Resources.formatLanShareFileSize(
    bytes: Long,
    locale: Locale = configuration.locales[0],
): String {
    if (bytes < 0) return getString(R.string.file_size_unknown)
    val number = NumberFormat.getNumberInstance(locale)
    return when {
        bytes < 1024 -> getString(R.string.lan_file_size_bytes, number.format(bytes))
        bytes < 1024 * 1024 -> getString(R.string.lan_file_size_kilobytes, number.format(bytes / 1024))
        else -> {
            number.minimumFractionDigits = 1
            number.maximumFractionDigits = 1
            getString(R.string.lan_file_size_megabytes, number.format(bytes / (1024.0 * 1024.0)))
        }
    }
}
