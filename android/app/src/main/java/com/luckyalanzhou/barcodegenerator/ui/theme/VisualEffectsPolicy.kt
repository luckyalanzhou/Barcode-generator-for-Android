package com.luckyalanzhou.barcodegenerator.ui.theme

import android.animation.ValueAnimator
import android.database.ContentObserver
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.luckyalanzhou.barcodegenerator.domain.StyleSettings

@Immutable
internal data class VisualEffectsPolicy(
    val reduceMotion: Boolean = false,
    val opaqueGlass: Boolean = false,
    val highContrast: Boolean = false,
)

internal val LocalVisualEffectsPolicy = staticCompositionLocalOf { VisualEffectsPolicy() }

internal fun resolveVisualEffectsPolicy(style: StyleSettings, animationsEnabled: Boolean, systemHighContrast: Boolean): VisualEffectsPolicy {
    val contrast = style.enhanceContrast || systemHighContrast
    return VisualEffectsPolicy(style.reduceMotion || !animationsEnabled, style.reduceTransparency || contrast, contrast)
}

/** Public system APIs only. Observe live changes and unregister everything with the composition. */
@Composable
internal fun rememberVisualEffectsPolicy(style: StyleSettings): VisualEffectsPolicy {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val accessibility = remember(context) { context.getSystemService(AccessibilityManager::class.java) }
    var animationsEnabled by remember { mutableStateOf(ValueAnimator.areAnimatorsEnabled()) }
    var contrast by remember { mutableStateOf(false) }
    DisposableEffect(context, accessibility, lifecycle) {
        fun refresh() {
            animationsEnabled = ValueAnimator.areAnimatorsEnabled()
            contrast = Build.VERSION.SDK_INT >= 36 && accessibility?.isHighContrastTextEnabled == true
        }
        refresh()
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { refresh() }
        }
        context.contentResolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer)
        val lifecycleObserver = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh()
        }
        lifecycle.addObserver(lifecycleObserver)
        val removeContrastListener = if (Build.VERSION.SDK_INT >= 36 && accessibility != null) {
            observeHighContrast(accessibility, context.mainExecutor) { contrast = it }
        } else ({})
        onDispose {
            context.contentResolver.unregisterContentObserver(observer)
            lifecycle.removeObserver(lifecycleObserver)
            removeContrastListener()
        }
    }
    return resolveVisualEffectsPolicy(style, animationsEnabled, contrast)
}

@RequiresApi(36)
private fun observeHighContrast(manager: AccessibilityManager, executor: java.util.concurrent.Executor, onChange: (Boolean) -> Unit): () -> Unit {
    val listener = AccessibilityManager.HighContrastTextStateChangeListener { onChange(it) }
    manager.addHighContrastTextStateChangeListener(executor, listener)
    return { manager.removeHighContrastTextStateChangeListener(listener) }
}
