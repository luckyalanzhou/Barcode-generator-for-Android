package com.luckyalanzhou.barcodegenerator.ui.feature.editor

import com.luckyalanzhou.barcodegenerator.ui.component.iosPressFeedback
import androidx.compose.foundation.interaction.MutableInteractionSource
import com.luckyalanzhou.barcodegenerator.ui.component.globalButtonChrome
import com.luckyalanzhou.barcodegenerator.ui.component.SingleChoiceMenuItem

import com.luckyalanzhou.barcodegenerator.ui.dialogs.AnchoredDropdownMenu
import com.luckyalanzhou.barcodegenerator.ui.dialogs.ComposeDropdownDivider
import com.luckyalanzhou.barcodegenerator.ui.theme.LocalAppColorScheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
internal fun ComposeChoiceField(
    value: String,
    options: List<String>,
    dark: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    compact: Boolean = false,
    onSelected: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var buttonWidth by remember { mutableIntStateOf(0) }
    var buttonHeight by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val interactions = remember { MutableInteractionSource() }
    Box(modifier) {
        OutlinedButton(
            onClick = { if (enabled) expanded = true },
            enabled = enabled,
            interactionSource = interactions,
            modifier = (if (compact) Modifier.wrapContentWidth() else Modifier.fillMaxWidth())
                .iosPressFeedback(interactions)
                .onGloballyPositioned { buttonWidth = it.size.width; buttonHeight = it.size.height }
                .globalButtonChrome(androidx.compose.foundation.shape.RoundedCornerShape(8.dp)),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
            border = null,
            contentPadding = if (compact) PaddingValues(horizontal = 12.dp, vertical = 8.dp) else ButtonDefaults.ContentPadding,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = LocalAppColorScheme.current.text.primary,
                containerColor = if (compact) LocalAppColorScheme.current.controls.button else androidx.compose.ui.graphics.Color.Transparent),
        ) {
            Text(value, color = LocalAppColorScheme.current.text.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        AnchoredDropdownMenu(
            dark = dark,
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
            containerColor = LocalAppColorScheme.current.surfaces.overlay,
            tonalElevation = 0.dp,
            shadowElevation = 1.dp,
            // compact 是编辑条码格式入口；文件夹选择保持现有展开方式。
            cornerReveal = compact,
            alignEndWithAnchor = compact,
            anchorHeight = with(density) { buttonHeight.toDp() },
            menuWidth = buttonWidth.takeIf { it > 0 }?.let {
                val measured = with(density) { it.toDp() }
                if (compact) measured.coerceAtLeast(160.dp) else measured
            },
        ) {
            options.forEachIndexed { index, option ->
                if (index > 0) ComposeDropdownDivider(dark)
                if (compact) SingleChoiceMenuItem(option, option == value, highlightSelection = false) {
                    onSelected(option); expanded = false
                } else DropdownMenuItem(
                    modifier = Modifier.height(40.dp),
                    text = { Text(option, color = LocalAppColorScheme.current.text.primary) },
                    onClick = { onSelected(option); expanded = false },
                )
            }
        }
    }
}
