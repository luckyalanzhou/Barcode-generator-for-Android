package com.luckyalanzhou.barcodegenerator.ui.feature.editor

import com.luckyalanzhou.barcodegenerator.ui.component.iosPressFeedback
import com.luckyalanzhou.barcodegenerator.icons.UnfoldMoreIcon
import androidx.compose.foundation.interaction.MutableInteractionSource
import com.luckyalanzhou.barcodegenerator.ui.component.glass.resultActionEdgeChrome
import com.luckyalanzhou.barcodegenerator.ui.component.SingleChoiceMenuItem
import com.luckyalanzhou.barcodegenerator.ui.component.menu.MenuChoiceItem
import com.luckyalanzhou.barcodegenerator.ui.component.menu.MenuSeparator
import com.luckyalanzhou.barcodegenerator.ui.theme.LocalVisualEffectsPolicy
import com.luckyalanzhou.barcodegenerator.ui.component.glass.LocalGlassDialogWindow

import com.luckyalanzhou.barcodegenerator.ui.dialogs.AnchoredDropdownMenu
import com.luckyalanzhou.barcodegenerator.ui.dialogs.ComposeDropdownDivider
import com.luckyalanzhou.barcodegenerator.ui.dialogs.CornerDropdownMenu
import com.luckyalanzhou.barcodegenerator.ui.theme.LocalAppColorScheme
import com.luckyalanzhou.barcodegenerator.ui.theme.MenuMetrics
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun ComposeChoiceField(
    value: String,
    options: List<String>,
    dark: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    compact: Boolean = false,
    scrollableFolderMenu: Boolean = false,
    fieldLabel: String? = null,
    optionLabel: (String) -> String = { it },
    onValueOverflowChanged: ((Boolean) -> Unit)? = null,
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
                .then(if (compact) Modifier else Modifier.resultActionEdgeChrome(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
            border = null,
            contentPadding = if (compact || fieldLabel != null) PaddingValues(horizontal = 12.dp, vertical = 8.dp) else ButtonDefaults.ContentPadding,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = if (compact) LocalAppColorScheme.current.text.secondary else LocalAppColorScheme.current.text.primary,
                containerColor = androidx.compose.ui.graphics.Color.Transparent),
        ) {
            val colors = LocalAppColorScheme.current
            if (fieldLabel == null) {
                // Value-only choice fields are clickable too; keep the disclosure cue on compact and full-width selectors.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        optionLabel(value),
                        color = if (compact) colors.text.secondary else colors.text.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Icon(
                        UnfoldMoreIcon,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = if (enabled) colors.text.secondary else colors.text.disabled,
                    )
                }
            } else {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 32.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(fieldLabel, Modifier.weight(1f), color = colors.text.primary, fontSize = 14.sp, lineHeight = 18.sp)
                    Text(
                        optionLabel(value),
                        Modifier.weight(1.25f),
                        color = if (value in options) colors.text.primary else colors.text.secondary,
                        fontSize = 14.sp,
                        lineHeight = 18.sp,
                        textAlign = TextAlign.End,
                        maxLines = if (onValueOverflowChanged != null) 1 else 2,
                        overflow = TextOverflow.Ellipsis,
                        onTextLayout = { onValueOverflowChanged?.invoke(it.hasVisualOverflow) },
                    )
                    Icon(UnfoldMoreIcon, null, Modifier.size(16.dp),
                        tint = if (enabled) colors.text.secondary else colors.text.disabled)
                }
            }
        }
        if (scrollableFolderMenu) {
            val windowHeight = LocalWindowInfo.current.containerSize.height
            val maxHeight = with(density) { (windowHeight * .42f).toDp() }.coerceIn(96.dp, 320.dp)
            val width = with(density) { buttonWidth.toDp() }
            CornerDropdownMenu(
                expanded = expanded,
                onDismiss = { expanded = false },
                modifier = Modifier.width(width),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(MenuMetrics.surfaceCornerRadius),
                color = LocalAppColorScheme.current.surfaces.panel,
                anchorHeight = with(density) { buttonHeight.toDp() },
                clearerGlass = false,
                dynamicBackdrop = true,
                shadowElevation = MenuMetrics.surfaceShadowElevation,
                backdropWindow = LocalGlassDialogWindow.current,
                scrollableContent = {
                    val selectedIndex = options.indexOf(value).coerceAtLeast(0)
                    val listState = rememberLazyListState(initialFirstVisibleItemIndex = selectedIndex)
                    val colors = LocalAppColorScheme.current
                    val indicatorColor = colors.text.secondary.copy(alpha = .65f)
                    val separatorColor = colors.text.primary.copy(
                        alpha = if (LocalVisualEffectsPolicy.current.highContrast) .40f else .18f,
                    )
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = maxHeight).drawWithContent {
                            drawContent()
                            val indicator = listState.scrollIndicatorState ?: return@drawWithContent
                            val extent = indicator.contentSize
                            val viewport = indicator.viewportSize
                            if (extent <= viewport || viewport <= 0 ||
                                extent == Int.MAX_VALUE || indicator.scrollOffset == Int.MAX_VALUE) return@drawWithContent
                            val track = size.height - 12.dp.toPx()
                            if (track <= 0f) return@drawWithContent
                            val thumb = (track * viewport / extent).coerceIn(minOf(20.dp.toPx(), track), track)
                            val fraction = (indicator.scrollOffset.toFloat() / (extent - viewport)).coerceIn(0f, 1f)
                            val thickness = 3.dp.toPx()
                            val inset = 3.dp.toPx()
                            val x = if (layoutDirection == LayoutDirection.Ltr) size.width - inset - thickness else inset
                            drawRoundRect(indicatorColor, Offset(x, 6.dp.toPx() + fraction * (track - thumb)),
                                Size(thickness, thumb), CornerRadius(thickness / 2f))
                        },
                        state = listState,
                        contentPadding = PaddingValues(vertical = 8.dp),
                    ) {
                        itemsIndexed(options, key = { _, option -> option }) { index, option ->
                            Column {
                                if (index > 0) MenuSeparator(separatorColor)
                                MenuChoiceItem(
                                    optionLabel(option), option == value, trailingCheckmark = true,
                                    labelMaxLines = if (onValueOverflowChanged != null) Int.MAX_VALUE else 2,
                                ) {
                                    onSelected(option)
                                    expanded = false
                                }
                            }
                        }
                    }
                },
            ) {}
        } else AnchoredDropdownMenu(
            dark = dark,
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = androidx.compose.foundation.shape.RoundedCornerShape(MenuMetrics.surfaceCornerRadius),
            containerColor = LocalAppColorScheme.current.surfaces.panel,
            tonalElevation = 0.dp,
            shadowElevation = MenuMetrics.surfaceShadowElevation,
            // compact 是编辑条码格式入口；文件夹选择保持现有展开方式。
            cornerReveal = compact,
            alignEndWithAnchor = compact,
            dynamicBackdrop = true,
            backdropWindow = LocalGlassDialogWindow.current,
            anchorHeight = with(density) { buttonHeight.toDp() },
            menuWidth = buttonWidth.takeIf { it > 0 }?.let {
                val measured = with(density) { it.toDp() }
                if (compact) measured.coerceAtLeast(160.dp) else measured
            },
        ) {
            options.forEachIndexed { index, option ->
                if (index > 0) ComposeDropdownDivider(dark)
                if (compact) SingleChoiceMenuItem(optionLabel(option), option == value, highlightSelection = false) {
                    onSelected(option); expanded = false
                } else SingleChoiceMenuItem(optionLabel(option), option == value, highlightSelection = false) {
                    onSelected(option); expanded = false
                }
            }
        }
    }
}
