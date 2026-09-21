package com.luckyalanzhou.barcodegenerator.ui

import com.luckyalanzhou.barcodegenerator.domain.ocrCorrectionOptions

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal data class SettingsColors(
    val primary: Color,
    val secondary: Color,
    val card: Color,
    val button: Color,
    val accent: Color,
)

@Composable
internal fun rememberSettingsColors(dark: Boolean): SettingsColors {
    val themeColors = LocalBarcodeThemeColors.current
    return SettingsColors(
        // Apple 设置使用系统语义文字色：主文字接近纯黑/纯白，次文字使用带透明度的灰白色。
        primary = if (dark) Color.White else Color.Black,
        secondary = if (dark) Color(0xffebebf5).copy(alpha = .60f) else Color(0xff3c3c43).copy(alpha = .60f),
        // 分组设置卡片：浅色为白色，深色为 systemGroupedBackground 的次级表面色。
        card = if (dark) Color(0xff1c1c1e) else Color.White,
        button = if (dark) Color(0xff2c2c2e) else Color(0xfff2f2f7),
        accent = themeColors.accent,
    )
}

@Composable
internal fun SettingsCard(color: Color, dark: Boolean, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().globalCardSurface(dark, color, RoundedCornerShape(16.dp), 2.dp).padding(horizontal = 15.dp, vertical = 4.dp), content = content)
}

@Composable
internal fun SettingsRow(title: String, color: Color, trailing: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().height(50.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = color, fontSize = 16.sp, modifier = Modifier.weight(1f))
        trailing()
    }
}

@Composable
internal fun SettingsActionRow(title: String, action: String, color: Color, buttonColor: Color, onClick: () -> Unit) {
    SettingsRow(title, color) { SettingsButton(action, buttonColor, color, onClick) }
}

@Composable
internal fun SettingsDropdownButton(text: String, color: Color, contentColor: Color, onClick: () -> Unit, onMeasured: (Int) -> Unit) {
    SettingsButton(text, color, contentColor, onClick, Modifier.onGloballyPositioned { onMeasured(it.size.width) })
}

@Composable
internal fun SettingsButton(text: String, color: Color, contentColor: Color, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(onClick = onClick, colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = contentColor), shape = RoundedCornerShape(14.dp), contentPadding = PaddingValues(horizontal = 14.dp), modifier = modifier.globalButtonChrome(RoundedCornerShape(14.dp), 1.dp).height(40.dp)) {
        Text(text, maxLines = 1, style = LocalTextStyle.current.copy(background = Color.Transparent))
    }
}

@Composable
internal fun SettingsSmallButton(text: String, color: Color, buttonColor: Color, onClick: () -> Unit) {
    SettingsButton(text, buttonColor, color, onClick)
}

@Composable
internal fun SettingsDropdown(dark: Boolean, expanded: Boolean, menuWidth: Dp, anchorWidth: Dp?, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    AnchoredDropdownMenu(dark = dark, expanded = expanded, onDismissRequest = onDismiss, shape = RoundedCornerShape(16.dp), containerColor = LocalBarcodeThemeColors.current.surfaceOverlay, tonalElevation = 0.dp, shadowElevation = 1.dp, menuWidth = menuWidth.coerceAtLeast(110.dp), anchorWidth = anchorWidth, alignEndWithAnchor = true, content = content)
}

@Composable
internal fun SettingsDivider(dark: Boolean) {
    Spacer(Modifier.fillMaxWidth().height(if (dark) 0.5.dp else 1.dp).background(LocalBarcodeThemeColors.current.divider))
}

@Composable
internal fun SettingsToggle(checked: Boolean, dark: Boolean, modifier: Modifier = Modifier, onCheckedChange: (Boolean) -> Unit) {
    val themeColors = LocalBarcodeThemeColors.current
    val trackColor by animateColorAsState(if (checked) themeColors.toggleOn else themeColors.toggleOff, spring(stiffness = 700f), label = "settings-toggle-track")
    val thumbOffset by animateDpAsState(if (checked) 20.dp else 0.dp, spring(dampingRatio = 0.72f, stiffness = 700f), label = "settings-toggle-thumb")
    Box(modifier.width(52.dp).height(32.dp).clip(RoundedCornerShape(16.dp)).background(trackColor).clickable { onCheckedChange(!checked) }.padding(2.dp), contentAlignment = Alignment.CenterStart) {
        Box(Modifier.offset { IntOffset(thumbOffset.roundToPx(), 0) }.size(28.dp).shadow(1.dp, CircleShape).clip(CircleShape).background(themeColors.thumb))
    }
}

internal val ocrReplacementLabels = ocrCorrectionOptions.map { it.label to it.bit }
