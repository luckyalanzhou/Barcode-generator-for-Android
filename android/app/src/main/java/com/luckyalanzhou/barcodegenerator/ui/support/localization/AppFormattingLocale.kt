package com.luckyalanzhou.barcodegenerator.ui.support.localization

import android.content.Context
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.LocaleManagerCompat
import java.util.Locale

/** Uses the app language while retaining the device region when that language has a regional system locale. */
internal fun resolveApplicationFormattingLocale(
    appLocale: Locale?,
    systemLocales: List<Locale>,
): Locale {
    if (appLocale == null) return systemLocales.firstOrNull() ?: Locale.SIMPLIFIED_CHINESE
    if (appLocale.country.isNotEmpty()) return appLocale

    val regionalLocale = systemLocales.firstOrNull {
        it.language == appLocale.language && it.country.isNotEmpty()
    } ?: return appLocale

    return Locale.Builder().setLocale(appLocale).setRegion(regionalLocale.country).build()
}

internal fun Context.applicationFormattingLocale(): Locale {
    val appLocales = if (Build.VERSION.SDK_INT >= 33) {
        LocaleManagerCompat.getApplicationLocales(this)
    } else {
        AppCompatDelegate.getApplicationLocales()
    }
    val systemLocales = LocaleManagerCompat.getSystemLocales(this)
    val locales = buildList {
        for (index in 0 until systemLocales.size()) {
            systemLocales[index]?.let(::add)
        }
    }
    return resolveApplicationFormattingLocale(appLocales[0], locales)
}

/** Recomputes when Android updates the app or system locale configuration. */
@Composable
internal fun rememberApplicationFormattingLocale(): Locale {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    return remember(context, configuration) { context.applicationFormattingLocale() }
}
