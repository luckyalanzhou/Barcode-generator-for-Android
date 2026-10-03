package com.luckyalanzhou.barcodegenerator.ui.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.luckyalanzhou.barcodegenerator.domain.StyleSettings
import com.luckyalanzhou.barcodegenerator.ui.theme.LocalAppColorScheme
import com.luckyalanzhou.barcodegenerator.ui.theme.LocalVisualEffectsPolicy

/** Compact secondary settings; the heading and close action stay visible as options scroll. */
@Composable
internal fun DisplayEffectsSettingsDialog(
    style: StyleSettings,
    dark: Boolean,
    onStyleChange: (StyleSettings) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalAppColorScheme.current
    val state = displayEffectsSettingsState(style, LocalVisualEffectsPolicy.current)
    val maxHeight = with(LocalDensity.current) { (LocalWindowInfo.current.containerSize.height * .85f).toDp() }
    val scroll = rememberScrollState()
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.widthIn(max = 400.dp).fillMaxWidth().heightIn(max = maxHeight)
                .semantics { paneTitle = "显示与动效" },
            shape = RoundedCornerShape(20.dp),
            color = colors.surfaces.surface,
        ) {
            Column(Modifier.padding(horizontal = 18.dp, vertical = 12.dp)) {
                Text("显示与动效", color = colors.text.primary, fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(vertical = 4.dp).semantics { heading() })
                Text("更改即时保存，仅影响导航与菜单效果。", color = colors.text.secondary,
                    fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp, bottom = 10.dp))
                Column(Modifier.weight(1f, fill = false).verticalScroll(scroll,
                    enabled = scroll.canScrollForward || scroll.canScrollBackward)) {
                    DisplayEffectOptionRow("减少动态效果", state.motion, dark) {
                        onStyleChange(style.copy(reduceMotion = it))
                    }
                    HorizontalDivider(color = colors.borders.divider)
                    DisplayEffectOptionRow("使用不透明导航与菜单", state.transparency, dark) {
                        onStyleChange(style.copy(reduceTransparency = it))
                    }
                    HorizontalDivider(color = colors.borders.divider)
                    DisplayEffectOptionRow("提高导航与菜单对比度", state.contrast, dark) {
                        onStyleChange(style.copy(enhanceContrast = it))
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) {
                        Text("完成", color = colors.controls.accent)
                    }
                }
            }
        }
    }
}

@Composable
private fun DisplayEffectOptionRow(
    title: String,
    state: DisplayEffectOptionState,
    dark: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val colors = LocalAppColorScheme.current
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .toggleable(value = state.checked, enabled = state.enabled, role = Role.Switch,
                onValueChange = onCheckedChange)
            .padding(vertical = 10.dp)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, color = colors.text.primary, fontSize = 16.sp)
            Text(state.explanation, color = colors.text.secondary, fontSize = 13.sp,
                modifier = Modifier.padding(top = 4.dp))
        }
        // The complete row is the single 48dp+ touch/accessibility target.
        SettingsToggle(checked = state.checked, dark = dark, enabled = false,
            modifier = Modifier.clearAndSetSemantics {}, onCheckedChange = {})
    }
}
