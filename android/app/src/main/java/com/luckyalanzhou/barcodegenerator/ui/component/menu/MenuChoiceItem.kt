package com.luckyalanzhou.barcodegenerator.ui.component.menu

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import com.luckyalanzhou.barcodegenerator.ui.theme.LocalAppColorScheme
import com.luckyalanzhou.barcodegenerator.ui.theme.LocalVisualEffectsPolicy
import com.luckyalanzhou.barcodegenerator.ui.theme.MenuMetrics
import com.luckyalanzhou.barcodegenerator.ui.theme.actionMenuColors

/** 值选项共用显示：勾号表示保存状态，底色只表示当前按压或悬停，不参与业务状态更新。 */
@Composable
internal fun MenuChoiceItem(
    label: String,
    checked: Boolean,
    multiple: Boolean = false,
    highlightSelection: Boolean = false,
    trailingCheckmark: Boolean = false,
    labelMaxLines: Int = 2,
    onClick: () -> Unit,
) {
    val colors = LocalAppColorScheme.current
    val labelColor = if (checked && trailingCheckmark) colors.text.link else colors.text.primary
    val density = LocalDensity.current
    val iconSize = menuOptionIconSize(density)
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    val hovered by interactions.collectIsHoveredAsState()
    val policy = LocalVisualEffectsPolicy.current
    val feedback = actionMenuColors(
        colors,
        dark = colors.surfaces.background.luminance() < .35f,
        highContrast = policy.highContrast,
    ).selection
    val checkmark: @Composable () -> Unit = {
        Box(Modifier.width(iconSize).clearAndSetSemantics { }, contentAlignment = Alignment.Center) {
            if (checked) Canvas(Modifier.size(iconSize).clearAndSetSemantics { }) {
                // 保留与正文相差 4dp 的布局尺寸，只将勾号墨迹收至 84%，平衡旁边的线性图标。
                val checkmarkGlyphScale = .84f
                val glyphWidth = size.width * checkmarkGlyphScale
                val glyphHeight = size.height * checkmarkGlyphScale
                val glyphLeft = (size.width - glyphWidth) / 2f
                val glyphTop = (size.height - glyphHeight) / 2f
                val strokeWidth = size.minDimension * checkmarkGlyphScale * .13f
                val joint = Offset(glyphLeft + glyphWidth * .42f, glyphTop + glyphHeight * .72f)
                drawLine(labelColor, Offset(glyphLeft + glyphWidth * .16f, glyphTop + glyphHeight * .50f), joint,
                    strokeWidth, cap = StrokeCap.Round)
                drawLine(labelColor, joint, Offset(glyphLeft + glyphWidth * .86f, glyphTop + glyphHeight * .24f),
                    strokeWidth, cap = StrokeCap.Round)
            }
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth()
            .hoverable(interactions)
            .background(if (pressed || hovered) feedback else if (highlightSelection && checked) colors.controls.selectedContainer else Color.Transparent)
            .clickable(interactionSource = interactions, indication = null,
                role = if (multiple) Role.Checkbox else Role.RadioButton, onClick = onClick)
            .semantics {
                role = if (multiple) Role.Checkbox else Role.RadioButton
                if (multiple) toggleableState = if (checked) ToggleableState.On else ToggleableState.Off
                else selected = checked
            }.padding(horizontal = MenuMetrics.choiceHorizontalPadding, vertical = menuOptionVerticalPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!trailingCheckmark) {
            checkmark()
            Spacer(Modifier.width(MenuMetrics.choiceMarkGap))
        }
        Text(label, color = labelColor, fontSize = MenuMetrics.optionTextSize, lineHeight = menuOptionLineHeight,
            style = MenuLineStyle, maxLines = labelMaxLines, modifier = Modifier.weight(1f))
        if (trailingCheckmark) {
            Spacer(Modifier.width(MenuMetrics.choiceMarkGap))
            checkmark()
        }
    }
}
