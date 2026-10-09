package com.luckyalanzhou.barcodegenerator.ui.feature.settings

import com.luckyalanzhou.barcodegenerator.ui.dialogs.AnchoredDropdownMenu
import com.luckyalanzhou.barcodegenerator.ui.animation.ComposeAnimationConfig
import com.luckyalanzhou.barcodegenerator.ui.theme.*
import com.luckyalanzhou.barcodegenerator.ui.component.globalButtonChrome
import com.luckyalanzhou.barcodegenerator.ui.component.groupedContentSurface
import com.luckyalanzhou.barcodegenerator.ui.component.iosPressFeedback

import com.luckyalanzhou.barcodegenerator.domain.ocrCorrectionOptions

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Icon
import com.luckyalanzhou.barcodegenerator.icons.KeyboardArrowDownIcon
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.TextStyle

private val SettingsCardHorizontalPadding = 15.dp

@Composable
internal fun rememberSettingsColors(): AppColorScheme = LocalAppColorScheme.current

@Composable
internal fun SettingsCard(color: Color, dark: Boolean, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().groupedContentSurface(dark, color, RoundedCornerShape(16.dp)).padding(horizontal = SettingsCardHorizontalPadding, vertical = 2.dp), content = content)
}

@Composable
internal fun SettingsRow(title: String, color: Color, modifier: Modifier = Modifier, trailing: @Composable () -> Unit) {
    Row(modifier.fillMaxWidth().heightIn(min = LocalAppDimensions.current.settingsRowHeight), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = color, fontSize = 16.sp, modifier = Modifier.weight(1f).padding(end = 12.dp))
        trailing()
    }
}

@Composable
internal fun SettingsActionRow(title: String, action: String, color: Color, buttonColor: Color, onClick: () -> Unit) {
    // 标题表达设置项，右侧操作表达可执行动作，两者不共用文字颜色。
    SettingsRow(title, color) { SettingsButton(action, buttonColor, LocalAppColorScheme.current.controls.accent, onClick) }
}

@Composable
internal fun SettingsDropdownButton(text: String, color: Color, contentColor: Color, onClick: () -> Unit, onMeasured: (IntSize) -> Unit, pickerLabel: String? = null, expanded: Boolean = false) {
    val modifier = Modifier
        .then(if (pickerLabel != null) Modifier.semantics {
            contentDescription = pickerLabel
            stateDescription = "$text，${if (expanded) "已展开" else "已收起"}"
        } else Modifier)
    SettingsButton(text, color, contentColor, onClick, modifier, showDisclosure = pickerLabel != null,
        chromeModifier = Modifier.onGloballyPositioned { onMeasured(it.size) })
}

/** 设置页通用按钮：忙碌时禁止重复点击；带下拉提示时只负责打开选择菜单。 */
@Composable
internal fun SettingsButton(text: String, color: Color, contentColor: Color, onClick: () -> Unit, modifier: Modifier = Modifier, busy: Boolean = false, showDisclosure: Boolean = false, chromeModifier: Modifier = Modifier) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(modifier.heightIn(min = 48.dp).iosPressFeedback(interactionSource)
        .clickable(enabled = !busy, interactionSource = interactionSource, indication = null,
            role = Role.Button, onClick = onClick), contentAlignment = Alignment.Center) {
      Row(chromeModifier.globalButtonChrome(RoundedCornerShape(10.dp))
          .clip(RoundedCornerShape(10.dp)).background(color)
          .heightIn(min = 34.dp).padding(horizontal = 10.dp, vertical = 4.dp),
          verticalAlignment = Alignment.CenterVertically) {
        if (busy) {
            androidx.compose.material3.CircularProgressIndicator(Modifier.size(16.dp), color = contentColor, strokeWidth = 2.dp)
            androidx.compose.foundation.layout.Spacer(Modifier.width(6.dp))
        }
        Text(text, color = contentColor, fontSize = 14.sp, maxLines = 1,
            style = settingsButtonTextStyle(LocalTextStyle.current))
        if (showDisclosure) Icon(KeyboardArrowDownIcon, contentDescription = null,
            tint = LocalAppColorScheme.current.settingsText.secondary,
            modifier = Modifier.padding(start = 6.dp).size(16.dp))
    }
    }
}

/** 按钮文字不继承宿主背景，避免文字层再绘制一块多余的背景矩形。 */
internal fun settingsButtonTextStyle(inherited: TextStyle): TextStyle =
    inherited.copy(background = Color.Transparent)

@Composable
internal fun SettingsDropdown(dark: Boolean, expanded: Boolean, menuWidth: Dp, anchorWidth: Dp?, anchorHeight: Dp?, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    AnchoredDropdownMenu(dark = dark, expanded = expanded, onDismissRequest = onDismiss, shape = RoundedCornerShape(16.dp), containerColor = LocalAppColorScheme.current.surfaces.overlay, tonalElevation = 0.dp, shadowElevation = 1.dp, menuWidth = menuWidth.coerceAtLeast(110.dp), anchorWidth = anchorWidth, anchorHeight = anchorHeight, alignEndWithAnchor = true, cornerReveal = true, content = content)
}

@Composable
internal fun SettingsDivider(dark: Boolean) {
    Spacer(
        Modifier
            .fillMaxWidth()
            .height(if (dark) 0.5.dp else 1.dp)
            .background(LocalAppColorScheme.current.borders.divider),
    )
}

/** 开关只有自身触控区域可切换；标题说明不响应点击，语义状态由开关控件统一提供。 */
@Composable
internal fun SettingsSwitchTarget(
    title: String,
    checked: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    explanation: String? = null,
    onCheckedChange: (Boolean) -> Unit,
) {
    // 触控和无障碍语义只归开关目标所有，避免内部轨道再次拦截点击或重复触发状态变更。
    Box(
        modifier.width(52.dp).heightIn(min = 48.dp)
            .toggleable(value = checked, enabled = enabled, role = Role.Switch,
                onValueChange = onCheckedChange)
            .semantics { contentDescription = switchAccessibilityLabel(title, explanation) },
        contentAlignment = Alignment.Center,
    ) {
        SettingsToggle(checked, enabled, Modifier.clearAndSetSemantics {})
    }
}

internal fun switchAccessibilityLabel(title: String, explanation: String?): String =
    if (explanation.isNullOrBlank()) title else "$title。$explanation"

internal fun settingsSwitchColor(color: Color, surface: Color, enabled: Boolean, highContrast: Boolean): Color =
    if (enabled || highContrast) color else androidx.compose.ui.graphics.lerp(surface, color, .55f)

@Composable
private fun SettingsToggle(checked: Boolean, enabled: Boolean, modifier: Modifier = Modifier) {
    val themeColors = LocalAppColorScheme.current
    val highContrast = LocalVisualEffectsPolicy.current.highContrast
    val track = settingsSwitchColor(if (checked) themeColors.controls.toggleOn else themeColors.controls.toggleOff,
        themeColors.surfaces.surface, enabled, highContrast)
    val thumb = settingsSwitchColor(themeColors.controls.thumb, themeColors.surfaces.surface, enabled, highContrast)
    val trackColor = animateColorAsState(track, ComposeAnimationConfig.toggleSpring(), label = "settings-toggle-track")
    val thumbOffset = animateDpAsState(if (checked) 20.dp else 0.dp, ComposeAnimationConfig.toggleSpring(), label = "settings-toggle-thumb")
    Box(
        modifier.width(52.dp).height(32.dp)
            .clip(RoundedCornerShape(16.dp))
            .drawBehind {
                drawRoundRect(
                    color = trackColor.value,
                    cornerRadius = CornerRadius(16.dp.toPx()),
                )
            }
            .padding(2.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier.size(28.dp)
                .graphicsLayer { translationX = thumbOffset.value.toPx() }
                .shadow(1.dp, CircleShape)
                .clip(CircleShape)
                .background(thumb),
        )
    }
}

internal val ocrReplacementLabels = ocrCorrectionOptions.map { it.label to it.bit }
