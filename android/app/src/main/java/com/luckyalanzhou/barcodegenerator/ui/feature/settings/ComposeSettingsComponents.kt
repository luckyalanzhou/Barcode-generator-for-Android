package com.luckyalanzhou.barcodegenerator.ui.feature.settings

import androidx.compose.ui.res.stringResource
import com.luckyalanzhou.barcodegenerator.R

import com.luckyalanzhou.barcodegenerator.ui.dialogs.AnchoredDropdownMenu
import com.luckyalanzhou.barcodegenerator.ui.theme.*
import com.luckyalanzhou.barcodegenerator.ui.component.glass.resultActionEdgeChrome
import com.luckyalanzhou.barcodegenerator.ui.component.groupedContentSurface
import com.luckyalanzhou.barcodegenerator.ui.component.iosPressFeedback

import com.luckyalanzhou.barcodegenerator.domain.ocrCorrectionOptions

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Icon
import com.luckyalanzhou.barcodegenerator.icons.UnfoldMoreIcon
import com.luckyalanzhou.barcodegenerator.icons.KeyboardArrowRightIcon
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight

private val SettingsCardHorizontalPadding = 15.dp

@Composable
internal fun rememberSettingsColors(): AppColorScheme = LocalAppColorScheme.current

@Composable
internal fun SettingsCard(color: Color, dark: Boolean, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().groupedContentSurface(dark, color, RoundedCornerShape(16.dp)).padding(horizontal = SettingsCardHorizontalPadding, vertical = 2.dp), content = content)
}

/** 分组标题位于卡片外，卡片只承载同一用途的设置或动作。 */
@Composable
internal fun SettingsSection(title: String, dark: Boolean, content: @Composable ColumnScope.() -> Unit) {
    val colors = LocalAppColorScheme.current
    Column {
        Text(title, color = colors.settingsText.secondary, fontSize = 13.sp, fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(start = SettingsCardHorizontalPadding, top = 8.dp, bottom = 6.dp)
                .semantics { heading() })
        SettingsCard(colors.surfaces.card, dark, content)
    }
}

/** 页面入口整行可点；右箭头表示进入页面，不表示立即执行操作。 */
@Composable
internal fun SettingsNavigationRow(title: String, onClick: () -> Unit, leading: (@Composable () -> Unit)? = null) {
    val colors = LocalAppColorScheme.current
    val interactions = remember { MutableInteractionSource() }
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).iosPressFeedback(interactions)
        .clickable(interactionSource = interactions, indication = null, role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(12.dp))
        }
        Text(title, color = colors.settingsText.primary, fontSize = 16.sp, modifier = Modifier.weight(1f))
        Icon(KeyboardArrowRightIcon, contentDescription = null, tint = colors.settingsText.secondary,
            modifier = Modifier.padding(start = 12.dp).size(20.dp))
    }
}

@Composable
internal fun SettingsRow(
    title: String,
    color: Color,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
    trailing: @Composable () -> Unit,
) {
    Row(modifier.fillMaxWidth().heightIn(min = LocalAppDimensions.current.settingsRowHeight), verticalAlignment = Alignment.CenterVertically) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(12.dp))
        }
        Text(title, color = color, fontSize = 16.sp, modifier = Modifier.weight(1f).padding(end = 12.dp))
        trailing()
    }
}

@Composable
internal fun SettingsActionRow(
    title: String,
    action: String,
    color: Color,
    buttonColor: Color,
    leading: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    // 标题表达设置项，右侧操作表达可执行动作，两者不共用文字颜色。
    SettingsRow(title, color, leading = leading) {
        SettingsButton(action, buttonColor, LocalAppColorScheme.current.controls.accent, onClick)
    }
}

@Composable
internal fun SettingsDropdownButton(text: String, contentColor: Color, onClick: () -> Unit, onMeasured: (IntSize) -> Unit, pickerLabel: String? = null, expanded: Boolean = false) {
    val pickerState = stringResource(
        R.string.control_state_with_value,
        text,
        stringResource(if (expanded) R.string.state_expanded else R.string.state_collapsed),
    )
    val modifier = Modifier
        .then(if (pickerLabel != null) Modifier.semantics {
            contentDescription = pickerLabel
            stateDescription = pickerState
        } else Modifier)
    // 值选择入口不画按钮框或底色，保留原点击区域、按压反馈与菜单锚点。
    SettingsButton(text, Color.Transparent, contentColor, onClick, modifier, showDisclosure = pickerLabel != null,
        showOutline = false,
        chromeModifier = Modifier.onGloballyPositioned { onMeasured(it.size) })
}

/** 设置页通用按钮：忙碌时禁止重复点击；带下拉提示时只负责打开选择菜单。 */
@Composable
internal fun SettingsButton(text: String, color: Color, contentColor: Color, onClick: () -> Unit, modifier: Modifier = Modifier, busy: Boolean = false, showDisclosure: Boolean = false, chromeModifier: Modifier = Modifier, showOutline: Boolean = true) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(modifier.heightIn(min = 48.dp).iosPressFeedback(interactionSource)
        .clickable(enabled = !busy, interactionSource = interactionSource, indication = null,
            role = Role.Button, onClick = onClick), contentAlignment = Alignment.Center) {
      Row(chromeModifier.then(if (showOutline) Modifier.resultActionEdgeChrome(RoundedCornerShape(10.dp)) else Modifier)
          .clip(RoundedCornerShape(10.dp)).background(color)
          .heightIn(min = 34.dp).padding(horizontal = 10.dp, vertical = 4.dp),
          verticalAlignment = Alignment.CenterVertically) {
        if (busy) {
            androidx.compose.material3.CircularProgressIndicator(Modifier.size(16.dp), color = contentColor, strokeWidth = 2.dp)
            androidx.compose.foundation.layout.Spacer(Modifier.width(6.dp))
        }
        Text(text, color = contentColor, fontSize = 16.sp, maxLines = 1,
            style = settingsButtonTextStyle(LocalTextStyle.current))
        if (showDisclosure) Icon(UnfoldMoreIcon, contentDescription = null,
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
    AnchoredDropdownMenu(dark = dark, expanded = expanded, onDismissRequest = onDismiss, shape = RoundedCornerShape(com.luckyalanzhou.barcodegenerator.ui.theme.MenuMetrics.surfaceCornerRadius), containerColor = LocalAppColorScheme.current.surfaces.panel, tonalElevation = 0.dp, shadowElevation = com.luckyalanzhou.barcodegenerator.ui.theme.MenuMetrics.surfaceShadowElevation, menuWidth = menuWidth.coerceAtLeast(110.dp), anchorWidth = anchorWidth, anchorHeight = anchorHeight, alignEndWithAnchor = true, cornerReveal = true, dynamicBackdrop = true, content = content)
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

/** 卡片内的轻量分组标题，帮助快速区分设置用途，不额外增加嵌套卡片。 */
@Composable
internal fun SettingsSubgroupLabel(text: String, color: Color, topPadding: Dp = 8.dp) {
    Text(
        text = text,
        color = color,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.fillMaxWidth().padding(top = topPadding, bottom = 2.dp),
    )
}

internal val ocrReplacementLabels = ocrCorrectionOptions.map { it.label to it.bit }
