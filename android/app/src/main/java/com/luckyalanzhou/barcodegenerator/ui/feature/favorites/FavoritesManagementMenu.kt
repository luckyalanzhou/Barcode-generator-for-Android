package com.luckyalanzhou.barcodegenerator.ui.feature.favorites

import androidx.compose.ui.res.stringResource
import com.luckyalanzhou.barcodegenerator.R
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.luckyalanzhou.barcodegenerator.ui.component.menu.MenuLineStyle
import com.luckyalanzhou.barcodegenerator.ui.component.menu.menuOptionLineHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import com.luckyalanzhou.barcodegenerator.icons.CloudDownloadIcon
import com.luckyalanzhou.barcodegenerator.icons.CloudUploadIcon
import com.luckyalanzhou.barcodegenerator.ui.dialogs.AnchoredDropdownMenu
import com.luckyalanzhou.barcodegenerator.ui.dialogs.ComposeDropdownDivider
import com.luckyalanzhou.barcodegenerator.ui.theme.LocalAppColorScheme
import com.luckyalanzhou.barcodegenerator.ui.theme.MenuMetrics

@Composable
internal fun FavoritesManagementMenu(
    dark: Boolean,
    onImport: () -> Unit,
    onExport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    var anchorHeight by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val colors = LocalAppColorScheme.current
    Box(modifier) {
        TextButton(
            onClick = {
                focus.clearFocus()
                keyboard?.hide()
                expanded = true
            },
            modifier = Modifier.heightIn(min = 48.dp).onSizeChanged { anchorHeight = it.height },
        ) {
            Text(stringResource(R.string.action_manage), color = colors.text.secondary)
        }
        AnchoredDropdownMenu(
            dark = dark,
            expanded = expanded,
            onDismissRequest = { expanded = false },
            menuWidth = 176.dp,
            anchorHeight = with(density) { anchorHeight.toDp() },
            cornerReveal = true,
        ) {
            DropdownMenuItem(
                modifier = Modifier.heightIn(min = MenuMetrics.minimumActionItemHeight),
                text = { Text(stringResource(R.string.favorite_import), color = colors.text.primary, fontSize = MenuMetrics.optionTextSize, lineHeight = menuOptionLineHeight, style = MenuLineStyle) },
                trailingIcon = { Icon(CloudDownloadIcon, contentDescription = null, tint = colors.text.primary) },
                onClick = { expanded = false; onImport() },
            )
            ComposeDropdownDivider(dark)
            DropdownMenuItem(
                modifier = Modifier.heightIn(min = MenuMetrics.minimumActionItemHeight),
                text = { Text(stringResource(R.string.favorite_export), color = colors.text.primary, fontSize = MenuMetrics.optionTextSize, lineHeight = menuOptionLineHeight, style = MenuLineStyle) },
                trailingIcon = { Icon(CloudUploadIcon, contentDescription = null, tint = colors.text.primary) },
                onClick = { expanded = false; onExport() },
            )
        }
    }
}
