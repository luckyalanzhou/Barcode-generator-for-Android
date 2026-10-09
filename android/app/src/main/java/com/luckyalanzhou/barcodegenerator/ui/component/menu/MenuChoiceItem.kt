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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luckyalanzhou.barcodegenerator.ui.theme.LocalAppColorScheme
import com.luckyalanzhou.barcodegenerator.ui.theme.LocalVisualEffectsPolicy

/** 值选项共用显示：勾号表示保存状态，底色只表示当前按压或悬停，不参与业务状态更新。 */
@Composable
internal fun MenuChoiceItem(label: String, checked: Boolean, multiple: Boolean = false, highlightSelection: Boolean = false, onClick: () -> Unit) {
    val colors = LocalAppColorScheme.current
    val density = LocalDensity.current
    val iconSize = menuOptionIconSize(density)
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    val hovered by interactions.collectIsHoveredAsState()
    val feedback = colors.controls.accent.copy(alpha = if (LocalVisualEffectsPolicy.current.highContrast) .20f else .10f)
    // 16sp 正文、20sp 行高，上下各 8dp；不限制最小高度，大字体和换行自然增高。
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
            }.padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(iconSize).clearAndSetSemantics { }, contentAlignment = Alignment.Center) {
            // 勾号放在文字前，使用同色笔画；未选中也保留标记列，避免文字跳动。
            if (checked) Canvas(Modifier.size(iconSize).clearAndSetSemantics { }) {
                val strokeWidth = size.minDimension * .12f
                val joint = Offset(size.width * .42f, size.height * .72f)
                drawLine(colors.text.primary, Offset(size.width * .16f, size.height * .50f), joint,
                    strokeWidth, cap = StrokeCap.Round)
                drawLine(colors.text.primary, joint, Offset(size.width * .86f, size.height * .24f),
                    strokeWidth, cap = StrokeCap.Round)
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(label, color = colors.text.primary, fontSize = 16.sp, lineHeight = 20.sp,
            style = MenuLineStyle, maxLines = 2, modifier = Modifier.weight(1f))
    }
}
