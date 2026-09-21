package com.luckyalanzhou.barcodegenerator.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ButtonDefaults
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

/** 收藏编辑相关的下拉选择字段，统一处理锚定宽度和选项高度。 */
@Composable
internal fun ComposeChoiceField(
    value: String,
    options: List<String>,
    dark: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onSelected: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var buttonWidth by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    Box(modifier) {
        OutlinedButton(
            onClick = { if (enabled) expanded = true },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth().onGloballyPositioned { buttonWidth = it.size.width },
            shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
            border = BorderStroke(0.5.dp, LocalBarcodeThemeColors.current.buttonBorder),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = LocalBarcodeThemeColors.current.primary),
        ) {
            Text(value, color = LocalBarcodeThemeColors.current.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        AnchoredDropdownMenu(
            dark = dark,
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
            containerColor = LocalBarcodeThemeColors.current.surfaceOverlay,
            tonalElevation = 0.dp,
            shadowElevation = 1.dp,
            menuWidth = buttonWidth.takeIf { it > 0 }?.let { with(density) { it.toDp() } },
        ) {
            options.forEachIndexed { index, option ->
                if (index > 0) ComposeDropdownDivider(dark)
                DropdownMenuItem(
                    modifier = Modifier.height(40.dp),
                    text = { Text(option, color = LocalBarcodeThemeColors.current.primary) },
                    onClick = { onSelected(option); expanded = false },
                )
            }
        }
    }
}
