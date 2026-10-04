package com.luckyalanzhou.barcodegenerator.ui.feature.settings

import com.luckyalanzhou.barcodegenerator.domain.StyleSettings
import com.luckyalanzhou.barcodegenerator.ui.theme.VisualEffectsPolicy

/** Effective values are presentation only; system overrides never overwrite stored preferences. */
internal data class DisplayEffectOptionState(
    val checked: Boolean,
    val enabled: Boolean,
    val explanation: String,
)

internal data class DisplayEffectsSettingsState(
    val summary: String,
    val motion: DisplayEffectOptionState,
    val contrast: DisplayEffectOptionState,
)

internal fun displayEffectsSettingsState(style: StyleSettings, policy: VisualEffectsPolicy): DisplayEffectsSettingsState =
    DisplayEffectsSettingsState(
        summary = when {
            style.reduceMotion || style.enhanceContrast -> "已调整"
            policy.systemReducedMotion || policy.systemHighContrast -> "跟随系统"
            else -> "默认"
        },
        motion = DisplayEffectOptionState(
            checked = policy.reduceMotion,
            enabled = !policy.systemReducedMotion,
            explanation = if (policy.systemReducedMotion) "系统已关闭动画，当前跟随系统。" else
                "减少导航切换、弹性与玻璃折射动画，不影响切页操作。",
        ),
        contrast = DisplayEffectOptionState(
            checked = policy.highContrast,
            enabled = !policy.systemHighContrast,
            explanation = if (policy.systemHighContrast) "系统已启用高对比文字，当前跟随系统并使用不透明背景。" else
                "加强导航与菜单文字、图标及选中状态的辨识度，同时使用不透明背景。",
        ),
    )
