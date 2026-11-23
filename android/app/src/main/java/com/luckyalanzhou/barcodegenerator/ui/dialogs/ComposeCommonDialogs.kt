package com.luckyalanzhou.barcodegenerator.ui.dialogs

import com.luckyalanzhou.barcodegenerator.ui.app.*

import com.luckyalanzhou.barcodegenerator.ui.theme.*
import com.luckyalanzhou.barcodegenerator.ui.component.glass.resultActionEdgeChrome
import com.luckyalanzhou.barcodegenerator.ui.component.iosPressFeedback
import com.luckyalanzhou.barcodegenerator.ui.component.menu.MenuSurface
import com.luckyalanzhou.barcodegenerator.ui.component.glass.LocalGlassBackdropSourceWindow
import com.luckyalanzhou.barcodegenerator.ui.component.glass.LocalGlassDialogWindow


import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 所有下拉菜单统一使用锚点宽度、最大高度和滚动容器，避免超出屏幕。 */
@Composable
internal fun AnchoredDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    dark: Boolean,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(MenuMetrics.surfaceCornerRadius),
    containerColor: Color? = null,
    tonalElevation: Dp = 0.dp,
    shadowElevation: Dp = 1.dp,
    menuWidth: Dp? = null,
    anchorWidth: Dp? = null,
    anchorHeight: Dp? = null,
    alignEndWithAnchor: Boolean = false,
    cornerReveal: Boolean = false,
    clearerGlass: Boolean = false,
    dynamicBackdrop: Boolean = true,
    backdropWindow: android.view.Window? = LocalGlassDialogWindow.current,
    content: @Composable ColumnScope.() -> Unit,
) {
    val containerSize = LocalWindowInfo.current.containerSize
    val density = LocalDensity.current
    val maxHeight = with(density) { (containerSize.height * 0.62f).toDp() }.coerceAtLeast(180.dp)
    val maxWidth = with(density) { (containerSize.width.toFloat() - 24.dp.toPx()).coerceAtLeast(1f).toDp() }
    val resolvedMenuWidth = menuWidth?.coerceAtMost(maxWidth)
    val widthModifier = if (menuWidth != null) Modifier.width(resolvedMenuWidth ?: maxWidth) else Modifier.widthIn(max = maxWidth)
    val horizontalOffset = if (alignEndWithAnchor && anchorWidth != null && resolvedMenuWidth != null) anchorWidth - resolvedMenuWidth else 0.dp
    val hostView = androidx.compose.ui.platform.LocalView.current
    val appearance = popupAppearance(PopupKind.Options)
    if (cornerReveal) {
        CornerDropdownMenu(expanded, onDismissRequest,
            modifier.then(widthModifier).heightIn(max = maxHeight), shape,
            containerColor ?: LocalAppColorScheme.current.surfaces.panel,
            anchorHeight, clearerGlass, dynamicBackdrop, shadowElevation, backdropWindow, content = content)
        return
    }
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        offset = DpOffset(horizontalOffset, 0.dp),
        modifier = modifier.then(widthModifier).heightIn(max = maxHeight),
        shape = shape,
        containerColor = Color.Transparent,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        androidx.compose.runtime.DisposableEffect(hostView) {
            onDispose { hostView.postInvalidateOnAnimation() }
        }
        com.luckyalanzhou.barcodegenerator.ui.component.menu.MenuSurface(
            shape = if (appearance.glass) appearance.shape else shape,
            screenSpaceBackdrop = true,
            dynamicBackdrop = dynamicBackdrop && appearance.glass,
            clearerGlass = clearerGlass,
            color = containerColor ?: LocalAppColorScheme.current.surfaces.panel,
            obscuresBackdropContent = !appearance.glass,
            popupGlass = appearance.glass,
            waitForScreenBackdropFrame = true,
            backdropWindow = backdropWindow,
        ) { Column(content = content) }
    }
}

@Composable
@Suppress("UNUSED_PARAMETER")
internal fun ComposeGlassDialogCard(dark: Boolean, horizontalPadding: Dp = 20.dp, content: @Composable ColumnScope.() -> Unit) {
    val card = LocalAppColorScheme.current.surfaces.panel
    val appearance = popupAppearance(PopupKind.Card)
    MenuSurface(
        modifier = Modifier.widthIn(min = 280.dp, max = 400.dp),
        shape = appearance.shape,
        color = card,
        dynamicBackdrop = appearance.glass,
        screenSpaceBackdrop = true,
        obscuresBackdropContent = !appearance.glass,
        popupGlass = appearance.glass,
        backdropWindow = LocalGlassBackdropSourceWindow.current,
        trackPointer = false,
        shadowElevation = if (appearance.glass) 3.dp else 0.dp,
        waitForScreenBackdropFrame = true,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = horizontalPadding, vertical = 20.dp),
            content = content,
        )
    }
}

@Composable
internal fun DialogAction(
    text: String,
    dark: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    destructive: Boolean = false,
    enabled: Boolean = true,
    supportingText: String? = null,
) {
    val colors = LocalAppColorScheme.current
    val appearance = popupAppearance(PopupKind.Action)
    val filled = appearance.glass && primary && enabled && !destructive
    val foreground = when {
        !enabled -> colors.text.disabled
        destructive -> colors.text.destructive
        filled -> colors.text.onAccent
        primary -> colors.controls.primaryAction
        else -> colors.text.primary
    }
    val background = if (!appearance.glass) Color.Transparent
        else if (!enabled) colors.controls.disabledContainer
        else if (filled) colors.controls.primaryAction else colors.controls.button
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .widthIn(min = 48.dp)
            .iosPressFeedback(interactionSource)
            .then(if (appearance.glass) Modifier.resultActionEdgeChrome(appearance.shape) else Modifier)
            .background(background, appearance.shape)
            .clickable(enabled = enabled, interactionSource = interactionSource, indication = null, role = Role.Button, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text, color = foreground, fontSize = 15.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                style = LocalTextStyle.current.copy(background = Color.Transparent))
            if (supportingText != null) {
                Text(
                    supportingText,
                    color = if (enabled) colors.text.secondary else colors.text.disabled,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

@Composable
internal fun ComposeDropdownDivider(dark: Boolean) {
    val colors = LocalAppColorScheme.current
    val separator = com.luckyalanzhou.barcodegenerator.ui.theme.actionMenuColors(
        colors, dark, LocalVisualEffectsPolicy.current.highContrast,
    ).separator
    com.luckyalanzhou.barcodegenerator.ui.component.menu.MenuSeparator(separator)
}
