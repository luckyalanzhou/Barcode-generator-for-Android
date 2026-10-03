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
    val transparency: DisplayEffectOptionState,
    val contrast: DisplayEffectOptionState,
)

internal fun displayEffectsSettingsState(style: StyleSettings, policy: VisualEffectsPolicy): DisplayEffectsSettingsState =
    DisplayEffectsSettingsState(
        summary = when {
            style.reduceMotion || style.reduceTransparency || style.enhanceContrast -> "已调整"
            policy.systemReducedMotion || policy.systemHighContrast -> "跟随系统"
            else -> "默认"
        },
        motion = DisplayEffectOptionState(
            checked = policy.reduceMotion,
            enabled = !policy.systemReducedMotion,
            explanation = if (policy.systemReducedMotion) "系统已关闭动画，当前跟随系统。" else
                "减少导航切换、弹性与玻璃折射动画，不影响切页操作。",
        ),
        transparency = DisplayEffectOptionState(
            checked = policy.opaqueGlass,
            enabled = !policy.highContrast,
            explanation = if (policy.highContrast) "提高对比度已包含不透明效果，无需重复开启。" else
                "关闭导航与菜单的背景透视、折射和模糊，让控件更清晰。",
        ),
        contrast = DisplayEffectOptionState(
            checked = policy.highContrast,
            enabled = !policy.systemHighContrast,
            explanation = if (policy.systemHighContrast) "系统已启用高对比文字，当前跟随系统并使用不透明背景。" else
                "加强导航与菜单文字、图标及选中状态的辨识度，同时使用不透明背景。",
        ),
    )
